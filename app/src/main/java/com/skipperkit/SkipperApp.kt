package com.skipperkit

import android.app.Application
import android.util.Log
import com.skipperkit.config.ConfigRepository
import com.skipperkit.config.RemoteConfigParser
import com.skipperkit.config.RemoteConfigSync
import com.skipperkit.data.SettingsStore
import com.skipperkit.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process entry point (Phase 6). On startup, in this order:
 *   1. restore persisted user toggles from DataStore,
 *   2. apply the cached remote config (works offline),
 *   3. fetch a fresh remote config over HTTPS; on success cache + apply it,
 *   4. keep persisting future toggle changes.
 *
 * Every step degrades gracefully: a missing/failed remote config just leaves the
 * bundled (or last cached) identifiers in place.
 */
class SkipperApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)

        scope.launch {
            // 1. Restore user toggles before the UI/service read them.
            runCatching {
                SettingsRepository.restore(store.loadUserSettings(SettingsRepository.snapshotDefaults()))
            }.onFailure { Log.w(TAG, "Could not load saved settings", it) }

            // 2. Apply cached remote config, if any.
            runCatching { store.cachedRemoteConfigJson() }.getOrNull()?.let { cached ->
                runCatching { RemoteConfigParser.parse(cached) }
                    .getOrNull()
                    ?.let { ConfigRepository.applyRemote(it) }
            }

            // 3. Fetch fresh; cache + apply only on a clean parse.
            val url = runCatching { store.remoteConfigUrl() }.getOrNull()
            if (url != null) {
                RemoteConfigSync.fetch(url)?.let { fresh ->
                    val parsed = runCatching { RemoteConfigParser.parse(fresh) }.getOrNull()
                    if (!parsed.isNullOrEmpty()) {
                        ConfigRepository.applyRemote(parsed)
                        runCatching { store.saveRemoteConfigJson(fresh) }
                        Log.i(TAG, "Applied remote config (${parsed.size} apps)")
                    }
                }
            }

            // 4. Persist all future toggle changes.
            SettingsRepository.settings.collect { runCatching { store.saveUserSettings(it) } }
        }
    }

    companion object {
        private const val TAG = "SkipperKit"
    }
}
