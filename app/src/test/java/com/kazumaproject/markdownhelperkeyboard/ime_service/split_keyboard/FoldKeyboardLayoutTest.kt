package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h1000dp-mdpi")
class FoldKeyboardLayoutTest {
    @Test fun splitAndAnchorsChoosePanesAndScreenEdges() {
        assertEquals(SplitSlot.entries, FoldKeyboardLayout.SPLIT.slots)
        assertEquals(listOf(SplitSlot.MAIN), FoldKeyboardLayout.LEFT.slots)
        assertEquals(listOf(SplitSlot.MAIN), FoldKeyboardLayout.RIGHT.slots)
        assertEquals(400, FoldKeyboardLayout.SPLIT.paneWidth(800, 280))
        assertEquals(200, FoldKeyboardLayout.SPLIT.paneWidth(400, 280))
        assertEquals(250, FoldKeyboardLayout.LEFT.paneWidth(250, 280))
        assertEquals(0, FoldKeyboardLayout.LEFT.paneX(SplitSlot.MAIN, 800, 280))
        assertEquals(520, FoldKeyboardLayout.RIGHT.paneX(SplitSlot.MAIN, 800, 280))
        assertEquals(0, FoldKeyboardLayout.SPLIT.paneX(SplitSlot.MAIN, 800, 280))
        assertEquals(520, FoldKeyboardLayout.SPLIT.paneX(SplitSlot.SUB, 800, 280))
    }

    @Test fun foldPanesDockInsideImeWithVisibleCandidatesAndNoFloatingChrome() {
        for (layout in FoldKeyboardLayout.entries) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val host = FrameLayout(activity)
            activity.setContentView(host)
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            prefs.edit().clear().putString(SplitKeyboardSettings.CANDIDATES, "SUB").commit()
            SplitKeyboardSettings(prefs).savePlacement(SplitSlot.MAIN, false, SplitPlacement(.5f, 0f, 600f, 500f))
            var activated: SplitSlot? = null
            val controller = SplitKeyboardController(activity, host, { activated = it }, {}, {}, foldLayout = layout)
            val bodies = layout.slots.associateWith { FrameLayout(activity) }
            val candidates = layout.slots.associateWith {
                object : RecyclerView(activity) {
                    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                        setMeasuredDimension(MeasureSpec.getSize(widthSpec), 110)
                    }
                    override fun onTouchEvent(event: MotionEvent): Boolean = true
                }
            }
            fun measureHost(width: Int) {
                host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(350, View.MeasureSpec.EXACTLY))
                host.layout(0, 0, width, 350)
            }
            bodies.forEach { (slot, body) -> controller.add(slot, body, candidates.getValue(slot), 240) }
            try {
                measureHost(800)
                controller.start()
                measureHost(800)
                assertEquals(layout.slots.size, host.childCount)
                for ((index, slot) in layout.slots.withIndex()) {
                    val pane = host.getChildAt(index) as ViewGroup
                    assertSame(host, pane.parent)
                    assertEquals(1, pane.childCount) // content only: no floating controls or borders
                    assertEquals(if (layout == FoldKeyboardLayout.SPLIT) 400 else 280, pane.width)
                    assertEquals(350, pane.height)
                    val list = candidates.getValue(slot)
                    assertEquals(View.VISIBLE, list.visibility)
                    assertEquals(110, list.height)
                    assertTrue(bodies.getValue(slot).height > 0)
                    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0)
                    try { pane.dispatchTouchEvent(event) } finally { event.recycle() }
                    assertEquals(slot, activated)
                }
                if (layout == FoldKeyboardLayout.SPLIT) {
                    assertEquals(host.getChildAt(0).right, host.getChildAt(1).left)
                } else {
                    assertEquals(if (layout == FoldKeyboardLayout.LEFT) 0 else 520, host.getChildAt(0).left)
                }
                controller.setEditing(true)
                assertFalse(controller.editing)
                controller.setCandidatesDetached(true)
                assertTrue(candidates.values.all { it.visibility == View.VISIBLE })
                measureHost(1000)
                controller.refresh()
                measureHost(1000)
                if (layout == FoldKeyboardLayout.SPLIT) {
                    assertEquals(500, host.getChildAt(0).width)
                    assertEquals(500, host.getChildAt(1).left)
                }
            } finally {
                controller.stop()
                assertEquals(0, host.childCount)
                prefs.edit().clear().commit()
                activity.finish()
            }
        }
    }
}
