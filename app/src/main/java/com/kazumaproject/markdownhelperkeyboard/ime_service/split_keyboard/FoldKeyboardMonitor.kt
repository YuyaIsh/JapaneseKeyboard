package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.preference.PreferenceManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/** Tracks the IME display, independently of portrait/landscape configuration. */
internal class FoldKeyboardMonitor(
    private val context: Context,
    private val anchor: View,
    private val onChanged: () -> Unit,
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foldingFeature: FoldingFeature? = null
    private var previous: Pair<Boolean, String?> = false to null
    private var stopped = false
    val expanded: Boolean get() = foldingFeature?.let { feature ->
        isExpanded(feature.state)
    } ?: isExpanded(context)
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { refresh() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SplitKeyboardSettings.FOLD_ENABLED || key == SplitKeyboardSettings.FOLD_LAYOUT) refresh()
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        anchor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        // Extension support for a non-Activity UI context is OEM dependent.
        scope.launch {
            try {
                WindowInfoTracker.getOrCreate(context).windowLayoutInfo(context).collect { info ->
                    foldingFeature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    refresh()
                }
            } catch (unsupported: UnsupportedOperationException) {
                Timber.i(unsupported, "IME folding features unavailable; using display metrics")
            } catch (unsupported: IllegalArgumentException) {
                Timber.i(unsupported, "IME context cannot report folding features; using display metrics")
            }
        }
        anchor.post { refresh() }
    }

    private fun refresh() {
        if (stopped) return
        val enabled = preferences.getBoolean(SplitKeyboardSettings.FOLD_ENABLED, false)
        val next = (enabled && expanded) to if (enabled) preferences.getString(SplitKeyboardSettings.FOLD_LAYOUT, "SPLIT") else null
        if (next == previous) return
        previous = next
        anchor.post { if (!stopped) onChanged() }
    }

    fun stop() {
        stopped = true
        scope.cancel()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        displays.unregisterDisplayListener(displayListener)
        anchor.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
    }

    companion object {
        /** Best effort when a device supplies no hinge data; also matches large tablets. */
        fun isExpanded(context: Context): Boolean {
            val manager = context.getSystemService(WindowManager::class.java)
            val bounds = if (Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds else {
                val size = Point().also { manager.defaultDisplay.getRealSize(it) }
                android.graphics.Rect(0, 0, size.x, size.y)
            }
            return isExpanded(bounds.width(), bounds.height(), context.resources.displayMetrics.density)
        }

        // A book/tabletop posture is physically open too; rotation is irrelevant.
        internal fun isExpanded(state: FoldingFeature.State): Boolean =
            state == FoldingFeature.State.FLAT || state == FoldingFeature.State.HALF_OPENED

        internal fun isExpanded(width: Int, height: Int, density: Float): Boolean =
            minOf(width, height) / density >= 600f
    }
}
