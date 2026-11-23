package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.app.Activity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizePreferenceAccessor
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeOrientation
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeKeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeValues
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
                    activated = null
                    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 5f, 5f, 0)
                    try { pane.dispatchTouchEvent(event) } finally { event.recycle() }
                    assertNull("Candidate strips must not change the shared editor owner", activated)
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

    @Test fun eitherCandidateStripReceivesRealTapsAndLongPressesWithoutActivatingItsKeyboard() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = activityController.get()
        val context = android.view.ContextThemeWrapper(activity,
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val host = FoldKeyboardDockHost(context)
        activity.setContentView(host)
        var active = SplitSlot.MAIN
        var activations = 0
        val controller = SplitKeyboardController(context, host, { active = it; activations++ }, {}, {},
            foldLayout = FoldKeyboardLayout.SPLIT, foldBodyHeight = { 180 }, foldCandidateHeight = { 58 })
        val source = com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter(java.util.concurrent.Executor { it.run() })
        var clicks = 0
        var longPresses = 0
        source.setOnItemClickListener { _, _ -> clicks++ }
        source.setOnItemLongClickListener { _, _ -> longPresses++ }
        source.suggestions = (0 until 30).map {
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
                "変換候補$it", 1.toByte(), 2.toUByte(), 0, "へんかん")
        }
        val lists = SplitSlot.entries.associateWith {
            RecyclerView(context).apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                itemAnimator = null
                adapter = com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter(java.util.concurrent.Executor { it.run() }).apply {
                    mirrorSplitContentFrom(source)
                }
            }
        }
        SplitSlot.entries.forEach { slot ->
            controller.add(slot, object : View(context) {
                override fun onTouchEvent(event: MotionEvent) = true
            }, lists.getValue(slot), 240)
        }
        fun measure() {
            host.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST))
            host.layout(0, 0, 800, host.measuredHeight)
        }
        fun touch(slot: SplitSlot, action: Int, x: Float, y: Float, down: Long) {
            val event = MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            try { host.getChildAt(slot.ordinal).dispatchTouchEvent(event) } finally { event.recycle() }
        }
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        try {
            controller.start()
            looper.idle()
            measure()
            activityController.windowFocusChanged(true)
            for (keyboard in SplitSlot.entries) {
                val down = android.os.SystemClock.uptimeMillis()
                touch(keyboard, MotionEvent.ACTION_DOWN, 30f, host.height - 20f, down)
                touch(keyboard, MotionEvent.ACTION_UP, 30f, host.height - 20f, down)
                assertEquals(keyboard, active)
                val before = activations
                for (strip in SplitSlot.entries) {
                    val list = lists.getValue(strip)
                    val holder = checkNotNull(list.findViewHolderForAdapterPosition(0))
                    val item = holder.itemView
                    val x = list.left + item.left + item.width / 2f
                    val y = list.top + item.top + item.height / 2f
                    val tapDown = android.os.SystemClock.uptimeMillis()
                    touch(strip, MotionEvent.ACTION_DOWN, x, y, tapDown)
                    touch(strip, MotionEvent.ACTION_UP, x, y, tapDown)
                    // Let the preceding tap's delayed pressed-state cleanup finish before
                    // holding the same view; otherwise it clears this new press mid-hold.
                    looper.idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getPressedStateDuration().toLong()))
                    assertTrue("Timed platform long presses require a focused test window", item.hasWindowFocus())
                    val longDown = android.os.SystemClock.uptimeMillis()
                    touch(strip, MotionEvent.ACTION_DOWN, x, y, longDown)
                    looper.idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 100))
                    touch(strip, MotionEvent.ACTION_UP, x, y, longDown)
                    looper.idle()
                    assertEquals(keyboard, active)
                    assertEquals("Candidate gestures must not activate or finish a pane", before, activations)
                }
            }
            assertEquals(4, clicks)
            assertEquals(4, longPresses)
            val left = lists.getValue(SplitSlot.MAIN)
            val right = lists.getValue(SplitSlot.SUB)
            left.scrollBy(500, 0)
            val leftManager = left.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            val rightManager = right.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            assertTrue(leftManager.findFirstVisibleItemPosition() > 0)
            assertEquals(0, rightManager.findFirstVisibleItemPosition())
            val leftPosition = leftManager.findFirstVisibleItemPosition()
            right.scrollBy(200, 0)
            assertEquals(leftPosition, leftManager.findFirstVisibleItemPosition())
        } finally {
            controller.stop()
            lists.values.forEach { (it.adapter as? com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter)?.release() }
            source.release()
            activity.finish()
        }
    }

    @Test fun touchingOtherFoldPaneCancelsKeyboardAndCandidateSequencesBeforeActivation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FoldKeyboardDockHost(activity)
        activity.setContentView(host)
        val events = mutableListOf<String>()
        val controller = SplitKeyboardController(activity, host,
            { events += "activate:$it" }, { events += "save:$it" }, {},
            onGestureChanged = { slot, touching -> events += "gesture:$slot:$touching" },
            foldLayout = FoldKeyboardLayout.SPLIT, foldBodyHeight = { 180 }, foldCandidateHeight = { 58 })
        val bodies = SplitSlot.entries.associateWith { slot ->
            object : View(activity) {
                private val held = Runnable { events += "held:$slot" }
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    events += "body:$slot:${event.actionMasked}"
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> postDelayed(held, 200)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(held)
                    }
                    return true
                }
            }
        }
        for (slot in SplitSlot.entries) {
            val list = object : RecyclerView(activity) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    events += "candidate:$slot:${event.actionMasked}"
                    return true
                }
            }
            controller.add(slot, bodies.getValue(slot), list, 240)
        }
        fun touch(slot: SplitSlot, action: Int, y: Float? = null) {
            // The activity may perform another bounded layout while the hold timer runs.
            // Always target the actual bottom-docked keyboard, rather than the gap above it.
            val body = bodies.getValue(slot).parent as View
            val contents = body.parent as View
            val touchY = y ?: (contents.top + body.top + body.height / 2f)
            val event = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), action, 30f, touchY, 0)
            try { host.getChildAt(slot.ordinal).dispatchTouchEvent(event) } finally { event.recycle() }
        }
        try {
            controller.start()
            host.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST))
            host.layout(0, 0, 800, host.measuredHeight)
            touch(SplitSlot.MAIN, MotionEvent.ACTION_DOWN)
            events.clear()
            touch(SplitSlot.SUB, MotionEvent.ACTION_DOWN)
            assertEquals("body:MAIN:${MotionEvent.ACTION_CANCEL}", events.first())
            assertTrue(events.indexOf("gesture:MAIN:false") < events.indexOf("activate:SUB"))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250))
            assertFalse(events.contains("held:MAIN"))
            events.clear()
            touch(SplitSlot.MAIN, MotionEvent.ACTION_MOVE)
            touch(SplitSlot.MAIN, MotionEvent.ACTION_UP)
            assertTrue("Canceled pointers must not re-activate, save, or dispatch to keys", events.isEmpty())
            touch(SplitSlot.SUB, MotionEvent.ACTION_UP)
            touch(SplitSlot.MAIN, MotionEvent.ACTION_DOWN, 10f) // candidate press
            events.clear()
            touch(SplitSlot.SUB, MotionEvent.ACTION_DOWN)
            assertEquals("candidate:MAIN:${MotionEvent.ACTION_CANCEL}", events.first())
            assertTrue(events.indexOf("gesture:MAIN:false") < events.indexOf("activate:SUB"))
            events.clear()
            touch(SplitSlot.MAIN, MotionEvent.ACTION_UP, 10f)
            assertTrue(events.isEmpty())
            touch(SplitSlot.MAIN, MotionEvent.ACTION_DOWN)
            assertTrue("A fresh DOWN starts a new valid sequence", events.contains("activate:MAIN"))
            assertTrue("Fresh DOWN must reach the keyboard: ${events.joinToString()}",
                events.contains("body:MAIN:${MotionEvent.ACTION_DOWN}"))
            touch(SplitSlot.MAIN, MotionEvent.ACTION_UP)
        } finally {
            controller.stop()
            activity.finish()
        }
    }

    @Test fun sizingPersistsAcrossRotationAndReopenForBothPaneSelections() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().clear().commit()
        val appPreference = AppPreference
        appPreference.init(activity)
        val accessor = KeyboardSizePreferenceAccessor(appPreference)
        val portrait = KeyboardSizeOrientation.Portrait
        val landscape = KeyboardSizeOrientation.Landscape
        val tenkey = KeyboardSizeKeyboardType.TenKey
        val qwerty = KeyboardSizeKeyboardType.Qwerty
        val closed = KeyboardSizeValues(220, 95, 0, 0, 0)
        accessor.save(portrait, tenkey, closed)
        try {
            for (orientation in listOf(portrait, landscape)) {
                accessor.save(orientation, tenkey, closed.copy(heightDp = 260, widthPercent = 80, bottomMarginDp = 20, marginEndDp = 12), true)
                accessor.save(orientation, qwerty, closed.copy(heightDp = 180, widthPercent = 60, bottomMarginDp = 10, marginEndDp = 8), true)
            }
            // Recreate the actual dock/controller with persisted settings, switching which
            // pane uses QWERTY to cover MAIN and SUB independently of input ownership.
            for (orientation in listOf(portrait, landscape, portrait)) {
                for (qwertySlot in SplitSlot.entries) {
                    fun values(slot: SplitSlot) = accessor.load(orientation, if (slot == qwertySlot) qwerty else tenkey, true)
                    val host = FoldKeyboardDockHost(activity) { slot ->
                        values(slot).let { FoldPanePlacement(it.widthPercent, it.bottomMarginDp, true, it.marginStartDp, it.marginEndDp) }
                    }
                    activity.setContentView(host)
                    val controller = SplitKeyboardController(activity, host, {}, {}, {}, foldLayout = FoldKeyboardLayout.SPLIT,
                        foldBodyHeight = { values(it).heightDp }, foldCandidateHeight = { 50 })
                    val bodies = SplitSlot.entries.associateWith { View(activity) }
                    bodies.forEach { (slot, body) -> controller.add(slot, body, RecyclerView(activity), 160) }
                    try {
                        controller.start()
                        val width = if (orientation == landscape) 1200 else 800
                        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST))
                        host.layout(0, 0, width, host.measuredHeight)
                        for (slot in SplitSlot.entries) {
                            val value = values(slot)
                            val pane = host.getChildAt(slot.ordinal)
                            assertEquals(width / 2 * value.widthPercent / 100, pane.width)
                            val origin = if (slot == SplitSlot.SUB) width / 2 else 0
                            assertEquals(origin + width / 2 - pane.width - value.marginEndDp, pane.left)
                            assertEquals(host.height - value.bottomMarginDp, pane.bottom)
                            assertEquals(value.heightDp, (bodies.getValue(slot).parent as View).height)
                        }
                        // A subsequent resize uses the same saved settings on each pane.
                        accessor.save(orientation, qwerty, values(qwertySlot).copy(widthPercent = 50, heightDp = 210), true)
                        controller.refresh()
                        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST))
                        host.layout(0, 0, width, host.measuredHeight)
                        assertEquals(width / 4, host.getChildAt(qwertySlot.ordinal).width)
                        assertEquals(210, (bodies.getValue(qwertySlot).parent as View).height)
                    } finally { controller.stop() }
                }
            }
            assertEquals(closed, accessor.load(portrait, tenkey))
        } finally { prefs.edit().clear().commit(); activity.finish() }
    }

}
