package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestionAdapterFoldActionTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private fun adapter() = SuggestionAdapter(Executor { it.run() })
    private val candidate = Candidate("今日", 1.toByte(), 3.toUByte(), 0, "きょう")
    private fun holder(strip: SuggestionAdapter, position: Int = 0): RecyclerView.ViewHolder {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        return strip.onCreateViewHolder(RecyclerView(context).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        }, strip.getItemViewType(position)).also {
            strip.onBindViewHolder(it, position)
        }
    }

    @Test fun oldBoundHolderCannotUseNewCallbacksAfterSourceReturnsToEqualContent() {
        val source = adapter()
        val strip = adapter()
        var clicks = 0
        var longPresses = 0
        source.setOnItemClickListener { _, _ -> clicks++ }
        source.setOnItemLongClickListener { _, _ -> longPresses++ }
        source.suggestions = listOf(candidate)
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true)
        val old = holder(strip)
        source.suggestions = listOf(candidate.copy(string = "京"))
        source.suggestions = listOf(candidate)
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true)
        old.itemView.performClick()
        old.itemView.performLongClick()
        assertEquals(0, clicks)
        assertEquals(0, longPresses)
        val fresh = holder(strip)
        fresh.itemView.performLongClick()
        fresh.itemView.performClick()
        assertEquals(1, clicks)
        assertEquals(1, longPresses)
        source.release(); strip.release()
    }

    @Test fun equalCandidatesReboundForNewEditorRequestKeepTheNewActionGuard() {
        val source = adapter()
        val strip = adapter()
        var request = 1
        var clicks = 0
        source.setOnItemClickListener { _, _ -> clicks++ }
        source.suggestions = listOf(candidate)
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true) { request == 1 }
        val old = holder(strip)
        request = 2
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true) { request == 2 }
        old.itemView.performClick()
        assertEquals(0, clicks)
        strip.onBindViewHolder(old, 0)
        old.itemView.performClick()
        assertEquals(1, clicks)
        source.release(); strip.release()
    }

    @Test fun identicalCandidatesInNewSessionCanBeSelectedAfterEarlierCommit() {
        val source = adapter()
        val strip = adapter()
        var clicks = 0
        source.setOnItemClickListener { _, _ -> clicks++ }
        source.suggestions = listOf(candidate)
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true, candidateActionSession = 1)
        holder(strip).itemView.performClick()
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true, candidateActionSession = 1)
        holder(strip).itemView.performClick()
        assertEquals(1, clicks)
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true, candidateActionSession = 2)
        holder(strip).itemView.performClick()
        assertEquals(2, clicks)
        source.release(); strip.release()
    }

    @Test fun disabledLoadingCandidateDoesNotConsumeSharedClickClaim() {
        val source = adapter()
        val strip = adapter()
        var clicks = 0
        source.setOnItemClickListener { _, _ -> clicks++ }
        source.suggestions = listOf(candidate, candidate.copy(string = "京"))
        strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true,
            isCandidateEnabled = { it.string != candidate.string })
        holder(strip).itemView.performClick()
        assertEquals(0, clicks)
        val enabled = holder(strip)
        strip.onBindViewHolder(enabled, 1)
        enabled.itemView.performClick()
        assertEquals(1, clicks)
        source.release(); strip.release()
    }
    private fun idlePanel(clipboard: String) =
        com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.EmptyState(
            showShortcutEntry = false,
            quickActions = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.QuickActionsState(
                incognitoVisible = false, undoEnabled = true, redoEnabled = false,
                reconvertEnabled = false, undoText = "Undo", redoText = ""),
            clipboardPreview = com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.ClipboardPreviewState(
                text = clipboard, bitmap = null, descriptionShown = true, tapToDelete = false),
            shortcutItems = emptyList(), showIntegratedShortcuts = false)

    @Test fun idleUndoAndClipboardHoldersRejectChangedContentAndOldEditorGuards() {
        val source = adapter()
        val strip = adapter()
        val clicks = mutableListOf<SuggestionAdapter.HelperIcon>()
        val longPresses = mutableListOf<SuggestionAdapter.HelperIcon>()
        source.setOnItemHelperIconClickListener { clicks += it }
        source.setOnItemHelperIconLongClickListener { longPresses += it }
        var editor = 1
        fun mirror() {
            val capturedEditor = editor
            strip.mirrorSplitContentFrom(source, consumeCandidateClicks = true,
                hasComposition = false, candidateActionSession = capturedEditor) { editor == capturedEditor }
        }
        fun controls(): Pair<android.view.View, android.view.View> {
            val undo = holder(strip, 0) as SuggestionAdapter.QuickActionsViewHolder
            val clipboard = holder(strip, 1) as SuggestionAdapter.ClipboardPreviewViewHolder
            return checkNotNull(undo.undoIconParent) to checkNotNull(clipboard.pasteIconParent)
        }
        fun activate(controls: Pair<android.view.View, android.view.View>) {
            controls.first.performClick()
            controls.first.performLongClick()
            controls.second.performClick()
            controls.second.performLongClick()
        }
        try {
            source.submitContent(idlePanel("first clip"))
            mirror()
            val old = controls()
            activate(old)
            val expected = listOf(SuggestionAdapter.HelperIcon.UNDO, SuggestionAdapter.HelperIcon.PASTE)
            assertEquals(expected, clicks)
            assertEquals(expected, longPresses)

            source.submitContent(idlePanel("second clip"))
            mirror()
            activate(old)
            assertEquals("An old holder must not pick up the replacement mirror's callbacks", expected, clicks)
            assertEquals(expected, longPresses)
            val current = controls()
            activate(current)
            assertEquals(expected + expected, clicks)
            assertEquals(expected + expected, longPresses)

            editor = 2
            mirror() // Equal panel data, different editor: old listeners must retain their guard.
            activate(current)
            assertEquals(expected + expected, clicks)
            assertEquals(expected + expected, longPresses)
            activate(controls())
            assertEquals(expected + expected + expected, clicks)
            assertEquals(expected + expected + expected, longPresses)
        } finally {
            source.release()
            strip.release()
        }
    }

    @Test fun zeroQueryHoldersClaimOneSharedCommitAndAcceptFreshSessionResults() {
        val source = adapter()
        val left = adapter()
        val right = adapter()
        var commits = 0
        var session = 1
        source.setOnZeroQueryCandidateClickListener { commits++ }
        fun mirror() {
            val capturedSession = session
            listOf(left, right).forEach {
                it.mirrorSplitContentFrom(source, consumeCandidateClicks = true,
                    hasComposition = false, candidateActionSession = capturedSession) { session == capturedSession }
            }
        }
        try {
            source.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.ZeroQuerySuggestions(listOf(candidate)))
            mirror()
            val oldLeft = holder(left, 1)
            val oldRight = holder(right, 1)
            oldLeft.itemView.performClick()
            oldRight.itemView.performClick()
            oldLeft.itemView.performClick()
            assertEquals("An asynchronous commit can retain source content while both strips receive taps", 1, commits)

            session = 2
            source.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.ZeroQuerySuggestions(
                listOf(candidate.copy(string = "明日"))))
            mirror()
            oldLeft.itemView.performClick()
            oldRight.itemView.performClick()
            assertEquals(1, commits)
            holder(right, 1).itemView.performClick()
            holder(left, 1).itemView.performClick()
            assertEquals(2, commits)

            session = 3
            mirror() // The same next-word result can be valid again after a new commit.
            holder(left, 1).itemView.performClick()
            assertEquals(3, commits)
        } finally {
            source.release()
            left.release()
            right.release()
        }
    }

}
