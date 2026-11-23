package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.KeyboardSizePreviewViewport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h700dp-mdpi")
class KeyboardSizePreviewViewportTest {
    @Test fun changingDraftDimensionsKeepsOnePreviewAndShowsHeightChangesAtStableScale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val canvas = FrameLayout(context)
        val keyboard = View(context)
        canvas.addView(keyboard, FrameLayout.LayoutParams(300, 200, Gravity.BOTTOM).apply { bottomMargin = 10 })
        val viewport = KeyboardSizePreviewViewport(context, canvas, keyboard)
        fun measure(height: Int) {
            viewport.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            viewport.layout(0, 0, 400, height)
        }
        measure(250)
        val scale = viewport.previewScale
        val before = keyboard.height * scale
        assertEquals(400f, canvas.width * scale, 1f)
        keyboard.layoutParams = (keyboard.layoutParams as FrameLayout.LayoutParams).apply { width = 200; height = 300; bottomMargin = 20 }
        measure(250)
        assertEquals(scale, viewport.previewScale, .001f)
        assertTrue(keyboard.height * scale > before)
        assertEquals(200, keyboard.width)
        assertEquals(1, viewport.childCount)
        assertSame(canvas, viewport.getChildAt(0))
        assertSame(keyboard, canvas.getChildAt(0))
        measure(120) // Available area shrinks when the software number keyboard opens.
        assertEquals(120, viewport.height)
        assertTrue(viewport.previewScale > 0)
        assertTrue(canvas.height * viewport.previewScale <= 120.1f)
    }
}
