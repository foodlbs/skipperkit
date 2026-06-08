package com.skipperkit.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the active *base* configs (the verified identifiers), which start as the
 * bundled [DefaultConfigs] and can be replaced by remote/cached config without an
 * app release (Phase 6). User toggles live separately in
 * [com.skipperkit.settings.SettingsRepository] and are applied on top.
 */
object ConfigRepository {

    private val _configs = MutableStateFlow(DefaultConfigs.ALL)
    val configs: StateFlow<List<AppConfig>> = _configs.asStateFlow()

    fun forPackage(packageName: String): AppConfig? =
        _configs.value.firstOrNull { it.packageName == packageName }

    /**
     * Overlay a remote/cached config over the bundled set: matching packages are
     * replaced, bundled-only packages are kept, remote-only packages are appended.
     * Order is deterministic (bundled order first). Empty input is ignored so a
     * failed/empty fetch can never blank out the configs.
     */
    fun applyRemote(remote: List<AppConfig>) {
        if (remote.isEmpty()) return
        val remoteByPkg = remote.associateBy { it.packageName }
        val ordered = DefaultConfigs.ALL.map { remoteByPkg[it.packageName] ?: it }
        val extras = remote.filter { r -> DefaultConfigs.ALL.none { it.packageName == r.packageName } }
        _configs.value = ordered + extras
    }
}
