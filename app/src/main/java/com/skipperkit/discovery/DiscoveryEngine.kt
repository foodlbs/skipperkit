package com.skipperkit.discovery

import com.skipperkit.config.SkipTarget
import com.skipperkit.matching.NodeView
import com.skipperkit.matching.TreeSearch

/**
 * A single discovered candidate skip/next control. [hasClickableTarget] reports
 * whether a clickable self-or-ancestor exists (i.e. whether we *could* act on it).
 */
data class DiscoveryCandidate(
    val target: SkipTarget,
    val text: String,
    val viewId: String?,
    val hasClickableTarget: Boolean,
)

/**
 * Heuristic, LLM-free discovery tier (prototype). Given a node tree, it finds
 * nodes whose text/description *signals* a skip/recap/next control and that aren't
 * obviously an ad/purchase action. Pure and unit-testable.
 *
 * It only ever **proposes** — it does not click anything. The intent is to run it
 * rarely (on a config miss, throttled, off the hot path) and log what it would
 * find, so the heuristic can be evaluated on real apps before it's ever allowed to
 * act. A future tier can hand ambiguous candidates to an on-device LLM ranker.
 */
object DiscoveryEngine {

    fun discover(
        root: NodeView,
        nextEpisodeClassifier: NextEpisodeClassifier = HeuristicNextEpisodeClassifier,
    ): List<DiscoveryCandidate> {
        val found = LinkedHashMap<String, DiscoveryCandidate>()
        walk(root, depth = 0) { node ->
            val text = (node.text?.trim().takeUnless { it.isNullOrEmpty() }
                ?: node.contentDescription?.trim().takeUnless { it.isNullOrEmpty() })
                ?: return@walk
            val lower = text.lowercase()
            if (DENY.any { lower.contains(it) }) return@walk
            val target = classify(lower) ?: return@walk
            // A next-episode control is only surfaced when it's the end-of-episode
            // card — never the always-present control-bar next button.
            if (target == SkipTarget.NEXT_EPISODE &&
                nextEpisodeClassifier.classify(node, root) != NextEpisodeVerdict.END_CARD
            ) {
                return@walk
            }
            val candidate = DiscoveryCandidate(
                target = target,
                text = text,
                viewId = node.viewId,
                hasClickableTarget = TreeSearch.firstClickableSelfOrAncestor(node) != null,
            )
            // Dedupe on (target, viewId|text) so repeated nodes collapse.
            found.putIfAbsent("${target}:${node.viewId ?: lower}", candidate)
        }
        return found.values.toList()
    }

    private fun classify(lower: String): SkipTarget? = when {
        lower.contains("skip intro") -> SkipTarget.SKIP_INTRO
        lower.contains("skip recap") || (lower.contains("recap") && lower.contains("skip")) ->
            SkipTarget.SKIP_RECAP
        NEXT_SIGNALS.any { lower.contains(it) } -> SkipTarget.NEXT_EPISODE
        lower.contains("skip credits") || lower.contains("skip") && lower.contains("intro") ->
            SkipTarget.SKIP_INTRO
        else -> null
    }

    private fun walk(node: NodeView, depth: Int, visit: (NodeView) -> Unit) {
        visit(node)
        if (depth >= TreeSearch.MAX_DEPTH) return
        for (i in 0 until node.childCount) {
            val child = node.childAt(i) ?: continue
            walk(child, depth + 1, visit)
        }
    }

    private val NEXT_SIGNALS = listOf("next episode", "next ep", "play next", "up next", "next up")

    /**
     * Ad / purchase / subscription action phrases. Phrase-level (not bare words)
     * so dynamic titles like "Next up: Free Guy" aren't wrongly rejected — only an
     * actual "ad free" / "free trial" action is.
     */
    private val DENY = listOf(
        "skip ad", "skip ads", "ad free", "ad-free", "go ad", "watch ad",
        "subscribe", "free trial", "sign up", "sign in", "buy", "rent",
        "purchase", "upgrade", "go premium", "get premium",
    )
}
