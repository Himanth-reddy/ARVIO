package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import org.junit.Assert.*
import org.junit.Test

class HomeCatalogRowsTest {
    private fun row(ids: IntRange, id: String = "custom") = Category(id, id, ids.map { MediaItem(it, "$it") })

    @Test fun `late initial result preserves appended page`() {
        assertEquals(28, preserveExtendedCatalogRows(listOf(row(1..8)), listOf(row(1..28))).single().items.size)
    }

    @Test fun `changed provider order and continue watching are refreshed`() {
        assertEquals(8, preserveExtendedCatalogRows(listOf(row(2..9)), listOf(row(1..28))).single().items.size)
        assertEquals(8, preserveExtendedCatalogRows(listOf(row(1..8, "continue_watching")), listOf(row(1..28, "continue_watching"))).single().items.size)
    }

    @Test fun `late placeholder does not erase a loaded deferred catalogue`() {
        assertEquals(20, preserveExtendedCatalogRows(listOf(row(1..0)), listOf(row(1..20))).single().items.size)
    }

    @Test fun `catalogue refresh keeps a continue watching row it does not carry`() {
        val result = preserveExtendedCatalogRows(
            incoming = listOf(row(1..8)),
            current = listOf(row(1..3, "continue_watching"), row(1..8))
        )
        assertEquals(listOf("continue_watching", "custom"), result.map { it.id })
        assertEquals(3, result.first().items.size)
    }

    @Test fun `an empty continue watching row is not resurrected`() {
        val result = preserveExtendedCatalogRows(
            incoming = listOf(row(1..8)),
            current = listOf(row(1..0, "continue_watching"), row(1..8))
        )
        assertEquals(listOf("custom"), result.map { it.id })
    }
}
