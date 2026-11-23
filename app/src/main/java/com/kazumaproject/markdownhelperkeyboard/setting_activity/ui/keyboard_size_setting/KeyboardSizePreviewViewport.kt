package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** One logical keyboard canvas, scaled into the anchored preview area (touches follow its matrix). */
internal class KeyboardSizePreviewViewport(context: Context, private val canvas: View, private val keyboard: View) : ViewGroup(context) {
    init { addView(canvas) }
    val previewScale get() = canvas.scaleX.coerceAtLeast(.01f)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val params = keyboard.layoutParams as android.view.ViewGroup.MarginLayoutParams
        val minimumCanvasHeight = (452 * resources.displayMetrics.density).toInt()
        val canvasHeight = maxOf(minimumCanvasHeight, params.height + params.bottomMargin + canvas.paddingTop + canvas.paddingBottom)
        val scale = minOf(1f, height.toFloat() / canvasHeight.coerceAtLeast(1)).coerceAtLeast(.01f)
        // Keep width percentages readable across the whole preview, even on a narrow Fold display.
        val canvasWidth = (width / scale).toInt().coerceAtLeast(width)
        canvas.measure(MeasureSpec.makeMeasureSpec(canvasWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(canvasHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val scale = minOf(1f, height.toFloat() / canvas.measuredHeight.coerceAtLeast(1))
        canvas.pivotX = 0f
        canvas.pivotY = 0f
        canvas.scaleX = scale
        canvas.scaleY = scale
        val x = ((width - canvas.measuredWidth * scale) / 2).toInt()
        canvas.layout(x, 0, x + canvas.measuredWidth, canvas.measuredHeight)
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
}
