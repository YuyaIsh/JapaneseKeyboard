package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionAggressiveness
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PredictionPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsAreSeparatedFromNBestAndMatchTheSettingsScreen() {
        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(PredictionConfig(), snapshot.predictionConfig)
        assertEquals(6, snapshot.predictionConfig.lookaheadCharacterCount)
        assertTrue(snapshot.predictionConfig.acceptsJapaneseReading("おつ".length, "おつかれさまです".length))
        assertEquals(4, snapshot.nBest)
        assertEquals(4, snapshot.userDictionaryPredictionCandidateLimit)
        assertEquals(4, snapshot.learnDictionaryPredictionCandidateLimit)
        assertEquals(4, snapshot.learnPredictionPreference)
    }

    @Test
    fun learnedDictionaryPredictionDefaultsToEnabledWithoutWritingAStoredValue() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "enable_prediction_search_learn_dictionary_preference"

        assertFalse(preferences.contains(key))
        assertTrue(AppPreference.enable_prediction_search_learn_dictionary_preference)
        assertTrue(ImePreferencesSnapshot.from(AppPreference).enablePredictionSearchLearnDictionaryPreference)
        assertFalse(preferences.contains(key))

        val screen = PreferenceManager(context).inflateFromResource(
            context, R.xml.pref_dictionary, null,
        )
        assertTrue(screen.findPreference<androidx.preference.SwitchPreferenceCompat>(key)?.isChecked == true)
    }

    @Test
    fun storedLearnedDictionaryPredictionChoiceSurvivesInitializationAndScreenInflation() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "enable_prediction_search_learn_dictionary_preference"

        for (stored in listOf(false, true)) {
            preferences.edit().putBoolean(key, stored).commit()
            AppPreference.init(context)

            assertEquals(stored, AppPreference.enable_prediction_search_learn_dictionary_preference)
            assertEquals(stored, ImePreferencesSnapshot.from(AppPreference).enablePredictionSearchLearnDictionaryPreference)
            val screen = PreferenceManager(context).inflateFromResource(
                context, R.xml.pref_dictionary, null,
            )
            assertEquals(stored, screen.findPreference<androidx.preference.SwitchPreferenceCompat>(key)?.isChecked)
            assertEquals(stored, preferences.getBoolean(key, !stored))
        }
    }

    @Test
    fun predictionSettingsArePersistedInTheImeSnapshot() {
        AppPreference.japanese_prediction_enable_preference = false
        AppPreference.english_prediction_enable_preference = false
        AppPreference.system_dictionary_prediction_enable_preference = false
        AppPreference.prediction_minimum_input_length_preference = 8
        AppPreference.system_prediction_candidate_limit_preference = 16
        AppPreference.prediction_lookahead_character_count_preference = 6
        AppPreference.prediction_aggressiveness_preference = "aggressive"
        AppPreference.system_user_dictionary_prediction_enable_preference = false
        AppPreference.reading_correction_prediction_enable_preference = false
        AppPreference.proverb_prediction_enable_preference = false
        AppPreference.external_mozc_prediction_enable_preference = false
        AppPreference.symbol_emoji_prediction_enable_preference = false
        AppPreference.symbol_candidate_enable_preference = false
        AppPreference.emoji_candidate_enable_preference = false
        AppPreference.emoticon_candidate_enable_preference = false
        AppPreference.user_dictionary_prediction_candidate_limit_preference = 2
        AppPreference.learn_dictionary_prediction_candidate_limit_preference = 7

        val snapshot = ImePreferencesSnapshot.from(AppPreference)
        val prediction = snapshot.predictionConfig

        assertFalse(prediction.japanesePredictionEnabled)
        assertFalse(prediction.englishPredictionEnabled)
        assertFalse(prediction.systemDictionaryEnabled)
        assertEquals(8, prediction.minimumInputLength)
        assertEquals(16, prediction.systemCandidateLimit)
        assertEquals(6, prediction.lookaheadCharacterCount)
        assertEquals(PredictionAggressiveness.AGGRESSIVE, prediction.aggressiveness)
        assertFalse(prediction.systemUserDictionaryEnabled)
        assertFalse(prediction.readingCorrectionEnabled)
        assertFalse(prediction.proverbEnabled)
        assertFalse(prediction.externalMozcEnabled)
        assertFalse(prediction.symbolEmojiEnabled)
        assertFalse(prediction.showSymbolCandidates)
        assertFalse(prediction.showEmojiCandidates)
        assertFalse(prediction.showEmoticonCandidates)
        assertEquals(2, snapshot.userDictionaryPredictionCandidateLimit)
        assertEquals(7, snapshot.learnDictionaryPredictionCandidateLimit)
    }

    @Test
    fun numericPreferencesAreClampedToSupportedRanges() {
        AppPreference.prediction_minimum_input_length_preference = 1
        AppPreference.system_prediction_candidate_limit_preference = 99
        AppPreference.prediction_lookahead_character_count_preference = 99
        AppPreference.user_dictionary_prediction_candidate_limit_preference = 0
        AppPreference.learn_dictionary_prediction_candidate_limit_preference = 99

        assertEquals(2, AppPreference.prediction_minimum_input_length_preference)
        assertEquals(16, AppPreference.system_prediction_candidate_limit_preference)
        assertEquals(6, AppPreference.prediction_lookahead_character_count_preference)
        assertEquals(1, AppPreference.user_dictionary_prediction_candidate_limit_preference)
        assertEquals(8, AppPreference.learn_dictionary_prediction_candidate_limit_preference)
    }

    @Test
    fun storedMinimumIsPreservedAndNewSnapshotsReflectChanges() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "prediction_minimum_input_length_preference"
        for (stored in 3..8) {
            preferences.edit().putInt(key, stored).commit()
            AppPreference.init(context)
            val before = ImePreferencesSnapshot.from(AppPreference)
            assertEquals(stored, before.predictionConfig.minimumInputLength)
            val screen = PreferenceManager(context).inflateFromResource(
                context, R.xml.pref_conversion_engine, null,
            )
            assertEquals(stored, screen.findPreference<androidx.preference.SeekBarPreference>(key)?.value)
            assertEquals(stored, preferences.getInt(key, -1))

            AppPreference.prediction_minimum_input_length_preference = 2
            assertEquals(stored, before.predictionConfig.minimumInputLength)
            assertEquals(2, ImePreferencesSnapshot.from(AppPreference).predictionConfig.minimumInputLength)
        }
        preferences.edit().putInt(key, 1).commit()
        assertEquals(2, AppPreference.prediction_minimum_input_length_preference)
        preferences.edit().putInt(key, 99).commit()
        assertEquals(8, AppPreference.prediction_minimum_input_length_preference)
        preferences.edit().remove(key).commit()
        assertEquals(2, ImePreferencesSnapshot.from(AppPreference).predictionConfig.minimumInputLength)
    }

    @Test
    fun storedLookaheadIsPreservedAndNewSnapshotsReflectChanges() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "prediction_lookahead_preference"
        for (stored in 1..6) {
            preferences.edit().putInt(key, stored).commit()
            AppPreference.init(context)
            val before = ImePreferencesSnapshot.from(AppPreference)
            assertEquals(stored, before.predictionConfig.lookaheadCharacterCount)
            val screen = PreferenceManager(context).inflateFromResource(
                context, R.xml.pref_conversion_engine, null,
            )
            assertEquals(stored, screen.findPreference<androidx.preference.SeekBarPreference>(key)?.value)
            assertEquals(stored, preferences.getInt(key, -1))

            val updated = if (stored == 6) 1 else 6
            AppPreference.prediction_lookahead_character_count_preference = updated
            assertEquals(stored, before.predictionConfig.lookaheadCharacterCount)
            assertEquals(updated, ImePreferencesSnapshot.from(AppPreference).predictionConfig.lookaheadCharacterCount)
        }
        preferences.edit().remove(key).commit()
        assertEquals(6, ImePreferencesSnapshot.from(AppPreference).predictionConfig.lookaheadCharacterCount)
    }

    @Test
    fun legacyLookaheadProfilesAreMigratedToPersistedIntegers() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val cases = mapOf("short" to 1, "standard" to 3, "long" to 6)

        cases.forEach { (legacyValue, expectedCount) ->
            preferences.edit()
                .putString("prediction_lookahead_preference", legacyValue)
                .commit()

            AppPreference.migratePredictionLookaheadPreferenceIfNeeded()

            assertEquals(expectedCount, AppPreference.prediction_lookahead_character_count_preference)
            assertEquals(expectedCount, preferences.all["prediction_lookahead_preference"])
        }
    }

    @Test
    fun legacyCombinedSymbolEmojiCandidatePreferenceIsSplitAndPersisted() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putBoolean("symbol_emoji_candidate_enable_preference", false)
            .commit()

        AppPreference.migrateSymbolEmojiCandidatePreferenceIfNeeded()

        assertFalse(AppPreference.symbol_candidate_enable_preference)
        assertFalse(AppPreference.emoji_candidate_enable_preference)
        assertFalse(AppPreference.emoticon_candidate_enable_preference)
        assertFalse(preferences.contains("symbol_emoji_candidate_enable_preference"))
    }

    @Test
    fun settingsAreIndexedUnderTheirOwningCategories() {
        val newSettings = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)

        CONVERSION_KEYS.forEach { key ->
            assertEquals(
                SettingCategory.CONVERSION_ENGINE,
                newSettings.first { it.key == key }.category,
            )
        }
        DICTIONARY_KEYS.forEach { key ->
            assertEquals(
                SettingCategory.DICTIONARY,
                newSettings.first { it.key == key }.category,
            )
        }
        assertTrue(CONVERSION_KEYS.none { it in DICTIONARY_KEYS })
    }

    @Test
    fun conversionEnginePreferenceScreenInflatesWithResolvedDependencies() {
        val screen = PreferenceManager(context).inflateFromResource(
            context,
            R.xml.pref_conversion_engine,
            null,
        )

        CONVERSION_KEYS.forEach { key ->
            assertEquals(key, screen.findPreference<androidx.preference.Preference>(key)?.key)
        }
        val minimum = screen.findPreference<androidx.preference.SeekBarPreference>(
            "prediction_minimum_input_length_preference",
        )
        assertEquals(2, minimum?.min)
        assertEquals(8, minimum?.max)
        assertEquals(2, minimum?.value)
        val lookahead = screen.findPreference<androidx.preference.SeekBarPreference>(
            "prediction_lookahead_preference",
        )
        assertEquals(1, lookahead?.min)
        assertEquals(6, lookahead?.max)
        assertEquals(6, lookahead?.value)
        assertTrue(lookahead?.showSeekBarValue == true)

        listOf(
            "symbol_candidate_enable_preference",
            "emoji_candidate_enable_preference",
            "emoticon_candidate_enable_preference",
        ).forEach { key ->
            val preference = screen.findPreference<androidx.preference.SwitchPreferenceCompat>(key)
            assertTrue(preference?.isChecked == true)
        }
    }

    private companion object {
        val CONVERSION_KEYS = listOf(
            "japanese_prediction_enable_preference",
            "english_prediction_enable_preference",
            "system_dictionary_prediction_enable_preference",
            "prediction_minimum_input_length_preference",
            "system_prediction_candidate_limit_preference",
            "prediction_lookahead_preference",
            "prediction_aggressiveness_preference",
            "system_user_dictionary_prediction_enable_preference",
            "reading_correction_prediction_enable_preference",
            "proverb_prediction_enable_preference",
            "external_mozc_prediction_enable_preference",
            "symbol_emoji_prediction_enable_preference",
            "symbol_candidate_enable_preference",
            "emoji_candidate_enable_preference",
            "emoticon_candidate_enable_preference",
        )
        val DICTIONARY_KEYS = listOf(
            "user_dictionary_prediction_candidate_limit_preference",
            "learn_dictionary_prediction_candidate_limit_preference",
        )
    }
}
