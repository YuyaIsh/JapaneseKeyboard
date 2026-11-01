package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Measures the original body at a usable size; ViewGroup maps touches through its child matrix. */
internal class SplitKeyboardBody(
    context: Context,
    private var layoutWidth: Int,
    private var layoutHeight: Int,
    private val preferredHeight: (() -> Int)? = null,
) : ViewGroup(context) {
    fun setMinimumLayoutSize(width: Int, height: Int) {
        if (layoutWidth == width && layoutHeight == height) return
        layoutWidth = width
        layoutHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val body = getChildAt(0)
        val contentWidth = width.coerceAtLeast(layoutWidth)
        val desiredHeight = if (preferredHeight != null) {
            // Background layers use MATCH_PARENT, so inspect content without imposing the
            // old floating XML height, then retain the user's normal keyboard height.
            body?.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val configuredHeight = preferredHeight.invoke()
            maxOf(if (configuredHeight > 0) configuredHeight else layoutHeight, body?.measuredHeight ?: 0)
        } else MeasureSpec.getSize(heightMeasureSpec)
        val height = if (preferredHeight != null) resolveSize(desiredHeight, heightMeasureSpec)
            else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val contentHeight = if (preferredHeight != null) maxOf(height, desiredHeight) else maxOf(height, layoutHeight)
        body?.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        getChildAt(0)?.let { body ->
            body.layout(0, 0, body.measuredWidth, body.measuredHeight)
            body.pivotX = 0f; body.pivotY = 0f
            body.scaleX = width.toFloat() / body.measuredWidth.coerceAtLeast(1)
            body.scaleY = height.toFloat() / body.measuredHeight.coerceAtLeast(1)
        }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
}
