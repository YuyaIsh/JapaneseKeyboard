package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRefreshCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Exercises the real service's queued update path, without starting dictionaries or IME windows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitKeyboardServiceSyncTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context)
        it.appPreference = AppPreference
    }).also {
        doReturn(EditorInfo().apply { inputType = 1; imeOptions = EditorInfo.IME_ACTION_SEARCH })
            .`when`(it).getCurrentInputEditorInfo()
        Reflect.setField(it, "splitController", SplitKeyboardController(context, View(context), {}, {}, {}))
        Reflect.setField(it, "zenzRuntimeClient", mock(IMEService::class.java.getDeclaredField("zenzRuntimeClient").type))
        // Mockito copies the service; rebind constructor callbacks to the spy's editor.
        val target = it
        Reflect.setField(it, "composingTextArbiter",
            com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter(
                writeComposingText = { value, cursor -> target.currentInputConnection?.setComposingText(value, cursor) ?: false },
                finishComposingText = { target.currentInputConnection?.finishComposingText() ?: false }))
    }
    private val modes = listOf(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Gojuon,
        TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji,
        TenKeyQWERTYMode.Sumire, TenKeyQWERTYMode.Custom)
    private val bindings = SplitSlot.entries.associateWith {
        FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context)).also { binding ->
            binding.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.createFinalLayout(
                KeyboardInputMode.HIRAGANA, emptyMap(), "switch-mode-effective", "default"))
        }
    }
    private val inputs get() = Reflect.getField<MutableMap<SplitSlot, Any>>(service, "splitInputs")
    private val text get() = Reflect.getField<MutableStateFlow<String>>(service, "_inputString")
    private fun call(name: String) = Reflect.callInstanceMethod<Unit>(service, name)
    private fun japanese(mode: TenKeyQWERTYMode) = mode != TenKeyQWERTYMode.TenKeyQWERTY
    private fun install(slot: SplitSlot, mode: TenKeyQWERTYMode) {
        // Use Kotlin's default-argument constructor so optional per-pane state retains production defaults.
        val type = Class.forName(IMEService::class.java.name + "\$SplitInputState")
        val constructor = type.declaredConstructors.single { it.parameterTypes.last().name.endsWith("DefaultConstructorMarker") }
        constructor.isAccessible = true
        val args = constructor.parameterTypes.map<Class<*>, Any?> { when (it) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            else -> null
        } }.toTypedArray()
        val required = arrayOf(slot, SplitKeyboardSelection(KeyboardType.TENKEY), bindings.getValue(slot),
            SuggestionAdapter(), mode, if (japanese(mode)) InputMode.ModeJapanese else InputMode.ModeEnglish,
            mode == TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        required.copyInto(args)
        args[args.lastIndex - 1] = -128 // first seven arguments supplied, all remaining arguments defaulted
        inputs[slot] = constructor.newInstance(*args)
    }
    private fun select(slot: SplitSlot, mode: TenKeyQWERTYMode) {
        Reflect.setField(service, "activeSplitSlot", slot)
        Reflect.setField(service, "floatingKeyboardBinding", bindings.getValue(slot))
        Reflect.setField(service, "currentInputModeForSession", if (japanese(mode)) InputMode.ModeJapanese else InputMode.ModeEnglish)
        Reflect.setField(service, "currentQwertyRomajiModeForSession", mode == TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value = mode
    }
    private fun assertEnter(slot: SplitSlot, mode: TenKeyQWERTYMode, composing: Boolean) {
        val binding = bindings.getValue(slot)
        when (mode) {
            TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Gojuon -> {
                val view = if (mode == TenKeyQWERTYMode.Default) binding.keyboardViewFloating.findViewById<ImageView>(com.kazumaproject.tenkey.R.id.key_enter)
                    else binding.gojuonViewFloating.findViewById<ImageView>(com.kazumaproject.gojuon_keyboard.R.id.key_enter)
                assertEquals(if (composing) com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
                    else com.kazumaproject.core.R.drawable.baseline_search_24, shadowOf(view.drawable).createdFromResId)
            }
            TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji -> assertEquals(
                if (composing) { if (japanese(mode)) "確定" else "done" } else { if (japanese(mode)) "検索" else "search" },
                binding.qwertyViewFloating.snapshotUiState().enterKeyText)
            else -> {
                val dynamic = Reflect.getField<Map<String, Any>>(binding.customLayoutFloating, "dynamicKeyMap")
                val key = Reflect.getField<com.kazumaproject.custom_keyboard.data.KeyData>(dynamic.getValue("enter_key"), "keyData")
                assertEquals(if (composing) "確定" else "検索", key.label)
            }
        }
    }
    @Test fun serviceDispatchUpdatesAll36PairsWithoutChangingTheInputSource() {
        for (left in modes) for (right in modes) {
            install(SplitSlot.MAIN, left); install(SplitSlot.SUB, right)
            for (source in SplitSlot.entries) {
                select(source, if (source == SplitSlot.MAIN) left else right)
                for (value in listOf("か", "は", "")) {
                    text.value = value
                    Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, source))
                    // Candidate notifications may arrive before or after the key's state save.
                    call("scheduleSplitCandidates")
                    shadowOf(Looper.getMainLooper()).idle()
                    assertEnter(SplitSlot.MAIN, left, value.isNotEmpty())
                    assertEnter(SplitSlot.SUB, right, value.isNotEmpty())
                    assertEquals(source, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
                    assertSame(bindings.getValue(source), Reflect.getField<Any>(service, "floatingKeyboardBinding"))
                }
            }
        }
    }
    @Test fun queuedUpdateReadsLatestTextAndDefersThePaneUnderTheFinger() {
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        call("syncSplitPresentation")
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "touching", true)
        text.value = "か"
        call("scheduleSplitCandidates")
        shadowOf(Looper.getMainLooper()).idle()
        assertEnter(SplitSlot.MAIN, TenKeyQWERTYMode.Default, true)
        assertEnter(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji, false)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "touching", false)
        call("syncSplitPresentation")
        assertEnter(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji, true)
        call("scheduleSplitCandidates")
        text.value = "" // confirmation before the posted candidate refresh executes
        shadowOf(Looper.getMainLooper()).idle()
        assertEnter(SplitSlot.MAIN, TenKeyQWERTYMode.Default, false)
        assertEnter(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji, false)
    }
    @Test fun clipboardBroadcastPreservesEachPanesSymbolTab() {
        val symbolMode = com.kazumaproject.core.data.clicked_symbol.SymbolMode.CLIPBOARD
        val shown = com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState(true)
        for (slot in SplitSlot.entries) {
            install(slot, TenKeyQWERTYMode.Default)
            Reflect.setField(inputs.getValue(slot), "symbolState", shown)
            Reflect.setField(inputs.getValue(slot), "symbolRequest", shown)
            bindings.getValue(slot).floatingSymbolKeyboard.setSymbolLists(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                if (slot == SplitSlot.MAIN) symbolMode else com.kazumaproject.core.data.clicked_symbol.SymbolMode.SYMBOL)
        }
        Reflect.getField<MutableStateFlow<com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState>>(
            service, "_keyboardSymbolViewState").value = shown
        val items = listOf(com.kazumaproject.core.data.clipboard.ClipboardItem.Text(1, "同期テスト"))
        for (source in SplitSlot.entries) {
            select(source, TenKeyQWERTYMode.Default)
            Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, source))
            Reflect.callInstanceMethod<Unit>(service, "updateFloatingClipboardItems", ClassParameter.from(List::class.java, items))
            shadowOf(Looper.getMainLooper()).idle()
            for (slot in SplitSlot.entries) {
                val view = bindings.getValue(slot).floatingSymbolKeyboard
                assertEquals(items, Reflect.getField<List<*>>(view, "clipBoardItems"))
                assertEquals(if (slot == SplitSlot.MAIN) symbolMode else com.kazumaproject.core.data.clicked_symbol.SymbolMode.SYMBOL,
                    Reflect.getField<Any>(view, "currentMode"))
            }
        }
    }

    @Test fun foldConfigurationRestartPreservesCompositionConversionAndCandidates() {
        Reflect.setField(service, "preservingFoldConfiguration", true)
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        val source = SuggestionAdapter()
        val candidates = listOf(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
            string = "仮名", type = 1.toByte(), length = 2.toUByte(), score = 0, yomi = "かな"))
        source.suggestions = candidates
        Reflect.setField(service, "suggestionAdapter", source)
        text.value = "かな"
        val converting = Reflect.getField<java.util.concurrent.atomic.AtomicBoolean>(service, "isHenkan")
        converting.set(true)
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        val container = android.widget.FrameLayout(context)
        Reflect.setField(service, "keyboardContainer", container)
        assertSame(container, service.onCreateInputView())
        service.onFinishInputView(false)
        service.onFinishInput()
        val info = EditorInfo().apply { inputType = 1 }
        service.onStartInput(info, true)
        service.onStartInputView(info, true)
        assertEquals("かな", text.value)
        assertTrue(converting.get())
        assertEquals(candidates, source.suggestions)
        assertSame(source, Reflect.getField<SuggestionAdapter>(service, "suggestionAdapter"))
        verify(connection, never()).finishComposingText()
    }

    @Test fun foldPaneHandoverCommitsOnlyTheActiveCompositionAndKeepsPaneModesIndependent() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        val source = SuggestionAdapter()
        Reflect.setField(service, "suggestionAdapter", source)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        val candidates = listOf(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
            string = "仮名", type = 1.toByte(), length = 2.toUByte(), score = 0, yomi = "かな"))
        source.suggestions = candidates
        text.value = "かな"
        Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.MAIN))
        call("scheduleSplitCandidates")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(candidates, Reflect.getField<SuggestionAdapter>(inputs.getValue(SplitSlot.MAIN), "adapter").suggestions)
        // This is the callback path used by a key tap on the other docked half.
        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.SUB).keyboardViewFloating))
        assertEquals(SplitSlot.SUB, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
        verify(connection).commitText("かな", 1)
        verify(connection).finishComposingText()
        verify(connection).beginBatchEdit()
        verify(connection).endBatchEdit()
        assertEquals("", text.value)
        assertFalse(Reflect.getField<java.util.concurrent.atomic.AtomicBoolean>(service, "isHenkan").get())
        for (slot in SplitSlot.entries) {
            assertTrue(Reflect.getField<SuggestionAdapter>(inputs.getValue(slot), "adapter").suggestions.isEmpty())
        }
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTY,
            Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.MAIN).keyboardViewFloating))
        assertEquals(SplitSlot.MAIN, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        assertEquals(TenKeyQWERTYMode.Default,
            Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
        verify(connection, times(1)).commitText("かな", 1)
    }

    @Test fun foldHandoverClearsZeroQueryActionsFromTheInactivePane() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        val candidate = com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
            "古い履歴", 1.toByte(), 4.toUByte(), 0, "ふるいりれき")
        val source = SuggestionAdapter()
        Reflect.setField(service, "suggestionAdapter", source)
        source.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.ZeroQuerySuggestions(listOf(candidate)))
        call("scheduleSplitCandidates")
        shadowOf(Looper.getMainLooper()).idle()
        val left = Reflect.getField<SuggestionAdapter>(inputs.getValue(SplitSlot.MAIN), "adapter")
        assertTrue(left.buildZeroQueryDisplayTextsForTesting().contains("古い履歴"))
        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.SUB).qwertyViewFloating))
        assertTrue("The old pane must not retain clickable zero-query candidates", left.buildZeroQueryDisplayTextsForTesting().isEmpty())
        // Also clear non-active strips when an asynchronous source update arrives.
        left.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.ZeroQuerySuggestions(listOf(candidate)))
        call("scheduleSplitCandidates")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(left.buildZeroQueryDisplayTextsForTesting().isEmpty())
    }

    @Test fun clearedSplitStripDoesNotForwardCapturedCandidateActions() {
        val candidate = com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
            "古い候補", 1.toByte(), 4.toUByte(), 0, "ふるいこうほ")
        val source = SuggestionAdapter()
        val strip = SuggestionAdapter()
        var ordinaryClicks = 0
        var historyClicks = 0
        source.setOnItemClickListener { _, _ -> ordinaryClicks++ }
        source.setOnZeroQueryCandidateClickListener { historyClicks++ }
        source.suggestions = listOf(candidate)
        strip.mirrorSplitContentFrom(source)
        val ordinary = Reflect.getField<(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate, Int) -> Unit>(strip, "onItemClickListener")
        ordinary(candidate, 0)
        assertEquals(1, ordinaryClicks)
        strip.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.Empty)
        ordinary(candidate, 0) // a ViewHolder can outlive the asynchronous list replacement
        assertEquals(1, ordinaryClicks)
        source.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.ZeroQuerySuggestions(listOf(candidate)))
        strip.mirrorSplitContentFrom(source)
        val history = Reflect.getField<(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate) -> Unit>(strip, "onZeroQueryCandidateClickListener")
        history(candidate)
        assertEquals(1, historyClicks)
        strip.submitContent(com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent.Empty)
        history(candidate)
        assertEquals(1, historyClicks)
    }

    @Test fun editorReplacementDoesNotCommitOldFoldTextOrRestoreOldPaneChoices() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        install(SplitSlot.MAIN, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        val first = android.view.inputmethod.BaseInputConnection(View(context), true)
        doReturn(first).`when`(service).getCurrentInputConnection()
        text.value = "かな"
        service.setComposingText("かな", 1)
        service.onFinishInputView(true)
        service.onFinishInput()
        val replacement = android.view.inputmethod.BaseInputConnection(View(context), true)
        replacement.commitText("new editor", 1)
        doReturn(replacement).`when`(service).getCurrentInputConnection()
        service.onWindowHidden()
        assertEquals("new editor", replacement.editable.toString())
        assertTrue(Reflect.getField<Map<*, *>>(service, "savedFoldInputs").isEmpty())
        assertNull(Reflect.getField<SplitSlot?>(service, "activeSplitSlot"))
        assertNull(Reflect.getField<Any?>(service, "splitController"))
    }

    @Test fun foldHandoverCommitsBeforeChangingSideAndRepeatedTouchRoutingDoesNotCommitAgain() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        doAnswer { invocation ->
            assertEquals("かな", invocation.getArgument<CharSequence>(0).toString())
            assertSame(connection, service.currentInputConnection)
            assertEquals(SplitSlot.MAIN, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
            assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
            assertEquals(TenKeyQWERTYMode.Default,
                Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
            true
        }.`when`(connection).commitText(any(CharSequence::class.java), eq(1))
        text.value = "かな"
        fun activate(slot: SplitSlot) = Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(slot).qwertyViewFloating))

        activate(SplitSlot.SUB)
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        // DOWN, subsequent MOVE/UP, and the key callback can all route the same gesture.
        repeat(3) { activate(SplitSlot.SUB) }
        activate(SplitSlot.MAIN)
        verify(connection, times(1)).commitText("かな", 1)
        val order = inOrder(connection)
        order.verify(connection).beginBatchEdit()
        order.verify(connection).commitText("かな", 1)
        order.verify(connection).finishComposingText()
        order.verify(connection).endBatchEdit()
        assertEquals("", text.value)
    }

    @Test fun foldSameEditorReopenRestoresPaneChoicesWithoutRestoringCommittedComposition() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        install(SplitSlot.MAIN, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Custom)
        val leftSelection = SplitKeyboardSelection(KeyboardType.ROMAJI)
        val rightSelection = SplitKeyboardSelection(KeyboardType.CUSTOM, "chosen-during-input")
        Reflect.setField(inputs.getValue(SplitSlot.MAIN), "selection", leftSelection)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "selection", rightSelection)
        select(SplitSlot.SUB, TenKeyQWERTYMode.Custom)
        Reflect.setField(service, "currentInputModeForSession", InputMode.ModeEnglish)
        Reflect.setField(service, "customKeyboardMode", KeyboardInputMode.ENGLISH)
        Reflect.setField(service, "isCustomLayoutDirectMode", true)
        val connection = android.view.inputmethod.BaseInputConnection(View(context), true)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "pending"
        service.setComposingText("pending", 1)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val before = prefs.all.toMap()

        // Exercise the real same-editor hide path, including view disposal.
        service.onFinishInputView(false)
        assertTrue(inputs.isEmpty())
        assertEquals("pending", connection.editable.toString())
        assertEquals("", text.value)

        // Startup selects the saved layout before applying language/mode and restoring the side.
        val saved = Reflect.getField<Map<SplitSlot, Any>>(service, "savedFoldInputs")
        assertEquals(leftSelection, Reflect.getField<SplitKeyboardSelection>(saved.getValue(SplitSlot.MAIN), "selection"))
        assertEquals(rightSelection, Reflect.getField<SplitKeyboardSelection>(saved.getValue(SplitSlot.SUB), "selection"))
        for (slot in SplitSlot.entries) {
            install(slot, TenKeyQWERTYMode.Default)
            val state = inputs.getValue(slot)
            Reflect.setField(state, "selection", Reflect.getField<SplitKeyboardSelection>(saved.getValue(slot), "selection"))
            IMEService::class.java.declaredMethods.single { it.name == "restoreFoldInputSession" }.apply {
                isAccessible = true
                invoke(service, state)
            }
        }
        assertEquals(leftSelection, Reflect.getField<SplitKeyboardSelection>(inputs.getValue(SplitSlot.MAIN), "selection"))
        assertEquals(rightSelection, Reflect.getField<SplitKeyboardSelection>(inputs.getValue(SplitSlot.SUB), "selection"))
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.MAIN), "mode"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
        assertEquals(TenKeyQWERTYMode.Custom, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.SUB), "mode"))
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
        assertTrue(Reflect.getField<Boolean>(inputs.getValue(SplitSlot.SUB), "customDirect"))
        val restoredSide = Reflect.getField<SplitSlot>(service, "savedFoldActiveSlot")
        assertEquals(SplitSlot.SUB, restoredSide)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, restoredSide))
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        call("finishFoldSideComposition")
        assertEquals("pending", connection.editable.toString()) // no restored preedit to commit again
        assertEquals(-1, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(checkNotNull(connection.editable)))
        assertEquals(before, prefs.all)
    }

    @Test fun endingFoldEditorSessionDropsOnlyItsInMemoryPaneChoices() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Gojuon)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val before = prefs.all.toMap()
        call("rememberFoldInputSession")
        service.onFinishInput()
        assertTrue(Reflect.getField<Map<*, *>>(service, "savedFoldInputs").isEmpty())
        assertNull(Reflect.getField<SplitSlot?>(service, "savedFoldActiveSlot"))
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        val fresh = inputs.getValue(SplitSlot.SUB)
        IMEService::class.java.declaredMethods.single { it.name == "restoreFoldInputSession" }.apply {
            isAccessible = true
            invoke(service, fresh)
        }
        assertEquals(TenKeyQWERTYMode.Default, Reflect.getField<TenKeyQWERTYMode>(fresh, "mode"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(fresh, "inputMode"))
        assertEquals(before, prefs.all)
    }

    @Test fun japaneseTenkeyAndRomajiAlternateOneReadingAndCandidateAction() {
        assertSharedReading(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            InputMode.ModeJapanese, "きょ", "う", "今日")
    }

    @Test fun japaneseTenkeyPanesAlternateOneReadingAndCandidateAction() {
        assertSharedReading(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Default,
            InputMode.ModeJapanese, "きょ", "う", "今日")
    }

    @Test fun englishPanesSharePredictionUsingActualLanguageDespiteJapaneseLayouts() {
        assertSharedReading(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            InputMode.ModeEnglish, "hel", "lo", "hello")
    }

    private fun assertSharedReading(leftMode: TenKeyQWERTYMode, rightMode: TenKeyQWERTYMode,
                                    language: InputMode, first: String, second: String, result: String) {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, leftMode)
        install(SplitSlot.SUB, rightMode)
        select(SplitSlot.MAIN, leftMode)
        Reflect.setField(service, "currentInputModeForSession", language)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "inputMode", language)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val source = SuggestionAdapter()
        Reflect.setField(service, "suggestionAdapter", source)
        val connection = spy(android.view.inputmethod.BaseInputConnection(View(context), true))
        doReturn(connection).`when`(service).getCurrentInputConnection()
        val coordinator = Reflect.getField<CandidateRefreshCoordinator>(service, "candidateRefreshCoordinator")
        text.value = first
        service.setComposingText(first, 1)
        val pending = coordinator.request(first, CandidateShowFlag.Updating)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        assertEquals(first, text.value)
        assertTrue(coordinator.isCurrent(pending))
        text.value += second
        service.setComposingText(text.value, 1)
        val candidate = com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(
            result, 1.toByte(), text.value.length.toUByte(), 0, text.value)
        var clicks = 0
        source.setOnItemClickListener { value, _ ->
            clicks++
            Reflect.callInstanceMethod<Unit>(service, "setCandidateClick",
                ClassParameter.from(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate::class.java, value),
                ClassParameter.from(String::class.java, text.value),
                ClassParameter.from(InputMode::class.java, language),
                ClassParameter.from(Int::class.javaPrimitiveType!!, 0),
                ClassParameter.from(List::class.java, source.suggestions))
        }
        source.suggestions = listOf(candidate)
        call("scheduleSplitCandidates")
        shadowOf(Looper.getMainLooper()).idle()
        val right = Reflect.getField<SuggestionAdapter>(inputs.getValue(SplitSlot.SUB), "adapter")
        val left = Reflect.getField<SuggestionAdapter>(inputs.getValue(SplitSlot.MAIN), "adapter")
        assertEquals(listOf(candidate), right.suggestions)
        assertTrue(left.suggestions.isEmpty())
        val stale = Reflect.getField<(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate, Int) -> Unit>(right, "onItemClickListener")
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.MAIN))
        stale(candidate, 0)
        assertEquals(0, clicks)
        assertEquals(listOf(candidate), left.suggestions)
        assertTrue(right.suggestions.isEmpty())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(candidate), left.suggestions)
        assertTrue(right.suggestions.isEmpty())
        verify(connection, never()).commitText(any(CharSequence::class.java), anyInt())
        Reflect.getField<(com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate, Int) -> Unit>(left, "onItemClickListener")(candidate, 0)
        assertEquals(1, clicks)
        assertEquals(result, connection.editable.toString())
        verify(connection, times(1)).commitText(result, 1)
    }

    @Test fun helperActionsFromInactiveOrReplacedPaneAreIgnored() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        val source = SuggestionAdapter()
        var actions = 0
        source.setOnItemHelperIconClickListener { actions++ }
        Reflect.setField(service, "suggestionAdapter", source)
        fun activate(slot: SplitSlot) = Reflect.callInstanceMethod<Unit>(service, "activateSplitInput",
            ClassParameter.from(SplitSlot::class.java, slot))
        activate(SplitSlot.SUB)
        val oldAdapter = Reflect.getField<SuggestionAdapter>(inputs.getValue(SplitSlot.SUB), "adapter")
        val stale = Reflect.getField<(SuggestionAdapter.HelperIcon) -> Unit>(oldAdapter, "onItemHelperIconClickListener")
        stale(SuggestionAdapter.HelperIcon.UNDO)
        assertEquals(1, actions)
        activate(SplitSlot.MAIN)
        stale(SuggestionAdapter.HelperIcon.UNDO)
        assertEquals(1, actions)
        // A recreated editor surface can reuse the slot, but must not revive its old callbacks.
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        activate(SplitSlot.SUB)
        stale(SuggestionAdapter.HelperIcon.UNDO)
        assertEquals(1, actions)
    }

    @Test fun changingBetweenActualJapaneseAndEnglishCommitsOnceInBothDirections() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "inputMode", InputMode.ModeEnglish)
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "きょう"
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        verify(connection, times(1)).commitText("きょう", 1)
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        text.value = "hello"
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.MAIN))
        verify(connection, times(1)).commitText("hello", 1)
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        repeat(3) { Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.MAIN)) }
        verify(connection, times(2)).finishComposingText()
        assertEquals("", text.value)
    }

    @Test fun numberAndCustomDirectPaneBoundariesFinishPriorComposition() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Custom)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "customDirect", true)
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "かな"
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        verify(connection, times(1)).commitText("かな", 1)
        assertEquals("", text.value)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.MAIN))
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "customDirect", false)
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "inputMode", InputMode.ModeNumber)
        text.value = "よみ"
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        verify(connection, times(1)).commitText("よみ", 1)
        assertEquals(InputMode.ModeNumber, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
        assertEquals("", text.value)
    }

    @Test fun foldJapanesePaneHandoverPreservesSelectedCandidateAndTailUntilLanguageChange() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Gojuon)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Gojuon)
        val source = SuggestionAdapter()
        Reflect.setField(service, "suggestionAdapter", source)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        val candidates = listOf(
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate("第一", 1.toByte(), 2.toUByte(), 0, "だいいち"),
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate("第二", 1.toByte(), 2.toUByte(), 0, "だいに"),
        )
        source.suggestions = candidates
        text.value = "だい"
        Reflect.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").set("尾")
        Reflect.getField<java.util.concurrent.atomic.AtomicBoolean>(service, "isHenkan").set(true)
        Reflect.setField(service, "suggestionClickNum", 2)
        val coordinator = Reflect.getField<CandidateRefreshCoordinator>(service, "candidateRefreshCoordinator")
        val staleRequest = coordinator.request("だい", CandidateShowFlag.Updating)

        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.SUB).qwertyViewFloating))

        verify(connection, never()).commitText(any(CharSequence::class.java), anyInt())
        verify(connection, never()).finishComposingText()
        assertTrue(coordinator.isCurrent(staleRequest))
        assertEquals("だい", text.value)
        assertEquals("尾", Reflect.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").get())
        assertTrue(Reflect.getField<java.util.concurrent.atomic.AtomicBoolean>(service, "isHenkan").get())
        assertEquals(2, Reflect.getField<Int>(service, "suggestionClickNum"))
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))

        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.MAIN).gojuonViewFloating))
        verify(connection, never()).commitText(any(CharSequence::class.java), anyInt())
        Reflect.setField(inputs.getValue(SplitSlot.SUB), "inputMode", InputMode.ModeEnglish)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        verify(connection, times(1)).commitText("第二尾", 1)
        assertFalse(coordinator.isCurrent(staleRequest))
        assertEquals("", text.value)
        assertEquals("", Reflect.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").get())
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(service, "currentInputModeForSession"))
    }

    @Test fun foldHandoverThenDeleteUsesOneEditorCompositionAtATime() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val editor = android.widget.EditText(context)
        val connection = object : android.view.inputmethod.BaseInputConnection(editor, true) {
            override fun getEditable() = editor.editableText
            override fun sendKeyEvent(event: android.view.KeyEvent) = editor.dispatchKeyEvent(event)
        }
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "かな"
        service.setComposingText("かな", 1)

        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.SUB).keyboardViewFloating))
        val editable = checkNotNull(connection.editable)
        assertEquals("かな", editable.toString())
        assertEquals(0, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editable))
        assertEquals("かな", text.value)

        Reflect.callInstanceMethod<Unit>(service, "handleDeleteKeyTap",
            ClassParameter.from(String::class.java, "かな"), ClassParameter.from(List::class.java, emptyList<Any>()))
        assertEquals("か", text.value)
        service.setComposingText(text.value, 1)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.MAIN).keyboardViewFloating))
        assertEquals("か", editable.toString())
        assertEquals("か", text.value)
        assertEquals(0, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editable))
    }

    @Suppress("DEPRECATION")
    @Test fun foldPaneMeasurementsUseConfiguredKeyboardAndCandidateHeightsAfterRotation() {
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        mapOf(
            "tenkeyHeightPreferenceValue" to 340,
            "qwertyHeightPreferenceValue" to 210,
            "tenkeyHeightLandScapePreferenceValue" to 170,
            "qwertyHeightLandScapePreferenceValue" to 130,
            "candidateViewHeightPreferenceValue" to 105,
            "candidateViewHeightEmptyPreferenceValue" to 70,
            "candidateViewLandScapeHeightPreferenceValue" to 90,
            "candidateViewLandScapeHeightEmptyPreferenceValue" to 44,
        ).forEach { (field, value) -> Reflect.setField(service, field, value) }
        val original = android.content.res.Configuration(context.resources.configuration)
        fun orientation(value: Int) {
            val config = android.content.res.Configuration(original).apply { orientation = value }
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
        fun height(method: String, slot: SplitSlot) = Reflect.callInstanceMethod<Int>(service, method,
            ClassParameter.from(SplitSlot::class.java, slot))
        try {
            orientation(android.content.res.Configuration.ORIENTATION_PORTRAIT)
            assertEquals(context.dpToPx(340), height("foldPaneBodyHeight", SplitSlot.MAIN))
            assertEquals(context.dpToPx(210), height("foldPaneBodyHeight", SplitSlot.SUB))
            assertEquals(context.dpToPx(70), height("foldPaneCandidateHeight", SplitSlot.MAIN))
            text.value = "かな"
            assertEquals(context.dpToPx(105), height("foldPaneCandidateHeight", SplitSlot.MAIN))
            assertEquals(context.dpToPx(70), height("foldPaneCandidateHeight", SplitSlot.SUB))

            orientation(android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            assertEquals(context.dpToPx(170), height("foldPaneBodyHeight", SplitSlot.MAIN))
            assertEquals(context.dpToPx(130), height("foldPaneBodyHeight", SplitSlot.SUB))
            assertEquals(context.dpToPx(90), height("foldPaneCandidateHeight", SplitSlot.MAIN))
            assertEquals(context.dpToPx(44), height("foldPaneCandidateHeight", SplitSlot.SUB))
        } finally {
            context.resources.updateConfiguration(original, context.resources.displayMetrics)
        }
    }

    @Test fun foldDockHostIsBottomDockedAndHidesTheRegularImeChildren() {
        val main = com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding.inflate(LayoutInflater.from(context))
        main.root.layoutParams = android.widget.FrameLayout.LayoutParams(500, 440, android.view.Gravity.BOTTOM).apply {
            setMargins(7, 11, 13, 17)
        }
        Reflect.setField(service, "mainLayoutBinding", main)
        val originalChildren = (0 until main.root.childCount).map(main.root::getChildAt)
        val originalVisibility = originalChildren.associateWith { it.visibility }
        val host = Reflect.callInstanceMethod<android.widget.FrameLayout>(service, "ensureFoldDockHost",
            ClassParameter.from(com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding::class.java, main))
        val params = host.layoutParams as android.widget.FrameLayout.LayoutParams
        assertSame(main.root, host.parent)
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, params.height)
        assertEquals(android.view.Gravity.BOTTOM, params.gravity)
        assertEquals(1f, main.root.alpha)
        val rootParams = main.root.layoutParams as android.widget.FrameLayout.LayoutParams
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, rootParams.height)
        assertEquals(listOf(7, 11, 13, 17), listOf(rootParams.leftMargin, rootParams.topMargin, rootParams.rightMargin, rootParams.bottomMargin))
        originalChildren.forEach { assertEquals(View.GONE, it.visibility) }
        // Measure through the real normal-root hierarchy, including its navigation padding.
        host.addView(android.widget.FrameLayout(context).apply { minimumHeight = 380 })
        main.root.setPadding(0, 0, 0, 24)
        fun measureRoot(limit: Int) {
            main.root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
        }
        measureRoot(700)
        assertEquals(404, main.root.measuredHeight)
        assertEquals(380, host.measuredHeight)
        measureRoot(250)
        assertEquals(250, main.root.measuredHeight)
        assertEquals(226, host.measuredHeight)
        Reflect.callInstanceMethod<Unit>(service, "stopSplitKeyboard", ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        val restored = main.root.layoutParams as android.widget.FrameLayout.LayoutParams
        assertEquals(500, restored.width)
        assertEquals(440, restored.height)
        assertEquals(listOf(7, 11, 13, 17), listOf(restored.leftMargin, restored.topMargin, restored.rightMargin, restored.bottomMargin))
        originalVisibility.forEach { (view, visibility) -> assertEquals(visibility, view.visibility) }
    }

    @Test fun foldHandoverPreservesDisplayedLiveConversionRatherThanRawReading() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        val connection = android.view.inputmethod.BaseInputConnection(View(context), true)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "かな"
        service.setComposingText("仮名", 1)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.SUB).keyboardViewFloating))
        assertEquals("仮名", connection.editable.toString())
        assertEquals(0, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(checkNotNull(connection.editable)))
        assertEquals("かな", text.value)
    }

    @Test fun foldQwertyUsesExistingLanguageKeyIndependently() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "isKeyboardFloatingMode", true)
        Reflect.setField(service, "englishEngine", mock(IMEService::class.java.getDeclaredField("englishEngine").type))
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        select(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTY)
        val main = com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding.inflate(LayoutInflater.from(context))
        val view = bindings.getValue(SplitSlot.SUB).qwertyViewFloating
        Reflect.callInstanceMethod<Unit>(service, "configureQwertyView",
            ClassParameter.from(com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView::class.java, view),
            ClassParameter.from(com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding::class.java, main))
        call("renderQwertyStateOnActiveSurface")
        assertTrue(view.snapshotUiState().showRomajiEnglishSwitchKey)
        val listener = Reflect.getField<com.kazumaproject.core.domain.listener.QWERTYKeyListener>(view, "qwertyKeyListener")
        val key = com.kazumaproject.core.domain.qwerty.QWERTYKey.QWERTYKeySwitchRomajiEnglish
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "hello"
        listener.onReleasedQWERTYKey(key, null, null)
        Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
        assertTrue(Reflect.getField<Boolean>(inputs.getValue(SplitSlot.SUB), "romaji"))
        verify(connection, times(1)).commitText("hello", 1)
        assertEquals("", text.value)
        text.value = "きょう"
        listener.onReleasedQWERTYKey(key, null, null)
        Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
        verify(connection, times(1)).commitText("きょう", 1)
        verify(connection, times(2)).finishComposingText()
        assertEquals("", text.value)
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
    }

    @Test fun foldLayoutKeyCyclesOnlyActivePaneWithoutChangingSettings() {
        Reflect.setField(service, "activeFoldLayout", FoldKeyboardLayout.SPLIT)
        Reflect.setField(service, "keyboardOrder", listOf(KeyboardType.TENKEY, KeyboardType.GOJUON, KeyboardType.QWERTY))
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        Reflect.setField(service, "suggestionAdapter", SuggestionAdapter())
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.Default)
        select(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val before = prefs.all.toMap()
        val connection = mock(android.view.inputmethod.InputConnection::class.java)
        doReturn(connection).`when`(service).getCurrentInputConnection()
        text.value = "きょ"
        service.switchNextKeyboard()
        assertEquals("きょ", text.value)
        verify(connection, never()).commitText(any(CharSequence::class.java), anyInt())
        assertEquals(TenKeyQWERTYMode.Gojuon, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.MAIN), "mode"))
        assertEquals(TenKeyQWERTYMode.Default, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.SUB), "mode"))
        service.switchNextKeyboard()
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTY, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.MAIN), "mode"))
        verify(connection, times(1)).commitText("きょ", 1)
        assertEquals("", text.value)
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
        assertEquals(before, prefs.all)
    }

    @Test fun detachedCandidateSurfaceUsesSharedSourceAndRestoresItsOriginalParent() {
        val main = com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding.inflate(LayoutInflater.from(context))
        Reflect.setField(service, "mainLayoutBinding", main)
        Reflect.setField(service, "listAdapter", mock(IMEService::class.java.getDeclaredField("listAdapter").type))
        val source = SuggestionAdapter()
        Reflect.setField(service, "suggestionAdapter", source)
        main.suggestionRecyclerView.adapter = source
        val original = main.suggestionViewParent.parent
        val target = android.widget.LinearLayout(context)
        install(SplitSlot.MAIN, TenKeyQWERTYMode.Default)
        install(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        select(SplitSlot.SUB, TenKeyQWERTYMode.TenKeyQWERTYRomaji)
        fun move(to: android.widget.LinearLayout?) = Reflect.callInstanceMethod<Unit>(service, "moveCandidateSurface",
            ClassParameter.from(android.widget.LinearLayout::class.java, to))
        move(target)
        assertSame(target, main.suggestionViewParent.parent)
        assertSame(source, main.suggestionRecyclerView.adapter)
        assertEquals(SplitSlot.SUB, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
        val firstHost = Reflect.getField<Any>(service, "candidateSurfaceHost")
        move(target)
        assertSame(firstHost, Reflect.getField<Any>(service, "candidateSurfaceHost"))
        move(null)
        assertSame(original, main.suggestionViewParent.parent)
        assertNull(Reflect.getField<Any?>(service, "candidateSurfaceHost"))
        assertEquals(SplitSlot.SUB, Reflect.getField<SplitSlot>(service, "activeSplitSlot"))
    }

}
