package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

/** The unfolded layout uses the existing service input session and candidate adapters. */
enum class FoldKeyboardLayout {
    SPLIT, LEFT, RIGHT;

    val slots: List<SplitSlot> get() = if (this == SPLIT) SplitSlot.entries else listOf(SplitSlot.MAIN)

    internal fun paneWidth(availableWidth: Int, maximumWidth: Int): Int =
        maximumWidth.coerceAtMost(if (this == SPLIT) availableWidth / 2 else availableWidth).coerceAtLeast(1)

    internal fun paneX(slot: SplitSlot, availableWidth: Int, paneWidth: Int): Int =
        if (this == RIGHT || (this == SPLIT && slot == SplitSlot.SUB)) availableWidth - paneWidth else 0
}
