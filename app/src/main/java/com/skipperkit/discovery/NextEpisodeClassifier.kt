package com.skipperkit.discovery

import com.skipperkit.matching.NodeView

enum class NextEpisodeVerdict { END_CARD, CONTROL_BAR, UNKNOWN }

interface NextEpisodeClassifier {
    /** Is [candidate] (a next-episode control) part of an end-of-episode card? */
    fun classify(candidate: NodeView, root: NodeView): NextEpisodeVerdict
}

/**
 * Distinguishes the **end-of-episode "up next" card** from the **always-present
 * control-bar next button** using structural markers captured from real devices.
 * The two are mutually-exclusive screen states, so scanning the whole window for
 * marker signals is reliable.
 *
 * Device-verified signals:
 *  - Netflix: end-card tags are Capital-N `NextEpisodeButtonTestTag` /
 *    `WatchCreditsButtonTestTag`; the control bar is lowercase-n
 *    `nextEpisodeButtonTestTag`. Exact, case-sensitive — that's the discriminator.
 *  - Disney+: end-card lives under `up_next_*` ids with "UP NEXT" / "YOU MAY ALSO
 *    LIKE"; the control bar is `id/nextButton` ("PLAY NEXT").
 *  - Prime: the end-card is the dynamic "Next up: <title>" text.
 *
 * When both/neither family of markers is present it returns the *end-card* verdict
 * only for an unambiguous end-card; otherwise UNKNOWN. The discovery use is
 * confirm-once (the user approves any suggestion), so an over-eager END_CARD here
 * can't auto-advance anything on its own.
 */
object HeuristicNextEpisodeClassifier : NextEpisodeClassifier {

    override fun classify(candidate: NodeView, root: NodeView): NextEpisodeVerdict {
        val ids = HashSet<String>()
        val texts = ArrayList<String>()
        scan(root, depth = 0, ids = ids, texts = texts)

        val endCard = ids.any { it in END_CARD_IDS } ||
            ids.any { id -> END_CARD_ID_FRAGMENTS.any { id.contains(it, ignoreCase = true) } } ||
            texts.any { t -> END_CARD_TEXTS.any { t.contains(it) } }

        val controlBar = ids.any { it in CONTROL_BAR_IDS } ||
            ids.any { id -> CONTROL_BAR_ID_FRAGMENTS.any { id.contains(it, ignoreCase = true) } } ||
            texts.any { t -> CONTROL_BAR_TEXTS.any { t.contains(it) } }

        return when {
            endCard -> NextEpisodeVerdict.END_CARD
            controlBar -> NextEpisodeVerdict.CONTROL_BAR
            else -> NextEpisodeVerdict.UNKNOWN
        }
    }

    private fun scan(node: NodeView, depth: Int, ids: MutableSet<String>, texts: MutableList<String>) {
        node.viewId?.takeIf { it.isNotEmpty() }?.let { ids.add(it) }
        node.text?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it.lowercase()) }
        node.contentDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it.lowercase()) }
        if (depth >= MAX_DEPTH) return
        for (i in 0 until node.childCount) {
            node.childAt(i)?.let { scan(it, depth + 1, ids, texts) }
        }
    }

    private const val MAX_DEPTH = 60

    private val END_CARD_IDS = setOf("NextEpisodeButtonTestTag", "WatchCreditsButtonTestTag")
    private val END_CARD_ID_FRAGMENTS = listOf("upNext", "up_next")
    private val END_CARD_TEXTS = listOf("up next", "next up:", "you may also like", "up next is", "watch credits")

    private val CONTROL_BAR_IDS = setOf("nextEpisodeButtonTestTag")
    private val CONTROL_BAR_ID_FRAGMENTS = listOf("playerControls", ":id/nextButton")
    private val CONTROL_BAR_TEXTS = listOf("next ep.")
}

/**
 * Scaffold for an on-device LLM classifier (Gemini Nano via ML Kit GenAI / AICore).
 * Not wired to a model yet — returns UNKNOWN so the composite falls back to the
 * heuristic. To implement: gate on feature availability, serialize the candidate's
 * local subtree **text only** (no pixels — preserves the privacy promise), prompt
 * "is this an end-of-episode autoplay card or the persistent control-bar next
 * button?", parse the yes/no. Must degrade to UNKNOWN when Nano is unavailable.
 */
object GeminiNanoNextEpisodeClassifier : NextEpisodeClassifier {
    override fun classify(candidate: NodeView, root: NodeView): NextEpisodeVerdict {
        // TODO(R3): integrate com.google.mlkit GenAI on supported devices.
        return NextEpisodeVerdict.UNKNOWN
    }
}

/** Heuristic first; the LLM only decides what the heuristic leaves UNKNOWN. */
class CompositeNextEpisodeClassifier(
    private val primary: NextEpisodeClassifier = HeuristicNextEpisodeClassifier,
    private val fallback: NextEpisodeClassifier = GeminiNanoNextEpisodeClassifier,
) : NextEpisodeClassifier {
    override fun classify(candidate: NodeView, root: NodeView): NextEpisodeVerdict {
        val verdict = primary.classify(candidate, root)
        return if (verdict != NextEpisodeVerdict.UNKNOWN) verdict else fallback.classify(candidate, root)
    }
}
