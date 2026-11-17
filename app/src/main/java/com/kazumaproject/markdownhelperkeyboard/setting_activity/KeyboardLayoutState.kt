package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Fold posture comes only from a physical hinge-angle sensor; unavailable means folded. */
object KeyboardLayoutState {
    private const val FLAT_HINGE_DEGREES = 165f
    @Volatile private var unfolded = false

    fun isUnfolded(@Suppress("UNUSED_PARAMETER") configuration: Configuration): Boolean = unfolded
    fun isUnfoldedHingeAngle(angleDegrees: Float?): Boolean = angleDegrees != null && angleDegrees >= FLAT_HINGE_DEGREES
    fun preferenceKey(key: String, unfolded: Boolean): String = if (unfolded) "${key}_unfolded" else key

    @Synchronized
    fun publishPosture(next: Boolean, onPostureChanged: (Boolean) -> Unit = {}) {
        if (unfolded == next) return
        unfolded = next
        onPostureChanged(next)
    }

    fun observeHinge(context: Context, onPostureChanged: (Boolean) -> Unit = {}): SensorEventListener? {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (manager == null || sensor == null) {
            publishPosture(false, onPostureChanged)
            onPostureChanged(false) // Notify this observer even if folded was already current.
            return null
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                publishPosture(isUnfoldedHingeAngle(event.values.firstOrNull()), onPostureChanged)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            publishPosture(false, onPostureChanged)
            onPostureChanged(false)
            return null
        }
        // Deliver the last known posture immediately; registration never resets shared state.
        onPostureChanged(unfolded)
        return listener
    }

    fun stopObserving(context: Context, listener: SensorEventListener?) {
        if (listener != null) (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(listener)
    }
}
