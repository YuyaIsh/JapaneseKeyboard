package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

/** Docked positions within the normal bottom IME surface. */
enum class FoldKeyboardLayout {
    SPLIT, LEFT, RIGHT;

    val slots: List<SplitSlot> get() = if (this == SPLIT) SplitSlot.entries else listOf(SplitSlot.MAIN)

    internal fun paneWidth(availableWidth: Int, maximumWidth: Int): Int =
        (if (this == SPLIT) availableWidth / 2 else maximumWidth.coerceAtMost(availableWidth)).coerceAtLeast(1)

    internal fun paneX(slot: SplitSlot, availableWidth: Int, paneWidth: Int): Int =
        if (this == RIGHT || (this == SPLIT && slot == SplitSlot.SUB)) availableWidth - paneWidth else 0
}
