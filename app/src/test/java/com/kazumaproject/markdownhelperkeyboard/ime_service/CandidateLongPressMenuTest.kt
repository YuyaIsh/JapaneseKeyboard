package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.PopupWindow
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary.UserDictionaryAddRequest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers as Reflect
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateLongPressMenuTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val gemma = mock(GemmaTranslationManager::class.java)
    private val learned = mock(LearnRepository::class.java)
    private val dictionary = mock(UserDictionaryRepository::class.java)
    private val service = spy(IMEService().also {
        Reflect.callInstanceMethod<Unit>(it, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        AppPreference.init(context)
        it.appPreference = AppPreference
        it.gemmaTranslationManager = gemma
        it.learnRepository = learned
        it.userDictionaryRepository = dictionary
    }).also {
        doReturn(editor).`when`(it).getCurrentInputEditorInfo()
        doNothing().`when`(it).startActivity(any(Intent::class.java))
        doNothing().`when`(it).requestHideSelf(anyInt())
        Reflect.setField(it, "isNgWordEnable", false)
        Reflect.getField<MutableStateFlow<String>>(it, "_inputString").value = "きょう"
    }
    private val candidate = Candidate("今日", 1, 3u, 1000, "きょう")

    private fun eligible(value: Candidate = candidate): Boolean = Reflect.callInstanceMethod(
        service, "shouldShowCandidateLongPressActions", ClassParameter.from(Candidate::class.java, value))

    private fun menu(
        value: Candidate = candidate,
        reading: String = "きょう",
        templates: List<GemmaPromptTemplate> = emptyList(),
        current: () -> Boolean = { true },
    ): ListView {
        Reflect.setField(service, "mainLayoutBinding", MainLayoutBinding.inflate(LayoutInflater.from(context)))
        val method = IMEService::class.java.declaredMethods.single { it.name == "showCandidateLongPressActionsPopup" }
        method.isAccessible = true
        method.invoke(service, "きょう", value, 0, templates, reading, current)
        val popup = Reflect.getField<PopupWindow>(service, "keyboardSelectionPopupWindow")
        return popup.contentView.findViewById(R.id.popup_listview)
    }

    private fun labels(list: ListView) = (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
    private fun click(list: ListView, position: Int) = list.performItemClick(null, position, position.toLong())

    @Test fun ordinaryJapaneseOpensMenuWithOptionalActionsDisabledButSecureAndMacroInputsDoNot() {
        assertTrue(eligible())
        assertFalse(eligible(candidate.copy(type = CANDIDATE_TYPE_TEXT_MACRO)))
        assertFalse(eligible(candidate.copy(sourceId = 1)))
        editor.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        assertFalse(eligible())
        editor.imeOptions = 0
        editor.inputType = InputType.TYPE_CLASS_NUMBER
        assertFalse(eligible())
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertFalse(eligible())
    }

    @Test fun newActionsComeFirstAndKeepForgetHideTranslateAndGemmaActions() {
        Reflect.setField(service, "isNgWordEnable", true)
        `when`(gemma.isTranslationAvailable()).thenReturn(true)
        val template = GemmaPromptTemplate(title = "要約", prompt = "summarize", isEnabled = true,
            sortOrder = 0, createdAt = 0, updatedAt = 0)
        val list = menu(candidate.copy(type = CANDIDATE_TYPE_LEARNED_DICTIONARY), templates = listOf(template))
        assertEquals(listOf(
            R.string.candidate_action_promote, R.string.candidate_action_register,
            R.string.candidate_action_forget_learning, R.string.candidate_action_hide_word,
            R.string.candidate_action_translate,
        ).map { service.getString(it) } + listOf("要約", service.getString(R.string.candidate_action_close)), labels(list))
        verifyNoInteractions(learned, dictionary)
        verify(service, never()).startActivity(any(Intent::class.java))
    }

    @Test fun registrationOpensAnEditableDraftWithCandidateReadingAndDoesNotSave() {
        val list = menu(reading = "きょうじゅう")
        click(list, 1)
        val intent = ArgumentCaptor.forClass(Intent::class.java)
        verify(service).startActivity(intent.capture())
        assertEquals("きょうじゅう" to "今日", UserDictionaryAddRequest.takeDraft(UserDictionaryAddRequest.arguments(intent.value)))
        verify(service).requestHideSelf(0)
        verifyNoInteractions(learned, dictionary)
    }

    @Test fun invalidReadingRequiresManualCorrectionAndDoesNotOfferPromotion() {
        val list = menu(reading = "")
        assertEquals(listOf(service.getString(R.string.candidate_action_register), service.getString(R.string.candidate_action_close)), labels(list))
        click(list, 0)
        val intent = ArgumentCaptor.forClass(Intent::class.java)
        verify(service).startActivity(intent.capture())
        assertEquals("" to "今日", UserDictionaryAddRequest.takeDraft(UserDictionaryAddRequest.arguments(intent.value)))
        verifyNoInteractions(learned, dictionary)
    }

    @Test fun aPopupFromAnOldPaneSessionCannotPromoteOrLaunchRegistration() {
        var current = true
        val list = menu(current = { current })
        current = false
        click(list, 0)
        click(list, 1)
        verifyNoInteractions(learned, dictionary)
        verify(service, never()).startActivity(any(Intent::class.java))
    }
}
