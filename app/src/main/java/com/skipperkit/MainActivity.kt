package com.skipperkit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skipperkit.config.ConfigRepository
import com.skipperkit.config.DefaultConfigs
import com.skipperkit.contribute.ContributionPort
import com.skipperkit.contribute.ContributionSender
import com.skipperkit.data.SettingsStore
import com.skipperkit.discovery.DiscoveryRepository
import com.skipperkit.service.InstalledAppsProvider
import com.skipperkit.service.ServiceRuntime
import com.skipperkit.service.SkipAccessibilityService
import com.skipperkit.config.CustomButton
import com.skipperkit.settings.CustomButtonsRepository
import com.skipperkit.settings.SettingsRepository
import com.skipperkit.settings.TaughtApp
import com.skipperkit.settings.TaughtAppPort
import com.skipperkit.settings.TaughtAppsRepository
import com.skipperkit.teach.TeachModeRepository
import com.skipperkit.ui.settings.AppUiState
import com.skipperkit.ui.settings.ContributionConsentDialog
import com.skipperkit.ui.settings.CustomButtonUi
import com.skipperkit.ui.settings.InstalledAppUi
import com.skipperkit.ui.settings.ServiceStatus
import com.skipperkit.ui.settings.SettingsUiState
import com.skipperkit.ui.settings.SkipperKitSettingsScreen
import com.skipperkit.ui.settings.SkipperKitTheme
import com.skipperkit.ui.settings.SuggestionUi
import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import com.skipperkit.ui.settings.TeachCandidateUi
import com.skipperkit.ui.settings.TeachKind
import com.skipperkit.ui.settings.TeachNameDialog
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkipperKitTheme {
                SettingsRoute(
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsRoute(onOpenAccessibilitySettings: () -> Unit) {
    val context = LocalContext.current

    val userSettings by SettingsRepository.settings.collectAsState()
    val baseConfigs by ConfigRepository.configs.collectAsState()
    val running by ServiceRuntime.running.collectAsState()
    val lastActivityMs by ServiceRuntime.lastActivityMs.collectAsState()
    val pendingSuggestions by DiscoveryRepository.pending.collectAsState()
    val taughtApps by TaughtAppsRepository.taughtApps.collectAsState()
    val teachArmed by TeachModeRepository.armedPackage.collectAsState()
    val teachCandidates by TeachModeRepository.candidates.collectAsState()
    val customButtonsMap by CustomButtonsRepository.buttons.collectAsState()
    var installed by remember { mutableStateOf(emptyList<InstalledAppUi>()) }
    val provider = remember { InstalledAppsProvider(context) }
    val pickerScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    var contributeOfferPkg by remember { mutableStateOf<String?>(null) }
    var consentPayloads by remember { mutableStateOf<List<String>?>(null) }
    var sendInFlight by remember { mutableStateOf(false) }
    var teachPickKey by remember { mutableStateOf<String?>(null) }

    // Whether the service is enabled in system Accessibility settings can change
    // while we're backgrounded (the user toggles it there), so refresh on RESUME.
    var enabledInSystem by remember { mutableStateOf(isServiceEnabledInSystem(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabledInSystem = isServiceEnabledInSystem(context)
                TeachModeRepository.expireIfStale()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val now = System.currentTimeMillis()
    val healthy = running && lastActivityMs != null && now - lastActivityMs!! < HEALTHY_WINDOW_MS

    val taughtNames = taughtApps.associate { it.packageName to it.displayName }

    fun appVersionOf(pkg: String): String? = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).versionName
    }.getOrNull()

    fun contributableEntries(pkg: String) = DiscoveryRepository.approvedForPackage(pkg)
    fun contributableCustoms(pkg: String) = (customButtonsMap[pkg] ?: emptyList()).filter { it.enabled }
    fun isContributable(pkg: String) =
        contributableEntries(pkg).isNotEmpty() || contributableCustoms(pkg).isNotEmpty()

    fun buildPayload(pkg: String): String? = ContributionPort.build(
        packageName = pkg,
        displayName = taughtNames[pkg] ?: displayNameFor(pkg),
        entries = contributableEntries(pkg),
        appVersionName = appVersionOf(pkg),
        skipperkitVersion = BuildConfig.VERSION_NAME,
        locale = Locale.getDefault().language,
        customButtons = contributableCustoms(pkg),
    )

    fun sendPayloads(payloads: List<String>) {
        if (sendInFlight || payloads.isEmpty()) return
        sendInFlight = true
        pickerScope.launch {
            try {
                // Concurrent sends: worst case is one 8 s timeout, not payloads × 8 s.
                val sent = withContext(Dispatchers.IO) {
                    payloads.map { p ->
                        async { ContributionSender.send(SettingsStore.CONTRIBUTION_URL, p) }
                    }.awaitAll().count { it }
                }
                Toast.makeText(
                    context,
                    when {
                        sent == 0 -> "Couldn't send — try again later"
                        sent < payloads.size -> "Sent $sent of ${payloads.size} — thank you!"
                        else -> "Sent — thank you!"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                sendInFlight = false
            }
        }
    }

    fun contributePayloads(payloads: List<String>) {
        if (payloads.isEmpty()) return
        pickerScope.launch {
            if (settingsStore.contributionConsent()) sendPayloads(payloads)
            else consentPayloads = payloads
        }
    }

    fun contribute(pkg: String) = contributePayloads(listOfNotNull(buildPayload(pkg)))

    fun contributeAll() = contributePayloads(
        baseConfigs.map { it.packageName }
            .filter(::isContributable)
            .mapNotNull(::buildPayload)
            .take(MAX_CONTRIBUTE_ALL), // server allows 10 submissions/IP/day
    )

    val apps = baseConfigs.map { base ->
        val toggles = userSettings.apps[base.packageName]
        val autoNextSupported = base.nextEpisodeViewIds.isNotEmpty() ||
            base.nextEpisodeLabels.isNotEmpty() ||
            base.nextEpisodeLabelPrefixes.isNotEmpty()
        val skipIntroSupported = base.skipIntroViewIds.isNotEmpty() || base.skipIntroLabels.isNotEmpty() ||
            base.skipIntroLabelPrefixes.isNotEmpty()
        val skipRecapSupported = base.skipRecapViewIds.isNotEmpty() || base.skipRecapLabels.isNotEmpty() ||
            base.skipRecapLabelPrefixes.isNotEmpty()
        AppUiState(
            packageName = base.packageName,
            displayName = taughtNames[base.packageName] ?: displayNameFor(base.packageName),
            enabled = toggles?.enabled ?: base.enabled,
            skipIntro = toggles?.skipIntro ?: true,
            skipRecap = toggles?.skipRecap ?: true,
            autoNext = toggles?.autoNext ?: base.autoNextEnabled,
            autoNextSupported = autoNextSupported,
            skipIntroSupported = skipIntroSupported,
            skipRecapSupported = skipRecapSupported,
            removable = taughtApps.any { it.packageName == base.packageName },
            contributable = isContributable(base.packageName),
            customButtons = (customButtonsMap[base.packageName] ?: emptyList()).map {
                CustomButtonUi(it.key, it.name, it.enabled)
            },
        )
    }

    val suggestions = pendingSuggestions.map { e ->
        SuggestionUi(
            key = e.key,
            appName = displayNameFor(e.packageName),
            label = e.displayLabel,
            detail = e.viewId ?: e.label.orEmpty(),
        )
    }

    val state = SettingsUiState(
        masterEnabled = userSettings.masterEnabled,
        service = ServiceStatus(
            enabledInSystem = enabledInSystem,
            running = running,
            lastActivityText = lastActivityMs?.let { relativeTime(now - it) },
            healthy = healthy,
        ),
        apps = apps,
        suggestions = suggestions,
        discoverySuggestionsEnabled = userSettings.discoverySuggestions,
        installedApps = installed,
        teachArmedPackage = teachArmed,
        teachCandidates = teachCandidates.map { TeachCandidateUi(it.key, it.viewId, it.text) },
    )

    SkipperKitSettingsScreen(
        state = state,
        onMasterToggle = SettingsRepository::setMaster,
        onAppEnabledToggle = SettingsRepository::setAppEnabled,
        onFeatureToggle = SettingsRepository::setFeature,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onApproveSuggestion = { key ->
            val pkg = DiscoveryRepository.pending.value.firstOrNull { it.key == key }?.packageName
            DiscoveryRepository.approve(key)
            contributeOfferPkg = pkg
        },
        onDismissSuggestion = DiscoveryRepository::dismiss,
        onDiscoveryToggle = SettingsRepository::setDiscoverySuggestions,
        onRemoveApp = TaughtAppsRepository::remove,
        onAddApp = { pkg, name -> TaughtAppsRepository.add(TaughtApp(pkg, name)) },
        onLoadInstalledApps = {
            val taken = baseConfigs.map { it.packageName }.toSet() + context.packageName
            pickerScope.launch {
                val result = withContext(Dispatchers.IO) {
                    provider.launchableApps(exclude = taken).map { InstalledAppUi(it.packageName, it.displayName) }
                }
                installed = result
            }
        },
        onExportApp = { pkg ->
            val app = taughtApps.firstOrNull { it.packageName == pkg } ?: TaughtApp(pkg, displayNameFor(pkg))
            val json = TaughtAppPort.export(app, DiscoveryRepository.approvedForPackage(pkg), customButtonsMap[pkg] ?: emptyList())
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "SkipperKit config: ${app.displayName}")
                putExtra(Intent.EXTRA_TEXT, json)
            }
            context.startActivity(Intent.createChooser(send, "Share ${app.displayName} config"))
        },
        onImportApp = { json ->
            val shared = TaughtAppPort.parse(json)
            if (shared != null) {
                // Built-ins must not become "taught": that would render them
                // removable, and removing would wipe their toggles and config.
                if (DefaultConfigs.forPackage(shared.app.packageName) == null) {
                    TaughtAppsRepository.add(shared.app)
                }
                DiscoveryRepository.addApproved(shared.entries)
                shared.customButtons.forEach { CustomButtonsRepository.add(shared.app.packageName, it) }
            }
            shared != null
        },
        onContributeApp = ::contribute,
        onContributeAll = ::contributeAll,
        contributeOffer = contributeOfferPkg?.let { taughtNames[it] ?: displayNameFor(it) },
        onContributeOfferSend = { contributeOfferPkg?.let { contribute(it) }; contributeOfferPkg = null },
        onContributeOfferDismiss = { contributeOfferPkg = null },
        onTeachApp = { pkg -> TeachModeRepository.arm(pkg) },
        onTeachCancel = { TeachModeRepository.disarm() },
        onTeachPick = { key -> teachPickKey = key },
        onCustomButtonToggle = CustomButtonsRepository::setEnabled,
        onCustomButtonRemove = CustomButtonsRepository::remove,
    )

    consentPayloads?.let { payloads ->
        ContributionConsentDialog(
            payload = payloads.joinToString("\n\n"),
            onConfirm = {
                pickerScope.launch { settingsStore.saveContributionConsent(true) }
                sendPayloads(payloads)
                consentPayloads = null
            },
            onDismiss = { consentPayloads = null },
        )
    }

    teachPickKey?.let { key ->
        val candidate = teachCandidates.firstOrNull { it.key == key }
        val armedPkg = teachArmed
        if (candidate == null || armedPkg == null) {
            Toast.makeText(context, "Teach session expired — tap Teach to try again", Toast.LENGTH_SHORT).show()
            teachPickKey = null
        } else {
            TeachNameDialog(
                candidate = TeachCandidateUi(candidate.key, candidate.viewId, candidate.text),
                onConfirm = { kind, name ->
                    when (kind) {
                        TeachKind.OTHER -> CustomButtonsRepository.add(
                            armedPkg,
                            CustomButton(
                                key = candidate.key,
                                name = name,
                                viewIds = listOfNotNull(candidate.viewId),
                                labels = if (candidate.viewId == null) listOfNotNull(candidate.text) else emptyList(),
                            ),
                        )
                        else -> {
                            val target = if (kind == TeachKind.SKIP_INTRO) SkipTarget.SKIP_INTRO else SkipTarget.SKIP_RECAP
                            DiscoveryRepository.addApproved(
                                listOf(
                                    DiscoveredEntry(
                                        packageName = armedPkg,
                                        target = target,
                                        viewId = candidate.viewId,
                                        label = if (candidate.viewId == null) candidate.text else null,
                                    ),
                                ),
                            )
                            // Taught a shareable skip button — offer to send it upstream.
                            contributeOfferPkg = armedPkg
                        }
                    }
                    TeachModeRepository.disarm()
                    teachPickKey = null
                },
                onDismiss = { teachPickKey = null },
            )
        }
    }
}

private const val HEALTHY_WINDOW_MS = 5 * 60 * 1000L
private const val MAX_CONTRIBUTE_ALL = 10

private fun displayNameFor(packageName: String): String = when (packageName) {
    "com.netflix.mediaclient" -> "Netflix"
    "com.amazon.avod.thirdpartyclient" -> "Prime Video"
    "com.disney.disneyplus" -> "Disney+"
    "com.crunchyroll.crunchyroid" -> "Crunchyroll"
    "com.wbd.stream" -> "HBO Max"
    "com.hulu.plus" -> "Hulu"
    "com.cbs.app" -> "Paramount+"
    "com.peacocktv.peacockandroid" -> "Peacock"
    "com.apple.atve.androidtv.appletv" -> "Apple TV"
    else -> packageName
}

private fun relativeTime(ageMs: Long): String = when {
    ageMs < 60_000 -> "just now"
    ageMs < 3_600_000 -> "${ageMs / 60_000} min ago"
    else -> "${ageMs / 3_600_000} hr ago"
}

private fun isServiceEnabledInSystem(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val component = ComponentName(context, SkipAccessibilityService::class.java).flattenToString()
    return enabled.split(':').any { it.equals(component, ignoreCase = true) }
}
