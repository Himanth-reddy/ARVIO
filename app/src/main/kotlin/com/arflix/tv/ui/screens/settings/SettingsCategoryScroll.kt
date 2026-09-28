package com.arflix.tv.ui.screens.settings

internal fun settingsCategoryScrollDelta(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
    center: Boolean
): Float {
    val viewportSize = (viewportEnd - viewportStart).coerceAtLeast(0)
    val top = itemOffset - viewportStart
    return if (center && itemSize < viewportSize) {
        top - (viewportSize - itemSize) / 2f
    } else {
        com.arflix.tv.ui.focus.focusRevealDelta(
            top.toFloat(), (top + itemSize).toFloat(), viewportSize.toFloat()
        )
    }
}
