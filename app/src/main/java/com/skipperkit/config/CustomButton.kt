package com.skipperkit.config

/**
 * A user-taught, named button beyond the built-in skip targets — e.g. a
 * recurring "rate this app" dismissal. Taught via teach mode, toggled
 * per-button in settings. Matching is exact (ids/labels), never prefix.
 */
data class CustomButton(
    val key: String,            // stable identity: the view-id, or "label:<lowercased label>"
    val name: String,           // user-given display name
    val viewIds: List<String>,
    val labels: List<String>,
    val enabled: Boolean = true,
)
