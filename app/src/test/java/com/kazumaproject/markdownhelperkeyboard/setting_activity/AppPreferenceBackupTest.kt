package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun setup() {
        prefs.edit().clear().commit()
        AppPreference.init(context)
    }

    class FeedbackFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(state: android.os.Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_operation_feedback, rootKey)
        }
    }

    @Test fun roundTripRestoresStorageAndNewPreferenceScreen() {
        val fixture = """{"version":1,"entries":[
            {"key":"vibration_preference","type":"boolean","value":false},
            {"key":"key_sound_volume_percent_preference","type":"int","value":67},
            {"key":"vibration_timing","type":"string","value":"30"},
            {"key":"backup_float","type":"float","value":1.25},
            {"key":"backup_long","type":"long","value":1234567890123},
            {"key":"backup_set","type":"string_set","value":["a","あ"]}
        ]}"""
        AppPreference.importAllFromJson(fixture)
        val expected = prefs.all.toMap()
        val exported = AppPreference.exportAllToJson()
        prefs.edit().clear().putBoolean("vibration_preference", true)
            .putInt("key_sound_volume_percent_preference", 2).commit()
        AppPreference.importAllFromJson(exported)
        assertEquals(expected, prefs.all)
        assertEquals(false, AppPreference.vibration_preference)
        assertEquals(67, AppPreference.key_sound_volume_percent_preference)
        repeat(2) {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
            val fragment = FeedbackFragment()
            controller.get().supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment).commitNow()
            assertFalse(fragment.findPreference<SwitchPreferenceCompat>("vibration_preference")!!.isChecked)
            assertEquals(67, fragment.findPreference<SeekBarPreference>("key_sound_volume_percent_preference")!!.value)
            assertEquals("30", fragment.findPreference<ListPreference>("vibration_timing")!!.value)
            controller.pause().stop().destroy()
        }
    }

    @Test fun unknownTypeDoesNotEraseExistingSettings() {
        prefs.edit().putString("keep", "original").commit()
        val result = runCatching {
            AppPreference.importAllFromJson("""{"version":1,"entries":[{"key":"x","type":"unknown","value":1}]}""")
        }
        assertEquals("original", prefs.getString("keep", null))
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun invalidBackupsNeverSucceedOrChangeStorage() {
        val invalid = listOf(
            "{}", "null", "{", "[]", "{\"version\":2,\"entries\":[]}",
            """{"version":1,"entries":[{"key":"x","type":"unknown","value":1}]}""",
            """{"version":1,"entries":[{"key":"x","type":"string_set","value":["ok",1]}]}""",
            """{"version":1,"entries":[{"key":"x","type":"int","value":1.5}]}""",
            """{"version":1,"entries":[{"key":"x","type":"int","value":2147483648}]}""",
            """{"version":1,"entries":[{"key":"x","type":"float","value":1e100}]}""",
            """{"version":1,"entries":[{"type":"string","value":"bad"}]}""",
            """{"version":1,"entries":[{"key":"x","type":"boolean"}]}""",
            """{"version":1,"entries":[{"key":"x","type":"boolean","value":true},{"key":"y","type":"int","value":"bad"}]}"""
        )
        for (replace in listOf(true, false)) for (json in invalid) {
            prefs.edit().putString("keep", "original").commit()
            val before = prefs.all.toMap()
            assertThrows("$replace: $json", IllegalArgumentException::class.java) {
                AppPreference.importAllFromJson(json, replace)
            }
            assertEquals(before, prefs.all)
        }
    }

    @Test fun legacyVersionOneSettingsUseExistingMigrations() {
        AppPreference.importAllFromJson("""{"version":1,"entries":[
            {"key":"sumire_keyboard_input_type_preference","type":"string","value":"flick-circle-flick"}
        ]}""")
        // Same post-import migrations as CommonPreferenceFragment's document callback.
        AppPreference.migrateSumirePreferenceIfNeeded()
        AppPreference.migratePredictionLookaheadPreferenceIfNeeded()
        assertEquals("circle", prefs.getString("sumire_keyboard_style_preference", null))
        assertEquals("flick", prefs.getString("sumire_input_method_preference", null))
        assertFalse(prefs.contains("sumire_keyboard_input_type_preference"))
    }

    @Test fun mergeRestoresEntriesAndSupportsNullRemoval() {
        prefs.edit().putString("keep", "original").putString("remove", "old").commit()
        AppPreference.importAllFromJson("""{"version":1,"entries":[
            {"key":"remove","type":"null"},
            {"key":"vibration_preference","type":"boolean","value":false}
        ]}""", false)
        assertEquals("original", prefs.getString("keep", null))
        assertFalse(prefs.contains("remove"))
        assertFalse(prefs.getBoolean("vibration_preference", true))
    }

    @Test fun versionOneEmptyBackupAndMergeAreSupported() {
        prefs.edit().putString("keep", "original").commit()
        AppPreference.importAllFromJson("""{"version":1,"entries":[]}""", false)
        assertEquals("original", prefs.getString("keep", null))
        AppPreference.importAllFromJson("""{"version":1,"entries":[]}""")
        assertFalse(prefs.contains("keep"))
    }
}
