package com.navadeep.kanso.tv.launcher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * Focus scrolling that only moves when the focused item comes within [leadingPx] / [trailingPx]
 * of the edges, and then by the least amount. The margins keep grown focused tiles unclipped and
 * let the next tile peek in.
 */
@OptIn(ExperimentalFoundationApi::class)
fun edgeScrollSpec(leadingPx: Float, trailingPx: Float, animation: AnimationSpec<Float>) =
    object : BringIntoViewSpec {
        @Deprecated("Overrides a deprecated member; still what the scroll animation uses.")
        override val scrollAnimationSpec: AnimationSpec<Float> = animation

        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val leading = offset - leadingPx
            val trailing = offset + size + trailingPx
            return when {
                leading >= 0f && trailing <= containerSize -> 0f
                size + leadingPx + trailingPx > containerSize -> leading
                leading < 0f -> leading
                else -> trailing - containerSize
            }
        }
    }

/**
 * Focus scrolling for the home page, as one rule so the page never fights itself:
 * - focus above "All Apps" (dock, header) glides the page back to the very top;
 * - focus in the grid first lifts "All Apps" to [titleTopPx] so the catalog fills the screen,
 *   then scrolls row by row only as needed.
 * Needs [dockRestPx], the dock's offset at the top of the page, for when the hero is off screen.
 */
@OptIn(ExperimentalFoundationApi::class)
fun pageScrollSpec(
    state: LazyGridState,
    heroKey: Any,
    dockKey: Any,
    titleKey: Any,
    dockRestPx: Float,
    titleTopPx: Float,
    leadingPx: Float,
    trailingPx: Float,
    animation: AnimationSpec<Float>,
) = object : BringIntoViewSpec {
    private val rows = edgeScrollSpec(leadingPx, trailingPx, animation)

    @Deprecated("Overrides a deprecated member; still what the scroll animation uses.")
    override val scrollAnimationSpec: AnimationSpec<Float> = animation

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val items = state.layoutInfo.visibleItemsInfo
        val title = items.firstOrNull { it.key == titleKey }
        if (title != null && offset < title.offset.y) {
            items.firstOrNull { it.key == heroKey }?.let { return it.offset.y.toFloat() }
            items.firstOrNull { it.key == dockKey }?.let { return it.offset.y - dockRestPx }
            return 0f
        }
        val row = rows.calculateScrollDistance(offset, size, containerSize)
        return if (title != null) maxOf(row, title.offset.y - titleTopPx) else row
    }
}
