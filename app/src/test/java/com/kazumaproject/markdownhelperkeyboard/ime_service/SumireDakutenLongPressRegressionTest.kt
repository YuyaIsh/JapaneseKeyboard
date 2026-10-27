package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.FixedGestureSessionConfigSource
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SumireDakutenLongPressRegressionTest {

    @Test
    fun longPressDakuten_shouldApplyExactlyOneTransform() {
        val fixture = fixture()
        val input = fixture.input
        input.value = "か"
        val gesture = fixture.controller(dakutenMap())
        gesture.button.dispatch(MotionEvent.ACTION_DOWN, 10f, 10f, 1L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100L))
        gesture.button.dispatch(MotionEvent.ACTION_UP, 10f, 10f, 1L)

        assertEquals(listOf("long", "up"), gesture.callbacks)
        assertEquals("が", input.value)
    }

    @Test
    fun tapDakuten_appliesOneTransform() {
        val fixture = fixture()
        fixture.input.value = "か"
        val gesture = fixture.controller(dakutenMap())
        gesture.button.dispatch(MotionEvent.ACTION_DOWN, 10f, 10f, 1L)
        gesture.button.dispatch(MotionEvent.ACTION_UP, 10f, 10f, 1L)
        assertEquals("が", fixture.input.value)
    }

    @Test
    fun longPressDirectionalDakuten_appliesOneTransformOnRelease() {
        val fixture = fixture()
        fixture.input.value = "か"
        val gesture = fixture.controller(dakutenMap())
        gesture.button.dispatch(MotionEvent.ACTION_DOWN, 10f, 10f, 1L)
        gesture.button.dispatch(MotionEvent.ACTION_MOVE, -100f, 10f, 1L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100L))
        gesture.button.dispatch(MotionEvent.ACTION_UP, -100f, 10f, 1L)
        assertEquals("が", fixture.input.value)
    }

    @Test
    fun longPressDirectionalHandakuten_appliesOneTransformOnRelease() {
        val fixture = fixture()
        fixture.input.value = "は"
        val gesture = fixture.controller(dakutenMap())
        gesture.button.dispatch(MotionEvent.ACTION_DOWN, 10f, 10f, 1L)
        gesture.button.dispatch(MotionEvent.ACTION_MOVE, 120f, 10f, 1L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100L))
        gesture.button.dispatch(MotionEvent.ACTION_UP, 120f, 10f, 1L)
        assertEquals("ぱ", fixture.input.value)
    }

    @Test
    fun canceledLongPressDakuten_doesNotTransform() {
        val fixture = fixture()
        fixture.input.value = "か"
        val gesture = fixture.controller(dakutenMap())
        gesture.button.dispatch(MotionEvent.ACTION_DOWN, 10f, 10f, 1L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100L))
        gesture.button.dispatch(MotionEvent.ACTION_CANCEL, 10f, 10f, 1L)
        assertEquals("か", fixture.input.value)
    }

    private inner class Fixture(
        val input: MutableStateFlow<String>,
        val listener: FlickKeyboardView.OnKeyboardActionListener,
    ) {
        fun controller(actions: Map<FlickDirection, FlickAction>): Gesture {
            val button = View(context())
            val callbacks = mutableListOf<String>()
            CrossFlickInputController(context(), FixedGestureSessionConfigSource(config())).also { controller ->
                controller.listener = object : CrossFlickInputController.CrossFlickListener {
                    override fun onPress(action: KeyAction) = Unit
                    override fun onFlick(action: KeyAction, isFlick: Boolean) = Unit
                    override fun onFlickCommitted(fallbackAction: KeyAction?, isFlick: Boolean, direction: FlickDirection) {
                        fallbackAction?.let { listener.onAction(it, isFlick) }
                    }
                    override fun onFlickLongPress(action: KeyAction) {
                        callbacks += "long"
                        listener.onFlickActionLongPress(action)
                    }
                    override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
                        callbacks += "up"
                        listener.onFlickActionUpAfterLongPress(action, isFlick)
                    }
                }
                controller.attach(button, actions)
            }
            return Gesture(button, callbacks)
        }
    }

    private data class Gesture(val button: View, val callbacks: List<String>)

    private fun fixture(): Fixture {
        val service = IMEService().also {
            ReflectionHelpers.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context()))
            AppPreference.init(context())
            it.appPreference = AppPreference
        }
        val binding = MainLayoutBinding.inflate(LayoutInflater.from(context()))
        ReflectionHelpers.setField(service, "mainLayoutBinding", binding)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "setupCustomKeyboardListeners", ClassParameter.from(MainLayoutBinding::class.java, binding))
        return Fixture(
            input = ReflectionHelpers.getField(service, "_inputString"),
            listener = ReflectionHelpers.getField(binding.customLayoutDefault, "listener")
        )
    }

    private fun dakutenMap(): Map<FlickDirection, FlickAction> {
        val layout = KeyboardDefaultLayouts.createFinalLayout(
            mode = KeyboardInputMode.HIRAGANA,
            dynamicKeyStates = mapOf("dakuten_toggle_key" to 1),
            inputLayoutType = "flick",
            inputStyle = "default"
        )
        val key = layout.items.filterIsInstance<KeyItem>().first { it.keyData.keyId == "dakuten_toggle_key" }.keyData
        return layout.flickKeyMaps[key.label]!!.first()
    }

    private fun config() = GestureSessionConfig(
        settingsRevision = 1L,
        flickSensitivity = 100,
        flickThresholdPx = 65f,
        longPressTimeoutMillis = 100L
    )

    private fun context(): Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
    )

    private fun View.dispatch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, downTime + 1L, action, x, y, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }
}
