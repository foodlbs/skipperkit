package com.skipperkit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import com.skipperkit.settings.AppToggles
import com.skipperkit.settings.TaughtApp
import com.skipperkit.settings.UserSettings
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

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
        val discoverySuggestions = prefs[DISCOVERY_SUGGESTIONS] ?: defaults.discoverySuggestions
        val apps = defaults.apps.mapValues { (pkg, d) ->
            AppToggles(
                enabled = prefs[appKey(pkg, "enabled")] ?: d.enabled,
                skipIntro = prefs[appKey(pkg, "skipIntro")] ?: d.skipIntro,
                skipRecap = prefs[appKey(pkg, "skipRecap")] ?: d.skipRecap,
                autoNext = prefs[appKey(pkg, "autoNext")] ?: d.autoNext,
            )
        }
        return UserSettings(masterEnabled = master, apps = apps, discoverySuggestions = discoverySuggestions)
    }

    suspend fun saveUserSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[MASTER_ENABLED] = settings.masterEnabled
            prefs[DISCOVERY_SUGGESTIONS] = settings.discoverySuggestions
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

    suspend fun contributionConsent(): Boolean =
        context.dataStore.data.first()[CONTRIBUTION_CONSENT] ?: false

    suspend fun saveContributionConsent(granted: Boolean) {
        context.dataStore.edit { it[CONTRIBUTION_CONSENT] = granted }
    }

    suspend fun loadDiscovered(): Pair<List<DiscoveredEntry>, Set<String>> {
        val prefs = context.dataStore.data.first()
        val approved = prefs[DISCOVERY_APPROVED]?.let(::parseEntries) ?: emptyList()
        val dismissed = prefs[DISCOVERY_DISMISSED]?.let(::parseKeys) ?: emptySet()
        return approved to dismissed
    }

    suspend fun saveApprovedDiscovered(entries: List<DiscoveredEntry>) {
        context.dataStore.edit { it[DISCOVERY_APPROVED] = entriesToJson(entries) }
    }

    suspend fun saveDismissedDiscovered(keys: Set<String>) {
        context.dataStore.edit { it[DISCOVERY_DISMISSED] = keysToJson(keys) }
    }

    suspend fun loadTaughtApps(): List<TaughtApp> {
        val json = context.dataStore.data.first()[TAUGHT_APPS] ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pkg = o.optString("packageName")
                if (pkg.isEmpty()) return@mapNotNull null
                TaughtApp(pkg, o.optString("displayName", pkg))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun saveTaughtApps(apps: List<TaughtApp>) {
        val arr = JSONArray()
        apps.forEach { arr.put(JSONObject().put("packageName", it.packageName).put("displayName", it.displayName)) }
        context.dataStore.edit { it[TAUGHT_APPS] = arr.toString() }
    }

    private fun entriesToJson(entries: List<DiscoveredEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("packageName", e.packageName)
                    .put("target", e.target.name)
                    .put("viewId", e.viewId ?: JSONObject.NULL)
                    .put("label", e.label ?: JSONObject.NULL),
            )
        }
        return arr.toString()
    }

    private fun parseEntries(json: String): List<DiscoveredEntry> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val target = runCatching { SkipTarget.valueOf(o.optString("target")) }.getOrNull()
                ?: return@mapNotNull null
            DiscoveredEntry(
                packageName = o.optString("packageName"),
                target = target,
                viewId = o.optString("viewId").ifEmpty { null }.takeUnless { o.isNull("viewId") },
                label = o.optString("label").ifEmpty { null }.takeUnless { o.isNull("label") },
            )
        }
    }.getOrDefault(emptyList())

    private fun keysToJson(keys: Set<String>): String =
        JSONArray().apply { keys.forEach { put(it) } }.toString()

    private fun parseKeys(json: String): Set<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    companion object {
        /**
         * Default endpoint, served from the SkipperKit project's config repo.
         * Forks should point this at their own trusted HTTPS host. If the fetch
         * fails the app falls back to cached → bundled config.
         */
        const val DEFAULT_REMOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/foodlbs/skipperkit-config/main/config.json"

        /** One-tap contribution endpoint (Supabase edge function). */
        const val CONTRIBUTION_URL =
            "https://jaxzkldvifgmqnoahvom.supabase.co/functions/v1/submit-config"

        private val Context.dataStore by preferencesDataStore(name = "skipperkit_settings")

        private val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        private val DISCOVERY_SUGGESTIONS = booleanPreferencesKey("discovery_suggestions")
        private val REMOTE_CONFIG_JSON = stringPreferencesKey("remote_config_json")
        private val REMOTE_CONFIG_URL = stringPreferencesKey("remote_config_url")
        private val DISCOVERY_APPROVED = stringPreferencesKey("discovery_approved")
        private val DISCOVERY_DISMISSED = stringPreferencesKey("discovery_dismissed")
        private val TAUGHT_APPS = stringPreferencesKey("taught_apps")
        private val CONTRIBUTION_CONSENT = booleanPreferencesKey("contribution_consent")

        private fun appKey(packageName: String, feature: String) =
            booleanPreferencesKey("app__${packageName}__$feature")
    }
}
