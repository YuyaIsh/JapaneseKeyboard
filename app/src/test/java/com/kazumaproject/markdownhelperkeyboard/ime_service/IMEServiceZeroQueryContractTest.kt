package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceZeroQueryContractTest {

    @Test
    fun zeroQueryDisplayAndCommitKeepPreferenceAndSecureEditorGuards() {
        val source = imeServiceSource()
        for ((start, end) in listOf(
            "private fun canShowZeroQueryAfterCommit" to "private fun String.isZeroQueryNumberKey",
            "private fun canCommitZeroQueryCandidate" to "private fun isCurrentInputTypePasswordOrEmailForZeroQuery"
        )) {
            val function = source.functionBody(start, end)
            assertTrue(function.contains("if (!zeroQuerySuggestionPreference) return false"))
            assertTrue(function.contains("if (inputString.value.isNotEmpty()) return false"))
            assertTrue(function.contains("if (stringInTail.get().isNotEmpty()) return false"))
            assertTrue(function.contains("if (isCurrentInputTypePasswordOrEmailForZeroQuery()) return false"))
            assertTrue(function.contains("if (editorTextSelected)"))
        }
        val secureTypes = source.functionBody(
            "private fun isCurrentInputTypePasswordOrEmailForZeroQuery",
            "private fun isSelectionActionsShownForCandidateStrip"
        )
        assertTrue(secureTypes.contains("currentInputType in passwordTypes"))
        assertTrue(secureTypes.contains("InputTypeForIME.TextEmailAddress"))
        assertTrue(secureTypes.contains("InputTypeForIME.TextWebEmailAddress"))
    }

    @Test
    fun commitPathInvokesLookupAndRechecksEligibilityBeforeDisplay() {
        val source = imeServiceSource()
        val lookup = source.functionBody(
            "private fun consumePendingZeroQueryAfterCommit",
            "private fun handleZeroQueryOnUpdateSelection"
        )
        val lookupIndex = lookup.indexOf("zeroQueryLookupUseCase.lookup(key)")
        val displayIndex = lookup.indexOf("zeroQueryVisible = true")
        assertTrue(lookupIndex > lookup.indexOf("canShowZeroQueryAfterCommit(key)"))
        assertTrue(lookup.indexOf("canShowZeroQueryAfterCommit(key)", lookupIndex) in lookupIndex until displayIndex)
        assertTrue(lookup.contains("refreshCandidateStripContent(candidatesShown = false)"))
        assertTrue(source.contains("rememberZeroQueryKeyAfterCommit(commitWord)"))
        assertTrue(source.contains("consumePendingZeroQueryAfterCommit()"))
        assertTrue(source.contains("zeroQuerySuggestionPreference = preferences.zeroQuerySuggestionPreference"))
    }

    @Test
    fun zeroQueryAfterCommitDoesNotBlockIncognitoMode() {
        val function = imeServiceSource().functionBody(
            start = "private fun canShowZeroQueryAfterCommit",
            end = "private fun String.isZeroQueryNumberKey"
        )

        assertFalse(function.contains("isPrivateMode"))
    }

    @Test
    fun zeroQueryAfterCommitAllowsNumberKeysOutsideJapaneseMode() {
        val function = imeServiceSource().functionBody(
            start = "private fun canShowZeroQueryAfterCommit",
            end = "private fun String.isZeroQueryNumberKey"
        )

        assertTrue(function.contains("currentInputModeForSession != InputMode.ModeJapanese"))
        assertTrue(function.contains("!committedText.isZeroQueryNumberKey()"))
    }

    @Test
    fun pendingZeroQueryLookupUsesCommittedTextForModeDecision() {
        val function = imeServiceSource().functionBody(
            start = "private fun consumePendingZeroQueryAfterCommit",
            end = "private fun handleZeroQueryOnUpdateSelection"
        )

        assertTrue(function.contains("canShowZeroQueryAfterCommit(key)"))
    }

    @Test
    fun zeroQueryCloseItemTogglesVisibilityInsteadOfClearingCandidates() {
        val source = imeServiceSource()

        assertTrue(source.contains("adapter.setOnZeroQueryCloseClickListener"))
        assertTrue(source.contains("toggleZeroQueryVisibility()"))
        assertFalse(
            source.functionBody(
                start = "adapter.setOnZeroQueryCloseClickListener",
                end = "        }"
            ).contains("clearZeroQueryAllState")
        )
    }

    @Test
    fun editorDeletionMutationsInvalidateZeroQueryState() {
        val source = imeServiceSource()

        listOf(
            "private fun deleteWordOrSymbolsBeforeCursor",
            "private fun deleteWordOrSymbolsAfterCursor",
            "private fun deleteLongPress",
            "private fun deleteLastGraphemeOrSelection",
            "private fun deleteStringCommon",
        ).forEach { functionStart ->
            assertTrue(
                "$functionStart must invalidate zero-query before mutating editor text",
                source.functionBody(
                    start = functionStart,
                    end = "\n    private fun"
                ).contains("invalidateZeroQueryForEditorMutation()")
            )
        }
    }

    @Test
    fun zeroQueryInvalidationForEditorMutationRefreshesCandidateStrip() {
        val function = imeServiceSource().functionBody(
            start = "private fun invalidateZeroQueryForEditorMutation",
            end = "\n    private fun toggleZeroQueryVisibility"
        )

        assertTrue(function.contains("clearZeroQueryAllState(refresh = true)"))
    }

    @Test
    fun candidateStripLayoutIsNotCachedBeforeAdapterIsAttached() {
        val function = imeServiceSource().functionBody(
            start = "private fun setMainSuggestionColumn",
            end = "\n    private fun toggleKeyboardLayoutEditMode"
        )
        val adapterGuard = function.indexOf("val adapter = recyclerView.adapter ?: run")
        val cacheWrite = function.indexOf("lastSuggestionLayoutKey = key")

        assertTrue(adapterGuard >= 0)
        assertTrue(cacheWrite > adapterGuard)
        assertTrue(function.contains("return@measureDebugSection"))
        assertTrue(function.contains("adapter.getItemViewType(position)"))
        assertFalse(function.contains("adapter?.getItemViewType(position)"))
    }

    private fun imeServiceSource(): String =
        listOf(
            File("app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
            File("src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
        ).first { it.isFile }.readText()

    private fun String.functionBody(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing start marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing end marker: $end" }
        return substring(startIndex, endIndex)
    }
}
