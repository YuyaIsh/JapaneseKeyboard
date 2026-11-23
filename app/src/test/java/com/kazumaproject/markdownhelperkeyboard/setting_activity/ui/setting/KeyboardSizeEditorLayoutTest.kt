package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h700dp-mdpi")
class KeyboardSizeEditorLayoutTest {
    @Test fun bothVisualEditorsKeepAlignmentAndResetControlsVisibleAndSeparate() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
        for (layout in listOf(R.layout.fragment_keyboard_setting, R.layout.fragment_keyboardsize_landscape)) {
            val root = LayoutInflater.from(context).inflate(layout, null)
            root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 400, 700)
            val alignment = root.findViewById<View>(R.id.keyboard_position_button)
            val reset = root.findViewById<View>(R.id.reset_layout_button)
            assertTrue("Alignment must have a usable touch target", alignment.width >= 48 && alignment.height >= 48)
            assertTrue("Reset must have a usable touch target", reset.width >= 48 && reset.height >= 48)
            assertTrue("Controls must share a row without overlapping", alignment.right <= reset.left && alignment.top == reset.top)
        }
    }
}
