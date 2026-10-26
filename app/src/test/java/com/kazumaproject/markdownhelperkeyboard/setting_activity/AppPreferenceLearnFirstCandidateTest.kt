package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceLearnFirstCandidateTest {
    private lateinit var context: Context
    private val key = "learn_first_candidate_dictionary_preference"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun unsetPreferenceEnablesLearningInSnapshotAndDictionarySettings() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertFalse(preferences.contains(key))
        assertTrue(AppPreference.learn_first_candidate_dictionary_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).learnFirstCandidateDictionaryPreference)
        assertFalse(preferences.contains(key))

        val screen = PreferenceManager(context).inflateFromResource(context, R.xml.pref_dictionary, null)
        assertTrue(screen.findPreference<SwitchPreferenceCompat>(key)?.isChecked == true)
        assertTrue(preferences.getBoolean(key, false))
    }

    @Test
    fun explicitOffSurvivesInitializationAndDictionarySettings() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putBoolean(key, false).commit()
        AppPreference.init(context)

        assertFalse(AppPreference.learn_first_candidate_dictionary_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).learnFirstCandidateDictionaryPreference)
        val screen = PreferenceManager(context).inflateFromResource(context, R.xml.pref_dictionary, null)
        assertFalse(screen.findPreference<SwitchPreferenceCompat>(key)?.isChecked == true)
        assertFalse(preferences.getBoolean(key, true))
    }

    @Test
    fun enabledPreferenceLearnsNormallyCommittedFirstCandidate() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("すみれ")
        session.record(
            LearningFragment(
                reading = "すみれ",
                output = "菫",
                candidateScore = 40_000,
                candidateIndex = 0,
            )
        )

        val entries = session.finish(
            learnFirstCandidate = ImePreferencesSnapshot.from(AppPreference).learnFirstCandidateDictionaryPreference,
            timestamp = 123L,
        )

        assertTrue(entries.any { it.input == "すみれ" && it.out == "菫" })
    }
}
