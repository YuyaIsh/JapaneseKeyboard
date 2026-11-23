package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.KeyboardLayoutState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualKeyboardSizeSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun setup() {
        KeyboardLayoutState.publishPosture(false)
        prefs.edit().clear().commit()
        AppPreference.init(context)
    }

    @After fun restorePosture() {
        KeyboardLayoutState.publishPosture(false)
    }

    @Test fun frequentSizeSettingsOpenHistoricalVisualEditors() {
        val candidates = SettingDestinations.frequentCandidates(context).associateBy { it.key }
        assertEquals(SettingDestinationType.NavDestination(R.id.keyboardSettingFragment),
            candidates.getValue("keyboard_screen_preference").destination)
        assertEquals(SettingDestinationType.NavDestination(R.id.keyboardSizeLandscapeFragment),
            candidates.getValue("keyboard_screen_landscape_preference").destination)
    }

    @Test fun visualEditorPreferencesPersistSeparatelyForPostureAndOrientation() {
        AppPreference.keyboard_height = 204
        AppPreference.keyboard_width = 73
        AppPreference.keyboard_position = false
        AppPreference.keyboard_vertical_margin_bottom = 8
        AppPreference.keyboard_margin_start_dp = 11
        AppPreference.qwerty_keyboard_height_landscape = 178
        AppPreference.qwerty_keyboard_width_landscape = 65
        AppPreference.qwerty_keyboard_position_landscape = true
        AppPreference.qwerty_keyboard_margin_end_dp_landscape = 9
        val closed = prefs.all.toMap()

        KeyboardLayoutState.publishPosture(true)
        AppPreference.keyboard_height = 285
        AppPreference.keyboard_width = 81
        AppPreference.keyboard_position = true
        AppPreference.keyboard_vertical_margin_bottom = 16
        AppPreference.keyboard_margin_end_dp = 7
        AppPreference.qwerty_keyboard_height_landscape = 245
        AppPreference.qwerty_keyboard_width_landscape = 88
        AppPreference.qwerty_keyboard_position_landscape = false
        AppPreference.qwerty_keyboard_margin_start_dp_landscape = 12
        AppPreference.init(context)

        assertEquals(285, AppPreference.keyboard_height)
        assertEquals(81, AppPreference.keyboard_width)
        assertEquals(true, AppPreference.keyboard_position)
        assertEquals(16, AppPreference.keyboard_vertical_margin_bottom)
        assertEquals(7, AppPreference.keyboard_margin_end_dp)
        assertEquals(245, AppPreference.qwerty_keyboard_height_landscape)
        assertEquals(88, AppPreference.qwerty_keyboard_width_landscape)
        assertEquals(false, AppPreference.qwerty_keyboard_position_landscape)
        assertEquals(12, AppPreference.qwerty_keyboard_margin_start_dp_landscape)
        closed.forEach { (key, value) -> assertEquals("Closed setting changed: $key", value, prefs.all[key]) }

        KeyboardLayoutState.publishPosture(false)
        assertEquals(204, AppPreference.keyboard_height)
        assertEquals(73, AppPreference.keyboard_width)
        assertEquals(false, AppPreference.keyboard_position)
        assertEquals(8, AppPreference.keyboard_vertical_margin_bottom)
        assertEquals(11, AppPreference.keyboard_margin_start_dp)
        assertEquals(178, AppPreference.qwerty_keyboard_height_landscape)
        assertEquals(65, AppPreference.qwerty_keyboard_width_landscape)
        assertEquals(true, AppPreference.qwerty_keyboard_position_landscape)
        assertEquals(9, AppPreference.qwerty_keyboard_margin_end_dp_landscape)
    }
    @Test fun asymmetricSumirePanelPreferencesRemainIndependentOfOuterKeyboardSizing() {
        for (landscape in listOf(false, true)) {
            val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                orientation = if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    else android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            val prefix = com.kazumaproject.markdownhelperkeyboard.ime_service.sumire.SumirePanelSettings.prefix(true, landscape)
            prefs.edit().putBoolean("${prefix}_enabled", true).putBoolean("${prefix}_symmetric", false)
                .putInt("${prefix}_left_width", 30).putInt("${prefix}_left_height", 60).putInt("${prefix}_left_offset", 5)
                .putInt("${prefix}_right_width", 40).putInt("${prefix}_right_height", 90).putInt("${prefix}_right_offset", 3).commit()
            KeyboardLayoutState.publishPosture(true)
            AppPreference.keyboard_width = 75
            AppPreference.qwerty_keyboard_width = 65
            val panels = com.kazumaproject.markdownhelperkeyboard.ime_service.sumire.SumirePanelSettings.load(prefs, configuration, true)!!
            assertEquals(com.kazumaproject.custom_keyboard.layout.SumirePanelSize(30, 60, 5), panels.left)
            assertEquals(com.kazumaproject.custom_keyboard.layout.SumirePanelSize(40, 90, 3), panels.right)
        }
    }

}
