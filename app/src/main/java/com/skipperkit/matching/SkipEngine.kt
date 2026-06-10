package com.skipperkit.matching

import com.skipperkit.config.AppConfig
import com.skipperkit.config.SkipTarget

/**
 * Pure orchestration: given a tree root and an [AppConfig], find the highest
 * priority target, click its clickable ancestor, and enforce a package-aware
 * debounce so rapid accessibility updates don't fire duplicate taps.
 *
 * The clock is injected so debounce is unit-testable without a real time source.
 */
class SkipEngine(
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val clock: () -> Long,
) {

    private val lastActionUptimeByPackage = HashMap<String, Long>()

    sealed interface Result {
        /** Config disabled, or no root. */
        data object Skipped : Result
        /** Within the debounce window since the last successful click. */
        data object Debounced : Result
        /** Nothing on screen matched. */
        data object NoMatch : Result
        /** Matched and clicked via ACTION_CLICK. */
        data class Clicked(val target: SkipTarget, val customName: String? = null) : Result
        /**
         * Matched, but no clickable self-or-ancestor accepted ACTION_CLICK.
         * Carries the on-screen point the service should tap via dispatchGesture.
         * Debounce IS armed on handoff (see below).
         */
        data class NeedsGesture(val target: SkipTarget, val point: Point, val customName: String? = null) : Result
    }

    fun onTree(packageName: String, root: NodeView?, config: AppConfig): Result {
        if (root == null || !config.enabled) return Result.Skipped

        val now = clock()
        val last = lastActionUptimeByPackage[packageName]
        if (last != null && now - last < debounceMs) return Result.Debounced

        for (matcher in config.activeMatchers()) {
            val hit = TreeSearch.findFirst(root, matcher.viewIds, matcher.labels, matcher.labelPrefixes)
                ?: continue
            val clickable = TreeSearch.firstClickableSelfOrAncestor(hit)
            if (clickable != null && clickable.click()) {
                lastActionUptimeByPackage[packageName] = clock()
                return Result.Clicked(matcher.target, matcher.customName)
            }

            // Fallback: tap the bounds center of the clickable target (or the
            // matched node if none). Debounce is armed here too, so we dispatch
            // at most one gesture per window instead of one per screen update.
            val point = (clickable ?: hit).boundsCenter() ?: return Result.NoMatch
            lastActionUptimeByPackage[packageName] = clock()
            return Result.NeedsGesture(matcher.target, point, matcher.customName)
        }
        return Result.NoMatch
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 1500L
    }
}
