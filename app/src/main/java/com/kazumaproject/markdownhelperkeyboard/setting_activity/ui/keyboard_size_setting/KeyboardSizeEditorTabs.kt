package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.core.os.bundleOf
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.KeyboardLayoutState
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeDirectInputFragment

/** Keeps the historical drag editor and its numeric values on the same settings page. */
internal class KeyboardSizeEditorTabs(
    private val fragment: Fragment,
    gui: View,
    private val landscape: Boolean,
    private val isQwerty: () -> Boolean,
    private val selectQwerty: (Boolean) -> Unit,
    private val refreshGui: () -> Unit,
) {
    private val context = gui.context
    private val posture = TextView(context).apply {
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, 0)
    }
    private val tabs = TabLayout(context).apply {
        addTab(newTab().setText(R.string.keyboard_size_gui_tab))
        addTab(newTab().setText(R.string.keyboard_size_numeric_tab))
    }
    private val guiContainer = FrameLayout(context).apply { addView(gui) }
    private val numericContainer = FrameLayout(context).apply {
        id = R.id.keyboard_size_numeric_container
        isVisible = false
    }
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isFocusableInTouchMode = true
        addView(posture, LinearLayout.LayoutParams(-1, -2))
        addView(tabs, LinearLayout.LayoutParams(-1, -2))
        addView(FrameLayout(context).apply {
            addView(guiContainer, FrameLayout.LayoutParams(-1, -1))
            addView(numericContainer, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    val selectedTab get() = tabs.selectedTabPosition
    private val numeric get() = fragment.childFragmentManager.findFragmentByTag(NUMERIC_TAG) as? KeyboardSizeDirectInputFragment

    fun bind(selected: Int) {
        var restoringNumeric = selected == 1 && numeric != null
        if (numeric == null) {
            val editor = KeyboardSizeDirectInputFragment().apply {
                arguments = bundleOf(
                    "embeddedSizeEditor" to true,
                    "initialOrientation" to if (landscape) "landscape" else "portrait",
                    "initialKeyboardType" to if (isQwerty()) "qwerty" else "tenkey",
                )
            }
            fragment.childFragmentManager.beginTransaction()
                .add(numericContainer.id, editor, NUMERIC_TAG).commitNow()
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                show(tab.position, !restoringNumeric)
                restoringNumeric = false
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        onPostureChanged()
        if (selected == 1) tabs.getTabAt(1)?.select() else show(0)
    }

    private fun show(position: Int, synchronizeFamily: Boolean = true) {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(root.windowToken, 0)
        root.clearFocus()
        root.requestFocus()
        if (position == 1) {
            if (synchronizeFamily) numeric?.selectKeyboardType(isQwerty())
            numeric?.refreshValues()
        } else {
            numeric?.let { selectQwerty(it.isQwertySelected()) }
            refreshGui()
        }
        guiContainer.isVisible = position == 0
        numericContainer.isVisible = position == 1
    }

    fun onPostureChanged() {
        val unfolded = KeyboardLayoutState.isUnfolded(context.resources.configuration)
        posture.setText(if (unfolded) R.string.keyboard_size_unfolded else R.string.keyboard_size_folded)
        numeric?.onFoldPostureChanged(unfolded)
    }

    private companion object { const val NUMERIC_TAG = "keyboard-size-numeric" }
}
