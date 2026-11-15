package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardInputStartSelectionTest {
    private lateinit var service: IMEService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        service = IMEService().also {
            Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
            it.appPreference = AppPreference
        }
        // Model the snapshot retained from the previous input session.
        Reflect.setField(service, "keyboardOrder", listOf(KeyboardType.TENKEY))
        Reflect.setField(service, "enableShowLastShownKeyboardInRestart", true)
        Reflect.setField(service, "lastSavedKeyboardPosition", 0)
    }

    private fun restartViewSelection(): KeyboardType {
        // The real view-only restart synchronizes runtime preferences before resetKeyboard.
        Reflect.callInstanceMethod<Unit>(service, "syncRuntimeInputPreferences")
        Reflect.callInstanceMethod<Unit>(service, "resetKeyboard")
        return Reflect.getField(service, "regularKeyboardType")
    }

    @Test
    fun viewOnlyRestartUsesNewlyConfiguredOrder() {
        AppPreference.keyboard_order = listOf(KeyboardType.SUMIRE)
        assertEquals(KeyboardType.SUMIRE, restartViewSelection())
    }

    @Test
    fun viewOnlyRestartRestoresLatestSavedPosition() {
        Reflect.setField(service, "enableShowLastShownKeyboardInRestart", false)
        AppPreference.keyboard_order = listOf(KeyboardType.TENKEY, KeyboardType.SUMIRE)
        AppPreference.save_last_used_keyboard_enable_preference = true
        AppPreference.save_last_used_keyboard_position_preference = 1
        assertEquals(KeyboardType.SUMIRE, restartViewSelection())
        Reflect.callInstanceMethod<Unit>(service, "resetKeyboard")
        assertEquals(KeyboardType.SUMIRE, Reflect.getField<KeyboardType>(service, "regularKeyboardType"))
    }

    @Test
    fun disablingRestoreUsesFirstConfiguredKeyboard() {
        Reflect.setField(service, "lastSavedKeyboardPosition", 1)
        AppPreference.keyboard_order = listOf(KeyboardType.SUMIRE, KeyboardType.QWERTY)
        AppPreference.save_last_used_keyboard_enable_preference = false
        AppPreference.save_last_used_keyboard_position_preference = 1
        assertEquals(KeyboardType.SUMIRE, restartViewSelection())
        assertEquals(1, AppPreference.save_last_used_keyboard_position_preference)
    }

    @Test
    fun staleSavedPositionNormalizesToFirstConfiguredKeyboard() {
        AppPreference.keyboard_order = listOf(KeyboardType.SUMIRE)
        AppPreference.save_last_used_keyboard_enable_preference = true
        AppPreference.save_last_used_keyboard_position_preference = 8
        assertEquals(KeyboardType.SUMIRE, restartViewSelection())
        assertEquals(0, AppPreference.save_last_used_keyboard_position_preference)
        assertEquals(0, Reflect.getField<Int>(service, "currentKeyboardOrder"))
    }

    @Test
    fun emptyOrderRetainsTenkeyFallbackWithoutPersistingAnIndex() {
        AppPreference.keyboard_order = emptyList()
        AppPreference.save_last_used_keyboard_enable_preference = true
        AppPreference.save_last_used_keyboard_position_preference = 8
        assertEquals(KeyboardType.TENKEY, restartViewSelection())
        assertEquals(8, AppPreference.save_last_used_keyboard_position_preference)
    }
}
