package com.skipperkit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skipperkit.settings.AppToggles
import com.skipperkit.settings.UserSettings
import kotlinx.coroutines.flow.first

/**
 * Preferences DataStore persistence (Phase 6): user toggles, the cached remote
 * config JSON, and the remote config URL. User toggles survive process death;
 * remote config is cached so the app works offline after the first successful
 * fetch.
 */
class SettingsStore(private val context: Context) {

    suspend fun loadUserSettings(defaults: UserSettings): UserSettings {
        val prefs = context.dataStore.data.first()
        val master = prefs[MASTER_ENABLED] ?: defaults.masterEnabled
        val apps = defaults.apps.mapValues { (pkg, d) ->
            AppToggles(
                enabled = prefs[appKey(pkg, "enabled")] ?: d.enabled,
                skipIntro = prefs[appKey(pkg, "skipIntro")] ?: d.skipIntro,
                skipRecap = prefs[appKey(pkg, "skipRecap")] ?: d.skipRecap,
                autoNext = prefs[appKey(pkg, "autoNext")] ?: d.autoNext,
            )
        }
        return UserSettings(masterEnabled = master, apps = apps)
    }

    suspend fun saveUserSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[MASTER_ENABLED] = settings.masterEnabled
            settings.apps.forEach { (pkg, t) ->
                prefs[appKey(pkg, "enabled")] = t.enabled
                prefs[appKey(pkg, "skipIntro")] = t.skipIntro
                prefs[appKey(pkg, "skipRecap")] = t.skipRecap
                prefs[appKey(pkg, "autoNext")] = t.autoNext
            }
        }
    }

    suspend fun cachedRemoteConfigJson(): String? =
        context.dataStore.data.first()[REMOTE_CONFIG_JSON]

    suspend fun saveRemoteConfigJson(json: String) {
        context.dataStore.edit { it[REMOTE_CONFIG_JSON] = json }
    }

    suspend fun remoteConfigUrl(): String =
        context.dataStore.data.first()[REMOTE_CONFIG_URL] ?: DEFAULT_REMOTE_CONFIG_URL

    companion object {
        /**
         * Default endpoint. Point this at your own trusted HTTPS host; until one is
         * published the fetch simply fails and the app uses bundled/cached config.
         */
        const val DEFAULT_REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/skipperkit/config/main/config.json"

        private val Context.dataStore by preferencesDataStore(name = "skipperkit_settings")

        private val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        private val REMOTE_CONFIG_JSON = stringPreferencesKey("remote_config_json")
        private val REMOTE_CONFIG_URL = stringPreferencesKey("remote_config_url")

        private fun appKey(packageName: String, feature: String) =
            booleanPreferencesKey("app__${packageName}__$feature")
    }
}
