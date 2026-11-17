package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test

class KeyboardLayoutStateTest {
    @Test fun expandedCurrentWidthClassifiesPostureEvenWhenDeviceMinimumRemainsCompact() {
        val folded = Configuration().apply { smallestScreenWidthDp = 360; screenWidthDp = 420 }
        val unfolded = Configuration().apply { smallestScreenWidthDp = 360; screenWidthDp = 700 }
        assertFalse(KeyboardLayoutState.isUnfolded(folded))
        assertTrue(KeyboardLayoutState.isUnfolded(unfolded))
    }

    @Test fun largeSmallestWidthDoesNotMisclassifyCurrentCompactWindow() {
        val compactWindow = Configuration().apply { smallestScreenWidthDp = 720; screenWidthDp = 500 }
        assertFalse(KeyboardLayoutState.isUnfolded(compactWindow))
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
