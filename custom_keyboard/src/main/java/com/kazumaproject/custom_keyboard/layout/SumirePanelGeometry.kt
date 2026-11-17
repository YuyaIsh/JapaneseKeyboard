package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.GridPlacement

/** Percentages of the available keyboard body, independent of orientation. */
data class SumirePanelSize(val width: Int = 40, val height: Int = 100, val offset: Int = 0)
data class SumirePanels(val left: SumirePanelSize, val right: SumirePanelSize)
data class PanelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/** Keeps every original cell and its order. Never reflects coordinates or key contents. */
class SumirePanelGeometry(
    private val columns: Int,
    private val rows: Int,
    placements: List<GridPlacement>,
    private val panels: SumirePanels,
) {
    // A boundary must never cut a spanning key. Ties keep the earlier original column.
    val boundary: Int? = (1 until columns).filter { column ->
        placements.none { it.columnUnits < column && it.columnUnits + it.columnSpanUnits > column }
    }.minByOrNull { kotlin.math.abs(it * 2 - columns) }

    fun panel(left: Boolean, width: Int, height: Int): PanelBounds {
        val size = if (left) panels.left else panels.right
        // Reserve a center gap even when saved values would otherwise overlap.
        val panelWidth = width * size.width.coerceIn(10, 49) / 100
        // offset is a 0..40 travel percentage across the available half-panel travel,
        // rather than a screen percentage. Every UI value therefore maps distinctly.
        val maxTravel = (width * 49 / 100 - panelWidth).coerceAtLeast(0)
        val offset = maxTravel * size.offset.coerceIn(0, 40) / 40
        val x = if (left) offset else width - offset - panelWidth
        val panelHeight = height * size.height.coerceIn(20, 100) / 100
        return PanelBounds(x, height - panelHeight, x + panelWidth, height)
    }

    fun cell(placement: GridPlacement, width: Int, height: Int): PanelBounds {
        val split = requireNotNull(boundary)
        val left = placement.columnUnits < split
        val bounds = panel(left, width, height)
        val origin = if (left) 0 else split
        val units = if (left) split else columns - split
        fun x(column: Int) = bounds.left + (bounds.right - bounds.left) * (column - origin) / units
        fun y(row: Int) = bounds.top + (bounds.bottom - bounds.top) * row / rows
        return PanelBounds(x(placement.columnUnits), y(placement.rowUnits),
            x(placement.columnUnits + placement.columnSpanUnits), y(placement.rowUnits + placement.rowSpanUnits))
    }
}
