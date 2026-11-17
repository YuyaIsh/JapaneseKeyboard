package com.kazumaproject.markdownhelperkeyboard.ime_service.sumire

import android.content.SharedPreferences
import android.content.res.Configuration
import com.kazumaproject.custom_keyboard.layout.SumirePanels
import com.kazumaproject.custom_keyboard.layout.SumirePanelSize

object SumirePanelSettings {
    fun prefix(unfolded: Boolean, landscape: Boolean) =
        "sumire_panels_${if (unfolded) "unfolded" else "folded"}_${if (landscape) "landscape" else "portrait"}"

    fun load(prefs: SharedPreferences, configuration: Configuration, unfolded: Boolean): SumirePanels? {
        val prefix = prefix(unfolded,
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        if (!prefs.getBoolean("${prefix}_enabled", false)) return null
        fun side(name: String) = SumirePanelSize(
            prefs.getInt("${prefix}_${name}_width", 40),
            prefs.getInt("${prefix}_${name}_height", 100),
            prefs.getInt("${prefix}_${name}_offset", 0))
        val left = side("left")
        return SumirePanels(left, if (prefs.getBoolean("${prefix}_symmetric", true)) left else side("right"))
    }
}
