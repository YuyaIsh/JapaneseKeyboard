package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardHingeObserversTest {
    @Test fun eachObserverReceivesEveryPostureTransitionEvenAfterAnotherPublishesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = shadowOf(context.getSystemService(SensorManager::class.java))
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_HINGE_ANGLE)
        manager.addSensor(sensor)
        KeyboardLayoutState.publishPosture(false)
        val gui = mutableListOf<Boolean>()
        val ime = mutableListOf<Boolean>()
        val first = KeyboardLayoutState.observeHinge(context, gui::add)
        val second = KeyboardLayoutState.observeHinge(context, ime::add)
        try {
            assertNotNull(first)
            assertNotNull(second)
            assertEquals(listOf(false), gui)
            assertEquals(listOf(false), ime)
            fun send(angle: Float) {
                val event = ShadowSensorManager.createSensorEvent(1).apply {
                    this.sensor = sensor
                    values[0] = angle
                }
                manager.sendSensorEventToListeners(event, sensor)
            }
            send(180f)
            send(175f) // Same posture must not deliver repeated callbacks.
            assertTrue(KeyboardLayoutState.isUnfolded(context.resources.configuration))
            assertEquals(listOf(false, true), gui)
            assertEquals(listOf(false, true), ime)
            send(0f)
            send(20f)
            assertFalse(KeyboardLayoutState.isUnfolded(context.resources.configuration))
            assertEquals(listOf(false, true, false), gui)
            assertEquals(listOf(false, true, false), ime)
        } finally {
            KeyboardLayoutState.stopObserving(context, first)
            KeyboardLayoutState.stopObserving(context, second)
            KeyboardLayoutState.publishPosture(false)
        }
    }
}
