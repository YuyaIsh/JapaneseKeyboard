package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Normal IME content: wraps the tallest pane within the space offered by the IME parent. */
internal class FoldKeyboardDockHost(context: Context) : FrameLayout(context) {
    var foldLayout = FoldKeyboardLayout.SPLIT
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val paneWidth = foldLayout.paneWidth(width, (280 * resources.displayMetrics.density).roundToInt())
        val heightLimit = MeasureSpec.getSize(heightMeasureSpec)
        val naturalHeightSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        else MeasureSpec.makeMeasureSpec(heightLimit, MeasureSpec.AT_MOST)
        var desiredHeight = suggestedMinimumHeight
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(paneWidth, MeasureSpec.EXACTLY), naturalHeightSpec)
            desiredHeight = maxOf(desiredHeight, child.measuredHeight)
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
        // Equal pane surfaces keep both candidate strips and keyboard bottoms aligned.
        for (index in 0 until childCount) {
            getChildAt(index).takeIf { it.visibility != View.GONE }?.measure(
                MeasureSpec.makeMeasureSpec(paneWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val slot = foldLayout.slots.getOrNull(index) ?: continue
            val x = foldLayout.paneX(slot, width, child.measuredWidth)
            child.layout(x, 0, x + child.measuredWidth, child.measuredHeight)
        }
    }
}
