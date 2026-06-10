package com.skipperkit.settings

import com.skipperkit.config.ConfigRepository
import com.skipperkit.config.CustomButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-taught custom buttons, keyed by package name. Persistence is handled
 * by the caller (SkipperApp) via [onChanged] + [restore].
 */
object CustomButtonsRepository {

    private const val MAX_PER_APP = 50

    private val _buttons = MutableStateFlow<Map<String, List<CustomButton>>>(emptyMap())
    val buttons: StateFlow<Map<String, List<CustomButton>>> = _buttons.asStateFlow()

    /** Invoked whenever the set changes, so the caller can persist it. */
    @Volatile var onChanged: ((Map<String, List<CustomButton>>) -> Unit)? = null

    fun forPackage(packageName: String): List<CustomButton> =
        _buttons.value[packageName] ?: emptyList()

    /** Add a button for a package; no-op if a button with the same [CustomButton.key] already exists or the per-app cap is reached. */
    @Synchronized
    fun add(packageName: String, button: CustomButton) {
        val existing = _buttons.value[packageName] ?: emptyList()
        if (existing.any { it.key == button.key }) return
        if (existing.size >= MAX_PER_APP) return
        _buttons.value = _buttons.value + (packageName to (existing + button))
        propagate()
    }

    /** Remove a specific button; no-op when absent. Drops the package entry when its list becomes empty. */
    @Synchronized
    fun remove(packageName: String, key: String) {
        val existing = _buttons.value[packageName] ?: return
        if (existing.none { it.key == key }) return
        val updated = existing.filterNot { it.key == key }
        _buttons.value = if (updated.isEmpty()) {
            _buttons.value - packageName
        } else {
            _buttons.value + (packageName to updated)
        }
        propagate()
    }

    /** Toggle the enabled state of a button; no-op when not found. */
    @Synchronized
    fun setEnabled(packageName: String, key: String, enabled: Boolean) {
        val existing = _buttons.value[packageName] ?: return
        val idx = existing.indexOfFirst { it.key == key }
        if (idx == -1) return
        val updated = existing.toMutableList()
        updated[idx] = updated[idx].copy(enabled = enabled)
        _buttons.value = _buttons.value + (packageName to updated)
        propagate()
    }

    /** Remove all buttons for a package. Used when a taught app is removed. Fires onChanged only when something changed. */
    @Synchronized
    fun removeForPackage(packageName: String) {
        if (!_buttons.value.containsKey(packageName)) return
        _buttons.value = _buttons.value - packageName
        propagate()
    }

    /** Re-hydrate persisted buttons on startup (before wiring [onChanged]). */
    @Synchronized
    fun restore(map: Map<String, List<CustomButton>>) {
        _buttons.value = map
        ConfigRepository.setCustomButtons(map)
    }

    private fun propagate() {
        ConfigRepository.setCustomButtons(_buttons.value)
        onChanged?.invoke(_buttons.value)
    }
}
