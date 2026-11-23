package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitSlot
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeValues
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.FoldSizingPreview
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h900dp-mdpi")
class FoldSizingPreviewTest {
    class PreviewFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = FrameLayout(requireContext())
    }

    @Test fun configuredBodiesStaySideBySideDuringLiveNumericAndGuiEdits() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            prefs.edit().clear()
                .putString(SplitKeyboardSettings.typeKey(SplitSlot.MAIN), "SUMIRE")
                .putString(SplitKeyboardSettings.typeKey(SplitSlot.SUB), "QWERTY").commit()
            AppPreference.init(activity)
            val repository = mock<KeyboardRepository>()
            whenever(repository.getLayoutsNotFlowEnsuringStableIds()).thenReturn(emptyList())
            val sumireRepository = mock<SumireSpecialKeyRepository>()
            whenever(sumireRepository.observeAllActionOverrides()).thenReturn(flowOf(emptyList()))
            whenever(sumireRepository.observeAllPlacementOverrides()).thenReturn(flowOf(emptyList()))
            val fragment = PreviewFragment()
            activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
            var mainValues = KeyboardSizeValues(220, 80, 10, 0, 12)
            var subValues = KeyboardSizeValues(190, 65, 20, 8, 0)
            var completedDrags = 0
            var selectedFamily: Boolean? = null
            val preview = FoldSizingPreview(fragment, AppPreference, repository, sumireRepository, false,
                values = { if (it) subValues else mainValues }, positionIsEnd = { !it },
                onSelectFamily = { selectedFamily = it },
                onGesture = { qwerty, value, finished ->
                    if (qwerty) subValues = value else mainValues = value
                    if (finished) completedDrags++
                })
            (fragment.requireView() as FrameLayout).addView(preview.view, FrameLayout.LayoutParams(-1, 280))
            // Repository work runs on IO; allow it to finish and drain Main's continuations.
            repeat(100) {
                advanceUntilIdle()
                if (descendants(preview.view).any { it is QWERTYKeyboardView }) return@repeat
                withContext(Dispatchers.IO) { Thread.sleep(10) }
            }
            advanceUntilIdle()
            fun measure() {
                preview.view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY))
                preview.view.layout(0, 0, 800, 280)
            }
            fun pane(slot: String) = descendants(preview.view).single { it.contentDescription == "$slot keyboard preview" }
            measure()
            val main = pane("MAIN")
            val sub = pane("SUB")
            assertTrue(descendants(main).any { it is FlickKeyboardView })
            assertTrue(descendants(sub).any { it is QWERTYKeyboardView })
            assertFalse(descendants(main).any { it is QWERTYKeyboardView })
            assertTrue(descendants(preview.view).filterIsInstance<TextView>().any { it.text.startsWith("MAIN ·") })
            assertTrue(descendants(preview.view).filterIsInstance<TextView>().any { it.text.startsWith("SUB ·") })
            val mainLocation = IntArray(2).also(main::getLocationOnScreen)
            val subLocation = IntArray(2).also(sub::getLocationOnScreen)
            assertTrue(mainLocation[0] + main.width * preview.previewScale <= subLocation[0] + 1)
            val before = prefs.all.toMap()
            val subWidth = sub.width
            val mainWidth = main.width
            mainValues = mainValues.copy(heightDp = 300, widthPercent = 50, bottomMarginDp = 24)
            preview.refresh()
            measure()
            assertTrue(main.width < mainWidth)
            assertEquals(300, main.height)
            assertEquals(subWidth, sub.width)
            assertEquals(before, prefs.all)
            preview.setEditing(false)
            assertEquals(View.VISIBLE, main.visibility)
            assertEquals(View.VISIBLE, sub.visibility)
            val handle = descendants(main).single { it.contentDescription == "MAIN resize height" }
            assertEquals(View.GONE, handle.visibility)
            preview.setEditing(true)
            val start = mainValues.heightDp
            for ((action, y) in listOf(MotionEvent.ACTION_DOWN to 100f, MotionEvent.ACTION_MOVE to 80f, MotionEvent.ACTION_UP to 80f)) {
                val event = MotionEvent.obtain(0, 0, action, 0f, y, 0)
                try { handle.dispatchTouchEvent(event) } finally { event.recycle() }
                if (action == MotionEvent.ACTION_MOVE) {
                    measure()
                    assertTrue("Dragging must resize before release", main.height > start)
                    assertEquals(0, completedDrags)
                    assertEquals(before, prefs.all)
                }
            }
            assertEquals(false, selectedFamily)
            assertTrue(mainValues.heightDp > start)
            assertEquals(1, completedDrags)
            assertEquals(before, prefs.all)
        } finally {
            activity.finish()
            Dispatchers.resetMain()
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
