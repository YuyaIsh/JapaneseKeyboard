package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.res.Configuration

/**
 * Classifies the current expanded display area, not the device's minimum width.
 * smallestScreenWidthDp is stable across folding and therefore cannot detect posture changes.
 */
object KeyboardLayoutState {
    fun isUnfolded(configuration: Configuration): Boolean =
        configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.smallestScreenWidthDp

    fun preferenceKey(key: String, unfolded: Boolean): String = if (unfolded) "${key}_unfolded" else key
}
