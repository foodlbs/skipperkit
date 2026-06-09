package com.skipperkit.config

import com.skipperkit.discovery.DiscoveredEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the active *base* configs, composed as **bundled ⊕ remote ⊕ discovered**:
 * the verified [DefaultConfigs], overlaid by a remote/cached config (Phase 6), then
 * overlaid by user-approved discovered identifiers (self-healing). User feature
 * toggles are applied on top of this by
 * [com.skipperkit.settings.SettingsRepository].
 */
object ConfigRepository {

    private var remoteOverride: List<AppConfig> = emptyList()
    private var discovered: List<DiscoveredEntry> = emptyList()

    private val _configs = MutableStateFlow(DefaultConfigs.ALL)
    val configs: StateFlow<List<AppConfig>> = _configs.asStateFlow()

    fun forPackage(packageName: String): AppConfig? =
        _configs.value.firstOrNull { it.packageName == packageName }

    /** Overlay a remote/cached config; empty input is ignored (a failed fetch can't blank configs). */
    fun applyRemote(remote: List<AppConfig>) {
        if (remote.isEmpty()) return
        remoteOverride = remote
        recompute()
    }

    /** Replace the set of user-approved discovered identifiers. */
    fun setDiscovered(entries: List<DiscoveredEntry>) {
        discovered = entries
        recompute()
    }

    private fun recompute() {
        val base = overlayRemote(DefaultConfigs.ALL, remoteOverride)
        _configs.value = applyDiscovered(base, discovered)
    }

    private fun overlayRemote(bundled: List<AppConfig>, remote: List<AppConfig>): List<AppConfig> {
        if (remote.isEmpty()) return bundled
        val remoteByPkg = remote.associateBy { it.packageName }
        val ordered = bundled.map { remoteByPkg[it.packageName] ?: it }
        val extras = remote.filter { r -> bundled.none { it.packageName == r.packageName } }
        return ordered + extras
    }

    private fun applyDiscovered(base: List<AppConfig>, entries: List<DiscoveredEntry>): List<AppConfig> {
        if (entries.isEmpty()) return base
        val byPkg = entries.groupBy { it.packageName }
        return base.map { config ->
            val forPkg = byPkg[config.packageName] ?: return@map config
            var c = config
            for (e in forPkg) {
                c = when (e.target) {
                    SkipTarget.SKIP_INTRO -> c.copy(
                        skipIntroViewIds = c.skipIntroViewIds.appendIfPresent(e.viewId),
                        skipIntroLabels = c.skipIntroLabels.appendIfPresent(e.label),
                    )
                    SkipTarget.SKIP_RECAP -> c.copy(
                        skipRecapViewIds = c.skipRecapViewIds.appendIfPresent(e.viewId),
                        skipRecapLabels = c.skipRecapLabels.appendIfPresent(e.label),
                    )
                    // Only end-of-episode cards reach here (vetted by the engine);
                    // still gated at runtime by the per-app auto-next toggle.
                    SkipTarget.NEXT_EPISODE -> c.copy(
                        nextEpisodeViewIds = c.nextEpisodeViewIds.appendIfPresent(e.viewId),
                        nextEpisodeLabels = c.nextEpisodeLabels.appendIfPresent(e.label),
                    )
                }
            }
            c
        }
    }

    private fun List<String>.appendIfPresent(value: String?): List<String> =
        if (value.isNullOrEmpty() || contains(value)) this else this + value
}
