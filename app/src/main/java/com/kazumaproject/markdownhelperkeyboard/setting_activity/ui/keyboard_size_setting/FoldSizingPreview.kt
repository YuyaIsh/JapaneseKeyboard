package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.KeyHitTestMode
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.markdownhelperkeyboard.ime_service.resolveInitialCustomKeyboardSelection
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.ime_service.sumire.SumirePanelSettings
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection.getKeyboardDisplayName
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input.KeyboardSizeValues
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionResolver
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

/** The Fold sizing canvas uses the same dock geometry and keyboard bodies as the IME. */
internal class FoldSizingPreview(
    private val fragment: Fragment,
    private val appPreference: AppPreference,
    private val keyboardRepository: KeyboardRepository,
    private val sumireSpecialKeyRepository: SumireSpecialKeyRepository,
    private val landscape: Boolean,
    private val values: (Boolean) -> KeyboardSizeValues,
    private val positionIsEnd: (Boolean) -> Boolean,
    private val onSelectFamily: (Boolean) -> Unit,
    private val onGesture: (Boolean, KeyboardSizeValues, Boolean) -> Unit,
) {
    private val context = fragment.requireContext()
    private val density = context.resources.displayMetrics.density
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings = SplitKeyboardSettings(preferences)
    private val panes = linkedMapOf<SplitSlot, Pane>()
    private var editing = true
    private val host = FoldKeyboardDockHost(context) { slot ->
        val qwerty = panes[slot]?.qwerty ?: false
        val value = values(qwerty)
        FoldPanePlacement(value.widthPercent, dp(value.bottomMarginDp), positionIsEnd(qwerty),
            dp(value.marginStartDp), dp(value.marginEndDp))
    }
    val previewScale get() = host.scaleX.coerceAtLeast(.01f)
    val view: View = object : ViewGroup(context) {
        init { addView(host) }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val logicalHeight = maxOf(dp(452), panes.values.maxOfOrNull {
                values(it.qwerty).let { value -> dp(value.heightDp + value.bottomMarginDp + 32) }
            } ?: 0)
            val scale = minOf(1f, height.toFloat() / logicalHeight.coerceAtLeast(1)).coerceAtLeast(.01f)
            host.measure(MeasureSpec.makeMeasureSpec((width / scale).roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(logicalHeight, MeasureSpec.EXACTLY))
            setMeasuredDimension(width, height)
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val scale = minOf(1f, height.toFloat() / host.measuredHeight.coerceAtLeast(1))
            host.pivotX = 0f
            host.pivotY = 0f
            host.scaleX = scale
            host.scaleY = scale
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        }
        override fun generateDefaultLayoutParams() = LayoutParams(-1, -1)
    }

    init {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val layouts = withContext(Dispatchers.IO) { keyboardRepository.getLayoutsNotFlowEnsuringStableIds() }
            val regularType = appPreference.keyboard_order.firstOrNull { it != KeyboardType.SPLIT } ?: KeyboardType.TENKEY
            val regularCustom = resolveInitialCustomKeyboardSelection(layouts,
                appPreference.remember_last_custom_keyboard_preference ?: false,
                appPreference.last_used_custom_keyboard_stable_id)?.let { layouts.getOrNull(it.index)?.stableId }.orEmpty()
            val regular = SplitKeyboardSelection(regularType, regularCustom)
            for (slot in SplitSlot.entries) {
                val selection = settings.foldSelection(slot, regular).resolved(layouts.map { it.stableId }.toSet())
                val pane = Pane(slot, selection)
                panes[slot] = pane
                host.addView(pane.surface, FrameLayout.LayoutParams(-1, -1))
                val keyboard: View = try {
                    when (selection.type) {
                        KeyboardType.TENKEY, KeyboardType.SPLIT -> TenKey(context).also { configureTenKeyPreview(context, appPreference, it) }
                        KeyboardType.GOJUON -> GojuonKeyboardView(context).also { configureGojuonPreview(context, appPreference, it) }
                        KeyboardType.QWERTY, KeyboardType.ROMAJI -> QWERTYKeyboardView(context).also {
                            configureQwertyPreview(context, appPreference, it, selection.type == KeyboardType.ROMAJI)
                        }
                        KeyboardType.SUMIRE, KeyboardType.CUSTOM -> FlickKeyboardView(context).also { flick ->
                            configureFlickKeyboardPreview(context, appPreference, flick,
                                if (selection.type == KeyboardType.SUMIRE) appPreference.sumire_keymap_guide_japanese
                                else appPreference.flick_keymap_guide_layout ?: false,
                                selection.type == KeyboardType.SUMIRE)
                            if (selection.type == KeyboardType.SUMIRE) {
                                val actions = withContext(Dispatchers.IO) { sumireSpecialKeyRepository.observeAllActionOverrides().first() }
                                val placements = withContext(Dispatchers.IO) { sumireSpecialKeyRepository.observeAllPlacementOverrides().first() }
                                val mode = KeyboardInputMode.HIRAGANA
                                val layoutType = appPreference.sumire_input_method
                                flick.setSumireSpecialKeyActionResolver(SumireSpecialKeyActionResolver(actions)::resolve, layoutType, mode.name)
                                val configuration = Configuration(context.resources.configuration).apply {
                                    orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                                }
                                flick.setSumirePanels(SumirePanelSettings.load(preferences, configuration, true))
                                flick.setKeyboard(createPreviewSumireKeyboardLayout(context, appPreference, mode, layoutType, actions, placements), KeyHitTestMode.NEAREST_KEY)
                            } else {
                                val selected = layouts.first { it.stableId == selection.customStableId }
                                val layout = withContext(Dispatchers.IO) {
                                    keyboardRepository.convertLayout(keyboardRepository.getFullLayout(selected.layoutId).first())
                                }
                                pane.label.text = "${slot.name} · ${selected.name}"
                                pane.body.setMinimumLayoutSize(dp((layout.columnCount * 24).coerceAtLeast(160)), dp((layout.rowCount * 32).coerceAtLeast(180)))
                                flick.setKeyboard(applyDeleteKeyFlickPreferences(appPreference, layout))
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (selection.type != KeyboardType.CUSTOM) throw failure
                    Timber.w(failure, "Fold sizing custom layout disappeared: %s", selection.customStableId)
                    pane.label.text = "${slot.name} · ${context.getKeyboardDisplayName(KeyboardType.TENKEY)}"
                    TenKey(context).also { configureTenKeyPreview(context, appPreference, it) }
                }
                pane.body.addView(keyboard)
            }
            refresh()
        }
    }

    fun refresh() {
        panes.values.forEach { pane ->
            val value = values(pane.qwerty)
            pane.bodyFrame.layoutParams.height = dp(value.heightDp)
            pane.bodyFrame.requestLayout()
        }
        host.requestLayout()
        view.requestLayout()
    }

    fun setEditing(value: Boolean) {
        editing = value
        panes.values.forEach { pane -> pane.handles.forEach { it.isVisible = value } }
    }

    private inner class Pane(val slot: SplitSlot, val selection: SplitKeyboardSelection) {
        val qwerty = selection.type == KeyboardType.QWERTY || selection.type == KeyboardType.ROMAJI
        val label = TextView(context).apply {
            text = "${slot.name} · ${context.getKeyboardDisplayName(selection.type)}"
            gravity = Gravity.CENTER
            textSize = 18f
            setOnClickListener { onSelectFamily(qwerty) }
        }
        val body = SplitKeyboardBody(context,
            dp(when (selection.type) { KeyboardType.GOJUON -> 360; KeyboardType.SUMIRE -> 200; else -> 240 }), dp(180))
        val bodyFrame = FrameLayout(context).apply {
            contentDescription = "${slot.name} keyboard preview"
            addView(body, FrameLayout.LayoutParams(-1, -1))
        }
        val contents = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, LinearLayout.LayoutParams(-1, dp(32)))
            addView(bodyFrame, LinearLayout.LayoutParams(-1, dp(values(qwerty).heightDp)))
        }
        val surface = FrameLayout(context).apply {
            addView(contents, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        val handles = listOf(
            handle("↕", "${slot.name} resize height", Gravity.TOP or Gravity.CENTER_HORIZONTAL, dp(64), dp(28), "height"),
            handle("↔", "${slot.name} resize width", Gravity.END or Gravity.CENTER_VERTICAL, dp(32), dp(64), "width"),
            handle("✥", "${slot.name} move", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, dp(64), dp(28), "move"),
        )

        @SuppressLint("ClickableViewAccessibility")
        private fun handle(text: String, description: String, gravity: Int, width: Int, height: Int, kind: String): View {
            var startX = 0f
            var startY = 0f
            var initial = values(qwerty)
            return TextView(context).apply {
                this.text = text
                textSize = 22f
                this.gravity = Gravity.CENTER
                contentDescription = description
                setBackgroundColor(0xCC607D8B.toInt())
                isVisible = editing
                bodyFrame.addView(this, FrameLayout.LayoutParams(width, height, gravity))
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            onSelectFamily(qwerty)
                            initial = values(qwerty)
                            startX = event.rawX
                            startY = event.rawY
                            parent.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                            val dx = (event.rawX - startX) / previewScale / density
                            val dy = (event.rawY - startY) / previewScale / density
                            val halfWidthDp = host.width / 2f / density
                            val next = when (kind) {
                                "height" -> initial.copy(heightDp = (initial.heightDp - dy).roundToInt().coerceIn(100, 420))
                                "width" -> initial.copy(widthPercent = (initial.widthPercent + dx * 100 / halfWidthDp.coerceAtLeast(1f)).roundToInt().coerceIn(32, 100))
                                else -> {
                                    val spare = (halfWidthDp * (100 - initial.widthPercent) / 100).roundToInt().coerceAtLeast(0)
                                    initial.copy(bottomMarginDp = (initial.bottomMarginDp - dy).roundToInt().coerceAtLeast(0),
                                        marginStartDp = if (positionIsEnd(qwerty)) initial.marginStartDp else (initial.marginStartDp + dx).roundToInt().coerceIn(0, spare),
                                        marginEndDp = if (!positionIsEnd(qwerty)) initial.marginEndDp else (initial.marginEndDp - dx).roundToInt().coerceIn(0, spare))
                                }
                            }
                            onGesture(qwerty, next, event.actionMasked == MotionEvent.ACTION_UP)
                            refresh()
                        }
                        MotionEvent.ACTION_CANCEL -> { onGesture(qwerty, initial, false); refresh() }
                    }
                    true
                }
            }
        }
    }

    private fun dp(value: Int) = (value * density).roundToInt()
}
