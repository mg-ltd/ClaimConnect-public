package app.melosome.claimconnect.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.melosome.claimconnect.BuildConfig
import app.melosome.claimconnect.R
import app.melosome.claimconnect.export.PdfPackGenerator
import app.melosome.claimconnect.ai.AiSuggestion
import app.melosome.claimconnect.ai.ApiKeyStore
import app.melosome.claimconnect.ai.quotaText
import app.melosome.claimconnect.location.DeviceLocation
import app.melosome.claimconnect.location.LocationSnapshot
import app.melosome.claimconnect.speech.SpeechTranscriber
import app.melosome.claimconnect.speech.TranscriptEvent
import app.melosome.claimconnect.model.*

private val steps = listOf("Vehicle", "Incident", "People", "Photos", "Review", "Evidence pack")
private const val ONBOARDING_PREFERENCES = "onboarding"
private const val ONBOARDING_COMPLETED = "completed"

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ClaimConnectApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onboardingPrefs = remember { context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE) }
    // This flag is intentionally not versioned: updates stay out of the user's way, while a
    // reinstall resets it because app backup is disabled in the manifest.
    var onboardingSeen by remember { mutableStateOf(onboardingPrefs.getBoolean(ONBOARDING_COMPLETED, false)) }
    fun completeOnboarding(navController: NavHostController) {
        onboardingPrefs.edit().putBoolean(ONBOARDING_COMPLETED, true).apply()
        onboardingSeen = true
        // Replaying the tour from Settings pops back to where it was opened; on first launch
        // there's nothing to pop back to, so move on to Home instead.
        if (navController.previousBackStackEntry != null) navController.popBackStack()
        else navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
    }
    // Standard Jetpack Navigation Compose: the NavController owns a real back stack, so the
    // system back button, a predictive-back gesture and the top bar's Up arrow all pop the same
    // way a user expects from any other Android app (for example Privacy backs to Settings, not
    // all the way to Home).
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val canGoBack = navController.previousBackStackEntry != null
    val inEditorWizard = currentRoute == "editor" && state.step in 1..4
    val completedEditor = currentRoute == "editor" && state.step == 5
    fun navigateBack() {
        // The generated pack is the terminal step: Back ends the journey instead of reopening Review.
        if (completedEditor) {
            if (vm.canLeaveEditor()) returnHome(navController)
            return
        }
        if (inEditorWizard) { vm.step(state.step - 1); return }
        if (vm.canLeaveEditor()) navController.popBackStack()
    }
    BackHandler(enabled = canGoBack || inEditorWizard || completedEditor) { navigateBack() }
    // Incidents, Emergency and Reports are peer top-level tabs reached from the bottom bar;
    // Settings, Privacy and the incident editor are pushed flows, not tabs.
    val topLevelRoutes = remember { setOf("home", "emergency", "reports") }
    Scaffold(
        topBar = {
            // The onboarding tour is a full-bleed experience with its own Skip/Next controls.
            if (currentRoute != "onboarding") TopAppBar(title = { Column {
                Text("ClaimConnect", fontWeight = FontWeight.Bold)
                Text("Your incident. Your evidence.", style = MaterialTheme.typography.labelMedium)
            } }, navigationIcon = {
                if (canGoBack) IconButton(onClick = ::navigateBack, enabled = !state.busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                // Settings lives in the top bar (with Privacy nested one level inside Settings),
                // so the bottom bar stays reserved for top-level tabs.
                if (currentRoute in topLevelRoutes) {
                    IconButton(onClick = { navController.navigate("settings") }, enabled = !state.busy) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            })
        },
        bottomBar = {
            // Bottom navigation (Material 3) covers only top-level tabs; Settings/Privacy are
            // reached via the top bar, and the guided incident editor stays a pushed flow, not
            // a tab, per Android design guidance. Incidents, Emergency and Reports are all real
            // destinations now (see docs/design/ROADMAP.md for remaining open questions).
            if (currentRoute in topLevelRoutes) {
                fun switchTab(route: String) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                NavigationBar {
                    NavigationBarItem(selected = currentRoute == "home", enabled = !state.busy, onClick = { switchTab("home") },
                        icon = { Icon(painterResource(R.drawable.ic_nav_incidents), contentDescription = null) },
                        label = { Text("Incidents") })
                    NavigationBarItem(selected = currentRoute == "emergency", enabled = !state.busy, onClick = { switchTab("emergency") },
                        icon = { Icon(painterResource(R.drawable.ic_nav_emergency), contentDescription = null) },
                        label = { Text("Emergency") })
                    NavigationBarItem(selected = currentRoute == "reports", enabled = !state.busy, onClick = { switchTab("reports") },
                        icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                        label = { Text("Reports") })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding()) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.saveFailed) TextButton(onClick = vm::retrySave, enabled = !state.busy) { Text("Retry save — not saved") }
                if (state.error != null || state.message != null) {
                    Surface(color = if(state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(state.error ?: state.message.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = vm::dismissMessage) { Text("Dismiss") }
                        }
                    }
                }
                NavHost(navController, startDestination = if (onboardingSeen) "home" else "onboarding", modifier = Modifier.weight(1f)) {
                    composable("onboarding") { Onboarding(onFinish = { completeOnboarding(navController) }) }
                    composable("home") { Home(state, vm, navController) }
                    composable("emergency") { Emergency(state, vm) }
                    composable("reports") { Reports(state, vm) }
                    composable("safety") {
                        Safety(state.busy) {
                            vm.createIncident { navController.navigate("editor") { popUpTo("safety") { inclusive = true } } }
                        }
                    }
                    composable("editor") { state.current?.let { Editor(state, it, vm, navController) } }
                    composable("settings") { Settings(state, vm, navController) }
                    composable("privacy") { Privacy() }
                }
            }
        }
    }
}

private data class OnboardingPage(val icon: ImageVector, val title: String, val body: String)

private val onboardingPages = listOf(
    OnboardingPage(Icons.Default.Warning, "Safety first",
        "Get everyone safe and away from traffic first. ClaimConnect records facts; it does not replace emergency services or your insurer."),
    OnboardingPage(Icons.Default.CheckCircle, "Capture the essentials",
        "Add the time, location, people and up to 10 photos. Progress saves on this device as you go."),
    OnboardingPage(Icons.Default.Info, "You stay in control",
        "Preview, save or share your PDF when ready. Nothing leaves until you choose where to send it. Replay this tour in Settings."),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Onboarding(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == onboardingPages.lastIndex
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text("Skip") }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = onboardingPages[index]
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(page.icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
        Text("${pagerState.currentPage + 1} of ${onboardingPages.size}", modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            onboardingPages.indices.forEach { index ->
                val selected = pagerState.currentPage == index
                Box(Modifier.padding(4.dp).size(if (selected) 10.dp else 8.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, CircleShape))
            }
        }
        Button(onClick = {
            if (lastPage) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (lastPage) "Get started" else "Next") }
    }
}

@Composable private fun Home(state: AppState, vm: AppViewModel, navController: NavHostController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Keep the details together", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Record the incident, organise photos and build an evidence pack.")
        Button(onClick = { navController.navigate("safety") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("New incident")
        }
        LocalNotice()
        Text("Your incidents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        // Archived incidents move to the Reports tab, so this list stays focused on drafts and
        // incidents still being worked on.
        val active = state.incidents.filterNot { it.incident.archived }
        if (active.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No incidents yet", style = MaterialTheme.typography.titleMedium)
                Text("Saves automatically as you go. Come back to a draft any time.")
            } }
        }
        active.forEach { detail ->
            OutlinedCard(onClick = { vm.openIncident(detail.incident.id) { navController.navigate("editor") } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(detail.incident.registration.ifBlank { "Vehicle incident" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Status(detail.status)
                    }
                    Text("${detail.incident.date} · ${photoCount(detail.evidence.size)}", style = MaterialTheme.typography.bodyMedium)
                    if (detail.incident.location.isNotBlank()) Text(detail.incident.location, maxLines = 2)
                    Text(detail.incident.reference, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Evidence storage: ${state.storageBytes / (1024 * 1024)} MB of 200 MB", style = MaterialTheme.typography.bodySmall)
    }
}

/** Effective market: an explicit choice wins, otherwise the device locale
 * decides, otherwise the Europe-wide 112 fallback applies. */
@Composable private fun Emergency(state: AppState, vm: AppViewModel) {
    val context = LocalContext.current
    var addDialog by remember { mutableStateOf(false) }
    var marketMenu by remember { mutableStateOf(false) }
    fun requestCall(number: String) {
        if (!openDialler(context, number)) vm.reportError("No phone app found. Dial $number manually.")
    }
    val localeCountry = remember { java.util.Locale.getDefault().country.uppercase() }
    val autoMarket = EmergencyMarkets.forCountryCode(localeCountry)
    val chosenCode = state.settings.emergencyMarket
    val market = if (chosenCode.isBlank()) autoMarket
        else EmergencyMarkets.ALL.firstOrNull { it.countryCode == chosenCode } ?: EmergencyMarkets.FALLBACK
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Emergency")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Standard numbers · ${market.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box {
                TextButton(onClick = { marketMenu = true }, enabled = !state.busy) {
                    Text(if (chosenCode.isBlank()) "Auto (${autoMarket.countryCode})" else market.countryCode)
                }
                DropdownMenu(expanded = marketMenu, onDismissRequest = { marketMenu = false }) {
                    DropdownMenuItem(text = { Text("Auto (${autoMarket.name})") },
                        onClick = { marketMenu = false; vm.saveSettings(state.settings.copy(emergencyMarket = "")) })
                    EmergencyMarkets.ALL.forEach { option ->
                        DropdownMenuItem(text = { Text("${option.name} (${option.countryCode})") },
                            onClick = { marketMenu = false; vm.saveSettings(state.settings.copy(emergencyMarket = option.countryCode)) })
                    }
                }
            }
        }
        market.numbers.forEach { entry -> EmergencyRow(entry.label, entry.number) { requestCall(entry.number) } }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My contacts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { addDialog = true }, enabled = !state.busy) { Text("Add") }
        }
        if (state.settings.emergencyContacts.isEmpty()) {
            Text("Numbers you want fast: breakdown cover, family.", style = MaterialTheme.typography.bodySmall)
        }
        state.settings.emergencyContacts.forEach { contact ->
            EmergencyRow(contact.label, contact.number, onRemove = { vm.removeEmergencyContact(contact.id) }) { requestCall(contact.number) }
        }
    }
    if (addDialog) {
        var label by remember { mutableStateOf("") }
        var number by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addDialog = false }, title = { Text("Add a number") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it.take(60) }, label = { Text("Label, for example AA Recovery") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it.take(20) }, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            } },
            confirmButton = { TextButton(onClick = {
                vm.addEmergencyContact(label.trim(), number.trim())
                addDialog = false
            }, enabled = label.isNotBlank() && number.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } })
    }
}

@Composable private fun EmergencyRow(label: String, number: String, onRemove: (() -> Unit)? = null, onCall: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(number, style = MaterialTheme.typography.bodyMedium)
            }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove $label") }
            IconButton(onClick = onCall) { Icon(Icons.Default.Call, contentDescription = "Call $label") }
        }
    }
}

// ACTION_DIAL needs no permission and never places a call without the user's confirmation.
internal fun openDialler(context: android.content.Context, number: String, launch: (Intent) -> Unit = context::startActivity): Boolean {
    val uri = Uri.parse("tel:" + Uri.encode(number.filter { it.isDigit() || it == '+' }))
    return try { launch(Intent(Intent.ACTION_DIAL, uri)); true } catch (_: ActivityNotFoundException) { false }
}

@Composable private fun Reports(state: AppState, vm: AppViewModel) {
    val context = LocalContext.current
    var shareTarget by remember { mutableStateOf<EvidencePack?>(null) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    var savePackId by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    // Reports are incidents with an evidence pack that still matches the incident's current
    // revision; editing an incident after generating a report invalidates the pack, so it drops
    // back to the Incidents tab (as "Draft"/"Ready") until the pack is regenerated there.
    val reports = state.incidents.filter { it.currentPack != null }
    val active = reports.filterNot { it.incident.archived }
    val archived = reports.filter { it.incident.archived }
    LaunchedEffect(archived.isEmpty()) { if (archived.isEmpty()) showArchived = false }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) reports.firstOrNull { it.currentPack?.id == savePackId }?.currentPack?.let { vm.saveCopy(it, uri) }
        savePackId = null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Reports")
        if (active.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (archived.isEmpty()) "No evidence packs yet" else "No active reports", style = MaterialTheme.typography.titleMedium)
                Text(if (archived.isEmpty()) "Generate an evidence pack from an incident's Review step to see it here." else "Archived reports remain available below when you need them.")
            } }
        }
        active.forEach { detail ->
            ReportCard(detail, state.busy,
                onPreview = { previewPack(context, vm, requireNotNull(detail.currentPack)) },
                onShare = { shareTarget = detail.currentPack },
                onSave = { savePackId = detail.currentPack?.id; save.launch("${detail.incident.reference}-r${detail.currentPack?.revision}.pdf") },
                onArchive = { vm.archiveIncident(detail.incident.id, true) },
                onRemove = { removeTarget = detail.incident.id })
        }
        if (archived.isNotEmpty()) {
            HorizontalDivider()
            // Archived reports stay out of the active list until the user explicitly opens them.
            OutlinedButton(onClick = { showArchived = !showArchived }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("archivedReportsToggle")) {
                Text(if (showArchived) "Hide archived reports (${archived.size})" else "Archived reports (${archived.size})")
            }
            if (showArchived) {
                archived.forEach { detail ->
                    ReportCard(detail, state.busy,
                        onPreview = { previewPack(context, vm, requireNotNull(detail.currentPack)) },
                        onShare = { shareTarget = detail.currentPack },
                        onSave = { savePackId = detail.currentPack?.id; save.launch("${detail.incident.reference}-r${detail.currentPack?.revision}.pdf") },
                        onRestore = { vm.archiveIncident(detail.incident.id, false) },
                        onRemove = { removeTarget = detail.incident.id })
                }
            }
        }
    }
    if (shareTarget != null) AlertDialog(onDismissRequest = { shareTarget = null }, title = { Text("Share this evidence pack?") },
        text = { Text("The PDF holds your account, registrations, contacts and photos. Share only with someone you trust. This is not an insurance claim.") },
        confirmButton = { TextButton(onClick = {
            val pack = shareTarget; shareTarget = null
            if (pack != null) vm.materialise(pack) { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Evidence pack", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(Intent.createChooser(intent, "Share evidence pack")) }
                catch (_: ActivityNotFoundException) { vm.reportError("No app can share this PDF. Use Save PDF.") }
            }
        }) { Text("Share") } },
        dismissButton = { TextButton(onClick = { shareTarget = null }) { Text("Cancel") } })
    if (removeTarget != null) AlertDialog(onDismissRequest = { removeTarget = null }, title = { Text("Delete this report?") },
        text = { Text("Removes the record and pack from this app. Copies you saved or shared stay where they are.") },
        confirmButton = { TextButton(onClick = { val id = removeTarget; removeTarget = null; id?.let(vm::removeIncident) }) { Text("Delete report") } },
        dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Keep report") } })
}

@Composable private fun ReportCard(
    detail: IncidentDetail, busy: Boolean, onPreview: () -> Unit, onShare: () -> Unit, onSave: () -> Unit,
    onRemove: () -> Unit, onArchive: (() -> Unit)? = null, onRestore: (() -> Unit)? = null,
) {
    val pack = detail.currentPack ?: return
    Card(Modifier.fillMaxWidth().testTag("reportCard:${detail.incident.registration}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(detail.incident.registration.ifBlank { "Vehicle incident" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Status(detail.status)
            }
            Text("${detail.incident.reference} · Revision ${pack.revision} · ${photoCount(detail.evidence.size)}", style = MaterialTheme.typography.bodyMedium)
            Text("Generated ${PdfPackGenerator.timestamp(pack.generatedAt)}", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPreview, enabled = !busy) { Text("Preview") }
                TextButton(onClick = onShare, enabled = !busy) { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Share") }
                TextButton(onClick = onSave, enabled = !busy) { Text("Save") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onArchive != null) TextButton(onClick = onArchive, enabled = !busy, modifier = Modifier.testTag("archiveReport:${detail.incident.registration}")) { Text("Archive") }
                if (onRestore != null) TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.testTag("restoreReport:${detail.incident.registration}")) { Text("Restore") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove, enabled = !busy) { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun previewPack(context: android.content.Context, vm: AppViewModel, pack: EvidencePack, onOpened: () -> Unit = {}) {
    vm.materialise(pack) { file ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Evidence pack", uri) }
        try { context.startActivity(intent); onOpened() }
        catch (_: ActivityNotFoundException) { vm.reportError("No PDF viewer installed. Save the PDF or use Share.") }
    }
}

private fun returnHome(navController: NavHostController) {
    if (!navController.popBackStack("home", false)) {
        navController.navigate("home") {
            popUpTo(navController.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
}

@Composable private fun Safety(busy: Boolean, continueAction: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Safety comes first", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Get everyone safe and clear of traffic.", style = MaterialTheme.typography.titleLarge)
        Text("Call emergency services if needed. Never use this app while driving. Photograph only when safe.")
        Text("Record facts in your own words. Skip personal or medical details.")
        LocalNotice()
        Button(onClick = continueAction, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("I'm safe — continue") }
    }
}

@Composable private fun Editor(state: AppState, detail: IncidentDetail, vm: AppViewModel, navController: NavHostController) {
    val incident = detail.incident
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.step) {
        focusManager.clearFocus()
        keyboard?.hide()
        scrollState.scrollTo(0)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.step + 1} of 6 · ${steps[state.step]}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(incident.reference, style = MaterialTheme.typography.labelSmall)
            }
            Text(if (state.saveFailed) "Not saved" else if (state.pendingWrites > 0) "Saving…" else "Saved on device", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(progress = { (state.step + 1) / 6f }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(scrollState).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.step) {
                0 -> {
                    SectionTitle("Your vehicle and driver")
                    Field("Vehicle registration", incident.registration, "registration", state.busy, capitals = true) { text -> vm.edit { it.copy(registration = text) } }
                    Field("Make and model (optional)", incident.vehicle, "vehicle", state.busy) { text -> vm.edit { it.copy(vehicle = text) } }
                    Field("Driver name", incident.driver, "driver", state.busy) { text -> vm.edit { it.copy(driver = text) } }
                    Field("Company or fleet (optional)", incident.company, "company", state.busy) { text -> vm.edit { it.copy(company = text) } }
                }
                1 -> {
                    SectionTitle("What happened?")
                    DateTimeFields(incident, !state.busy, vm)
                    LocationField(incident, state.busy, vm)
                    NarrationField(incident, state.busy, vm)
                }
                2 -> {
                    SectionTitle("People involved")
                    Field("Other parties and witnesses (optional)", incident.otherParties, "otherParties", state.busy, multiline = true) { text -> vm.edit { it.copy(otherParties = text) } }
                }
                3 -> Photos(detail, state.busy, vm)
                4 -> Review(detail, state, vm, navController)
                5 -> Pack(detail, state.busy, vm) { returnHome(navController) }
            }
        }
        if (state.step < 4) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.step > 0) OutlinedButton(onClick = { vm.step(state.step - 1) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = { vm.step(state.step + 1) }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if(state.step == 3) "Review incident" else "Continue") }
            }
        }
    }
}

@Composable private fun DateTimeFields(incident: Incident, enabled: Boolean, vm: AppViewModel) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = {
            val date = runCatching { LocalDate.parse(incident.date) }.getOrDefault(LocalDate.now())
            DatePickerDialog(context, { _, year, month, day -> vm.edit { it.copy(date = LocalDate.of(year, month + 1, day).toString()) } },
                date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, enabled = enabled, modifier = Modifier.weight(1f).testTag("date")) { Text(incident.date) }
        OutlinedButton(onClick = {
            val time = runCatching { LocalTime.parse(incident.time) }.getOrDefault(LocalTime.now())
            TimePickerDialog(context, { _, hour, minute -> vm.edit { it.copy(time = LocalTime.of(hour, minute).toString()) } },
                time.hour, time.minute, true).show()
        }, enabled = enabled, modifier = Modifier.weight(1f).testTag("time")) { Text(incident.time) }
    }
    Text("Incident time zone: ${incident.timeZone}", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun NarrationField(incident: Incident, busy: Boolean, vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var review by remember { mutableStateOf<String?>(null) }
    val holder = remember { arrayOfNulls<SpeechTranscriber>(1) }
    DisposableEffect(Unit) { onDispose { holder[0]?.destroy(); holder[0] = null } }
    fun teardown() { holder[0]?.destroy(); holder[0] = null; listening = false; finishing = false; partial = "" }
    fun startDictation() {
        if (!SpeechTranscriber.isAvailable(context)) {
            vm.reportError("Transcription isn't available on this device. Type your account instead.")
            return
        }
        val transcriber = runCatching {
            SpeechTranscriber(context) { event ->
                when (event) {
                    is TranscriptEvent.Partial -> partial = event.text
                    is TranscriptEvent.Final -> { teardown(); review = event.text }
                    is TranscriptEvent.Error -> { teardown(); vm.reportError(event.message) }
                }
            }
        }.getOrNull()
        if (transcriber == null) {
            vm.reportError("Transcription isn't available right now. Type your account instead.")
            return
        }
        holder[0] = transcriber
        // Language matching queries the recognizer first — at most ~2 s.
        scope.launch {
            runCatching { transcriber.start() }
                .onFailure { teardown(); vm.reportError("Transcription isn't available right now. Type your account instead.") }
                .onSuccess { listening = true }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startDictation()
        else vm.reportError("Microphone denied. Type your account instead.")
    }
    Field("Your account of the incident", incident.notes, "notes", busy, multiline = true) { text -> vm.edit { it.copy(notes = text) } }
    OutlinedButton(onClick = {
        if (SpeechTranscriber.hasPermission(context)) startDictation()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }, enabled = !busy && !listening, modifier = Modifier.fillMaxWidth().testTag("dictate")) {
        Text("Dictate instead")
    }
    if (listening) AlertDialog(
        onDismissRequest = { holder[0]?.cancel(); teardown() },
        title = { Text("Listening…") },
        text = { Text(partial.ifBlank { "Speak now." }) },
        confirmButton = {
            TextButton(onClick = { finishing = true; holder[0]?.stop() }, enabled = !finishing) {
                Text(if (finishing) "Finishing…" else "Done")
            }
        },
        dismissButton = { TextButton(onClick = { holder[0]?.cancel(); teardown() }, enabled = !finishing) { Text("Cancel") } })
    review?.let { transcript ->
        AlertDialog(
            onDismissRequest = { review = null },
            title = { Text("Dictated text") },
            text = { Text(transcript) },
            confirmButton = {
                TextButton(onClick = {
                    vm.edit { it.copy(notes = SpeechTranscriber.appendTranscript(it.notes, transcript)) }
                    review = null
                }) { Text("Keep") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.edit { it.copy(notes = transcript) }; review = null }) { Text("Replace") }
                    TextButton(onClick = { review = null }) { Text("Discard") }
                }
            })
    }
}

@Composable private fun LocationField(incident: Incident, busy: Boolean, vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    fun locate() {
        locating = true
        scope.launch {
            val text = DeviceLocation.currentLocationText(context)
            locating = false
            if (text != null) vm.edit { it.copy(location = text) }
            else vm.reportError("Can't find your location. Turn on location services or type it manually.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) locate() else vm.reportError("Location denied. Type the incident location manually.")
    }
    Field("Location", incident.location, "location", busy) { text -> vm.edit { it.copy(location = text) } }
    OutlinedButton(onClick = {
        if (DeviceLocation.hasPermission(context)) locate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, enabled = !busy && !locating, modifier = Modifier.fillMaxWidth().testTag("useLocation")) {
        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (locating) "Finding your location…" else "Use current location")
    }
}

@Composable private fun Photos(detail: IncidentDetail, busy: Boolean, vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshotState by remember { mutableStateOf<String?>(null) }
    fun addMapSnapshot() {
        snapshotState = "locating"
        scope.launch {
            val point = DeviceLocation.currentCoordinates(context)
            if (point == null) {
                snapshotState = null
                vm.reportError("Can't find your location. Turn on location services and retry.")
                return@launch
            }
            snapshotState = "capturing"
            val stamp = PdfPackGenerator.timestamp(System.currentTimeMillis())
            // Runs on the main thread, which the map snapshotter requires; falls back to an
            // offline diagram when tiles cannot be fetched.
            val capture = LocationSnapshot.capture(context, point, "${LocationSnapshot.coordinatesText(point)} · $stamp")
            val png = withContext(Dispatchers.IO) {
                java.io.ByteArrayOutputStream().use {
                    capture.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    it.toByteArray()
                }
            }
            capture.bitmap.recycle()
            snapshotState = null
            vm.importMapSnapshot(png,
                if (capture.offlineFallback) "Location diagram (map unavailable)" else "Map snapshot",
                LocationSnapshot.captionFor(point, stamp, capture.offlineFallback))
        }
    }
    val mapPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) addMapSnapshot()
        else vm.reportError("Location denied. The map snapshot needs your location.")
    }
    val cameraDirectory = remember(detail.incident.id) { File(context.cacheDir, "camera/${detail.incident.id}").apply { mkdirs() } }
    var cameraName by rememberSaveable(detail.incident.id) {
        mutableStateOf(cameraDirectory.listFiles()?.firstOrNull { it.extension == "jpg" && it.length() > 0 }?.name)
    }
    fun importCapture(file: File) {
        vm.importPhotos(listOf(FileProvider.getUriForFile(context, "${context.packageName}.files", file)),
            "Captured using device camera", captureId = file.nameWithoutExtension) { saved ->
            if (saved) { file.delete(); cameraName = null }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.importPhotos(uris, "Imported from photo picker")
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraName?.let { File(cameraDirectory, it) }
        if (file != null) {
            if (success) importCapture(file)
            else { file.delete(); cameraName = null }
        }
    }
    SectionTitle("Photographs (${detail.evidence.size}/10)")
    cameraName?.let { name ->
        val pending = File(cameraDirectory, name)
        if (pending.exists() && pending.length() > 0) OutlinedButton(onClick = { importCapture(pending) }, enabled = !busy) { Text("Retry save") }
        TextButton(onClick = { pending.delete(); cameraName = null }, enabled = !busy) { Text("Discard") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            val file = File(cameraDirectory, "${UUID.randomUUID()}.jpg")
            file.parentFile?.mkdirs()
            cameraName = file.name
            try { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
            catch (_: ActivityNotFoundException) { file.delete(); cameraName = null; vm.reportError("No camera app found. Import a photo instead.") }
        }, enabled = !busy && detail.evidence.size < 10 && cameraName == null, modifier = Modifier.weight(1f)) { Text("Take photo") }
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy && detail.evidence.size < 10, modifier = Modifier.weight(1f)) { Text("Import photos") }
    }
    OutlinedButton(onClick = {
        if (DeviceLocation.hasPermission(context)) addMapSnapshot()
        else mapPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }, enabled = !busy && snapshotState == null && detail.evidence.size < 10,
        modifier = Modifier.fillMaxWidth().testTag("addMapSnapshot")) {
        Text(when (snapshotState) {
            "locating" -> "Finding your location…"
            "capturing" -> "Capturing map…"
            else -> "Add map snapshot"
        })
    }
    Text("Up to 10 photos, 15 MB each. Map snapshots count too.", style = MaterialTheme.typography.bodySmall)
    if (detail.evidence.isNotEmpty()) {
        Card(onClick = { vm.step(4) }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI photo check", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Get review-only notes on the Review step.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go to Review")
            }
        }
    }
    detail.evidence.forEachIndexed { index, photo -> PhotoCard(photo, index, busy, vm) }
}

@Composable private fun PhotoCard(photo: Evidence, index: Int, busy: Boolean, vm: AppViewModel) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.id) { value = runCatching { vm.thumbnail(photo) }.getOrNull() }
    DisposableEffect(bitmap) {
        val retainedBitmap = bitmap
        onDispose { retainedBitmap?.recycle() }
    }
    var captionDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bitmap?.let { Image(it.asImageBitmap(), "Photo ${index + 1}: ${photo.caption.ifBlank { "Incident evidence" }}", Modifier.fillMaxWidth().heightIn(max = 230.dp)) }
            Text("Photo ${index + 1}", style = MaterialTheme.typography.titleMedium)
            Text(photo.caption.ifBlank { "No caption" })
            Text("${photo.sizeBytes / 1024} KB · ${PdfPackGenerator.timestamp(photo.addedAt)}", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { captionDialog = true }, enabled = !busy) { Text("Edit caption") }
                TextButton(onClick = { removeDialog = true }, enabled = !busy) { Text("Remove") }
            }
        }
    }
    if (captionDialog) {
        var caption by rememberSaveable(photo.id) { mutableStateOf(photo.caption) }
        AlertDialog(onDismissRequest = { captionDialog = false }, title = { Text("Photo ${index + 1} caption") },
            text = { OutlinedTextField(caption, { caption = it.take(500) }, label = { Text("Caption (optional)") }, minLines = 2) },
            confirmButton = { TextButton(onClick = { vm.caption(photo.id, caption); captionDialog = false }) { Text("Save caption") } },
            dismissButton = { TextButton(onClick = { captionDialog = false }) { Text("Cancel") } })
    }
    if (removeDialog) AlertDialog(onDismissRequest = { removeDialog = false }, title = { Text("Remove photograph?") },
        text = { Text("Removes this photo from the incident. Exported copies stay where they are.") },
        confirmButton = { TextButton(onClick = { vm.removePhoto(photo.id); removeDialog = false }) { Text("Remove photo") } },
        dismissButton = { TextButton(onClick = { removeDialog = false }) { Text("Cancel") } })
}

@Composable private fun AiCheckSection(detail: IncidentDetail, state: AppState, vm: AppViewModel, onOpenSettings: () -> Unit) {
    val ai = state.ai
    var consent by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var selected by remember(detail.incident.id, detail.evidence.size) {
        mutableStateOf(detail.evidence.take(3).map { it.id })
    }
    LaunchedEffect(detail.incident.id) { vm.refreshAiQuota() }
    fun photoLabel(item: AiSuggestion): String =
        item.photoIndices.mapNotNull { ai.sentEvidenceIds.getOrNull(it) }
            .mapNotNull { id -> detail.evidence.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
            .distinct().joinToString(", ") { "Photo $it" }.ifBlank { "No photo link" }
    val backend = vm.aiBackend()
    val simulated = backend == AiBackend.SIMULATED
    val byok = backend == AiBackend.BYOK
    val subtitle = when {
        ai.checking -> "Analysing ${ai.sentEvidenceIds.size} photo(s)…"
        ai.suggestions.isNotEmpty() -> "Review each suggestion below"
        ai.stale && ai.accepted.isNotEmpty() -> "Notes stale — re-check or discard"
        ai.accepted.isNotEmpty() -> "${ai.accepted.size} note(s) kept for the pack"
        else -> quotaText(ai.quotaRemaining, ai.quotaAllowance, ai.entitled, simulated, byok)
    }
    Card(modifier = Modifier.fillMaxWidth().testTag("aiCheckCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI photo check", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { info = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "How AI checks work")
                }
            }
            if (!simulated && !ai.entitled && ai.quotaRemaining != null && (ai.quotaAllowance ?: 0) > 0) {
                LinearProgressIndicator(
                    progress = { ai.quotaRemaining.toFloat() / ai.quotaAllowance!! },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (info) QuotaInfoDialog(simulated, byok, ai.quotaAllowance) { info = false }
    if (ai.stale && ai.accepted.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Evidence changed. Kept notes are stale and won't be in the next pack.")
                Row {
                    TextButton(onClick = vm::recheckAi, enabled = !state.busy) { Text("Re-check") }
                    TextButton(onClick = vm::discardAiNotes, enabled = !state.busy) { Text("Discard notes") }
                }
            }
        }
    }
    if (ai.accepted.isNotEmpty() && !ai.stale) {
        Text("${ai.accepted.size} AI note(s) kept for the pack.", style = MaterialTheme.typography.bodyMedium)
    }
    when {
        ai.checking -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Analysing ${ai.sentEvidenceIds.size} photo(s)…", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = vm::cancelAnalysis, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        ai.suggestions.isNotEmpty() -> {
            Text("${ai.suggestions.size} suggestion(s) · review each one", style = MaterialTheme.typography.bodyMedium)
            ai.suggestions.forEach { item -> SuggestionCard(item, photoLabel(item), state.busy, vm) }
        }
        detail.evidence.isNotEmpty() -> {
            val exhausted = !simulated && !byok && ai.quotaRemaining == 0 && !ai.entitled
            if (simulated) {
                OutlinedButton(onClick = onOpenSettings, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("aiAddKey")) {
                    Text("Add API key in Settings")
                }
                Text("Live analysis needs your Meta API key. Until then checks run on-device demos only.",
                    style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = { consent = true }, enabled = !state.busy && !exhausted,
                modifier = Modifier.fillMaxWidth().testTag("aiCheck")) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Check my record")
            }
            if (exhausted) {
                Text("Free checks used up this month.", style = MaterialTheme.typography.bodySmall)
                if (vm.aiProductId().isBlank()) {
                    Text("Paid unlock isn't configured in this build.", style = MaterialTheme.typography.bodySmall)
                } else if (ai.purchasing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Processing purchase…", style = MaterialTheme.typography.bodySmall)
                } else {
                    LaunchedEffect(Unit) { vm.loadAiProduct() }
                    val activity = LocalActivity.current
                    OutlinedButton(onClick = { activity?.let(vm::buyAiUnlock) },
                        enabled = !state.busy && ai.productPrice != null && activity != null,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(ai.productPrice?.let { "Unlock unlimited checks ($it)" } ?: "Unlock unlimited checks")
                    }
                    Text("One-time Google Play purchase. Unlocks this install only.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        else -> Text("Add photos to enable the AI check.", style = MaterialTheme.typography.bodySmall)
    }
    if (consent) AiConsentDialog(detail, selected, simulated, byok,
        onToggle = { id ->
            selected = if (id in selected) selected - id
            else if (selected.size < 3) selected + id else selected
        },
        onConfirm = { consent = false; vm.analyzePhotos(selected) },
        onDismiss = { consent = false })
}

@Composable private fun SuggestionCard(item: AiSuggestion, photoLabel: String, busy: Boolean, vm: AppViewModel) {
    var editing by remember(item) { mutableStateOf(false) }
    var draft by remember(item) { mutableStateOf(item.observation) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(photoLabel, style = MaterialTheme.typography.labelMedium)
            if (editing) {
                OutlinedTextField(draft, { draft = it.take(2000) }, label = { Text("Observation") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = { vm.acceptAiSuggestion(item, draft); editing = false }, enabled = !busy) { Text("Save") }
                    TextButton(onClick = { editing = false; draft = item.observation }) { Text("Cancel") }
                }
            } else {
                Text(item.observation)
                if (item.uncertainty.isNotBlank()) Text("Unsure: ${item.uncertainty}", style = MaterialTheme.typography.bodySmall)
                if (!item.missingInformation.isNullOrBlank()) Text("Follow-up: ${item.missingInformation}", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { vm.acceptAiSuggestion(item, item.observation) }, enabled = !busy) { Text("Keep") }
                    TextButton(onClick = { editing = true }, enabled = !busy) { Text("Edit") }
                    TextButton(onClick = { vm.dropAiSuggestion(item) }, enabled = !busy) { Text("Drop") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AiConsentDialog(
    detail: IncidentDetail, selected: List<String>, simulated: Boolean, byok: Boolean,
    onToggle: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Analyse photos with AI?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when {
                    simulated -> "Simulator (offline build): nothing leaves this device."
                    byok -> "Display copies are sent to the Meta API using your key — billed to your Meta account. Originals stay on this device."
                    else -> "Display copies are sent for analysis. Originals stay on this device."
                })
                Text("Choose up to 3 photos:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.evidence.forEachIndexed { index, evidence ->
                        FilterChip(selected = evidence.id in selected, onClick = { onToggle(evidence.id) },
                            label = { Text("Photo ${index + 1}") })
                    }
                }
                Text("Every suggestion needs your review before it reaches the pack.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selected.isNotEmpty()) {
                Text("Analyse ${selected.size} photo(s)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun QuotaInfoDialog(simulated: Boolean, byok: Boolean, allowance: Int?, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("How AI checks work") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("One check covers up to 3 photos. Nothing is kept unless you tap Keep.")
                if (simulated) {
                    Text("No API key saved: answers on-device demos, nothing leaves your device. Add your Meta API key in Settings for live analysis.")
                } else if (byok) {
                    Text("Live analysis with your own Meta API key. Display copies — never originals — are sent to the Meta API, and usage bills to your Meta account.")
                    Text("Manage the key in Settings; revoke it any time in your Meta console.")
                } else {
                    Text("${allowance ?: 10} free checks a month per install. Display copies — never originals — are sent for analysis.")
                    Text("Need more? A one-time Google Play unlock removes the limit. It appears here when free checks run out.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } })
}

@Composable private fun Review(detail: IncidentDetail, state: AppState, vm: AppViewModel, navController: NavHostController) {
    val context = LocalContext.current
    var deleteDialog by remember { mutableStateOf(false) }
    var aiDialog by remember { mutableStateOf(false) }
    SectionTitle("Review your record")
    Status(detail.status)
    ReviewSection("Vehicle and driver", "${detail.incident.registration}\n${detail.incident.vehicle}\n${detail.incident.driver}\n${detail.incident.company}", !state.busy) { vm.step(0) }
    ReviewSection("Incident", "${detail.incident.date} ${detail.incident.time} (${detail.incident.timeZone})\n${detail.incident.location}\n\n${detail.incident.notes}", !state.busy) { vm.step(1) }
    ReviewSection("People involved", detail.incident.otherParties.ifBlank { "No details supplied" }, !state.busy) { vm.step(2) }
    ReviewSection("Photographs", "${photoCount(detail.evidence.size)} attached", !state.busy) { vm.step(3) }
    AiCheckSection(detail, state, vm) { navController.navigate("settings") }
    val errors = detail.incident.validationErrors()
    errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
    if (detail.evidence.isEmpty()) Text("This pack has no photos. Add some before generating.", style = MaterialTheme.typography.bodySmall)
    LocalNotice()
    Text(ProductLimits.NOTICE, style = MaterialTheme.typography.bodySmall)
    Button(onClick = vm::generate, enabled = errors.isEmpty() && !state.busy && state.pendingWrites == 0 && !state.saveFailed,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Generate evidence pack") }
    if (detail.currentPack != null) OutlinedButton(onClick = { vm.step(5) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Open pack") }
    OutlinedButton(onClick = { aiDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Ask an AI app") }
    TextButton(onClick = { deleteDialog = true }, enabled = !state.busy) { Text("Delete incident", color = MaterialTheme.colorScheme.error) }
    if (aiDialog) AlertDialog(onDismissRequest = { aiDialog = false }, title = { Text("Use an AI app to help?") },
        text = { Text("This sends your details and photos to an AI app you choose, outside ClaimConnect. Only choose an assistant you trust.") },
        confirmButton = { TextButton(onClick = {
            aiDialog = false
            vm.shareWithAi { session ->
                val uris = session.files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
                val prompt = aiPrompt(detail.incident, detail.evidence.size)
                val intent = if (uris.isEmpty()) Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, prompt)
                } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_TEXT, prompt)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    clipData = ClipData.newUri(context.contentResolver, "Evidence photos", uris.first())
                        .apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(Intent.createChooser(intent, "Describe with AI")) }
                catch (_: ActivityNotFoundException) { vm.abortAiShare(session); vm.reportError("No AI app found. Install an assistant (for example ChatGPT) to use this.") }
            }
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { aiDialog = false }) { Text("Cancel") } })
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Delete this incident?") },
        text = { Text("Removes the incident, photos and pack from this app. Saved or shared copies stay outside it.") },
        confirmButton = { TextButton(onClick = { deleteDialog = false; vm.deleteCurrent { navController.popBackStack("home", false) } }, modifier = Modifier.testTag("confirmDelete")) { Text("Delete incident") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Keep incident") } })
}

@Composable private fun ReviewSection(title: String, body: String, enabled: Boolean, edit: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = edit, enabled = enabled) { Text("Edit") }
        }
        Text(body.trim().ifBlank { "No details supplied" })
    } }
}

@Composable private fun Pack(detail: IncidentDetail, busy: Boolean, vm: AppViewModel, onJourneyComplete: () -> Unit) {
    val context = LocalContext.current
    val pack = detail.currentPack
    var actionDialog by rememberSaveable(pack?.id) { mutableStateOf(pack != null) }
    var shareDialog by rememberSaveable(pack?.id) { mutableStateOf(false) }
    var savePackId by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val selectedPack = pack?.takeIf { it.id == savePackId }
        savePackId = null
        if (uri != null && selectedPack != null) vm.saveCopy(selectedPack, uri, onJourneyComplete)
        else if (pack != null) actionDialog = true
    }
    SectionTitle("Your evidence pack")
    if (pack == null) {
        Text("This record changed. Review it and generate a new pack.")
        Button(onClick = { vm.step(4) }, enabled = !busy) { Text("Review incident") }
        return
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(detail.incident.reference, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("PDF · ${photoCount(detail.evidence.size)} · Revision ${pack.revision}")
        Text("Generated ${PdfPackGenerator.timestamp(pack.generatedAt)}", style = MaterialTheme.typography.bodySmall)
        Status(detail.status)
    } }
    OutlinedButton(onClick = { previewPack(context, vm, pack, onJourneyComplete) },
        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Preview PDF") }
    Button(onClick = { savePackId = pack.id; save.launch("${detail.incident.reference}-r${pack.revision}.pdf") },
        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save PDF") }
    OutlinedButton(onClick = { shareDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Share PDF") }
    Text("PDF SHA-256\n${pack.sha256}", style = MaterialTheme.typography.bodySmall)
    LocalNotice()
    TextButton(onClick = onJourneyComplete, enabled = !busy) { Text("Back to home") }
    if (actionDialog) AlertDialog(onDismissRequest = onJourneyComplete, title = { Text("Evidence pack ready") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Incident saved and PDF ready. Pick what to do next, then you'll return to Home.")
            OutlinedButton(onClick = {
                actionDialog = false
                previewPack(context, vm, pack, onJourneyComplete)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("completionPreview")) { Text("Preview PDF") }
            Button(onClick = {
                actionDialog = false
                savePackId = pack.id
                save.launch("${detail.incident.reference}-r${pack.revision}.pdf")
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("completionSave")) { Text("Save PDF") }
            OutlinedButton(onClick = {
                actionDialog = false
                shareDialog = true
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("completionShare")) { Text("Share PDF") }
        } },
        confirmButton = { TextButton(onClick = onJourneyComplete, enabled = !busy, modifier = Modifier.testTag("completionHome")) { Text("Back to home") } })
    if (shareDialog) AlertDialog(onDismissRequest = { shareDialog = false; actionDialog = true }, title = { Text("Share this evidence pack?") },
        text = { Text("The PDF holds your account, registrations, contacts and photos. Share only with someone you trust. This is not an insurance claim.") },
        confirmButton = { TextButton(onClick = {
            shareDialog = false
            vm.materialise(pack) { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Evidence pack", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(Intent.createChooser(intent, "Share evidence pack")); onJourneyComplete() }
                catch (_: ActivityNotFoundException) { vm.reportError("No app can share this PDF. Use Save PDF.") }
            }
        }) { Text("Share") } },
        dismissButton = { TextButton(onClick = { shareDialog = false; actionDialog = true }) { Text("Cancel") } })
}

@Composable private fun Settings(state: AppState, vm: AppViewModel, navController: NavHostController) {
    var driver by rememberSaveable(state.settings) { mutableStateOf(state.settings.driver) }
    var company by rememberSaveable(state.settings) { mutableStateOf(state.settings.company) }
    var registration by rememberSaveable(state.settings) { mutableStateOf(state.settings.registration) }
    var vehicle by rememberSaveable(state.settings) { mutableStateOf(state.settings.vehicle) }
    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backup(uri)
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Driver defaults")
        Text("Prefill for new incidents. Change them per record.")
        Field("Driver name", driver, "defaultDriver", state.busy) { driver = it }
        Field("Company or fleet", company, "defaultCompany", state.busy) { company = it }
        Field("Vehicle registration", registration, "defaultRegistration", state.busy, capitals = true) { registration = it }
        Field("Make and model", vehicle, "defaultVehicle", state.busy) { vehicle = it }
        Button(onClick = { vm.saveSettings(state.settings.copy(driver = driver, company = company, registration = registration, vehicle = vehicle)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Save defaults") }
        HorizontalDivider()
        SectionTitle("AI photo check")
        val keyLast4 = state.aiKeyLast4
        var keyInput by rememberSaveable(keyLast4) { mutableStateOf("") }
        if (keyLast4 == null) {
            Text("Add your Meta API key for live analysis. It is encrypted on this device and sent to the Meta API only when you confirm a check — usage bills to your Meta account.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Key saved (••••$keyLast4). Live analysis is on — usage bills to your Meta account.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value = keyInput, onValueChange = { keyInput = it.take(ApiKeyStore.MAX_KEY_LENGTH) },
            label = { Text(if (keyLast4 == null) "Meta API key" else "Replace API key") },
            modifier = Modifier.fillMaxWidth().testTag("aiKeyField"), enabled = !state.busy,
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None))
        Button(onClick = { vm.saveAiKey(keyInput); keyInput = "" }, enabled = !state.busy && keyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("aiKeySave")) { Text("Save key") }
        if (keyLast4 != null) {
            OutlinedButton(onClick = vm::clearAiKey, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("aiKeyClear")) { Text("Remove key") }
        }
        HorizontalDivider()
        SectionTitle("Backup")
        Text("Saves an encrypted copy to a file you choose. Restorable only on this device.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { backupExport.launch("claimconnect-backup-${LocalDate.now()}.zip") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Back up to a file") }
        OutlinedButton(onClick = { backupImport.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Restore from a file") }
        HorizontalDivider()
        LocalNotice()
        TextButton(onClick = { navController.navigate("onboarding") }, enabled = !state.busy) { Text("Replay tour") }
        TextButton(onClick = { navController.navigate("privacy") }, enabled = !state.busy) { Text("Privacy, help and limits") }
        Text("ClaimConnect ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Privacy() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Privacy and product limits")
        Text("Your records stay on this device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Details, photos and packs are encrypted on this device. No account, ads, analytics or background network. The only network use is map tiles for \"Add map snapshot\" (below) and AI checks you explicitly confirm (below) — your records are never uploaded by themselves.")
        LocalNotice()
        Text("Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Never read automatically. \"Use current location\" reads your position once, turns it into an address and fills the field — edit, clear or type it manually any time.")
        Text("Voice dictation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("\"Dictate instead\" uses your device's speech recognition, on-device first. Text is added only when you tap Keep or Replace. Only kept text is stored — never audio.")
        Text("Map snapshots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("\"Add map snapshot\" reads your position once and fetches map tiles from OpenFreeMap. Tile requests reveal your approximate location; no incident details or photos are sent. The snapshot is encrypted like any photo and counts toward the 10-photo limit. Without tiles, an offline diagram is stored instead. Map data © OpenStreetMap contributors, tiles © OpenMapTiles / OpenFreeMap.")
        Text("AI assistant handoff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("\"Ask an AI app\" opens Android's share sheet to send this incident's details and photos to an assistant you trust (for example ChatGPT, Gemini or Copilot). ClaimConnect connects to no AI service itself and sends nothing until you confirm — you control, and pay for, that assistant directly.")
        Text("AI photo checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Optional and review-only: nothing reaches your pack unless you tap Keep. Save your own Meta API key in Settings and each confirmed check sends display copies (downscaled, location-stripped — never originals) to the Meta API. Usage bills to your Meta account; the key is encrypted on this device and revocable in your Meta console. Without a key, checks run on-device demos only. In service builds, checks instead use our quota service (free monthly allowance per install, one-time unlock for unlimited).")
        Text("Saving and sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Saving, previewing or sharing creates an unencrypted copy your provider may store or upload. A temporary copy stays in private cache and clears after 24 hours. ClaimConnect can't delete or recall exported copies.")
        Text("Photographs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Capture uses your camera app; the file waits in private cache until imported or discarded, and failed imports can retry. Imports use Android's photo picker. Originals may carry camera metadata including location. Hashes identify file bytes, not authenticity. PDFs hold scaled copies. Added times show when files entered the app.")
        Text("Retention and deletion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Records stay until you delete them or uninstall. To delete: open the incident, go to Review, choose Delete incident. App records and their evidence are removed; your other photos and exported copies stay under your control. Device backup is disabled (a restore elsewhere couldn't decrypt records) — use manual Backup in Settings; it restores on this device only.")
        Text("Scope", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(ProductLimits.NOTICE)
        Text("ClaimConnect doesn't submit claims or replace emergency services, your insurer or professional advice. Record relevant facts only.")
        Text("Help and pilot feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Get help via your pilot organiser's support channel. If an action fails, check storage and retry. Keep the app installed if a record won't open. Never put real incident details or photos in feedback.")
    }
}

@Composable private fun LocalNotice() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(ProductLimits.LOCAL_NOTICE, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
@Composable private fun Status(text: String) {
    Text(text, Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
}
@Composable private fun Field(label: String, value: String, tag: String, busy: Boolean, multiline: Boolean = false,
                             capitals: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { change(it.take(if (multiline) ProductLimits.MAX_TEXT_LENGTH else 200)) },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(tag), enabled = !busy,
        singleLine = !multiline, minLines = if(multiline) 5 else 1,
        keyboardOptions = KeyboardOptions(capitalization = if(capitals) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences))
}

private fun aiPrompt(incident: Incident, evidenceCount: Int): String = buildString {
    appendLine("Please help describe and analyse this vehicle incident record for an evidence pack.")
    appendLine()
    appendLine("Reference: ${incident.reference}")
    appendLine("Date/time: ${incident.date} ${incident.time} (${incident.timeZone})")
    appendLine("Location: ${incident.location.ifBlank { "Not recorded" }}")
    appendLine("Vehicle: ${"${incident.registration} ${incident.vehicle}".trim()}")
    appendLine("Driver: ${incident.driver}${if (incident.company.isNotBlank()) " (${incident.company})" else ""}")
    appendLine("Other parties/witnesses: ${incident.otherParties.ifBlank { "None recorded" }}")
    appendLine()
    appendLine("Account of what happened:")
    appendLine(incident.notes.ifBlank { "Not recorded" })
    appendLine()
    append("Attached: $evidenceCount photograph(s). Please suggest a clear, factual description and flag anything inconsistent or missing.")
}
