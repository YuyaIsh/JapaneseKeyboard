package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.KeyboardSizeEditorTabs
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSizeEditorCreationTest {
    class EditorFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
            // Both production editors construct the tabs before a viewLifecycleOwner exists.
            val gui = inflater.inflate(R.layout.fragment_keyboard_setting, container, false)
            return KeyboardSizeEditorTabs(this, gui, false, { false }, {}, { true }, {},
                AppPreference, mock<KeyboardRepository>(), mock<SumireSpecialKeyRepository>()).root
        }
    }

    @Test fun constructingEditorDuringOnCreateViewDoesNotAccessViewLifecycleOwner() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.Theme_MarkdownKeyboard)
        val activity = controller.setup().get()
        AppPreference.init(activity)
        try {
            val fragment = EditorFragment()
            activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
            assertNotNull(fragment.viewLifecycleOwner)
            assertNotNull(fragment.requireView().findViewById<View>(R.id.keyboard_container))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
