package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import org.junit.Assert.*
import org.junit.Test

class SumirePanelGeometryTest {
    @Test fun originalCellsKeepTheirOrderAndContentsInBothOrientationsAndAllModes() {
        for (type in listOf("toggle", "flick", "switch-mode-effective")) {
            for (mode in KeyboardInputMode.values()) {
                val layout = KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), type, "default")
                for ((width, height) in listOf(400 to 220, 900 to 220)) {
                    val geometry = SumirePanelGeometry(layout.columnUnitCount, layout.rowUnitCount,
                        layout.items.map { it.placement }, SumirePanels(SumirePanelSize(), SumirePanelSize()))
                    assertNotNull("$type $mode", geometry.boundary)
                    assertTrue(geometry.panel(true, width, height).right < geometry.panel(false, width, height).left)
                    for (item in layout.items) {
                        val cell = geometry.cell(item.placement, width, height)
                        assertTrue(cell.left < cell.right)
                        assertTrue(cell.top < cell.bottom)
                        for (next in layout.items) {
                            val a = item.placement
                            val b = next.placement
                            if (a.columnUnits + a.columnSpanUnits <= b.columnUnits) {
                                assertTrue("Original horizontal order must survive", cell.right <= geometry.cell(b, width, height).left)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun independentSizesOffsetsAndCenterGapAreClampedToEachHalf() {
        val layout = KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA, emptyMap(), "toggle", "default")
        val geometry = SumirePanelGeometry(layout.columnUnitCount, layout.rowUnitCount,
            layout.items.map { it.placement },
            SumirePanels(SumirePanelSize(30, 60, 10), SumirePanelSize(45, 90, 40)))
        assertEquals(PanelBounds(47, 120, 347, 300), geometry.panel(true, 1000, 300))
        assertEquals(PanelBounds(510, 30, 960, 300), geometry.panel(false, 1000, 300))
        val start = SumirePanelGeometry(layout.columnUnitCount, layout.rowUnitCount, layout.items.map { it.placement },
            SumirePanels(SumirePanelSize(40, 100, 0), SumirePanelSize(40, 100, 0)))
        val end = SumirePanelGeometry(layout.columnUnitCount, layout.rowUnitCount, layout.items.map { it.placement },
            SumirePanels(SumirePanelSize(40, 100, 40), SumirePanelSize(40, 100, 40)))
        assertTrue(start.panel(true, 1000, 300).left < end.panel(true, 1000, 300).left)
        assertTrue(start.panel(false, 1000, 300).right > end.panel(false, 1000, 300).right)
        assertFalse(geometry.panel(true, 1000, 300).contains(500f, 200f))
        assertFalse(geometry.panel(false, 1000, 300).contains(500f, 200f))
    }
}
