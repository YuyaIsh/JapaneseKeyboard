package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeDirectInputFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardSizeLivePreviewValuesTest {
    private fun parse(height: String? = "260", width: String? = "85", bottom: String? = "10", start: String? = "0", end: String? = "6") =
        KeyboardSizeDirectInputFragment.parsePreviewValues(height, width, bottom, start, end)

    @Test fun completeValidDraftIncludesEverySizeAndMarginWithoutClamping() {
        assertEquals(KeyboardSizeValues(260, 85, 10, 0, 6), parse())
        assertEquals(KeyboardSizeValues(100, 32, 0, 0, 0), parse("100", "32", "0", "0", "0"))
        assertEquals(KeyboardSizeValues(420, 100, 12, 25, 30), parse("420", "100", "12", "25", "30"))
    }

    @Test fun incompleteInvalidAndOutOfRangeInputCannotBecomePreviewOrSavedValues() {
        for (invalid in listOf(null, "", "-", "abc", "99", "421", "2147483648")) {
            assertNull("Invalid height $invalid", parse(height = invalid))
        }
        for (invalid in listOf("", "-", "31", "101", "2147483648")) {
            assertNull("Invalid width $invalid", parse(width = invalid))
        }
        for (invalid in listOf(null, "", "-", "-1", "2147483648")) {
            assertNull("Invalid bottom margin $invalid", parse(bottom = invalid))
            assertNull("Invalid start margin $invalid", parse(start = invalid))
            assertNull("Invalid end margin $invalid", parse(end = invalid))
        }
    }
}
