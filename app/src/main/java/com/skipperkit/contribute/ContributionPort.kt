package com.skipperkit.contribute

import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the JSON a device sends when the user taps "Send to project". Pure and
 * unit-testable. Mirrors TaughtAppPort constraints: skip-only targets, ≤ 20
 * buttons. The server re-validates everything; these caps just keep honest
 * payloads small.
 */
object ContributionPort {

    private const val FORMAT_KEY = "skipperkitContribution"
    private const val FORMAT_VERSION = 1
    private const val MAX_BUTTONS = 20

    /** Returns null when there is nothing contributable (no skip buttons). */
    fun build(
        packageName: String,
        displayName: String,
        entries: List<DiscoveredEntry>,
        appVersionName: String?,
        skipperkitVersion: String,
        locale: String,
    ): String? {
        val buttons = entries
            .filter { it.target != SkipTarget.NEXT_EPISODE }
            .take(MAX_BUTTONS)
        if (buttons.isEmpty()) return null
        return JSONObject().apply {
            put(FORMAT_KEY, FORMAT_VERSION)
            put("packageName", packageName)
            put("displayName", displayName)
            put("buttons", JSONArray().apply {
                buttons.forEach { e ->
                    put(JSONObject()
                        .put("target", e.target.name)
                        .put("viewId", e.viewId ?: JSONObject.NULL)
                        .put("label", e.label ?: JSONObject.NULL))
                }
            })
            appVersionName?.let { put("appVersionName", it) }
            put("skipperkitVersion", skipperkitVersion)
            put("locale", locale)
        }.toString(2)
    }
}
