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
    private fun holder(strip: SuggestionAdapter): RecyclerView.ViewHolder {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        return strip.onCreateViewHolder(RecyclerView(context).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        }, strip.getItemViewType(0)).also {
            strip.onBindViewHolder(it, 0)
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
}
