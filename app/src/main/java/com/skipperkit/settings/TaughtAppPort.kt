package com.skipperkit.settings

import com.skipperkit.config.CustomButton
import com.skipperkit.config.SkipTarget
import com.skipperkit.discovery.DiscoveredEntry
import org.json.JSONArray
import org.json.JSONObject

/** A taught app plus its approved skip buttons and custom buttons, as shared between users. */
data class SharedTaughtApp(
    val app: TaughtApp,
    val entries: List<DiscoveredEntry>,
    val customButtons: List<CustomButton> = emptyList(),
)

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
    private const val FORMAT_VERSION = 2
    private const val MAX_DISPLAY_NAME = 50
    private const val MAX_CUSTOM_NAME = 50

    // A hand-taught app has a handful of buttons; a crafted file must not be able
    // to flood the approved set or the config singleton with unbounded data.
    private const val MAX_BUTTONS = 20
    private const val MAX_STRING = 256

    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    private val RISKY = Regex("(?i)\\b(pay|buy|purchase|order|confirm|subscribe|delete|remove|send|transfer|checkout)\\b")

    fun export(
        app: TaughtApp,
        entries: List<DiscoveredEntry>,
        customButtons: List<CustomButton> = emptyList(),
    ): String =
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
            .put(
                // Serializes only the first viewId/label per button; the teach flow only ever creates one.
                "customButtons",
                JSONArray().apply {
                    customButtons.forEach { b ->
                        put(
                            JSONObject()
                                .put("name", b.name)
                                .put("viewId", b.viewIds.firstOrNull() ?: JSONObject.NULL)
                                .put("label", b.labels.firstOrNull() ?: JSONObject.NULL)
                                .put("enabled", b.enabled),
                        )
                    }
                },
            )
            .toString(2)

    /**
     * Returns null when [json] is not a valid shared taught app.
     *
     * Imported buttons that look payment/destructive arrive disabled; the user must enable them deliberately.
     */
    fun parse(json: String): SharedTaughtApp? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val version = root.optInt(FORMAT_KEY)
        if (version < 1 || version > FORMAT_VERSION) return null
        val packageName = root.optString("packageName").trim()
        if (!PACKAGE_REGEX.matches(packageName)) return null
        val displayName = root.optString("displayName").trim()
            .take(MAX_DISPLAY_NAME).ifEmpty { packageName }

        val buttons = root.optJSONArray("buttons") ?: JSONArray()
        val entries = (0 until minOf(buttons.length(), MAX_BUTTONS)).mapNotNull { i ->
            val o = buttons.optJSONObject(i) ?: return@mapNotNull null
            val target = runCatching { SkipTarget.valueOf(o.optString("target")) }.getOrNull()
                ?: return@mapNotNull null
            if (target == SkipTarget.NEXT_EPISODE) return@mapNotNull null
            val viewId = o.optStringOrNull("viewId")
            val label = o.optStringOrNull("label")
            if (viewId == null && label == null) return@mapNotNull null
            DiscoveredEntry(packageName = packageName, target = target, viewId = viewId, label = label)
        }

        val customButtons = if (version >= 2) {
            val arr = root.optJSONArray("customButtons") ?: JSONArray()
            (0 until minOf(arr.length(), MAX_BUTTONS)).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optStringOrNull("name")?.take(MAX_CUSTOM_NAME) ?: return@mapNotNull null
                val viewId = o.optStringOrNull("viewId")?.take(MAX_STRING)
                val label = o.optStringOrNull("label")?.take(MAX_STRING)
                if (viewId == null && label == null) return@mapNotNull null
                val key = viewId ?: "label:${label!!.lowercase()}"
                val parsedEnabled = if (o.has("enabled")) o.optBoolean("enabled", true) else true
                val risky = RISKY.containsMatchIn(
                    listOfNotNull(name, label, viewId?.substringAfterLast('/')?.replace('_', ' ')).joinToString(" ")
                )
                CustomButton(
                    key = key,
                    name = name,
                    viewIds = listOfNotNull(viewId),
                    labels = listOfNotNull(label),
                    enabled = (parsedEnabled && !risky),
                )
            }
        } else {
            emptyList()
        }

        return SharedTaughtApp(TaughtApp(packageName, displayName), entries, customButtons)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().take(MAX_STRING).ifEmpty { null }
}
