package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserDictionaryAddRequestTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun intentNavigatesToExistingDictionaryWithDraftArguments() {
        val intent = UserDictionaryAddRequest.createIntent(context, "よみ", "候補")
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        val activity = mock(MainActivity::class.java, CALLS_REAL_METHODS)
        val navController = mock(NavController::class.java)
        MainActivity::class.java.getDeclaredField("mainNavController").apply {
            isAccessible = true
            set(activity, navController)
        }
        val handled = MainActivity::class.java.getDeclaredMethod("handleIntent", Intent::class.java).apply {
            isAccessible = true
        }.invoke(activity, intent)
        assertEquals(true, handled)
        val args = ArgumentCaptor.forClass(Bundle::class.java)
        verify(navController).navigate(eq(R.id.navigation_user_dictionary), args.capture())
        assertEquals("よみ" to "候補", UserDictionaryAddRequest.takeDraft(args.value))
    }

    @Test
    fun prefillOpensEditableExistingFormAndIsConsumedOnce() {
        val args = UserDictionaryAddRequest.arguments(
            UserDictionaryAddRequest.createIntent(context, "よみ", "候補")
        )
        val (reading, output) = requireNotNull(UserDictionaryAddRequest.takeDraft(args))
        val binding = inflateBinding()
        showUserDictionaryDraft(binding, reading, output)
        assertTrue(binding.cardViewAddWord.isVisible)
        assertFalse(binding.layoutExplanation.isVisible)
        assertEquals("よみ", binding.editTextReading.text.toString())
        assertEquals("候補", binding.editTextWord.text.toString())
        binding.editTextReading.setText("へんしゅう")
        binding.editTextWord.setText("編集")
        assertEquals("へんしゅう", binding.editTextReading.text.toString())
        assertEquals("編集", binding.editTextWord.text.toString())
        assertNull(UserDictionaryAddRequest.takeDraft(args))
        // Populating the form does not activate Add.
        assertFalse(binding.buttonAdd.isPressed)
    }

    @Test
    fun openingDraftDoesNotWriteAndSaveStateKeepsUserEdits() {
        val repository = mock(UserDictionaryRepository::class.java)
        `when`(repository.allWords).thenReturn(MutableLiveData(emptyList()))
        val viewModel = UserDictionaryViewModel(repository, context as Application)
        clearInvocations(repository)
        val fragment = UserDictionaryFragment()
        val binding = inflateBinding()
        UserDictionaryFragment::class.java.getDeclaredField("_binding").apply {
            isAccessible = true
            set(fragment, binding)
        }
        UserDictionaryFragment::class.java.getDeclaredField("viewModel\$delegate").apply {
            isAccessible = true
            set(fragment, lazy { viewModel })
        }
        UserDictionaryFragment::class.java.getDeclaredMethod("setupListeners").apply {
            isAccessible = true
        }.invoke(fragment)
        showUserDictionaryDraft(binding, "よみ", "候補")
        binding.editTextReading.setText("へんしゅう")
        binding.editTextWord.setText("編集")
        val state = Bundle()
        fragment.onSaveInstanceState(state)
        assertTrue(state.getBoolean("add_word_visible"))
        assertEquals("へんしゅう", state.getString("add_word_reading"))
        assertEquals("編集", state.getString("add_word_output"))
        binding.editTextReading.setText("")
        binding.editTextWord.setText("")
        binding.cardViewAddWord.visibility = android.view.View.GONE
        UserDictionaryFragment::class.java.getDeclaredMethod("restoreDraft", Bundle::class.java).apply {
            isAccessible = true
        }.invoke(fragment, state)
        assertTrue(binding.cardViewAddWord.isVisible)
        assertEquals("へんしゅう", binding.editTextReading.text.toString())
        assertEquals("編集", binding.editTextWord.text.toString())
        // Noun is the first POS in both localized resource arrays.
        assertEquals(0, viewModel.defaultPosIndex)
        verifyNoInteractions(repository)
    }

    @Test
    fun uncertainReadingRemainsBlankForManualCorrection() {
        val binding = inflateBinding()
        showUserDictionaryDraft(binding, "", "予測候補")
        assertEquals("", binding.editTextReading.text.toString())
        assertEquals("予測候補", binding.editTextWord.text.toString())
        assertTrue(binding.editTextReading.hasFocus())
    }

    @Test
    fun reopeningCreatesAFreshDraft() {
        repeat(2) {
            val args = UserDictionaryAddRequest.arguments(
                UserDictionaryAddRequest.createIntent(context, "よみ", "候補")
            )
            assertEquals("よみ" to "候補", UserDictionaryAddRequest.takeDraft(args))
            assertNull(UserDictionaryAddRequest.takeDraft(args))
        }
    }

    private fun inflateBinding(): FragmentUserDictionaryBinding =
        FragmentUserDictionaryBinding.inflate(
            LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard))
        )
}
