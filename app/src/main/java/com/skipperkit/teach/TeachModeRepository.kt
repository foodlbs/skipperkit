package com.skipperkit.teach

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A clickable node observed while teach mode is armed. */
data class TeachCandidate(val viewId: String?, val text: String?) {
    val key: String get() = viewId ?: "label:${text?.lowercase()}"
}

/**
 * Holds teach-mode state for ONE armed app at a time.
 *
 * The service calls [offer] on every sweep; the UI arms via [arm] and reads
 * [candidates] to present the picker. Collection only — this object never clicks.
 *
 * [clock] is swappable for testing (default: wall clock).
 */
object TeachModeRepository {

    /** Three minutes. After this the session is considered abandoned. */
    const val AUTO_DISARM_MS = 3 * 60 * 1_000L

    /** Maximum candidates retained per session. */
    private const val MAX_CANDIDATES = 100

    @Volatile
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val _armedPackage = MutableStateFlow<String?>(null)
    val armedPackage: StateFlow<String?> = _armedPackage.asStateFlow()

    private val _candidates = MutableStateFlow<List<TeachCandidate>>(emptyList())
    val candidates: StateFlow<List<TeachCandidate>> = _candidates.asStateFlow()

    /** Uptime at which [arm] was last called. Compared against [clock]. */
    private var armedAt = 0L

    /** The set of keys already in [_candidates] for O(1) dedupe. */
    private val seenKeys = HashSet<String>()

    /** Start a teach session for [packageName]. Clears any previous session. */
    @Synchronized
    fun arm(packageName: String) {
        armedAt = clock()
        _armedPackage.value = packageName
        _candidates.value = emptyList()
        seenKeys.clear()
    }

    /** End the teach session. Clears armed package and all candidates. */
    @Synchronized
    fun disarm() {
        _armedPackage.value = null
        _candidates.value = emptyList()
        seenKeys.clear()
    }

    /**
     * Fast check for the service hot-path. Returns false (and auto-disarms) if
     * the session has expired.
     */
    @Synchronized
    fun isArmedFor(packageName: String): Boolean {
        if (_armedPackage.value != packageName) return false
        if (clock() - armedAt > AUTO_DISARM_MS) {
            disarm()
            return false
        }
        return true
    }

    /**
     * Record a candidate from the armed app.
     *
     * Ignored when:
     * - [packageName] is not the armed package
     * - session has expired (auto-disarms)
     * - both [TeachCandidate.viewId] and [TeachCandidate.text] are null/blank
     * - the candidate's key is already recorded (dedupe)
     * - the list is already at [MAX_CANDIDATES]
     */
    @Synchronized
    fun offer(packageName: String, candidate: TeachCandidate) {
        if (!isArmedFor(packageName)) return

        // Reject blank candidates — nothing actionable for the user.
        val hasViewId = !candidate.viewId.isNullOrBlank()
        val hasText = !candidate.text.isNullOrBlank()
        if (!hasViewId && !hasText) return

        val key = candidate.key
        if (seenKeys.contains(key)) return
        if (_candidates.value.size >= MAX_CANDIDATES) return

        seenKeys.add(key)
        _candidates.value = _candidates.value + candidate
    }
}
