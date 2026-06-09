package com.skipperkit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skipperkit.config.ConfigRepository
import com.skipperkit.discovery.DiscoveryRepository
import com.skipperkit.service.InstalledAppsProvider
import com.skipperkit.service.ServiceRuntime
import com.skipperkit.service.SkipAccessibilityService
import com.skipperkit.settings.SettingsRepository
import com.skipperkit.settings.TaughtApp
import com.skipperkit.settings.TaughtAppPort
import com.skipperkit.settings.TaughtAppsRepository
import com.skipperkit.ui.settings.AppUiState
import com.skipperkit.ui.settings.InstalledAppUi
import com.skipperkit.ui.settings.ServiceStatus
import com.skipperkit.ui.settings.SettingsUiState
import com.skipperkit.ui.settings.SkipperKitSettingsScreen
import com.skipperkit.ui.settings.SkipperKitTheme
import com.skipperkit.ui.settings.SuggestionUi

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
    var installed by remember { mutableStateOf(emptyList<InstalledAppUi>()) }
    val provider = remember { InstalledAppsProvider(context) }
    val pickerScope = rememberCoroutineScope()

    // Whether the service is enabled in system Accessibility settings can change
    // while we're backgrounded (the user toggles it there), so refresh on RESUME.
    var enabledInSystem by remember { mutableStateOf(isServiceEnabledInSystem(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabledInSystem = isServiceEnabledInSystem(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val now = System.currentTimeMillis()
    val healthy = running && lastActivityMs != null && now - lastActivityMs!! < HEALTHY_WINDOW_MS

    val taughtNames = taughtApps.associate { it.packageName to it.displayName }
    val apps = baseConfigs.map { base ->
        val toggles = userSettings.apps[base.packageName]
        val autoNextSupported = base.nextEpisodeViewIds.isNotEmpty() ||
            base.nextEpisodeLabels.isNotEmpty() ||
            base.nextEpisodeLabelPrefixes.isNotEmpty()
        AppUiState(
            packageName = base.packageName,
            displayName = taughtNames[base.packageName] ?: displayNameFor(base.packageName),
            enabled = toggles?.enabled ?: base.enabled,
            skipIntro = toggles?.skipIntro ?: true,
            skipRecap = toggles?.skipRecap ?: true,
            autoNext = toggles?.autoNext ?: base.autoNextEnabled,
            autoNextSupported = autoNextSupported,
            removable = taughtApps.any { it.packageName == base.packageName },
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
    )

    SkipperKitSettingsScreen(
        state = state,
        onMasterToggle = SettingsRepository::setMaster,
        onAppEnabledToggle = SettingsRepository::setAppEnabled,
        onFeatureToggle = SettingsRepository::setFeature,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onApproveSuggestion = DiscoveryRepository::approve,
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
            val json = TaughtAppPort.export(app, DiscoveryRepository.approvedForPackage(pkg))
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
                TaughtAppsRepository.add(shared.app)
                DiscoveryRepository.addApproved(shared.entries)
            }
            shared != null
        },
    )
}

private const val HEALTHY_WINDOW_MS = 5 * 60 * 1000L

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
