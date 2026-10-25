package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionConfigTest {

    @Test
    fun defaultLookaheadAcceptsExactlySixAdditionalCharacters() {
        val config = PredictionConfig()

        assertFalse(config.acceptsJapaneseReading(inputLength = 0, readingLength = 3))
        assertFalse(config.acceptsJapaneseReading(inputLength = 1, readingLength = 3))
        assertTrue(config.acceptsJapaneseReading(inputLength = 2, readingLength = 3))
        assertFalse(config.acceptsJapaneseReading(inputLength = 2, readingLength = 2))
        assertTrue(config.acceptsJapaneseReading("おつ".length, "おつかれさまです".length))
        for (inputLength in listOf(2, 3, 5, 7)) {
            assertTrue(config.acceptsJapaneseReading(inputLength, inputLength + 6))
            assertFalse(config.acceptsJapaneseReading(inputLength, inputLength + 7))
        }
        assertFalse(config.acceptsJapaneseReading(inputLength = 17, readingLength = 18))
    }

    @Test
    fun explicitMinimumInputLengthsAreRespected() {
        for (minimum in 3..8) {
            val config = PredictionConfig(minimumInputLength = minimum)
            assertFalse(config.acceptsJapaneseReading(minimum - 1, minimum + 1))
            assertTrue(config.acceptsJapaneseReading(minimum, minimum + 1))
        }
    }

    @Test
    fun lookaheadCharacterCountControlsMaximumCompletionLength() {
        val oneCharacter = PredictionConfig(lookaheadCharacterCount = 1)
        val sixCharacters = PredictionConfig(lookaheadCharacterCount = 6)

        assertTrue(oneCharacter.acceptsJapaneseReading(inputLength = 4, readingLength = 5))
        assertFalse(oneCharacter.acceptsJapaneseReading(inputLength = 4, readingLength = 6))
        assertTrue(sixCharacters.acceptsJapaneseReading(inputLength = 4, readingLength = 10))
        assertFalse(sixCharacters.acceptsJapaneseReading(inputLength = 4, readingLength = 11))
    }

    @Test
    fun disabledJapanesePredictionRejectsCompletions() {
        val config = PredictionConfig(japanesePredictionEnabled = false)

        assertFalse(config.acceptsJapaneseReading(inputLength = 2, readingLength = 3))
        assertFalse(config.acceptsJapaneseReading(inputLength = 3, readingLength = 4))
    }

    @Test
    fun limitsAreNormalizedAndAggressivenessChangesPenalty() {
        val belowRange = PredictionConfig(
            minimumInputLength = 0,
            systemCandidateLimit = 0,
            lookaheadCharacterCount = 0,
        )
        val aboveRange = PredictionConfig(
            minimumInputLength = 99,
            systemCandidateLimit = 99,
            lookaheadCharacterCount = 99,
        )

        assertEquals(2, belowRange.normalizedMinimumInputLength)
        assertEquals(1, belowRange.normalizedSystemCandidateLimit)
        assertEquals(1, belowRange.normalizedLookaheadCharacterCount)
        assertEquals(8, aboveRange.normalizedMinimumInputLength)
        assertEquals(16, aboveRange.normalizedSystemCandidateLimit)
        assertEquals(6, aboveRange.normalizedLookaheadCharacterCount)

        val conservative = PredictionConfig(
            aggressiveness = PredictionAggressiveness.CONSERVATIVE,
        ).completionPenalty(inputLength = 4, readingLength = 6)
        val standard = PredictionConfig().completionPenalty(inputLength = 4, readingLength = 6)
        val aggressive = PredictionConfig(
            aggressiveness = PredictionAggressiveness.AGGRESSIVE,
        ).completionPenalty(inputLength = 4, readingLength = 6)

        assertTrue(conservative > standard)
        assertTrue(standard > aggressive)
    }

    @Test
    fun standardAggressivenessKeepsEachDictionarysExistingPenaltyScale() {
        val config = PredictionConfig()

        assertEquals(6_000, config.completionPenalty(inputLength = 6, readingLength = 9))
        assertEquals(
            24_000,
            config.completionPenalty(
                inputLength = 6,
                readingLength = 9,
                penaltyPerCharacter = 8_000,
                longInputFlatPenalty = null,
            ),
        )
        assertEquals(
            4_500,
            config.completionPenalty(
                inputLength = 6,
                readingLength = 9,
                penaltyPerCharacter = 1_500,
                longInputFlatPenalty = null,
            ),
        )
    }

    @Test
    fun unknownStoredAggressivenessFallsBackToStandard() {
        assertEquals(
            PredictionAggressiveness.STANDARD,
            PredictionAggressiveness.fromPreference("unknown"),
        )
    }
}
