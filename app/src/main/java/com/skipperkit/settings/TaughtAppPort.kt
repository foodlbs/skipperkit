package com.skipperkit.settings

import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import org.json.JSONArray
import org.json.JSONObject

/** A taught app plus its approved skip buttons, as shared between users. */
data class SharedTaughtApp(val app: TaughtApp, val entries: List<DiscoveredEntry>)

/**
 * Serializes a taught app (and its user-approved skip buttons) to a small JSON
 * document so users can share configurations with each other, and parses such
 * documents back. Pure org.json, unit-testable.
 *
 * Trust model: an imported file can only add SKIP_INTRO / SKIP_RECAP matchers for
 * the single package it declares — the same thing the user could teach by hand.
 * NEXT_EPISODE is excluded in both directions (a shared matcher could tap an
 * always-present control-bar button), every button is forced onto the declared
 * package, and importing still requires the user's explicit action.
 */
object TaughtAppPort {

    private const val FORMAT_KEY = "skipperkitTaughtApp"
    private const val FORMAT_VERSION = 1
    private const val MAX_DISPLAY_NAME = 50

    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun export(app: TaughtApp, entries: List<DiscoveredEntry>): String =
        JSONObject()
            .put(FORMAT_KEY, FORMAT_VERSION)
            .put("packageName", app.packageName)
            .put("displayName", app.displayName)
            .put(
                "buttons",
                JSONArray().apply {
                    entries
                        .filter { it.target != SkipTarget.NEXT_EPISODE }
                        .forEach { e ->
                            put(
                                JSONObject()
                                    .put("target", e.target.name)
                                    .put("viewId", e.viewId ?: JSONObject.NULL)
                                    .put("label", e.label ?: JSONObject.NULL),
                            )
                        }
                },
            )
            .toString(2)

    /** Returns null when [json] is not a valid shared taught app. */
    fun parse(json: String): SharedTaughtApp? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt(FORMAT_KEY) != FORMAT_VERSION) return null
        val packageName = root.optString("packageName").trim()
        if (!PACKAGE_REGEX.matches(packageName)) return null
        val displayName = root.optString("displayName").trim()
            .take(MAX_DISPLAY_NAME).ifEmpty { packageName }

        val buttons = root.optJSONArray("buttons") ?: JSONArray()
        val entries = (0 until buttons.length()).mapNotNull { i ->
            val o = buttons.optJSONObject(i) ?: return@mapNotNull null
            val target = runCatching { SkipTarget.valueOf(o.optString("target")) }.getOrNull()
                ?: return@mapNotNull null
            if (target == SkipTarget.NEXT_EPISODE) return@mapNotNull null
            val viewId = o.optStringOrNull("viewId")
            val label = o.optStringOrNull("label")
            if (viewId == null && label == null) return@mapNotNull null
            DiscoveredEntry(packageName = packageName, target = target, viewId = viewId, label = label)
        }
        return SharedTaughtApp(TaughtApp(packageName, displayName), entries)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifEmpty { null }
}
