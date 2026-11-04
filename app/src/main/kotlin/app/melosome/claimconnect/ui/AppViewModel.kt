package app.melosome.claimconnect.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import app.melosome.claimconnect.BuildConfig
import app.melosome.claimconnect.ClaimConnectApplication
import app.melosome.claimconnect.ai.AcceptedNote
import app.melosome.claimconnect.ai.AiClient
import app.melosome.claimconnect.ai.ApiKeyStore
import app.melosome.claimconnect.ai.BillingManager
import app.melosome.claimconnect.ai.AiOutcome
import app.melosome.claimconnect.ai.AiPhoto
import app.melosome.claimconnect.ai.AiSuggestion
import app.melosome.claimconnect.ai.DisplayCopies
import app.melosome.claimconnect.ai.EntitlementClient
import app.melosome.claimconnect.ai.InstallId
import app.melosome.claimconnect.ai.MetaApiClient
import app.melosome.claimconnect.ai.ProxyAiClient
import app.melosome.claimconnect.ai.SimulatedAiClient
import app.melosome.claimconnect.data.AiShareSession
import app.melosome.claimconnect.evidence.ImageTools
import app.melosome.claimconnect.model.*

data class AppState(
    val step: Int = 0,
    val incidents: List<IncidentDetail> = emptyList(),
    val current: IncidentDetail? = null,
    val settings: DriverSettings = DriverSettings(),
    val busy: Boolean = true,
    val pendingWrites: Int = 0,
    val saveFailed: Boolean = false,
    val storageBytes: Long = 0,
    val error: String? = null,
    val message: String? = null,
    val ai: AiUiState = AiUiState(),
    /** Last 4 chars of the user's own API key, or null when none is saved. The full key is never held in state. */
    val aiKeyLast4: String? = null,
)

/** Which analysis backend serves the AI check: our proxy, the user's own key (BYOK v1), or the offline simulator. */
enum class AiBackend { PROXY, BYOK, SIMULATED }

/** In-memory AI check state for the open incident. Cleared on generate; never persisted. */
data class AiUiState(
    val checking: Boolean = false,
    val suggestions: List<AiSuggestion> = emptyList(),
    val accepted: List<AcceptedNote> = emptyList(),
    val stale: Boolean = false,
    val quotaRemaining: Int? = null,
    val quotaAllowance: Int? = null,
    val entitled: Boolean = false,
    val sentEvidenceIds: List<String> = emptyList(),
    val purchasing: Boolean = false,
    val productPrice: String? = null,
)

class AppViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as ClaimConnectApplication).repository
    private val apiKeyStore: ApiKeyStore by lazy { ApiKeyStore.default(getApplication()) }
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init {
        viewModelScope.launch {
            for (command in commands) {
                try { command() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.update { it.copy(error = readableError(e)) } }
            }
        }
        enqueue {
            try {
                repository.recover()
                val settings = repository.settings()
                val keyLast4 = withContext(Dispatchers.IO) {
                    runCatching { apiKeyStore.load() }.getOrNull()?.let(ApiKeyStore::last4)?.takeIf { it.isNotEmpty() }
                }
                mutable.update { it.copy(settings = settings, storageBytes = repository.storageBytes(), aiKeyLast4 = keyLast4) }
                val selected = savedState.get<String>("incidentId")
                if (selected != null) {
                    val detail = repository.detail(selected)
                    mutable.update { it.copy(current = detail, step = savedState["step"] ?: 0) }
                }
            } finally { mutable.update { it.copy(busy = false) } }
        }
        viewModelScope.launch {
            try { repository.incidents.collect { records -> mutable.update { it.copy(incidents = records) } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(error = "Some records couldn't be opened. Don't uninstall the app or clear its data. Seek technical advice if available.") } }
        }
    }
    private fun enqueue(block: suspend () -> Unit) { commands.trySend(block) }
    private fun operation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        if (mutable.value.saveFailed) { reportError("Save failed. Tap Retry save to continue."); return }
        mutable.update { it.copy(busy = true, error = null, message = null) }
        enqueue {
            try {
                if (mutable.value.saveFailed) { reportError("Save failed. Tap Retry save to continue.") }
                else block()
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    /** Guards leaving the open incident editor: navigation (back button, up arrow, bottom nav)
     * should not be able to abandon unsaved changes or interrupt an in-flight operation. The
     * navigation destination itself is owned by the NavController, not this ViewModel. */
    fun canLeaveEditor(): Boolean {
        if (mutable.value.busy) return false
        // Keep the editor open until autosave finishes so navigation cannot cancel a pending write.
        if (mutable.value.pendingWrites > 0) return false
        if (mutable.value.saveFailed) { reportError("Save failed. Tap Retry save before leaving this incident."); return false }
        return true
    }
    fun createIncident(onCreated: () -> Unit = {}) = operation {
        val detail = repository.create()
        selected(detail, 0)
        onCreated()
    }
    fun openIncident(id: String, onOpened: () -> Unit = {}) = operation { selected(repository.detail(id), 4); onOpened() }
    private fun selected(detail: IncidentDetail, step: Int) {
        savedState["incidentId"] = detail.incident.id; savedState["step"] = step
        mutable.update { it.copy(current = detail, step = step) }
    }
    fun step(step: Int) {
        savedState["step"] = step
        mutable.update { it.copy(step = step, error = null, message = null) }
    }
    fun edit(transform: (Incident) -> Incident) {
        if (mutable.value.busy) return
        val detail = mutable.value.current ?: return
        val next = transform(detail.incident).copy(revision = detail.incident.revision + 1, updatedAt = System.currentTimeMillis())
        mutable.update { it.copy(current = detail.copy(incident = next), pendingWrites = it.pendingWrites + 1, error = null) }
        markAiStale()
        enqueue {
            try { repository.save(next); mutable.update { it.copy(saveFailed = false) } }
            catch (e: Exception) { mutable.update { it.copy(saveFailed = true) }; throw e }
            finally { mutable.update { it.copy(pendingWrites = (it.pendingWrites - 1).coerceAtLeast(0)) } }
        }
    }
    fun retrySave() {
        val incident = mutable.value.current?.incident ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        enqueue {
            try { repository.save(incident); mutable.update { it.copy(saveFailed = false, error = null) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun importPhotos(uris: List<Uri>, source: String, captureId: String? = null, onComplete: (Boolean) -> Unit = {}) {
        val id = mutable.value.current?.incident?.id ?: return
        operation {
            var complete = false
            try {
                for (uri in uris) refresh(repository.addPhoto(id, uri, source, captureId))
                mutable.update { it.copy(storageBytes = repository.storageBytes()) }
                markAiStale()
                complete = true
            } finally { onComplete(complete) }
        }
    }
    fun caption(evidenceId: String, text: String) {
        val id = mutable.value.current?.incident?.id ?: return
        operation { refresh(repository.caption(id, evidenceId, text)); markAiStale() }
    }
    /** Stores a generated map snapshot PNG as evidence with its auto-caption. Counts toward the photo limit. */
    fun importMapSnapshot(png: ByteArray, source: String, caption: String) {
        val id = mutable.value.current?.incident?.id ?: return
        operation {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "maps/$id").apply { mkdirs() }
            val file = File(dir, "${java.util.UUID.randomUUID()}.png")
            try {
                file.writeBytes(png)
                val before = mutable.value.current?.evidence.orEmpty().map { it.id }.toSet()
                val detail = repository.addPhoto(id,
                    androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.files", file), source)
                refresh(detail)
                detail.evidence.firstOrNull { it.id !in before }?.let { refresh(repository.caption(id, it.id, caption)) }
                mutable.update { it.copy(storageBytes = repository.storageBytes()) }
                markAiStale()
            } finally { file.delete() }
        }
    }
    fun removePhoto(evidenceId: String) {
        val id = mutable.value.current?.incident?.id ?: return
        operation { refresh(repository.removePhoto(id, evidenceId)); mutable.update { it.copy(storageBytes = repository.storageBytes()) }; markAiStale() }
    }
    fun generate() {
        val id = mutable.value.current?.incident?.id ?: return
        operation {
            val ai = mutable.value.ai
            val notes = if (ai.stale) emptyList() else ai.accepted
            refresh(repository.generate(id, notes))
            mutable.update {
                it.copy(step = 5, storageBytes = repository.storageBytes(),
                    ai = AiUiState(quotaRemaining = it.ai.quotaRemaining, entitled = it.ai.entitled),
                    message = if (ai.stale && ai.accepted.isNotEmpty())
                        "Edits invalidated the AI notes — left out of this pack." else it.message)
            }
            savedState["step"] = 5
        }
    }
    // ---- AI check (accept/edit/discard; notes bake into the next generated pack) ----
    private var analysisJob: kotlinx.coroutines.Job? = null
    fun aiBackend(): AiBackend = when {
        BuildConfig.AI_PROXY_URL.isNotBlank() -> AiBackend.PROXY
        mutable.value.aiKeyLast4 != null -> AiBackend.BYOK
        else -> AiBackend.SIMULATED
    }
    fun isAiSimulated(): Boolean = aiBackend() == AiBackend.SIMULATED
    private fun aiClient(): AiClient {
        val url = BuildConfig.AI_PROXY_URL
        if (url.isNotBlank()) return ProxyAiClient(url, InstallId.get(getApplication()))
        if (mutable.value.aiKeyLast4 != null) {
            return MetaApiClient(BuildConfig.AI_API_BASE_URL,
                { runCatching { apiKeyStore.load() }.getOrNull() }, BuildConfig.AI_MODEL)
        }
        val scenario = runCatching { SimulatedAiClient.Scenario.valueOf(BuildConfig.AI_SIMULATE_SCENARIO) }
            .getOrDefault(SimulatedAiClient.Scenario.SUPPORTED)
        return SimulatedAiClient(scenario)
    }
    fun saveAiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) { reportError("Paste your API key first."); return }
        if (trimmed.length > ApiKeyStore.MAX_KEY_LENGTH) { reportError("That key looks too long to be valid."); return }
        operation {
            withContext(Dispatchers.IO) { apiKeyStore.save(trimmed) }
            mutable.update { it.copy(aiKeyLast4 = ApiKeyStore.last4(trimmed), message = "API key saved.") }
        }
    }
    fun clearAiKey() = operation {
        withContext(Dispatchers.IO) { apiKeyStore.clear() }
        mutable.update { it.copy(aiKeyLast4 = null, message = "API key removed.") }
    }
    fun refreshAiQuota() {
        val client = aiClient()
        if (client !is ProxyAiClient) return
        viewModelScope.launch {
            when (val quota = client.quota()) {
                is ProxyAiClient.Quota.Known -> mutable.update {
                    it.copy(ai = it.ai.copy(quotaRemaining = quota.remaining,
                        quotaAllowance = quota.allowance, entitled = quota.entitled))
                }
                ProxyAiClient.Quota.Unknown -> Unit
            }
        }
    }
    fun analyzePhotos(evidenceIds: List<String>) {
        val detail = mutable.value.current ?: return
        if (mutable.value.busy || mutable.value.ai.checking) return
        analysisJob?.cancel()
        mutable.update { it.copy(ai = it.ai.copy(checking = true, suggestions = emptyList(), sentEvidenceIds = evidenceIds)) }
        analysisJob = viewModelScope.launch {
            try {
                val ordered = evidenceIds.mapNotNull { id -> detail.evidence.firstOrNull { it.id == id } }
                val photos = withContext(Dispatchers.IO) {
                    ordered.mapIndexed { index, evidence ->
                        val bitmap = ImageTools.decode(repository.original(evidence), 1024)
                        try {
                            AiPhoto(index, DisplayCopies.jpeg(bitmap))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                when (val outcome = aiClient().analyze(photos)) {
                    is AiOutcome.Suggestions -> mutable.update { it.copy(ai = it.ai.copy(suggestions = outcome.items)) }
                    is AiOutcome.Rejected -> reportError(outcome.reason)
                    is AiOutcome.Failed -> reportError(outcome.message)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportError("Analysis failed. Try again.") }
            finally { mutable.update { it.copy(ai = it.ai.copy(checking = false)) } }
        }
    }
    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        mutable.update { it.copy(ai = it.ai.copy(checking = false, suggestions = emptyList())) }
    }
    fun acceptAiSuggestion(item: AiSuggestion, text: String) {
        val detail = mutable.value.current ?: return
        val numbers = item.photoIndices.mapNotNull { subset ->
            mutable.value.ai.sentEvidenceIds.getOrNull(subset)
                ?.let { id -> detail.evidence.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
        }.distinct()
        mutable.update {
            it.copy(ai = it.ai.copy(
                suggestions = it.ai.suggestions - item,
                accepted = it.ai.accepted + AcceptedNote(text, numbers, item.modelVersion),
            ))
        }
    }
    fun dropAiSuggestion(item: AiSuggestion) {
        mutable.update { it.copy(ai = it.ai.copy(suggestions = it.ai.suggestions - item)) }
    }
    fun discardAiNotes() {
        mutable.update { it.copy(ai = it.ai.copy(suggestions = emptyList(), accepted = emptyList(), stale = false)) }
    }
    fun recheckAi() {
        val ids = mutable.value.ai.sentEvidenceIds.intersect(mutable.value.current?.evidence.orEmpty().map { it.id }.toSet()).toList()
        if (ids.isEmpty()) {
            discardAiNotes()
            reportError("The checked photos are gone. Run a new check.")
            return
        }
        discardAiNotes()
        analyzePhotos(ids)
    }
    private fun markAiStale() {
        mutable.update { if (it.ai.accepted.isNotEmpty()) it.copy(ai = it.ai.copy(stale = true)) else it }
    }
    // ---- AI unlock purchase (one-time INAPP; entitlement decided by the proxy) ----
    private var billing: BillingManager? = null
    private var billingProduct: com.android.billingclient.api.ProductDetails? = null
    fun aiProductId(): String = BuildConfig.AI_PRODUCT_ID
    fun loadAiProduct() {
        val productId = BuildConfig.AI_PRODUCT_ID
        if (isAiSimulated() || productId.isBlank() || billing != null) return
        viewModelScope.launch {
            val manager = BillingManager(getApplication(), ::onAiPurchaseResult)
            if (!manager.connect()) return@launch
            val product = manager.product(productId)
            if (product == null) {
                manager.endConnection()
                return@launch
            }
            billing = manager
            billingProduct = product
            mutable.update { it.copy(ai = it.ai.copy(productPrice = product.oneTimePurchaseOfferDetails?.formattedPrice)) }
        }
    }
    fun buyAiUnlock(activity: android.app.Activity) {
        val manager = billing
        val product = billingProduct
        if (manager == null || product == null || mutable.value.ai.purchasing) return
        mutable.update { it.copy(ai = it.ai.copy(purchasing = true)) }
        if (manager.launch(activity, product) != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
            mutable.update { it.copy(ai = it.ai.copy(purchasing = false)) }
            reportError("Purchase failed to start. Try again.")
        }
    }
    private fun onAiPurchaseResult(result: com.android.billingclient.api.BillingResult, purchases: List<com.android.billingclient.api.Purchase>?) {
        if (result.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            mutable.update { it.copy(ai = it.ai.copy(purchasing = false)) }
            if (result.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED) {
                reportError("The purchase failed. Try again.")
            }
            return
        }
        val purchase = purchases.firstOrNull { it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED }
        if (purchase == null) {
            mutable.update { it.copy(ai = it.ai.copy(purchasing = false)) }
            reportError("Purchase pending. It unlocks once Google confirms it.")
            return
        }
        viewModelScope.launch {
            val verified = EntitlementClient(BuildConfig.AI_PROXY_URL, InstallId.get(getApplication()))
                .verify(BuildConfig.AI_PRODUCT_ID, purchase.purchaseToken)
            if (verified) {
                billing?.acknowledge(purchase.purchaseToken)
                mutable.update { it.copy(ai = it.ai.copy(purchasing = false, entitled = true, quotaRemaining = null),
                    message = "AI checks unlocked.") }
                refreshAiQuota()
            } else {
                mutable.update { it.copy(ai = it.ai.copy(purchasing = false)) }
                reportError("The purchase could not be verified. Contact support if you were charged.")
            }
        }
    }
    fun materialise(pack: EvidencePack, useFile: (File) -> Unit) = operation { useFile(repository.materialise(pack)) }
    /** Decrypts current photographs to app-private cache files so they can be handed to an
     * external AI assistant app the user chooses via the share sheet. Originals stay encrypted
     * at rest; only this temporary copy is exposed, same pattern as PDF pack sharing. */
    fun shareWithAi(onReady: (AiShareSession) -> Unit) {
        val id = mutable.value.current?.incident?.id ?: return
        operation {
            val session = repository.materialiseForAi(id)
            // In-process, best-effort expiry per session; the startup/onStart sweep is the fallback after process death.
            // NonCancellable wraps only the cleanup so a cancelled scope still deletes the plaintext.
            viewModelScope.launch {
                delay((session.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                runCatching { withContext(NonCancellable) { repository.discardAiShare(session) } }
            }
            try { onReady(session) } catch (e: Exception) { repository.discardAiShare(session); throw e }
        }
    }
    /** Call when the share sheet could not be opened so no plaintext is left behind. */
    fun abortAiShare(session: AiShareSession) { viewModelScope.launch { runCatching { repository.discardAiShare(session) } } }
    fun sweepAiShares() { viewModelScope.launch { runCatching { repository.sweepAiShares() } } }
    fun saveCopy(pack: EvidencePack, destination: Uri, onSaved: () -> Unit = {}) = operation {
        refresh(repository.saveCopy(pack, destination))
        mutable.update { it.copy(message = "PDF copy saved.") }
        onSaved()
    }
    fun deleteCurrent(onDeleted: () -> Unit = {}) {
        val id = mutable.value.current?.incident?.id ?: return
        operation {
            repository.delete(id)
            savedState["incidentId"] = null
            mutable.update { it.copy(current = null, storageBytes = repository.storageBytes(), message = "Incident deleted from this app.") }
            onDeleted()
        }
    }
    /** Archive/remove act on a report from the Reports tab, which is not necessarily the open
     * ("current") incident, so these take an explicit id rather than reading mutable.value.current. */
    fun archiveIncident(id: String, archived: Boolean) = operation {
        val detail = repository.setArchived(id, archived)
        refresh(detail)
        mutable.update { it.copy(message = if (archived) "Report moved to Archived." else "Report restored.") }
    }
    fun removeIncident(id: String) = operation {
        repository.delete(id)
        if (mutable.value.current?.incident?.id == id) { savedState["incidentId"] = null; mutable.update { it.copy(current = null) } }
        mutable.update { it.copy(storageBytes = repository.storageBytes(), message = "Report deleted from this app.") }
    }
    fun saveSettings(settings: DriverSettings) = operation {
        repository.saveSettings(settings)
        mutable.update { it.copy(settings = settings, message = "Driver defaults saved.") }
    }
    fun backup(destination: Uri) = operation {
        repository.backup(destination)
        mutable.update { it.copy(message = "Encrypted backup saved. Restores on this device only.") }
    }
    fun restoreBackup(source: Uri) = operation {
        val summary = repository.restoreBackup(source)
        mutable.update { it.copy(storageBytes = repository.storageBytes()) }
        val skipped = if (summary.incidentsSkipped > 0) ", ${summary.incidentsSkipped} skipped (already on this device)" else ""
        mutable.update { it.copy(message = "Restore complete: ${summary.incidentsAdded} added$skipped.") }
    }
    fun addEmergencyContact(label: String, number: String) = operation {
        val updated = mutable.value.settings.copy(emergencyContacts = mutable.value.settings.emergencyContacts + EmergencyContact(label = label, number = number))
        repository.saveSettings(updated)
        mutable.update { it.copy(settings = updated) }
    }
    fun removeEmergencyContact(id: String) = operation {
        val updated = mutable.value.settings.copy(emergencyContacts = mutable.value.settings.emergencyContacts.filterNot { it.id == id })
        repository.saveSettings(updated)
        mutable.update { it.copy(settings = updated) }
    }
    suspend fun thumbnail(photo: Evidence): Bitmap = repository.thumbnail(photo)
    fun reportError(text: String) { mutable.update { it.copy(error = text) } }
    fun dismissMessage() { mutable.update { it.copy(error = null, message = null) } }
    private fun refresh(detail: IncidentDetail) {
        if (mutable.value.current?.incident?.id == detail.incident.id) mutable.update { it.copy(current = detail) }
    }
    private fun readableError(e: Exception): String = when (e) {
        is IllegalArgumentException -> e.message ?: "Check your incident details and try again."
        is IllegalStateException -> if (e.message?.contains("storage", true) == true || e.message?.contains("pack", true) == true || e.message?.contains("photograph", true) == true) e.message!! else "Action failed. Your saved record remains on this device."
        is java.security.GeneralSecurityException -> "Evidence couldn't be decrypted. Don't uninstall the app or clear its data."
        else -> "Action failed. Check device storage and try again."
    }
}
