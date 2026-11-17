package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_direct_input

import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditConstraints

data class KeyboardSizeValues(
    val heightDp: Int,
    val widthPercent: Int,
    val bottomMarginDp: Int,
    val marginStartDp: Int,
    val marginEndDp: Int,
)

enum class KeyboardSizeOrientation {
    Portrait,
    Landscape,
}

enum class KeyboardSizeKeyboardType {
    TenKey,
    Qwerty,
}

class KeyboardSizePreferenceAccessor(
    private val appPreference: AppPreference,
) {
    fun load(orientation: KeyboardSizeOrientation, keyboardType: KeyboardSizeKeyboardType, unfolded: Boolean = false): KeyboardSizeValues {
        fun read(field: String, default: Int) = appPreference.keyboardSizeInt(key(orientation, keyboardType, field), default, unfolded)
        return KeyboardSizeValues(read("height", DefaultHeightDp), read("width", DefaultWidthPercent),
            read("vertical_margin_bottom", 0), read("margin_start_dp", 0), read("margin_end_dp", 0))
    }

    fun save(orientation: KeyboardSizeOrientation, keyboardType: KeyboardSizeKeyboardType, values: KeyboardSizeValues, unfolded: Boolean = false) {
        val safe = normalize(values)
        listOf("height" to safe.heightDp, "width" to safe.widthPercent,
            "vertical_margin_bottom" to safe.bottomMarginDp, "margin_start_dp" to safe.marginStartDp,
            "margin_end_dp" to safe.marginEndDp).forEach { (field, value) ->
            appPreference.saveKeyboardSizeInt(key(orientation, keyboardType, field), value, unfolded)
        }
    }

    fun reset(orientation: KeyboardSizeOrientation, keyboardType: KeyboardSizeKeyboardType, unfolded: Boolean = false) {
        save(orientation, keyboardType, DefaultValues, unfolded)
        appPreference.saveKeyboardSizePosition(key(orientation, keyboardType, "position"), unfolded)
    }

    private fun key(orientation: KeyboardSizeOrientation, keyboardType: KeyboardSizeKeyboardType, field: String): String {
        val prefix = if (keyboardType == KeyboardSizeKeyboardType.Qwerty) "qwerty_keyboard" else "keyboard"
        val suffix = if (orientation == KeyboardSizeOrientation.Landscape) "_landscape" else ""
        return "${prefix}_${field}${suffix}_preference"
    }

    private fun normalize(values: KeyboardSizeValues): KeyboardSizeValues {
        return values.copy(
            heightDp = values.heightDp.coerceIn(MinHeightDp, MaxHeightDp),
            widthPercent = values.widthPercent.coerceIn(MinWidthPercent, MaxWidthPercent),
            bottomMarginDp = values.bottomMarginDp.coerceAtLeast(MinMarginDp),
            marginStartDp = values.marginStartDp.coerceAtLeast(MinMarginDp),
            marginEndDp = values.marginEndDp.coerceAtLeast(MinMarginDp),
        )
    }

    companion object {
        const val MinHeightDp = KeyboardLayoutEditConstraints.MinHeightDp
        const val MaxHeightDp = KeyboardLayoutEditConstraints.MaxHeightDp
        const val MinWidthPercent = KeyboardLayoutEditConstraints.MinWidthPercent
        const val MaxWidthPercent = KeyboardLayoutEditConstraints.MaxWidthPercent
        private const val MinMarginDp = KeyboardLayoutEditConstraints.MinMarginDp
        private const val DefaultHeightDp = KeyboardLayoutEditConstraints.DefaultHeightDp
        private const val DefaultWidthPercent = KeyboardLayoutEditConstraints.DefaultWidthPercent
        private const val DefaultMarginDp = KeyboardLayoutEditConstraints.DefaultMarginDp

        val DefaultValues = KeyboardSizeValues(
            heightDp = DefaultHeightDp,
            widthPercent = DefaultWidthPercent,
            bottomMarginDp = DefaultMarginDp,
            marginStartDp = DefaultMarginDp,
            marginEndDp = DefaultMarginDp,
        )
    }
}
