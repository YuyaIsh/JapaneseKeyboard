package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test

class KeyboardLayoutStateTest {
    @Test fun postureUsesHingeSignalIndependentlyOfOrientationAndFallsBackFolded() {
        val foldedPhone = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE; screenWidthDp = 800; smallestScreenWidthDp = 390 }
        val unfoldedFoldable = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT; screenWidthDp = 400; smallestScreenWidthDp = 390 }
        // The orientation and available width do not decide posture: only the hinge signal does.
        assertFalse(KeyboardLayoutState.isUnfoldedHingeAngle(null))
        assertFalse(KeyboardLayoutState.isUnfoldedHingeAngle(90f))
        assertTrue(KeyboardLayoutState.isUnfoldedHingeAngle(170f))
        assertEquals(foldedPhone.orientation, Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(unfoldedFoldable.orientation, Configuration.ORIENTATION_PORTRAIT)
    }

    @Test fun expandedPreferenceReadsLegacyValueUntilExplicitlyOverriddenAndResetWritesExpandedKey() {
        val legacy = mapOf("keyboard_height_preference" to 260)
        fun read(values: Map<String, Int>, key: String, default: Int, unfolded: Boolean) =
            values[KeyboardLayoutState.preferenceKey(key, unfolded)] ?: values[key] ?: default
        assertEquals(260, read(legacy, "keyboard_height_preference", 220, true))
        val expandedSave = legacy + (KeyboardLayoutState.preferenceKey("keyboard_height_preference", true) to 220)
        assertEquals(220, read(expandedSave, "keyboard_height_preference", 0, true))
        assertEquals(260, read(expandedSave, "keyboard_height_preference", 0, false))
        assertEquals("keyboard_height_landscape_preference_unfolded",
            KeyboardLayoutState.preferenceKey("keyboard_height_landscape_preference", true))
        assertEquals("keyboard_height_landscape_preference",
            KeyboardLayoutState.preferenceKey("keyboard_height_landscape_preference", false))
    }
}
