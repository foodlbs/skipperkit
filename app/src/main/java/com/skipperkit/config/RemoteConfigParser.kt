package com.skipperkit.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the remote config JSON into [AppConfig]s using the built-in org.json
 * (no extra dependency). Pure and unit-testable.
 *
 * Schema:
 * {
 *   "version": 1,
 *   "apps": [
 *     {
 *       "packageName": "com.netflix.mediaclient",
 *       "locale": "en",
 *       "skipIntroViewIds": ["..."], "skipIntroLabels": ["..."],
 *       "skipRecapViewIds": ["..."], "skipRecapLabels": ["..."],
 *       "nextEpisodeViewIds": ["..."], "nextEpisodeLabels": ["..."],
 *       "enabled": true, "autoNextEnabled": false
 *     }
 *   ]
 * }
 *
 * Entries missing a packageName are skipped. Throws on structurally invalid JSON
 * (the caller treats that as a failed fetch and keeps the current configs).
 */
object RemoteConfigParser {

    fun parse(json: String): List<AppConfig> {
        val root = JSONObject(json)
        val apps = root.optJSONArray("apps") ?: return emptyList()
        val result = ArrayList<AppConfig>(apps.length())
        for (i in 0 until apps.length()) {
            val o = apps.optJSONObject(i) ?: continue
            val packageName = o.optString("packageName").trim()
            if (packageName.isEmpty()) continue
            result.add(
                AppConfig(
                    packageName = packageName,
                    locale = o.optString("locale", "en"),
                    skipIntroViewIds = o.stringList("skipIntroViewIds"),
                    skipIntroLabels = o.stringList("skipIntroLabels"),
                    skipRecapViewIds = o.stringList("skipRecapViewIds"),
                    skipRecapLabels = o.stringList("skipRecapLabels"),
                    nextEpisodeViewIds = o.stringList("nextEpisodeViewIds"),
                    nextEpisodeLabels = o.stringList("nextEpisodeLabels"),
                    enabled = o.optBoolean("enabled", true),
                    autoNextEnabled = o.optBoolean("autoNextEnabled", false),
                ),
            )
        }
        return result
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val arr: JSONArray = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
