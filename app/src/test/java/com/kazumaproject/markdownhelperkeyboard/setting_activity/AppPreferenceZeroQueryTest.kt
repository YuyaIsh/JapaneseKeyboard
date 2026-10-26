package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceZeroQueryTest {
    private lateinit var context: Context
    private val key = "zero_query_suggestion_preference"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun absentPreferenceEnablesZeroQueryInSnapshot() {
        assertTrue(AppPreference.zero_query_suggestion_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).zeroQuerySuggestionPreference)
    }

    @Test
    fun newAndLegacySettingsInitializeZeroQueryEnabled() {
        for (resource in listOf(R.xml.pref_dictionary, R.xml.pref_common_legacy)) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().remove(key).commit()
            PreferenceManager.setDefaultValues(context, resource, true)
            assertTrue(preferences.getBoolean(key, false))
        }
    }

    @Test
    fun explicitOffSurvivesInitializationAndBothSettingsDefaults() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putBoolean(key, false).commit()
        for (resource in listOf(R.xml.pref_dictionary, R.xml.pref_common_legacy)) {
            PreferenceManager.setDefaultValues(context, resource, true)
            AppPreference.init(context)
            assertFalse(preferences.getBoolean(key, true))
            assertFalse(AppPreference.zero_query_suggestion_preference)
            assertFalse(ImePreferencesSnapshot.from(AppPreference).zeroQuerySuggestionPreference)
        }
    }
}
