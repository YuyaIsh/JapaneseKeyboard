package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import androidx.window.layout.FoldingFeature
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FoldKeyboardMonitorTest {
    @Test fun flatAndHalfOpenedPosturesArePhysicallyOpen() {
        assertTrue(FoldKeyboardMonitor.isExpanded(FoldingFeature.State.FLAT))
        assertTrue(FoldKeyboardMonitor.isExpanded(FoldingFeature.State.HALF_OPENED))
    }

    @Test fun metricsFallbackTracksDisplaySizeIndependentlyOfOrientation() {
        // Same physical display in either rotation: closed landscape must stay regular.
        assertFalse(FoldKeyboardMonitor.isExpanded(400, 900, 1f))
        assertFalse(FoldKeyboardMonitor.isExpanded(900, 400, 1f))
        assertTrue(FoldKeyboardMonitor.isExpanded(700, 900, 1f))
        assertTrue(FoldKeyboardMonitor.isExpanded(900, 700, 1f))
        assertFalse(FoldKeyboardMonitor.isExpanded(1797, 2400, 3f))
        assertTrue(FoldKeyboardMonitor.isExpanded(1800, 2400, 3f))
        // Open then close without any rotation.
        assertEquals(listOf(false, true, false), listOf(400, 700, 400).map {
            FoldKeyboardMonitor.isExpanded(it, 900, 1f)
        })
    }

    @Test fun foldSettingsAreOptInAndDoNotChangeExistingSplitSelections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        val settings = SplitKeyboardSettings(prefs)
        assertFalse(settings.foldEnabled)
        assertEquals(FoldKeyboardLayout.SPLIT, settings.foldLayout)
        for (layout in FoldKeyboardLayout.entries) {
            prefs.edit().putBoolean(SplitKeyboardSettings.FOLD_ENABLED, true)
                .putString(SplitKeyboardSettings.FOLD_LAYOUT, layout.name).commit()
            assertTrue(settings.foldEnabled)
            assertEquals(layout, settings.foldLayout)
            assertEquals(SplitKeyboardSelection(), settings.selection(SplitSlot.MAIN))
        }
        prefs.edit().putString(SplitKeyboardSettings.FOLD_LAYOUT, "unknown").commit()
        assertEquals(FoldKeyboardLayout.SPLIT, settings.foldLayout)
        prefs.edit().clear().commit()
    }
}
