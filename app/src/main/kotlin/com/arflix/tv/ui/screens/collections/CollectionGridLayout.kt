package com.arflix.tv.ui.screens.collections

import com.arflix.tv.ui.screens.home.HOME_MOBILE_LANDSCAPE_CARD_WIDTH_DP
import com.arflix.tv.ui.screens.home.HOME_MOBILE_POSTER_CARD_WIDTH_DP
import com.arflix.tv.ui.screens.home.HOME_TV_LANDSCAPE_CARD_WIDTH_DP
import com.arflix.tv.ui.screens.home.HOME_TV_POSTER_CARD_WIDTH_DP

internal data class CollectionGridLayout(val columns: Int, val cardWidthDp: Int)

internal fun collectionGridLayout(screenWidthDp: Int, mobile: Boolean, posters: Boolean): CollectionGridLayout {
    val available = (screenWidthDp - if (mobile) 40 else 84).coerceAtLeast(1)
    val width = when {
        posters && mobile -> HOME_MOBILE_POSTER_CARD_WIDTH_DP
        posters -> HOME_TV_POSTER_CARD_WIDTH_DP
        mobile -> HOME_MOBILE_LANDSCAPE_CARD_WIDTH_DP
        else -> HOME_TV_LANDSCAPE_CARD_WIDTH_DP
    }.coerceAtMost(available)
    return CollectionGridLayout(((available + 12) / (width + 12)).coerceAtLeast(1), width)
}
