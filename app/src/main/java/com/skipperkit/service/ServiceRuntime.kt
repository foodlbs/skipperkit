package com.skipperkit.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live runtime signals from the accessibility service, observed by the settings
 * UI to render the status card. Updated by [SkipAccessibilityService]; read by
 * MainActivity. Process-wide singleton (UI and service share the process).
 */
object ServiceRuntime {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Wall-clock ms of the last accessibility event from a supported app, or null. */
    private val _lastActivityMs = MutableStateFlow<Long?>(null)
    val lastActivityMs: StateFlow<Long?> = _lastActivityMs.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun recordActivity(nowMs: Long) {
        _lastActivityMs.value = nowMs
    }
}
