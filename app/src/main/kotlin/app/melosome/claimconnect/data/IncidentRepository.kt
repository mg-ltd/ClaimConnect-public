package app.melosome.claimconnect.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import app.melosome.claimconnect.ai.AcceptedNote
import app.melosome.claimconnect.evidence.ImageTools
import app.melosome.claimconnect.export.PdfPackGenerator
import app.melosome.claimconnect.model.*
import app.melosome.claimconnect.security.CryptoBox

/** Decrypted photographs exposed to an external app. [expiresAt] is when cleanup is due, not a guaranteed
 * deletion time: if the process dies the copies remain until the next startup sweep. */
class AiShareSession(val incidentId: String, val id: String, val files: List<File>, val expiresAt: Long)

class IncidentRepository(
    private val context: Context,
    private val db: ClaimDatabase,
    crypto: CryptoBox = CryptoBox(),
) {
    private val dao = db.dao()
    private val codec = RecordCodec(crypto)
    private val files = SecureFiles(context, crypto)
    private val backupManager = BackupManager(db, files)
    private val mutex = Mutex()
    val incidents = dao.observeIncidents().map { rows -> io {
        db.withTransaction { rows.mapNotNull { row -> if (dao.incident(row.id) == null) null else detailUnlocked(row.id) } }
    } }
    private suspend fun detailUnlocked(id: String): IncidentDetail {
        val incident = codec.incident(requireNotNull(dao.incident(id)) { "This incident is no longer available." })
        return IncidentDetail(incident, dao.evidence(id).map(codec::evidence), dao.pack(id)?.let(codec::pack))
    }
    suspend fun detail(id: String): IncidentDetail = io { detailUnlocked(id) }
    suspend fun settings(): DriverSettings = io { dao.settings()?.let(codec::settings) ?: DriverSettings() }
    suspend fun saveSettings(settings: DriverSettings) = io { dao.saveSettings(codec.row(settings)) }

    suspend fun create(): IncidentDetail = io {
        val defaults = dao.settings()?.let(codec::settings) ?: DriverSettings()
        val incident = Incident(registration = defaults.registration, vehicle = defaults.vehicle,
            driver = defaults.driver, company = defaults.company)
        dao.insertIncident(codec.row(incident))
        detailUnlocked(incident.id)
    }
    suspend fun save(incident: Incident) = io {
        requireNotNull(dao.incident(incident.id)) { "This incident is no longer available." }
        dao.updateIncident(codec.row(incident))
        clearShared(incident.id)
    }
    suspend fun addPhoto(incidentId: String, uri: Uri, source: String, captureId: String? = null): IncidentDetail = io {
        val current = detailUnlocked(incidentId)
        if (captureId != null) {
            UUID.fromString(captureId)
            if (current.evidence.any { it.id == captureId }) return@io current
        }
        require(current.evidence.size < ProductLimits.MAX_PHOTOS) { "Each incident can contain up to 10 photographs." }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                require(output.size().toLong() + count <= ProductLimits.MAX_PHOTO_BYTES) { "This photograph exceeds 15 MB. Choose a smaller image." }
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("The photograph could not be opened. Please select it again.")
        val mime = ImageTools.mimeType(bytes)
        // Validate full decoding before retaining the original, while keeping the original bytes intact.
        ImageTools.decode(bytes, 256).recycle()
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.take(255) else null
            }
        }.getOrNull() ?: "Photograph"
        val evidence = Evidence(captureId ?: UUID.randomUUID().toString(), incidentId, System.currentTimeMillis(), source,
            name, mime, bytes.size.toLong(), sha256(bytes))
        files.write(incidentId, evidence.id, bytes)
        try {
            db.withTransaction {
                dao.insertEvidence(codec.row(evidence))
                touch(current.incident)
            }
        } catch (e: Exception) { files.delete(incidentId, evidence.id); throw e }
        detailUnlocked(incidentId)
    }
    suspend fun caption(incidentId: String, evidenceId: String, caption: String): IncidentDetail = io {
        val detail = detailUnlocked(incidentId)
        val photo = detail.evidence.first { it.id == evidenceId }
        db.withTransaction { dao.updateEvidence(codec.row(photo.copy(caption = caption))); touch(detail.incident) }
        detailUnlocked(incidentId)
    }
    suspend fun removePhoto(incidentId: String, evidenceId: String): IncidentDetail = io {
        val detail = detailUnlocked(incidentId)
        require(detail.evidence.any { it.id == evidenceId })
        db.withTransaction { dao.deleteEvidence(evidenceId); touch(detail.incident) }
        files.delete(incidentId, evidenceId)
        detailUnlocked(incidentId)
    }
    suspend fun thumbnail(evidence: Evidence) = io { ImageTools.decode(files.read(evidence.incidentId, evidence.id), 400) }
    suspend fun original(evidence: Evidence): ByteArray = io { files.read(evidence.incidentId, evidence.id) }

    suspend fun generate(incidentId: String, aiNotes: List<AcceptedNote> = emptyList()): IncidentDetail = io {
        val detail = detailUnlocked(incidentId)
        val generatedAt = System.currentTimeMillis()
        val bytes = PdfPackGenerator().generate(detail, generatedAt, aiNotes) { files.read(incidentId, it.id) }
        val pack = EvidencePack(UUID.randomUUID().toString(), incidentId, detail.incident.revision, generatedAt, sha256(bytes))
        files.write(incidentId, pack.id, bytes)
        try {
            db.withTransaction { dao.deletePacks(incidentId); dao.insertPack(codec.row(pack)); dao.updateIncident(codec.row(detail.incident.copy(updatedAt = generatedAt))) }
        } catch (e: Exception) { files.delete(incidentId, pack.id); throw e }
        detail.pack?.let { files.delete(incidentId, it.id) }
        detailUnlocked(incidentId)
    }
    private suspend fun packBytes(pack: EvidencePack): ByteArray {
        val detail = detailUnlocked(pack.incidentId)
        require(detail.currentPack?.id == pack.id) { "The incident has changed. Generate a new pack first." }
        return files.read(pack.incidentId, pack.id).also { check(sha256(it) == pack.sha256) { "The evidence pack failed its integrity check. Generate it again." } }
    }
    suspend fun materialise(pack: EvidencePack): File = io {
        val target = File(context.cacheDir, "shared/${pack.incidentId}/${pack.id}.pdf")
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${pack.id}.tmp")
        try {
            java.io.FileOutputStream(temporary).use { it.write(packBytes(pack)); it.fd.sync() }
            check(temporary.renameTo(target)) { "The evidence pack could not be prepared for sharing." }
        } finally { temporary.delete() }
        target
    }
    suspend fun saveCopy(pack: EvidencePack, destination: Uri): IncidentDetail = io {
        val bytes = packBytes(pack)
        context.contentResolver.openOutputStream(destination, "wt")?.use { out -> out.write(bytes); out.flush() }
            ?: throw IllegalStateException("The destination could not be opened. Please choose another location.")
        db.withTransaction {
            dao.updatePack(codec.row(pack.copy(savedAt = System.currentTimeMillis())))
            val incident = detailUnlocked(pack.incidentId).incident
            dao.updateIncident(codec.row(incident.copy(updatedAt = System.currentTimeMillis())))
        }
        detailUnlocked(pack.incidentId)
    }
    suspend fun setArchived(incidentId: String, archived: Boolean): IncidentDetail = io {
        // Archiving is a filing action, not an edit: it must not bump the incident's revision
        // (which would invalidate its already-generated evidence pack) or touch cached PDFs.
        val detail = detailUnlocked(incidentId)
        dao.updateIncident(codec.row(detail.incident.copy(archived = archived, updatedAt = System.currentTimeMillis())))
        detailUnlocked(incidentId)
    }
    suspend fun delete(incidentId: String) = io {
        dao.deleteIncident(incidentId)
        files.deleteIncident(incidentId)
        clearShared(incidentId)
        File(context.cacheDir, "camera/$incidentId").deleteRecursively()
        File(context.cacheDir, "maps/$incidentId").deleteRecursively()
    }
    suspend fun storageBytes(): Long = io { files.bytesUsed() }
    suspend fun backup(destination: Uri) = io { backupManager.export(context.contentResolver, destination) }
    suspend fun restoreBackup(source: Uri): RestoreSummary = io { backupManager.restore(context.contentResolver, source) }
    suspend fun recover() = io {
        val retained = dao.allIncidents().associate { row ->
            row.id to (dao.evidence(row.id).map { it.id } + listOfNotNull(dao.pack(row.id)?.id)).toSet()
        }
        files.reconcile(retained)
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        for (name in listOf("shared", "camera", "maps")) File(context.cacheDir, name).walkBottomUp()
            .filter { it.isFile && it.lastModified() < cutoff }.forEach { it.delete() }
        sweepUnlocked()
    }
    /** Decrypts an incident's photographs into a fresh, independent session directory for the AI share sheet.
     * Other sessions are untouched, so each one expires on its own schedule. */
    suspend fun materialiseForAi(incidentId: String): AiShareSession = io {
        val createdAt = System.currentTimeMillis()
        val id = "$createdAt-${UUID.randomUUID()}"
        val session = File(context.cacheDir, "$AI_SHARE_DIR/$incidentId/$id").apply { mkdirs() }
        try {
            val photos = detailUnlocked(incidentId).evidence.map { evidence ->
                val extension = evidence.mimeType.substringAfterLast('/', "jpg").filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }
                File(session, "${evidence.id}.$extension").apply { writeBytes(files.read(evidence.incidentId, evidence.id)) }
            }
            AiShareSession(incidentId, id, photos, createdAt + AI_SHARE_GRACE_MS)
        } catch (e: Exception) { session.deleteRecursively(); throw e }
    }
    /** Removes one session's decrypted copies; other sessions of the same incident are kept. */
    suspend fun discardAiShare(session: AiShareSession) = io {
        File(context.cacheDir, "$AI_SHARE_DIR/${session.incidentId}/${session.id}").deleteRecursively()
    }
    /** Deletes every session older than the grace period (or with an unreadable name), plus empty incident folders. */
    suspend fun sweepAiShares(now: Long = System.currentTimeMillis(), graceMs: Long = AI_SHARE_GRACE_MS) = io { sweepUnlocked(now, graceMs) }
    private fun sweepUnlocked(now: Long = System.currentTimeMillis(), graceMs: Long = AI_SHARE_GRACE_MS) {
        File(context.cacheDir, AI_SHARE_DIR).listFiles().orEmpty().forEach { incident ->
            incident.listFiles().orEmpty().forEach { session ->
                val createdAt = session.name.substringBefore('-').toLongOrNull()
                if (createdAt == null || now - createdAt >= graceMs) session.deleteRecursively()
            }
            if (incident.listFiles().isNullOrEmpty()) incident.delete()
        }
    }
    private fun clearAiShares(incidentId: String) { File(context.cacheDir, "$AI_SHARE_DIR/$incidentId").deleteRecursively() }
    private fun clearShared(incidentId: String) {
        File(context.cacheDir, "shared/$incidentId").deleteRecursively()
        clearAiShares(incidentId)
    }
    private suspend fun touch(incident: Incident) {
        dao.updateIncident(codec.row(incident.copy(revision = incident.revision + 1, updatedAt = System.currentTimeMillis())))
        clearShared(incident.id)
    }
    companion object {
        private const val AI_SHARE_DIR = "ai_share"
        /** How long a recipient app has to read shared photographs before the decrypted copies are removed. */
        const val AI_SHARE_GRACE_MS = 15 * 60 * 1000L
    }
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
