package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.res.Configuration

/** Expanded window classification; uses the current configuration on posture/size changes. */
object KeyboardLayoutState {
    fun isUnfolded(configuration: Configuration): Boolean = configuration.smallestScreenWidthDp >= 600
    fun preferenceKey(key: String, unfolded: Boolean): String = if (unfolded) "${key}_unfolded" else key
}
