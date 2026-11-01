package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame
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
        assertEquals(280, FoldKeyboardLayout.SPLIT.paneWidth(800, 280))
        assertEquals(200, FoldKeyboardLayout.SPLIT.paneWidth(400, 280))
        assertEquals(250, FoldKeyboardLayout.LEFT.paneWidth(250, 280))
        assertEquals(0, FoldKeyboardLayout.LEFT.paneX(SplitSlot.MAIN, 800, 280))
        assertEquals(520, FoldKeyboardLayout.RIGHT.paneX(SplitSlot.MAIN, 800, 280))
        assertEquals(0, FoldKeyboardLayout.SPLIT.paneX(SplitSlot.MAIN, 800, 280))
        assertEquals(520, FoldKeyboardLayout.SPLIT.paneX(SplitSlot.SUB, 800, 280))
    }

    @Test fun foldPanesIgnoreSavedPlacementAndKeepMeasuredCandidatesAboveBody() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().putString(SplitKeyboardSettings.CANDIDATES, "SUB").commit()
        val settings = SplitKeyboardSettings(prefs)
        settings.savePlacement(SplitSlot.MAIN, false, SplitPlacement(.5f, 0f, 600f, 500f))
        val controller = SplitKeyboardController(activity, activity.window.decorView, {}, {}, {}, foldLayout = FoldKeyboardLayout.SPLIT)
        val bodies = SplitSlot.entries.associateWith { FrameLayout(activity) }
        val candidates = SplitSlot.entries.associateWith {
            object : RecyclerView(activity) {
                override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                    setMeasuredDimension(MeasureSpec.getSize(widthSpec), 110)
                }
            }
        }
        fun panel(body: View): FloatingPanelFrame {
            var current = body
            while (current !is FloatingPanelFrame) current = current.parent as View
            return current
        }
        bodies.forEach { (slot, body) -> controller.add(slot, body, candidates.getValue(slot), 240) }
        try {
            controller.start()
            val main = panel(bodies.getValue(SplitSlot.MAIN))
            val sub = panel(bodies.getValue(SplitSlot.SUB))
            val left = main.layoutParams as WindowManager.LayoutParams
            val right = sub.layoutParams as WindowManager.LayoutParams
            assertEquals(280, left.width)
            assertTrue(left.x + left.width <= right.x)
            assertEquals(left.y + left.height, right.y + right.height)
            for (slot in SplitSlot.entries) {
                val list = candidates.getValue(slot)
                assertEquals(View.VISIBLE, list.visibility)
                assertEquals(110, list.layoutParams.height)
                val root = panel(bodies.getValue(slot))
                val params = root.layoutParams as WindowManager.LayoutParams
                assertEquals(240 + 110 + root.contentInsets.top + root.contentInsets.bottom, params.height)
                assertEquals(View.GONE, root.getChildAt(1).visibility)
            }
            controller.setEditing(true)
            assertFalse(controller.editing)
            val withCandidates = left.height
            controller.setCandidatesDetached(true)
            assertEquals(110, withCandidates - (main.layoutParams as WindowManager.LayoutParams).height)
        } finally {
            controller.stop()
            prefs.edit().clear().commit()
            activity.finish()
        }
    }
}
