package com.skipperkit.matching

/** Test double for [NodeView]; builds in-memory trees with parent links. */
class FakeNode(
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val viewId: String? = null,
    override val isClickable: Boolean = false,
    private val children: List<FakeNode> = emptyList(),
    private val clickResult: Boolean = true,
    private val center: Point? = null,
) : NodeView {

    override var parent: NodeView? = null
        private set

    var clicked: Boolean = false
        private set

    init {
        children.forEach { it.parent = this }
    }

    override val childCount: Int get() = children.size
    override fun childAt(index: Int): NodeView? = children.getOrNull(index)

    override fun click(): Boolean {
        clicked = true
        return clickResult
    }

    override fun boundsCenter(): Point? = center
}
