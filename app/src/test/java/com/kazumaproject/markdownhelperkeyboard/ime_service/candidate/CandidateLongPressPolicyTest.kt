package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.*
import org.junit.Test

class CandidateLongPressPolicyTest {
    private fun candidate(yomi: String? = null, type: Byte = 1, length: Int = 2) =
        Candidate("日本", type, length.toUByte(), 3000, yomi)

    @Test fun ordinaryJapaneseNeedsNoOptionalActions() {
        assertTrue(CandidateLongPressPolicy.canRegister(candidate(), "にほん", true, false))
        assertFalse(CandidateLongPressPolicy.canRegister(candidate(), "にほん", false, false))
        assertFalse(CandidateLongPressPolicy.canRegister(candidate(), "にほん", true, true))
        assertFalse(CandidateLongPressPolicy.canRegister(candidate(), "", true, false))
    }

    @Test fun exactCandidateReadingWinsForPredictionAndPartialWords() {
        assertEquals("にほん", CandidateLongPressPolicy.reading(candidate("にほん", 9, 2), "にほ"))
        assertEquals("にほん", CandidateLongPressPolicy.reading(candidate("にほん", 2, 3), "にほんご"))
        assertEquals("にほん", CandidateLongPressPolicy.reading(candidate(null, 2, 3), "にほんご"))
    }

    @Test fun uncertainReadingRequiresManualCorrectionInsteadOfWrongRegistration() {
        assertEquals("", CandidateLongPressPolicy.reading(candidate(null, 9, 2), "にほ"))
        assertEquals("", CandidateLongPressPolicy.reading(candidate(null, 1, 4), "にほ"))
        assertEquals("", CandidateLongPressPolicy.reading(candidate("日本"), "にほん"))
        assertTrue(CandidateLongPressPolicy.canRegister(candidate("日本"), "にほん", true, false))
    }

    @Test fun actionMacroNonTextAndControlOutputAreExcluded() {
        for (type in listOf<Byte>(43, 44, 52, 53, 54, -1)) {
            assertFalse(CandidateLongPressPolicy.isText(candidate(type = type)))
            assertFalse(CandidateLongPressPolicy.canRegister(candidate(type = type), "にほん", true, false))
        }
        assertFalse(CandidateLongPressPolicy.isText(candidate().copy(sourceId = 4)))
        assertFalse(CandidateLongPressPolicy.isText(candidate().copy(commitText = "other")))
        assertFalse(CandidateLongPressPolicy.isText(candidate().copy(string = "\n")))
        // Existing translated and utility text can retain Translate/Hide, but cannot be learned.
        assertTrue(CandidateLongPressPolicy.isText(candidate(type = 41)))
        assertFalse(CandidateLongPressPolicy.canRegister(candidate(type = 41), "にほん", true, false))
    }
}
