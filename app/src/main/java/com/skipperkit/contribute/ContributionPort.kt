package com.skipperkit.contribute

import com.skipperkit.config.CustomButton
import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the JSON a device sends when the user taps "Send to project". Pure and
 * unit-testable. Mirrors TaughtAppPort constraints: skip-only targets plus named
 * custom buttons (format v2), ≤ 20 of each. The server re-validates everything;
 * these caps just keep honest payloads small.
 */
object ContributionPort {

    private const val FORMAT_KEY = "skipperkitContribution"
    private const val FORMAT_VERSION = 2
    private const val MAX_BUTTONS = 20
    private const val MAX_DISPLAY_NAME = 50
    private const val MAX_STRING = 256

    /** Returns null when there is nothing contributable (no skip or custom buttons). */
    fun build(
        packageName: String,
        displayName: String,
        entries: List<DiscoveredEntry>,
        appVersionName: String?,
        skipperkitVersion: String,
        locale: String,
        customButtons: List<CustomButton> = emptyList(),
    ): String? {
        val buttons = entries
            .filter { it.target != SkipTarget.NEXT_EPISODE }
            .filter { it.viewId != null || it.label != null }
            .take(MAX_BUTTONS)
        val customs = customButtons
            .filter { it.viewIds.isNotEmpty() || it.labels.isNotEmpty() }
            .take(MAX_BUTTONS)
        if (buttons.isEmpty() && customs.isEmpty()) return null
        return JSONObject().apply {
            put(FORMAT_KEY, FORMAT_VERSION)
            put("packageName", packageName)
            put("displayName", displayName.take(MAX_DISPLAY_NAME))
            put("buttons", JSONArray().apply {
                buttons.forEach { e ->
                    put(JSONObject()
                        .put("target", e.target.name)
                        .put("viewId", e.viewId ?: JSONObject.NULL)
                        .put("label", e.label ?: JSONObject.NULL))
                }
            })
            put("customButtons", JSONArray().apply {
                // First viewId/label only — the teach flow only ever creates one.
                customs.forEach { b ->
                    put(JSONObject()
                        .put("name", b.name.take(MAX_DISPLAY_NAME))
                        .put("viewId", b.viewIds.firstOrNull()?.take(MAX_STRING) ?: JSONObject.NULL)
                        .put("label", b.labels.firstOrNull()?.take(MAX_STRING) ?: JSONObject.NULL))
                }
            })
            appVersionName?.let { put("appVersionName", it) }
            put("skipperkitVersion", skipperkitVersion)
            put("locale", locale)
        }.toString(2)
    }
}
