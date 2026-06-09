package com.skipperkit.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/* ════════════════════════════════════════════════════════════════════════
 *  SkipperKit — Settings screen
 *  Stateless composables driven by hoisted state + sample @Preview.
 *  Material 3 (Material You), dark-theme-first, light supported.
 *  Translated from the Claude Design handoff (SkipperKit Settings.html).
 * ════════════════════════════════════════════════════════════════════════ */

/* ── Data contract ─────────────────────────────────────────────────────── */

data class ServiceStatus(
    val enabledInSystem: Boolean,   // toggled on in Accessibility settings
    val running: Boolean,           // service currently bound
    val lastActivityText: String?,  // e.g. "2 min ago", null if never
    val healthy: Boolean,           // events seen recently
)

data class AppUiState(
    val packageName: String,
    val displayName: String,        // "Netflix", "Prime Video", "Disney+"
    val enabled: Boolean,
    val skipIntro: Boolean,
    val skipRecap: Boolean,
    val autoNext: Boolean,
    val autoNextSupported: Boolean = true, // false where the app exposes no usable next id (Prime)
    val removable: Boolean = false, // true for user-added apps
)

/** An installable app shown in the "add an app" picker. */
data class InstalledAppUi(val packageName: String, val displayName: String)

/** A skip button SkipperKit discovered that isn't configured yet, awaiting approval. */
data class SuggestionUi(
    val key: String,
    val appName: String,
    val label: String,   // "Skip Intro"
    val detail: String,  // what was matched, e.g. a view-id or visible text
)

data class SettingsUiState(
    val masterEnabled: Boolean,
    val service: ServiceStatus,
    val apps: List<AppUiState>,
    val suggestions: List<SuggestionUi> = emptyList(),
    val discoverySuggestionsEnabled: Boolean = true,
    val installedApps: List<InstalledAppUi> = emptyList(),
)

/* Feature keys used by onFeatureToggle. */
object SkipperFeature {
    const val SKIP_INTRO = "skipIntro"
    const val SKIP_RECAP = "skipRecap"
    const val AUTO_NEXT = "autoNext"
}

/* ── Semantic colors (success / warning) — M3 ColorScheme has no slots for
 *    these, so we ship them via a CompositionLocal alongside the theme. ──── */

data class SkipperSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val DarkSemantic = SkipperSemanticColors(
    success = Color(0xFF7FD89A),
    successContainer = Color(0xFF1D4A33),
    onSuccessContainer = Color(0xFFB6F2C8),
    warning = Color(0xFFF0C750),
    warningContainer = Color(0xFF4A3A05),
    onWarningContainer = Color(0xFFFFE08B),
)

private val LightSemantic = SkipperSemanticColors(
    success = Color(0xFF2E6B49),
    successContainer = Color(0xFFB4F1C7),
    onSuccessContainer = Color(0xFF00210F),
    warning = Color(0xFF7A5900),
    warningContainer = Color(0xFFFFE08B),
    onWarningContainer = Color(0xFF261A00),
)

private val LocalSkipperSemanticColors =
    staticCompositionLocalOf { DarkSemantic }

/* Brand accents — used as small leading accents only, never as backgrounds. */
private object Brand {
    val Netflix = Color(0xFFE50914)
    val Prime = Color(0xFF00A8E1)
    val Disney = Color(0xFF1F4FE0)

    fun accentFor(packageName: String, displayName: String): Pair<Color, Char> =
        when (packageName) {
            "com.netflix.mediaclient" -> Netflix to 'N'
            "com.amazon.avod.thirdpartyclient" -> Prime to 'P'
            "com.disney.disneyplus" -> Disney to 'D'
            else -> Color(0xFF8E9099) to (displayName.firstOrNull() ?: '?')
        }
}

/* ── Color schemes (calm blue seed) ────────────────────────────────────── */

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondaryContainer = Color(0xFF3A4759),
    onSecondaryContainer = Color(0xFFD7E3F8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3E4759),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun SkipperKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val semantic = if (darkTheme) DarkSemantic else LightSemantic
    CompositionLocalProvider(LocalSkipperSemanticColors provides semantic) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/* ════════════════════════════════════════════════════════════════════════
 *  Screen
 * ════════════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipperKitSettingsScreen(
    state: SettingsUiState,
    onMasterToggle: (Boolean) -> Unit,
    onAppEnabledToggle: (packageName: String, Boolean) -> Unit,
    onFeatureToggle: (packageName: String, feature: String, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
    onApproveSuggestion: (String) -> Unit = {},
    onDismissSuggestion: (String) -> Unit = {},
    onDiscoveryToggle: (Boolean) -> Unit = {},
    onRemoveApp: (packageName: String) -> Unit = {},
    onAddApp: (packageName: String, displayName: String) -> Unit = { _, _ -> },
    onLoadInstalledApps: () -> Unit = {},
    onExportApp: (packageName: String) -> Unit = {},
    onImportApp: (json: String) -> Boolean = { false },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SkipperKit",
                        fontWeight = FontWeight.Medium,
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("status") {
                StatusCard(
                    service = state.service,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                )
            }

            item("master") {
                MasterSwitchCard(
                    checked = state.masterEnabled,
                    onCheckedChange = onMasterToggle,
                )
            }

            if (state.suggestions.isNotEmpty()) {
                item("suggestions-label") { SectionLabel("Suggestions") }
                items(state.suggestions, key = { "sug-${it.key}" }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onApprove = { onApproveSuggestion(suggestion.key) },
                        onDismiss = { onDismissSuggestion(suggestion.key) },
                    )
                }
            }

            item("section") {
                SectionLabel("Streaming apps")
            }

            items(state.apps, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
                    masterEnabled = state.masterEnabled,
                    onAppEnabledToggle = onAppEnabledToggle,
                    onFeatureToggle = onFeatureToggle,
                    onRemoveApp = onRemoveApp,
                    onExportApp = onExportApp,
                )
            }

            item("add-app") {
                AddAppCard(
                    installed = state.installedApps,
                    onExpand = onLoadInstalledApps,
                    onPick = onAddApp,
                    onImport = onImportApp,
                )
            }

            item("general-label") { SectionLabel("General") }
            item("discovery-toggle") {
                ToggleRowCard(
                    title = "Suggest new skip buttons",
                    subtitle = "Notice skip buttons SkipperKit isn't configured for and offer to use them",
                    checked = state.discoverySuggestionsEnabled,
                    onCheckedChange = onDiscoveryToggle,
                )
            }
        }
    }
}

/* ── Generic single-toggle settings card ───────────────────────────────── */

@Composable
private fun ToggleRowCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(16.dp))
            SkipperSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/* ── Small uppercase section label. ────────────────────────────────────── */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}

/* ── Discovery suggestion card ─────────────────────────────────────────── */

@Composable
private fun SuggestionCard(
    suggestion: SuggestionUi,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Found a “${suggestion.label}” button in ${suggestion.appName}",
                color = cs.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Not in your config yet. Start using it?",
                color = cs.onSurfaceVariant,
                fontSize = 12.5.sp,
            )
            Text(
                suggestion.detail,
                color = cs.onSurfaceVariant,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onApprove) { Text("Use it") }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

/* ── Status card — the most prominent element. Three visual states. ─────── */

@Composable
private fun StatusCard(
    service: ServiceStatus,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val isOn = service.enabledInSystem && service.running
    if (!isOn) ServiceOffCard(onOpenAccessibilitySettings) else ServiceOnCard(service)
}

@Composable
private fun ServiceOffCard(onOpenAccessibilitySettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.errorContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusGlyph(
                    icon = Icons.Outlined.GppMaybe,
                    tint = cs.error,
                    container = Color.Black.copy(alpha = 0.22f),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "SERVICE OFF",
                        color = cs.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        "Not running",
                        color = cs.onErrorContainer,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "SkipperKit needs the accessibility service to work. " +
                    "It can't be turned on automatically.",
                color = cs.onErrorContainer,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(18.dp))
            FilledTonalButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = cs.error,
                    contentColor = cs.errorContainer,
                ),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Open Accessibility settings", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
            Row {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = cs.onErrorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Some phones require enabling Restricted settings first before the toggle becomes available.",
                    color = cs.onErrorContainer.copy(alpha = 0.85f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ServiceOnCard(service: ServiceStatus) {
    val sem = LocalSkipperSemanticColors.current
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = sem.successContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusGlyph(
                    icon = Icons.Outlined.GppGood,
                    tint = sem.success,
                    container = Color.Black.copy(alpha = 0.18f),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "SERVICE ACTIVE",
                        color = sem.success,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        "Running & protected",
                        color = sem.onSuccessContainer,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            // Subtle health line — healthy vs. "no events" warning.
            val healthy = service.healthy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (healthy) Color.Black.copy(alpha = 0.16f) else sem.warningContainer,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = if (healthy) Icons.Outlined.Bolt else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (healthy) sem.success else sem.warning,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (healthy) {
                        "Last activity: ${service.lastActivityText ?: "—"}"
                    } else {
                        "No events seen — open a supported app to test."
                    },
                    color = if (healthy) sem.onSuccessContainer else sem.onWarningContainer,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusGlyph(
    icon: ImageVector,
    tint: Color,
    container: Color,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(container, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/* ── Master enable switch ──────────────────────────────────────────────── */

@Composable
private fun MasterSwitchCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (checked) cs.secondaryContainer else cs.surfaceContainerHigh
    val titleColor = if (checked) cs.onSecondaryContainer else cs.onSurface
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Enable SkipperKit", color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Master switch for every supported app",
                    color = cs.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(16.dp))
            SkipperSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/* ── Per-app card ──────────────────────────────────────────────────────── */

@Composable
private fun AppCard(
    app: AppUiState,
    masterEnabled: Boolean,
    onAppEnabledToggle: (String, Boolean) -> Unit,
    onFeatureToggle: (String, String, Boolean) -> Unit,
    onRemoveApp: (String) -> Unit = {},
    onExportApp: (String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val subActive = masterEnabled && app.enabled
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Header: brand mark + name + per-app enable switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            BrandMark(app)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (masterEnabled) 1f else 0.55f),
            ) {
                Text(app.displayName, color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (app.enabled) "Active" else "Off",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            SkipperSwitch(
                checked = app.enabled,
                enabled = masterEnabled,
                onCheckedChange = { onAppEnabledToggle(app.packageName, it) },
            )
            if (app.removable) {
                TextButton(onClick = { onExportApp(app.packageName) }) {
                    Text("Share")
                }
                TextButton(onClick = { onRemoveApp(app.packageName) }) {
                    Text("Remove")
                }
            }
        }

        // Sub-toggles: always present; disabled/greyed when the app (or master) is off.
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)) {
            HorizontalDivider(color = cs.outlineVariant)
            FeatureRow(
                title = "Skip Intro",
                subtitle = "Tap the intro skip button",
                checked = app.skipIntro,
                enabled = subActive,
                onCheckedChange = { onFeatureToggle(app.packageName, SkipperFeature.SKIP_INTRO, it) },
            )
            FeatureRow(
                title = "Skip Recap",
                subtitle = "Tap “Skip recap” when shown",
                checked = app.skipRecap,
                enabled = subActive,
                onCheckedChange = { onFeatureToggle(app.packageName, SkipperFeature.SKIP_RECAP, it) },
            )
            FeatureRow(
                title = "Auto-play Next Episode",
                subtitle = if (app.autoNextSupported) {
                    "Hit “Next episode” automatically"
                } else {
                    "Not available in this app"
                },
                checked = app.autoNext && app.autoNextSupported,
                enabled = subActive && app.autoNextSupported,
                onCheckedChange = { onFeatureToggle(app.packageName, SkipperFeature.AUTO_NEXT, it) },
            )
        }
    }
}

@Composable
private fun AddAppCard(
    installed: List<InstalledAppUi>,
    onExpand: () -> Unit,
    onPick: (String, String) -> Unit,
    onImport: (String) -> Boolean = { false },
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Add an app", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                "Adding lets SkipperKit read this app's on-screen text to find skip buttons. " +
                    "It never captures video or sends anything off-device. Manage anytime.",
                color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { open = true; onExpand() }) { Text("Choose an app") }
                TextButton(onClick = { importOpen = true }) { Text("Import shared") }
            }
            if (importOpen) {
                ImportAppDialog(
                    onImport = onImport,
                    onClose = { importOpen = false },
                )
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                installed.forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.packageName, app.displayName); open = false }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(app.displayName, color = cs.onSurface, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportAppDialog(
    onImport: (String) -> Boolean,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Import a shared app") },
        text = {
            Column {
                Text(
                    "Paste a configuration another SkipperKit user shared. It can only " +
                        "add skip buttons for the one app it names — you can remove it anytime.",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "That doesn't look like a shared SkipperKit app.",
                        color = cs.error,
                        fontSize = 12.5.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onImport(text)) onClose() else error = true }) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel") }
        },
    )
}

@Composable
private fun FeatureRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.45f),
        ) {
            Text(title, color = cs.onSurface, fontSize = 15.sp)
            Text(subtitle, color = cs.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        SkipperSwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/*
 * Brand mark — shows each app's REAL launcher icon, loaded at runtime from
 * PackageManager. Nothing is bundled and no logo is recreated, so there are no
 * trademark/asset concerns: it's simply the icon the streaming app ships with.
 * Falls back to a branded letter tile when the app isn't installed or in
 * @Preview (where the sample package names don't resolve).
 */
@Composable
private fun BrandMark(app: AppUiState) {
    val cs = MaterialTheme.colorScheme
    val (color, letter) = Brand.accentFor(app.packageName, app.displayName)
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(app.packageName) {
        runCatching {
            // Explicit size: adaptive icons can report no intrinsic size, which
            // makes the no-arg toBitmap() throw.
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = app.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color),
            )
            Text(letter.toString(), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ── Switch with M3 checkmark thumb ────────────────────────────────────── */

@Composable
private fun SkipperSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = if (checked) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else null,
    )
}

/* ════════════════════════════════════════════════════════════════════════
 *  Preview — service OFF + populated 3-app list
 * ════════════════════════════════════════════════════════════════════════ */

private val sampleApps = listOf(
    AppUiState(
        packageName = "com.netflix.mediaclient",
        displayName = "Netflix",
        enabled = true, skipIntro = true, skipRecap = true, autoNext = false,
    ),
    AppUiState(
        packageName = "com.amazon.avod.thirdpartyclient",
        displayName = "Prime Video",
        enabled = true, skipIntro = true, skipRecap = false, autoNext = false,
        autoNextSupported = false,
    ),
    AppUiState(
        packageName = "com.disney.disneyplus",
        displayName = "Disney+",
        enabled = false, skipIntro = true, skipRecap = true, autoNext = true,
    ),
)

@Preview(name = "Settings · Service OFF", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun SettingsScreenOffPreview() {
    SkipperKitTheme(darkTheme = true) {
        Surface {
            SkipperKitSettingsScreen(
                state = SettingsUiState(
                    masterEnabled = true,
                    service = ServiceStatus(
                        enabledInSystem = false,
                        running = false,
                        lastActivityText = null,
                        healthy = false,
                    ),
                    apps = sampleApps,
                ),
                onMasterToggle = {},
                onAppEnabledToggle = { _, _ -> },
                onFeatureToggle = { _, _, _ -> },
                onOpenAccessibilitySettings = {},
            )
        }
    }
}

@Preview(name = "Settings · Service ON (healthy)", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun SettingsScreenOnPreview() {
    SkipperKitTheme(darkTheme = true) {
        Surface {
            SkipperKitSettingsScreen(
                state = SettingsUiState(
                    masterEnabled = true,
                    service = ServiceStatus(
                        enabledInSystem = true,
                        running = true,
                        lastActivityText = "2 min ago",
                        healthy = true,
                    ),
                    apps = sampleApps,
                ),
                onMasterToggle = {},
                onAppEnabledToggle = { _, _ -> },
                onFeatureToggle = { _, _, _ -> },
                onOpenAccessibilitySettings = {},
            )
        }
    }
}

@Preview(name = "Settings · Light", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun SettingsScreenLightPreview() {
    SkipperKitTheme(darkTheme = false) {
        Surface {
            SkipperKitSettingsScreen(
                state = SettingsUiState(
                    masterEnabled = true,
                    service = ServiceStatus(
                        enabledInSystem = true,
                        running = true,
                        lastActivityText = null,
                        healthy = false,
                    ),
                    apps = sampleApps,
                ),
                onMasterToggle = {},
                onAppEnabledToggle = { _, _ -> },
                onFeatureToggle = { _, _, _ -> },
                onOpenAccessibilitySettings = {},
            )
        }
    }
}
