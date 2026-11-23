package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Normal IME content: wraps the tallest pane within the space offered by the IME parent. */
internal data class FoldPanePlacement(
    val widthPercent: Int = 100,
    val bottomMargin: Int = 0,
    val positionIsEnd: Boolean = true,
    val marginStart: Int = 0,
    val marginEnd: Int = 0,
)

internal class FoldKeyboardDockHost(
    context: Context,
    private val placement: (SplitSlot) -> FoldPanePlacement = { FoldPanePlacement() },
) : FrameLayout(context) {
    private fun size(slot: SplitSlot) =
        if (foldLayout == FoldKeyboardLayout.SPLIT) placement(slot) else FoldPanePlacement()

    private fun childWidth(slot: SplitSlot, paneWidth: Int) =
        (paneWidth * size(slot).widthPercent.coerceIn(32, 100) / 100).coerceAtLeast(1)

    var foldLayout = FoldKeyboardLayout.SPLIT
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val paneWidth = foldLayout.paneWidth(width, (280 * resources.displayMetrics.density).roundToInt())
        val heightLimit = MeasureSpec.getSize(heightMeasureSpec)
        var desiredHeight = suggestedMinimumHeight
        // A fixed preview already supplies its height. Avoid a discarded natural pass
        // through both real keyboard layouts; the ordinary wrap-content IME still needs it.
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            val naturalHeightSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            else MeasureSpec.makeMeasureSpec(heightLimit, MeasureSpec.AT_MOST)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.visibility == View.GONE) continue
                val slot = foldLayout.slots.getOrNull(index) ?: continue
                child.measure(MeasureSpec.makeMeasureSpec(childWidth(slot, paneWidth), MeasureSpec.EXACTLY), naturalHeightSpec)
                desiredHeight = maxOf(desiredHeight, child.measuredHeight + size(slot).bottomMargin.coerceAtLeast(0))
            }
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
        // Equal pane surfaces keep both candidate strips and keyboard bottoms aligned.
        for (index in 0 until childCount) {
            val slot = foldLayout.slots.getOrNull(index) ?: continue
            getChildAt(index).takeIf { it.visibility != View.GONE }?.measure(
                MeasureSpec.makeMeasureSpec(childWidth(slot, paneWidth), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((measuredHeight - size(slot).bottomMargin.coerceAtLeast(0)).coerceAtLeast(0), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val slot = foldLayout.slots.getOrNull(index) ?: continue
            val paneWidth = foldLayout.paneWidth(width, (280 * resources.displayMetrics.density).roundToInt())
            val value = size(slot)
            val spare = (paneWidth - child.measuredWidth).coerceAtLeast(0)
            val end = value.positionIsEnd != (layoutDirection == View.LAYOUT_DIRECTION_RTL)
            val margin = (if (value.positionIsEnd) value.marginEnd else value.marginStart).coerceIn(0, spare)
            val offset = if (end) spare - margin else margin
            val x = foldLayout.paneX(slot, width, paneWidth) + offset
            child.layout(x, 0, x + child.measuredWidth, child.measuredHeight)
        }
    }
}
