package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.tenkey.R as TenKeyR
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TenKeyPointerDispatchTest {
    private lateinit var activity: Activity
    private lateinit var keyboard: TenKey
    private val releases = mutableListOf<Pair<GestureType, Key>>()
    private val characters = mutableListOf<Char?>()
    private var time = 0L

    @Before fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        keyboard = TenKey(activity, Robolectric.buildAttributeSet().build())
        keyboard.applyKeyboardTheme(
            themeMode = "default",
            currentNightMode = android.content.res.Configuration.UI_MODE_NIGHT_NO,
            isDynamicColorEnabled = false,
            customBgColor = 0, customKeyColor = 0, customSpecialKeyColor = 0,
            customKeyTextColor = 0, customSpecialKeyTextColor = 0,
            liquidGlassEnable = false, customBorderEnable = false,
            customBorderColor = 0, liquidGlassKeyAlphaEnable = 255, borderWidth = 0
        )
        activity.setContentView(keyboard)
        keyboard.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        keyboard.layout(0, 0, 1000, 800)
        // Match the service's composing state: this key converts kana rather than switching IMEs.
        keyboard.setBackgroundSmallLetterKey(isLanguageEnable = false, isEnglish = false)
        keyboard.setOnFlickListener(object : FlickListener {
            override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                if (gestureType != GestureType.Down) {
                    releases += gestureType to key
                    characters += char
                }
            }
        })
    }

    @After fun tearDown() { activity.finish() }

    @Test fun nonzeroInitialPointerIdStillReleasesDakuten() {
        val dakuten = pointer(4, TenKeyR.id.key_small_letter)
        touch(MotionEvent.ACTION_DOWN, dakuten)
        touch(MotionEvent.ACTION_UP, dakuten)
        assertEquals(listOf(GestureType.Tap to Key.KeyDakutenSmall), releases)
    }

    @Test fun rolloverToDakutenThenAnotherKeyUsesPointerIndices() {
        val kana = pointer(0, TenKeyR.id.key_2)
        val dakuten = pointer(1, TenKeyR.id.key_small_letter)
        val next = pointer(2, TenKeyR.id.key_1)
        touch(MotionEvent.ACTION_DOWN, kana)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten)
        touch(MotionEvent.ACTION_POINTER_UP, kana, dakuten)
        touch(MotionEvent.ACTION_MOVE, dakuten)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), dakuten, next)
        touch(MotionEvent.ACTION_POINTER_UP, dakuten, next)
        touch(MotionEvent.ACTION_UP, next)
        assertEquals(listOf(GestureType.Tap to Key.KeyKA, GestureType.Tap to Key.KeyDakutenSmall, GestureType.Tap to Key.KeyA), releases)
    }

    @Test fun liftingTrackedDakutenThenMovingRemainingFingerDoesNotRepeat() {
        val kana = pointer(0, TenKeyR.id.key_2)
        val dakuten = pointer(1, TenKeyR.id.key_small_letter)
        touch(MotionEvent.ACTION_DOWN, kana)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten)
        touch(MotionEvent.ACTION_MOVE, kana)
        touch(MotionEvent.ACTION_UP, kana)
        assertEquals(listOf(GestureType.Tap to Key.KeyKA, GestureType.Tap to Key.KeyDakutenSmall), releases)
    }

    @Test fun newPointerAfterTrackedDakutenLiftCommitsOnlyTheNewKey() {
        val kana = pointer(0, TenKeyR.id.key_2)
        val dakuten = pointer(1, TenKeyR.id.key_small_letter)
        val next = pointer(2, TenKeyR.id.key_1)
        touch(MotionEvent.ACTION_DOWN, kana)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, next)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, next)
        touch(MotionEvent.ACTION_MOVE, kana)
        touch(MotionEvent.ACTION_UP, kana)
        assertEquals(
            listOf(GestureType.Tap to Key.KeyKA, GestureType.Tap to Key.KeyDakutenSmall, GestureType.Tap to Key.KeyA),
            releases
        )
    }

    @Test fun newPointerDoesNotResolveTrackedFingerMissingAfterThreeFingerSequence() {
        val kana = pointer(0, TenKeyR.id.key_2)
        val dakuten = pointer(1, TenKeyR.id.key_small_letter)
        val ignored = pointer(2, TenKeyR.id.key_3)
        val next = pointer(3, TenKeyR.id.key_1)
        touch(MotionEvent.ACTION_DOWN, kana)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten)
        touch(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten, ignored)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), kana, dakuten, ignored)
        touch(MotionEvent.ACTION_POINTER_UP, kana, ignored)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), ignored, next)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), ignored, next)
        touch(MotionEvent.ACTION_UP, ignored)
        assertEquals(
            listOf(GestureType.Tap to Key.KeyKA, GestureType.Tap to Key.KeyA),
            releases
        )
    }

    @Test fun cursorModeTracksNonzeroPointerIdForMovement() {
        val space = pointer(4, TenKeyR.id.key_space)
        touch(MotionEvent.ACTION_DOWN, space)
        keyboard.setTextToMoveCursorMode(true)
        val moved = space.copy(x = space.x + 40f)
        touch(MotionEvent.ACTION_MOVE, moved)
        touch(MotionEvent.ACTION_UP, moved)
        assertEquals(
            listOf(GestureType.Tap to Key.SideKeyCursorRight, GestureType.Tap to Key.SideKeySpace),
            releases
        )
    }

    @Test fun standardTapAndFlickKeepTheirCharacterOutputs() {
        val kana = pointer(0, TenKeyR.id.key_2)
        touch(MotionEvent.ACTION_DOWN, kana)
        touch(MotionEvent.ACTION_UP, kana)
        touch(MotionEvent.ACTION_DOWN, kana)
        val left = kana.copy(x = kana.x - 150f)
        touch(MotionEvent.ACTION_MOVE, left)
        touch(MotionEvent.ACTION_UP, left)
        assertEquals(listOf(GestureType.Tap to Key.KeyKA, GestureType.FlickLeft to Key.KeyKA), releases)
        assertEquals(listOf('か', 'き'), characters)
    }

    @Test fun deleteAndCursorRolloverDoNotDispatchAgainAfterTrackedFingerLifts() {
        val delete = pointer(0, TenKeyR.id.key_delete)
        val cursor = pointer(1, TenKeyR.id.key_soft_left)
        touch(MotionEvent.ACTION_DOWN, delete)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), delete, cursor)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), delete, cursor)
        touch(MotionEvent.ACTION_MOVE, delete)
        touch(MotionEvent.ACTION_UP, delete)
        assertEquals(listOf(GestureType.Tap to Key.SideKeyDelete, GestureType.Tap to Key.SideKeyCursorLeft), releases)
    }

    @Test fun cancelledDakutenDoesNotDispatchRelease() {
        val dakuten = pointer(4, TenKeyR.id.key_small_letter)
        touch(MotionEvent.ACTION_DOWN, dakuten)
        touch(MotionEvent.ACTION_CANCEL, dakuten)
        assertEquals(emptyList<Pair<GestureType, Key>>(), releases)
    }

    private data class Pointer(val id: Int, val x: Float, val y: Float)
    private fun pointer(id: Int, viewId: Int): Pointer {
        val view = keyboard.findViewById<View>(viewId)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        check(view.width > 0 && view.height > 0)
        return Pointer(id, location[0] + view.width / 2f, location[1] + view.height / 2f)
    }

    private fun touch(action: Int, vararg pointers: Pointer) {
        val properties = pointers.map { p -> MotionEvent.PointerProperties().apply { id = p.id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coordinates = pointers.map { p -> MotionEvent.PointerCoords().apply { x = p.x; y = p.y; pressure = 1f; size = 1f } }.toTypedArray()
        val event = MotionEvent.obtain(0, ++time, action, pointers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { keyboard.onTouch(keyboard, event) } finally { event.recycle() }
    }
}
