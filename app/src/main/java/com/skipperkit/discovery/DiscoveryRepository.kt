package com.skipperkit.discovery

import com.skipperkit.config.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds discovery state: entries awaiting the user's decision ([pending]), entries
 * the user approved ([approved], merged into [ConfigRepository] so the deterministic
 * engine acts on them), and entries the user dismissed (suppressed forever).
 *
 * The service only ever [propose]s; it never auto-applies. Approval is the
 * confirm-once gate. Persistence is handled by the caller (SkipperApp) via the
 * snapshot/restore hooks.
 */
object DiscoveryRepository {

    private val _pending = MutableStateFlow<List<DiscoveredEntry>>(emptyList())
    val pending: StateFlow<List<DiscoveredEntry>> = _pending.asStateFlow()

    private val approvedByKey = LinkedHashMap<String, DiscoveredEntry>()
    private val dismissedKeys = HashSet<String>()

    /** Listener invoked whenever the approved set changes, so it can be persisted. */
    var onApprovedChanged: ((List<DiscoveredEntry>) -> Unit)? = null
    var onDismissedChanged: ((Set<String>) -> Unit)? = null

    /**
     * A newly observed candidate. Ignored if already approved, dismissed, or
     * pending. Next-episode candidates reach here only after the DiscoveryEngine
     * has vetted them as end-of-episode cards (never the control-bar button).
     */
    @Synchronized
    fun propose(entry: DiscoveredEntry) {
        val key = entry.key
        if (approvedByKey.containsKey(key) || dismissedKeys.contains(key)) return
        if (_pending.value.any { it.key == key }) return
        _pending.value = _pending.value + entry
    }

    @Synchronized
    fun approve(key: String) {
        val entry = _pending.value.firstOrNull { it.key == key } ?: return
        _pending.value = _pending.value.filterNot { it.key == key }
        approvedByKey[key] = entry
        ConfigRepository.setDiscovered(approvedByKey.values.toList())
        onApprovedChanged?.invoke(approvedByKey.values.toList())
    }

    @Synchronized
    fun dismiss(key: String) {
        _pending.value = _pending.value.filterNot { it.key == key }
        if (dismissedKeys.add(key)) onDismissedChanged?.invoke(dismissedKeys.toSet())
    }

    @Synchronized
    fun approvedForPackage(packageName: String): List<DiscoveredEntry> =
        approvedByKey.values.filter { it.packageName == packageName }

    /**
     * Merge externally shared entries straight into the approved set (the import
     * path — the user's explicit import action is the consent). Dedupes by key.
     */
    @Synchronized
    fun addApproved(entries: List<DiscoveredEntry>) {
        var changed = false
        entries.forEach { e ->
            if (approvedByKey.put(e.key, e) == null) changed = true
        }
        if (!changed) return
        ConfigRepository.setDiscovered(approvedByKey.values.toList())
        onApprovedChanged?.invoke(approvedByKey.values.toList())
    }

    @Synchronized
    fun removeForPackage(packageName: String) {
        _pending.value = _pending.value.filterNot { it.packageName == packageName }
        val approvedRemoved = approvedByKey.values.removeAll { it.packageName == packageName }
        val dismissedRemoved = dismissedKeys.removeAll { it.startsWith("$packageName|") }
        if (approvedRemoved) {
            ConfigRepository.setDiscovered(approvedByKey.values.toList())
            onApprovedChanged?.invoke(approvedByKey.values.toList())
        }
        if (dismissedRemoved) onDismissedChanged?.invoke(dismissedKeys.toSet())
    }

    /** Re-hydrate persisted decisions on startup (before wiring listeners). */
    fun restore(approved: List<DiscoveredEntry>, dismissed: Set<String>) {
        approvedByKey.clear()
        approved.forEach { approvedByKey[it.key] = it }
        dismissedKeys.clear()
        dismissedKeys.addAll(dismissed)
        ConfigRepository.setDiscovered(approvedByKey.values.toList())
    }
}
