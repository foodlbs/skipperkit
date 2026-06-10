package com.skipperkit.settings

import com.skipperkit.config.ConfigRepository
import com.skipperkit.discovery.DiscoveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A streaming/video app the user added beyond the built-ins. */
data class TaughtApp(val packageName: String, val displayName: String)

/**
 * User-added apps. The accessibility service widens its scope to include these,
 * and [ConfigRepository] synthesizes an empty config stub for each so the existing
 * discovery + confirm-once pipeline can populate its skip buttons. Persistence is
 * handled by the caller (SkipperApp) via [onChanged] + [restore].
 */
object TaughtAppsRepository {

    private val _taughtApps = MutableStateFlow<List<TaughtApp>>(emptyList())
    val taughtApps: StateFlow<List<TaughtApp>> = _taughtApps.asStateFlow()

    /** Invoked whenever the set changes, so the caller can persist it. */
    @Volatile var onChanged: ((List<TaughtApp>) -> Unit)? = null

    fun currentPackages(): List<String> = _taughtApps.value.map { it.packageName }

    @Synchronized
    fun add(app: TaughtApp) {
        if (_taughtApps.value.any { it.packageName == app.packageName }) return
        _taughtApps.value = _taughtApps.value + app
        ConfigRepository.setTaughtApps(currentPackages())
        SettingsRepository.ensureApp(app.packageName)
        onChanged?.invoke(_taughtApps.value)
    }

    @Synchronized
    fun remove(packageName: String) {
        if (_taughtApps.value.none { it.packageName == packageName }) return
        _taughtApps.value = _taughtApps.value.filterNot { it.packageName == packageName }
        ConfigRepository.setTaughtApps(currentPackages())
        DiscoveryRepository.removeForPackage(packageName)
        CustomButtonsRepository.removeForPackage(packageName)
        SettingsRepository.dropApp(packageName)
        onChanged?.invoke(_taughtApps.value)
    }

    /** Re-hydrate persisted apps on startup (before wiring [onChanged]). */
    @Synchronized
    fun restore(apps: List<TaughtApp>) {
        _taughtApps.value = apps
        ConfigRepository.setTaughtApps(currentPackages())
        apps.forEach { SettingsRepository.ensureApp(it.packageName) }
    }
}
