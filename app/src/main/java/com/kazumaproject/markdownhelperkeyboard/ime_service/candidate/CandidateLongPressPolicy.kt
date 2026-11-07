package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** Reading belongs to the candidate, not necessarily to the whole composing input. */
internal object CandidateLongPressPolicy {
    fun isText(candidate: Candidate): Boolean =
        candidate.type.toInt() in 1..51 && candidate.type.toInt() !in setOf(43, 44) &&
            candidate.presentation == null && candidate.sourceId == null &&
            candidate.string.isNotBlank() && candidate.commitText == candidate.string &&
            candidate.string.none { it.isISOControl() }

    fun canRegister(candidate: Candidate, input: String, japanese: Boolean, secure: Boolean): Boolean =
        japanese && !secure && input.isNotEmpty() && isText(candidate) &&
            candidate.type.toInt() in 1..39 && candidate.type.toInt() != 15

    fun reading(candidate: Candidate, input: String): String {
        // A supplied but invalid yomi must never silently become the composing input.
        val value = candidate.yomi ?: when {
            candidate.type.toInt() in setOf(1, 2, 3, 4, 5, 6, 7, 8, 34) &&
                candidate.length.toInt() in 1..input.length -> input.take(candidate.length.toInt())
            else -> ""
        }
        return value.takeIf { it.isNotBlank() && it.all { c -> c in 'ぁ'..'ゖ' || c == 'ー' } } ?: ""
    }
}
