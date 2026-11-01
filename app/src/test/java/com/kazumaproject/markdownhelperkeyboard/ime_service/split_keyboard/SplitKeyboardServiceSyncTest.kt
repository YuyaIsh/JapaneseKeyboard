package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
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
import kotlin.math.roundToInt

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

    @Test fun foldJapanesePaneHandoverCommitsSelectedCandidateAndTailOnce() {
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

        verify(connection).commitText("第二尾", 1)
        verify(connection).finishComposingText()
        assertFalse(coordinator.isCurrent(staleRequest))
        assertEquals("", text.value)
        assertEquals("", Reflect.getField<java.util.concurrent.atomic.AtomicReference<String>>(service, "stringInTail").get())
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTYRomaji,
            Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.MAIN), "inputMode"))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))

        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.MAIN).gojuonViewFloating))
        verify(connection, times(1)).commitText("第二尾", 1)
        assertEquals(TenKeyQWERTYMode.Gojuon,
            Reflect.getField<MutableStateFlow<TenKeyQWERTYMode>>(service, "_tenKeyQWERTYMode").value)
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
        assertEquals(-1, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editable))
        assertEquals("", text.value)

        Reflect.callInstanceMethod<Unit>(service, "handleDeleteKeyTap",
            ClassParameter.from(String::class.java, ""), ClassParameter.from(List::class.java, emptyList<Any>()))
        assertEquals("か", editable.toString())

        text.value = "に"
        service.setComposingText("に", 1)
        Reflect.callInstanceMethod<Unit>(service, "activateSplitView",
            ClassParameter.from(View::class.java, bindings.getValue(SplitSlot.MAIN).keyboardViewFloating))
        assertEquals("かに", editable.toString())
        assertEquals(-1, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editable))
    }

    @Test fun foldDockHostIsBottomDockedAndHidesTheRegularImeChildren() {
        val main = com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding.inflate(LayoutInflater.from(context))
        val originalChildren = (0 until main.root.childCount).map(main.root::getChildAt)
        val host = Reflect.callInstanceMethod<android.widget.FrameLayout>(service, "ensureFoldDockHost",
            ClassParameter.from(com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding::class.java, main))
        val params = host.layoutParams as android.widget.FrameLayout.LayoutParams
        assertSame(main.root, host.parent)
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(android.view.Gravity.BOTTOM, params.gravity)
        assertEquals(1f, main.root.alpha)
        assertEquals((298 * context.resources.displayMetrics.density).roundToInt(), (main.root.layoutParams as android.widget.FrameLayout.LayoutParams).height)
        originalChildren.forEach { assertEquals(View.GONE, it.visibility) }
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
        assertEquals(-1, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(checkNotNull(connection.editable)))
        assertEquals("", text.value)
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
        listener.onReleasedQWERTYKey(key, null, null)
        Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        assertEquals(InputMode.ModeJapanese, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
        assertTrue(Reflect.getField<Boolean>(inputs.getValue(SplitSlot.SUB), "romaji"))
        listener.onReleasedQWERTYKey(key, null, null)
        Reflect.callInstanceMethod<Unit>(service, "saveSplitInput", ClassParameter.from(SplitSlot::class.java, SplitSlot.SUB))
        assertEquals(InputMode.ModeEnglish, Reflect.getField<InputMode>(inputs.getValue(SplitSlot.SUB), "inputMode"))
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
        service.switchNextKeyboard()
        assertEquals(TenKeyQWERTYMode.Gojuon, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.MAIN), "mode"))
        assertEquals(TenKeyQWERTYMode.Default, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.SUB), "mode"))
        service.switchNextKeyboard()
        assertEquals(TenKeyQWERTYMode.TenKeyQWERTY, Reflect.getField<TenKeyQWERTYMode>(inputs.getValue(SplitSlot.MAIN), "mode"))
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
