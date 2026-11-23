package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.KeyboardLayoutState
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeDirectInputFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeValues
import kotlin.math.roundToInt
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizePreferenceAccessor
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeOrientation
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeKeyboardType

/** Tabs switch only controls; both edit the same permanently visible keyboard preview. */
internal class KeyboardSizeEditorTabs(
    private val fragment: Fragment,
    gui: View,
    private val landscape: Boolean,
    private val isQwerty: () -> Boolean,
    private val selectQwerty: (Boolean) -> Unit,
    private val isRightAligned: () -> Boolean,
    private val refreshGui: () -> Unit,
    private val appPreference: AppPreference,
    private val keyboardRepository: KeyboardRepository,
    private val sumireSpecialKeyRepository: SumireSpecialKeyRepository,
) {
    private val context = gui.context
    private val density = context.resources.displayMetrics.density
    private val previousSoftInputMode = fragment.requireActivity().window.attributes.softInputMode
    private val canvas = gui.findViewById<ConstraintLayout>(R.id.keyboard_setting_constraint)
    private val keyboard = gui.findViewById<View>(R.id.keyboard_container)
    private val handles = listOf(R.id.handle_top, R.id.handle_bottom, R.id.handle_left, R.id.handle_right, R.id.handle_move).map { gui.findViewById<View>(it) }
    private val posture = TextView(context).apply { setPadding(dp(12), dp(4), dp(12), 0) }
    private val tabs = TabLayout(context).apply {
        addTab(newTab().setText(R.string.keyboard_size_gui_tab))
        addTab(newTab().setText(R.string.keyboard_size_numeric_tab))
    }
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(12)) }
    private val guiControls = ScrollView(context).apply { addView(controls) }
    private val numericContainer = FrameLayout(context).apply { id = R.id.keyboard_size_numeric_container; isVisible = false }
    private val viewport: KeyboardSizePreviewViewport
    private val previewArea = FrameLayout(context)
    private var splitPreview: FoldSizingPreview? = null
    private val drafts = mutableMapOf<Boolean, KeyboardSizeValues>()
    private val sizePreferences = KeyboardSizePreferenceAccessor(appPreference)
    private val orientation get() = if (landscape) KeyboardSizeOrientation.Landscape else KeyboardSizeOrientation.Portrait
    private var unfolded = false
    val previewScale get() = if (unfolded) splitPreview?.previewScale ?: 1f else viewport.previewScale

    private fun values(qwerty: Boolean) = drafts[qwerty] ?: sizePreferences.load(orientation,
        if (qwerty) KeyboardSizeKeyboardType.Qwerty else KeyboardSizeKeyboardType.TenKey, unfolded)

    private fun position(qwerty: Boolean): Boolean = if (qwerty) {
        if (landscape) appPreference.qwerty_keyboard_position_landscape ?: true else appPreference.qwerty_keyboard_position ?: true
    } else {
        if (landscape) appPreference.keyboard_position_landscape ?: true else appPreference.keyboard_position ?: true
    }
    val root: LinearLayout
    val selectedTab get() = tabs.selectedTabPosition
    private val numeric get() = fragment.childFragmentManager.findFragmentByTag(NUMERIC_TAG) as? KeyboardSizeDirectInputFragment

    init {
        fun take(id: Int) = gui.findViewById<View>(id).also { (it.parent as ViewGroup).removeView(it) }
        fun row(first: View, second: View) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(first, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
            addView(second, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) })
        }
        controls.addView(row(take(R.id.tenkey_tooltip_button), take(R.id.qwerty_tooltip_button)))
        controls.addView(take(R.id.keyboard_position_title), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        controls.addView(row(take(R.id.keyboard_position_button), take(R.id.reset_layout_button)))
        (canvas.parent as ViewGroup).removeView(canvas)
        ConstraintSet().apply {
            clone(canvas)
            connect(keyboard.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            applyTo(canvas)
        }
        viewport = KeyboardSizePreviewViewport(context, canvas, keyboard)
        canvas.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) applyDraft()
        }
        previewArea.addView(viewport, FrameLayout.LayoutParams(-1, -1))
        root = object : LinearLayout(context) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val available = (MeasureSpec.getSize(heightSpec) - paddingBottom).coerceAtLeast(0)
                val height = minOf(dp(280), (available * .44f).toInt()).coerceAtLeast(dp(80))
                previewArea.layoutParams.height = height
                super.onMeasure(widthSpec, heightSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            addView(posture, LinearLayout.LayoutParams(-1, -2))
            addView(previewArea, LinearLayout.LayoutParams(-1, dp(280)))
            addView(tabs, LinearLayout.LayoutParams(-1, -2))
            addView(FrameLayout(context).apply {
                addView(guiControls, FrameLayout.LayoutParams(-1, -1))
                addView(numericContainer, FrameLayout.LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        // Edge-to-edge settings windows otherwise leave numeric controls behind the IME.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.post {
                if (!ViewCompat.isAttachedToWindow(view)) return@post
                val bounds = androidx.window.layout.WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(fragment.requireActivity()).bounds
                val location = IntArray(2).also(view::getLocationOnScreen)
                val overlap = if (ime == 0) 0 else (location[1] + view.height - (bounds.bottom - ime)).coerceAtLeast(0)
                // If adjustResize already shortened the parent, overlap is zero.
                view.setPadding(0, 0, 0, overlap.coerceAtMost((ime - navigation).coerceAtLeast(0)))
            }
            insets
        }
    }

    fun bind(selected: Int) {
        fragment.requireActivity().window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (numeric == null) {
            fragment.childFragmentManager.beginTransaction().add(numericContainer.id,
                KeyboardSizeDirectInputFragment().apply {
                    arguments = bundleOf("embeddedSizeEditor" to true,
                        "initialOrientation" to if (landscape) "landscape" else "portrait",
                        "initialKeyboardType" to if (isQwerty()) "qwerty" else "tenkey")
                }, NUMERIC_TAG).commitNow()
        }
        numeric?.apply {
            onPreviewValuesChanged = ::applyValues
            onKeyboardTypeChanged = selectQwerty
            onValuesSaved = refreshGui
        }
        if (selected == 1) numeric?.let { selectQwerty(it.isQwertySelected()) }
        else onGuiFamilyChanged()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        splitPreview = FoldSizingPreview(fragment, appPreference, keyboardRepository,
            sumireSpecialKeyRepository, landscape, ::values, ::position,
            onSelectFamily = { qwerty -> selectQwerty(qwerty) },
            onGesture = { qwerty, value, finished ->
                if (isQwerty() != qwerty) selectQwerty(qwerty)
                drafts[qwerty] = value
                numeric?.selectKeyboardType(qwerty)
                numeric?.showPreviewValues(value)
                if (finished) {
                    sizePreferences.save(orientation, if (qwerty) KeyboardSizeKeyboardType.Qwerty else KeyboardSizeKeyboardType.TenKey,
                        value, unfolded)
                }
            })
        previewArea.addView(splitPreview!!.view, FrameLayout.LayoutParams(-1, -1))
        onPostureChanged()
        numeric?.refreshValues()
        if (selected == 1) tabs.getTabAt(1)?.select() else show(0)
        ViewCompat.requestApplyInsets(root)
    }

    private fun show(position: Int) {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(root.windowToken, 0)
        root.requestFocus()
        guiControls.isVisible = position == 0
        numericContainer.isVisible = position == 1
        handles.forEach { it.isVisible = position == 0 && !unfolded }
        splitPreview?.setEditing(position == 0)
        // No reload here: switching lower controls must retain the current numeric draft.
    }

    fun onGuiFamilyChanged() {
        if (numeric?.isQwertySelected() != isQwerty()) numeric?.selectKeyboardType(isQwerty())
    }

    fun reloadValues() { numeric?.refreshValues(); splitPreview?.refresh() }

    fun applyDraft() { numeric?.currentPreviewValues()?.let(::applyValues) }

    private fun applyValues(values: KeyboardSizeValues) {
        drafts[isQwerty()] = values
        splitPreview?.refresh()
        val available = (canvas.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) - canvas.paddingLeft - canvas.paddingRight
        keyboard.layoutParams = (keyboard.layoutParams as ConstraintLayout.LayoutParams).apply {
            height = dp(values.heightDp)
            width = (available * values.widthPercent / 100f).roundToInt()
            bottomMargin = dp(values.bottomMarginDp)
            marginStart = if (isRightAligned()) 0 else dp(values.marginStartDp)
            marginEnd = if (isRightAligned()) dp(values.marginEndDp) else 0
        }
    }

    fun onGuiGeometryChanged() {
        if (unfolded) {
            splitPreview?.refresh()
            return
        }
        val params = keyboard.layoutParams as ConstraintLayout.LayoutParams
        val available = (canvas.width - canvas.paddingLeft - canvas.paddingRight).coerceAtLeast(1)
        numeric?.showPreviewValues(KeyboardSizeValues(
            (params.height / density).roundToInt(),
            ((if (params.width < 0) available else params.width) * 100f / available).roundToInt(),
            (params.bottomMargin / density).roundToInt(),
            (params.marginStart / density).roundToInt(),
            (params.marginEnd / density).roundToInt(),
        ))
    }

    fun onPostureChanged() {
        val next = KeyboardLayoutState.isUnfolded(context.resources.configuration)
        if (unfolded != next) drafts.clear()
        unfolded = next
        viewport.isVisible = !unfolded
        splitPreview?.view?.isVisible = unfolded
        handles.forEach { it.isVisible = !unfolded && selectedTab == 0 }
        splitPreview?.setEditing(selectedTab == 0)
        posture.setText(if (unfolded) R.string.keyboard_size_unfolded else R.string.keyboard_size_folded)
        numeric?.onFoldPostureChanged(unfolded)
        splitPreview?.refresh()
    }
    fun dispose() { fragment.requireActivity().window.setSoftInputMode(previousSoftInputMode) }

    private fun dp(value: Int) = (value * density).roundToInt()
    private companion object { const val NUMERIC_TAG = "keyboard-size-numeric" }
}
