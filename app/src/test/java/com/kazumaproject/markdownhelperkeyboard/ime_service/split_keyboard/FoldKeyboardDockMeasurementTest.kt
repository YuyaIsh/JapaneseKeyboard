package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.content.Context
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1000dp-mdpi")
class FoldKeyboardDockMeasurementTest {
    private class MeasuredPane(context: Context, private val naturalHeight: Int) : View(context) {
        val heightModes = mutableListOf<Int>()
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            heightModes += MeasureSpec.getMode(heightMeasureSpec)
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(naturalHeight, heightMeasureSpec))
        }
    }

    @Test fun fixedPreviewMeasuresEachPaneOnceAndRetainsPlacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            val host = host(activity)
            val main = MeasuredPane(activity, 180)
            val sub = MeasuredPane(activity, 220)
            host.addView(main)
            host.addView(sub)
            for (height in listOf(452, 480)) {
                measure(host, height, View.MeasureSpec.EXACTLY)
                assertEquals(320, main.width)
                assertEquals(68, main.left)
                assertEquals(height - 10, main.height)
                assertEquals(260, sub.width)
                assertEquals(408, sub.left)
                assertEquals(height - 20, sub.height)
                assertEquals(0, main.top)
                assertEquals(0, sub.top)
            }
            assertEquals(listOf(View.MeasureSpec.EXACTLY, View.MeasureSpec.EXACTLY), main.heightModes)
            assertEquals(listOf(View.MeasureSpec.EXACTLY, View.MeasureSpec.EXACTLY), sub.heightModes)
        } finally { activity.finish() }
    }

    @Test fun wrapContentImeStillUsesNaturalHeightsThenAlignsPaneSurfaces() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            val host = host(activity)
            val main = MeasuredPane(activity, 180)
            val sub = MeasuredPane(activity, 220)
            host.addView(main)
            host.addView(sub)
            measure(host, 700, View.MeasureSpec.AT_MOST)
            assertEquals(240, host.height)
            assertEquals(230, main.height)
            assertEquals(220, sub.height)
            assertEquals(320, main.width)
            assertEquals(68, main.left)
            assertEquals(260, sub.width)
            assertEquals(408, sub.left)
            val modes = listOf(View.MeasureSpec.AT_MOST, View.MeasureSpec.EXACTLY)
            assertEquals(modes, main.heightModes)
            assertEquals(modes, sub.heightModes)
        } finally { activity.finish() }
    }

    private fun host(context: Context) = FoldKeyboardDockHost(context) { slot ->
        if (slot == SplitSlot.MAIN) FoldPanePlacement(80, 10, true, 0, 12)
        else FoldPanePlacement(65, 20, false, 8, 0)
    }

    private fun measure(host: FoldKeyboardDockHost, height: Int, mode: Int) {
        host.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, mode))
        host.layout(0, 0, 800, host.measuredHeight)
    }
}
