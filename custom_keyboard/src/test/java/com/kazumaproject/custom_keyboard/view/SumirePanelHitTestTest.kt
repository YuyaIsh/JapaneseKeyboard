package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.layout.SumirePanels
import com.kazumaproject.custom_keyboard.layout.SumirePanelSize
import com.kazumaproject.custom_keyboard.layout.SumirePanelGeometry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SumirePanelHitTestTest {
    @Test fun resizedChildrenAreTouchTargetsAndGapDoesNotTypeInEitherOrientation() {
        val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val keyboard = FlickKeyboardView(context)
        val definition = KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA, emptyMap(), "toggle", "default")
        keyboard.setKeyboard(definition, KeyHitTestMode.NEAREST_KEY)
        keyboard.setSumirePanels(SumirePanels(SumirePanelSize(30, 60, 5), SumirePanelSize(40, 90, 3)))
        val findTarget = FlickKeyboardView::class.java.getDeclaredMethod("findTargetView", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
        for (width in listOf(400, 900)) {
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, width, 300)
            val surfaceLocation = IntArray(2).also(keyboard::getLocationOnScreen)
            assertNull("The center gap is not an input target", findTarget.invoke(keyboard, surfaceLocation[0] + width / 2f, surfaceLocation[1] + 250f))
            assertNull("The outer offset is not an input target", findTarget.invoke(keyboard, surfaceLocation[0], surfaceLocation[1] + 250f))
            val geometry = SumirePanelGeometry(definition.columnUnitCount, definition.rowUnitCount,
                definition.items.map { it.placement }, SumirePanels(SumirePanelSize(30, 60, 5), SumirePanelSize(40, 90, 3)))
            val keyInfosField = FlickKeyboardView::class.java.getDeclaredField("keyInfos").apply { isAccessible = true }
            val keyInfoCount = (keyInfosField.get(keyboard) as List<*>).size
            for (i in definition.items.indices) {
                val child = keyboard.getChildAt(i)
                assertTrue(child.width > 0)
                // View.getLocationOnScreen is zeroed for this unattached Robolectric view;
                // real dispatch uses the surface screen origin plus child-local bounds.
                val centerX = surfaceLocation[0] + child.left + child.width / 2f
                val centerY = surfaceLocation[1] + child.top + child.height / 2f
                val location = intArrayOf(surfaceLocation[0] + child.left, surfaceLocation[1] + child.top)
                val target = findTarget.invoke(keyboard, centerX, centerY)
                when (definition.items[i]) {
                    is KeyItem -> {
                        val bounds = geometry.cell(definition.items[i].placement, width, 300)
                        val diagnostic = "keyIndex=$i child=(left=${child.left},top=${child.top},width=${child.width},height=${child.height}) " +
                            "screen=(${location[0]},${location[1]}) center=($centerX,$centerY) " +
                            "panelLeft=${geometry.panel(true, width, 300)} panelRight=${geometry.panel(false, width, 300)} " +
                            "cell=$bounds keyInfos=$keyInfoCount target=$target"
                        assertNotNull("Key at index $i must be touchable at its rendered center; $diagnostic", target)
                        val viewField = target!!.javaClass.getDeclaredField("view").apply { isAccessible = true }
                        assertSame("Hit target must match the rendered key at index $i", child, viewField.get(target))
                    }
                    is SpacerItem -> assertNull("Spacer at index $i is not an input target", target)
                }
            }
        }
        keyboard.setKeyboard(definition) // Switching to custom/bounds mode clears the split geometry.
        val panels = FlickKeyboardView::class.java.getDeclaredField("sumirePanels").apply { isAccessible = true }
        assertNull(panels.get(keyboard))
    }
}
