package com.skipperkit.settings

import com.skipperkit.config.AppConfig
import com.skipperkit.config.ConfigRepository
import com.skipperkit.config.DefaultConfigs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-app user toggles surfaced by the settings UI. */
data class AppToggles(
    val enabled: Boolean,
    val skipIntro: Boolean,
    val skipRecap: Boolean,
    val autoNext: Boolean,
)

data class UserSettings(
    val masterEnabled: Boolean,
    val apps: Map<String, AppToggles>,
    val discoverySuggestions: Boolean = true,
)

/**
 * In-memory source of truth for the settings UI and the accessibility service.
 * The UI observes [settings]; the service reads [configFor] each pass. Persistence
 * across process death is intentionally deferred to Phase 6 (DataStore) — for now
 * toggles take effect for the lifetime of the process.
 *
 * A plain object (process-wide singleton) is enough because both the UI and the
 * service live in the same process.
 */
object SettingsRepository {

    private val _settings = MutableStateFlow(defaultSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    /** Default toggles, exposed so persistence can fill gaps with bundled values. */
    fun snapshotDefaults(): UserSettings = defaultSettings()

    /** Replace current toggles, e.g. from persisted DataStore values on startup. */
    fun restore(settings: UserSettings) {
        _settings.value = settings
    }

    private fun defaultSettings(): UserSettings {
        val apps = DefaultConfigs.ALL.associate { cfg ->
            cfg.packageName to AppToggles(
                enabled = cfg.enabled,
                skipIntro = cfg.skipIntroViewIds.isNotEmpty() || cfg.skipIntroLabels.isNotEmpty(),
                skipRecap = cfg.skipRecapViewIds.isNotEmpty() || cfg.skipRecapLabels.isNotEmpty(),
                autoNext = cfg.autoNextEnabled,
            )
        }
        return UserSettings(masterEnabled = true, apps = apps, discoverySuggestions = true)
    }

    fun setMaster(enabled: Boolean) {
        _settings.value = _settings.value.copy(masterEnabled = enabled)
    }

    fun setDiscoverySuggestions(enabled: Boolean) {
        _settings.value = _settings.value.copy(discoverySuggestions = enabled)
    }

    /** Read by the service to decide whether to offer discovery suggestions. */
    fun discoverySuggestionsEnabled(): Boolean = _settings.value.discoverySuggestions

    fun setAppEnabled(packageName: String, enabled: Boolean) =
        update(packageName) { it.copy(enabled = enabled) }

    fun setFeature(packageName: String, feature: String, value: Boolean) =
        update(packageName) { toggles ->
            when (feature) {
                "skipIntro" -> toggles.copy(skipIntro = value)
                "skipRecap" -> toggles.copy(skipRecap = value)
                "autoNext" -> toggles.copy(autoNext = value)
                else -> toggles
            }
        }

    @Synchronized
    fun ensureApp(packageName: String) {
        val cur = _settings.value
        if (cur.apps.containsKey(packageName)) return
        _settings.value = cur.copy(
            apps = cur.apps + (packageName to AppToggles(enabled = true, skipIntro = true, skipRecap = true, autoNext = false)),
        )
    }

    @Synchronized
    fun dropApp(packageName: String) {
        val cur = _settings.value
        if (!cur.apps.containsKey(packageName)) return
        _settings.value = cur.copy(apps = cur.apps - packageName)
    }

    private fun update(packageName: String, transform: (AppToggles) -> AppToggles) {
        val current = _settings.value
        val toggles = current.apps[packageName] ?: return
        _settings.value = current.copy(apps = current.apps + (packageName to transform(toggles)))
    }

    /**
     * The config the engine should act on, with user toggles applied on top of the
     * verified [DefaultConfigs] identifiers. Disabled features have their id/label
     * lists emptied — empty lists never match, so no engine change is needed.
     */
    fun configFor(packageName: String): AppConfig? {
        val base = ConfigRepository.forPackage(packageName) ?: return null
        val s = _settings.value
        if (!s.masterEnabled) return base.copy(enabled = false)
        val toggles = s.apps[packageName] ?: return base
        return base.copy(
            enabled = toggles.enabled,
            skipIntroViewIds = if (toggles.skipIntro) base.skipIntroViewIds else emptyList(),
            skipIntroLabels = if (toggles.skipIntro) base.skipIntroLabels else emptyList(),
            skipIntroLabelPrefixes = if (toggles.skipIntro) base.skipIntroLabelPrefixes else emptyList(),
            skipRecapViewIds = if (toggles.skipRecap) base.skipRecapViewIds else emptyList(),
            skipRecapLabels = if (toggles.skipRecap) base.skipRecapLabels else emptyList(),
            skipRecapLabelPrefixes = if (toggles.skipRecap) base.skipRecapLabelPrefixes else emptyList(),
            autoNextEnabled = toggles.autoNext,
        )
    }
}
