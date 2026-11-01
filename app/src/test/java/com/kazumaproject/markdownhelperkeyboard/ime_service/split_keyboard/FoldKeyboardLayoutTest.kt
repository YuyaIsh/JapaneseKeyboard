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
            val host = FoldKeyboardDockHost(activity).apply { foldLayout = layout }
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
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun dockMeasuresLargeFontCandidatesAndConfiguredBodiesAcrossNarrowPortraitAndRotation() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.8f)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = activity
        assertEquals(1.8f, context.resources.configuration.fontScale, .001f)
        assertEquals(context.resources.displayMetrics.density * 1.8f, context.resources.displayMetrics.scaledDensity, .001f)
        val host = FoldKeyboardDockHost(context)
        activity.setContentView(host)
        var bodyHeight = 320
        val controller = SplitKeyboardController(context, host, {}, {}, {}, foldLayout = FoldKeyboardLayout.SPLIT,
            foldBodyHeight = { bodyHeight }, foldCandidateHeight = { 58 })
        val bodies = SplitSlot.entries.associateWith { View(context) }
        val candidates = SplitSlot.entries.associateWith {
            RecyclerView(context).apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = 1
                    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
                        object : RecyclerView.ViewHolder(android.widget.TextView(context).apply {
                            textSize = 32f
                            text = "変換候補"
                            layoutParams = RecyclerView.LayoutParams(-2, -2)
                        }) {}
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
                }
            }
        }
        for (slot in SplitSlot.entries) controller.add(slot, bodies.getValue(slot), candidates.getValue(slot), 240)
        fun measure(width: Int, heightLimit: Int) {
            host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightLimit, View.MeasureSpec.AT_MOST))
            host.layout(0, 0, width, host.measuredHeight)
        }
        try {
            controller.start()
            measure(800, 1000)
            val candidateHeight = candidates.getValue(SplitSlot.MAIN).height
            assertTrue("Font scale must enlarge the actual candidate row (actual=$candidateHeight)", candidateHeight > 58)
            assertEquals(320 + candidateHeight, host.height)
            assertEquals(1f, bodies.getValue(SplitSlot.MAIN).scaleY, .001f)
            measure(360, 1000) // narrow portrait: child width stays usable and touch matrices scale it
            assertEquals(180, host.getChildAt(0).width)
            assertEquals(180, host.getChildAt(1).left)
            assertEquals(320 + candidateHeight, host.height)
            assertEquals(.75f, bodies.getValue(SplitSlot.MAIN).scaleX, .001f)
            bodyHeight = 260 // user's landscape setting
            measure(900, 300)
            assertEquals(300, host.height)
            assertEquals(candidateHeight, candidates.getValue(SplitSlot.MAIN).height)
            assertEquals(450, host.getChildAt(0).width)
            assertEquals(450, host.getChildAt(1).left)
            assertTrue(bodies.getValue(SplitSlot.MAIN).scaleY < 1f)
            assertEquals(260, bodies.getValue(SplitSlot.MAIN).height)
        } finally {
            controller.stop()
            activity.finish()
        }
    }

    @Test fun realJapaneseAndEnglishBodiesKeepTheirConfiguredHeightsAndBottomAlignment() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = android.view.ContextThemeWrapper(activity, com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val host = FoldKeyboardDockHost(context)
        activity.setContentView(host)
        val controller = SplitKeyboardController(context, host, {}, {}, {}, foldLayout = FoldKeyboardLayout.SPLIT,
            foldBodyHeight = { if (it == SplitSlot.MAIN) 140 else 260 }, foldCandidateHeight = { 58 })
        val bindings = SplitSlot.entries.associateWith { slot ->
            com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding.inflate(android.view.LayoutInflater.from(context)).apply {
                keyboardViewFloating.visibility = if (slot == SplitSlot.MAIN) View.VISIBLE else View.GONE
                qwertyViewFloating.visibility = if (slot == SplitSlot.SUB) View.VISIBLE else View.GONE
                gojuonViewFloating.visibility = View.GONE
                customLayoutFloating.visibility = View.GONE
                val body = root as FrameLayout
                body.removeView(floatingKeyboardContent)
                listOf(floatingKeyboardContainer, floatingSymbolKeyboard).forEach { child ->
                    (child.parent as? ViewGroup)?.removeView(child)
                    body.addView(child, FrameLayout.LayoutParams(-1, -1))
                }
                floatingKeyboardBackgroundContainer.layoutParams = FrameLayout.LayoutParams(-1, -1)
                floatingKeyboardTouchEffectContainer.layoutParams = FrameLayout.LayoutParams(-1, -1)
                body.fitsSystemWindows = false
                controller.add(slot, body, suggestionRecyclerView, 240)
            }
        }
        try {
            controller.start()
            host.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
            host.layout(0, 0, 800, host.measuredHeight)
            val leftBody = (bindings.getValue(SplitSlot.MAIN).root.parent as View)
            val rightBody = (bindings.getValue(SplitSlot.SUB).root.parent as View)
            assertEquals(140, leftBody.height)
            assertEquals(140, bindings.getValue(SplitSlot.MAIN).root.height)
            assertEquals(1f, bindings.getValue(SplitSlot.MAIN).root.scaleY, .001f)
            assertEquals(260, rightBody.height)
            assertEquals(260, bindings.getValue(SplitSlot.SUB).root.height)
            assertEquals(1f, bindings.getValue(SplitSlot.SUB).root.scaleY, .001f)
            assertEquals(host.height, leftBody.bottom)
            assertEquals(host.height, rightBody.bottom)
            assertEquals(318, host.height)
            assertEquals(58, bindings.getValue(SplitSlot.MAIN).suggestionRecyclerView.height)
        } finally {
            controller.stop()
            activity.finish()
        }
    }

}
