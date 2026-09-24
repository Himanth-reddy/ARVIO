package com.arflix.tv.data.repository

import com.arflix.tv.data.api.StalkerApi
import com.arflix.tv.data.model.PlaylistGroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The category selection a user makes per Stalker portal: which catalog groups
 * a movie or episode lookup is allowed to search.
 *
 * The sieve itself is `internal` on [IptvRepository] so it can be tested
 * without a portal, the same convention the matching helpers next door follow.
 */
class IptvRepositoryStalkerCategoryFilterTest {

    private val portal = "portal-a"

    private fun newRepository(): IptvRepository {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val okHttpClient = io.mockk.mockk<okhttp3.OkHttpClient>(relaxed = true)
        val profileManager = io.mockk.mockk<ProfileManager>(relaxed = true)
        val invalidationBus = io.mockk.mockk<CloudSyncInvalidationBus>(relaxed = true)
        return IptvRepository(context, okHttpClient, profileManager, invalidationBus)
    }

    private fun movie(id: String, categoryId: String?) =
        StalkerApi.StalkerVodItem(id = id, name = "Ted Lasso", cmd = "/media/$id.mpg", categoryId = categoryId)

    private fun show(id: String, categoryId: String?) =
        StalkerApi.StalkerSeriesItem(id = id, name = "Breaking Bad", cmd = "/media/$id", categoryId = categoryId)

    private fun hidden(portalId: String, vararg categoryIds: String) =
        categoryIds.map { PlaylistGroupKey.build(portalId, it) }

    // ── The two deliberate non-filters ────────────────────────────────────

    @Test
    fun `an empty selection searches every category`() {
        val repository = newRepository()
        val items = listOf(movie("1", "958"), movie("2", "486"))

        val filtered = repository.filterStalkerCategorySelection(items, portal, emptyList()) { it.categoryId }

        // Same instance, not just same content: a portal nobody configured must
        // not even pay for a copy of its own answer.
        assertSame(items, filtered)
    }

    @Test
    fun `an entry without a category id is kept`() {
        val repository = newRepository()
        val items = listOf(movie("1", null), movie("2", "  "), movie("3", "486"))

        val filtered = repository.filterStalkerCategorySelection(
            items,
            portal,
            hidden(portal, "486")
        ) { it.categoryId }

        // "Unknown" is not "unwanted": the portal did not say where these
        // belong, and dropping them would hide films nobody deselected.
        assertEquals(listOf("1", "2"), filtered.map { it.id })
    }

    // ── The sieve itself ──────────────────────────────────────────────────

    @Test
    fun `a hidden category is dropped and the rest stays in portal order`() {
        val repository = newRepository()
        val items = listOf(movie("1", "977"), movie("2", "958"), movie("3", "486"), movie("4", "956"))

        val filtered = repository.filterStalkerCategorySelection(
            items,
            portal,
            hidden(portal, "977", "956")
        ) { it.categoryId }

        assertEquals(listOf("2", "3"), filtered.map { it.id })
    }

    @Test
    fun `hiding every category leaves nothing to search`() {
        val repository = newRepository()
        val items = listOf(movie("1", "977"), movie("2", "958"))

        val filtered = repository.filterStalkerCategorySelection(
            items,
            portal,
            hidden(portal, "977", "958")
        ) { it.categoryId }

        // Deliberate, and the same thing hiding every live TV group does: an
        // explicit "none of them" is an answer, unlike an untouched portal.
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `an unknown category id in the selection changes nothing`() {
        val repository = newRepository()
        val items = listOf(movie("1", "977"), movie("2", "958"))

        val filtered = repository.filterStalkerCategorySelection(
            items,
            portal,
            hidden(portal, "does-not-exist")
        ) { it.categoryId }

        assertEquals(listOf("1", "2"), filtered.map { it.id })
    }

    @Test
    fun `one portal never reads another portal's selection`() {
        val repository = newRepository()
        val items = listOf(movie("1", "977"), movie("2", "958"))

        val filtered = repository.filterStalkerCategorySelection(
            items,
            portal,
            hidden("portal-b", "977")
        ) { it.categoryId }

        assertEquals(listOf("1", "2"), filtered.map { it.id })
    }

    @Test
    fun `the series path sieves on the same keys`() {
        val repository = newRepository()
        // Breaking Bad as a portal really lists it: one show entry per
        // language, all with the same name.
        val shows = listOf(show("al", "10"), show("ar", "11"), show("de", "12"), show("en", "13"))

        val filtered = repository.filterStalkerCategorySelection(
            shows,
            portal,
            hidden(portal, "10", "11")
        ) { it.categoryId }

        // This is what the setting is for: the binding cap downstream keeps the
        // first few shows it is handed, so the sieve has to run before it - by
        // then the Albanian and Arabic entries are gone and the cap spends its
        // places on German and English.
        assertEquals(listOf("de", "en"), filtered.map { it.id })
    }

    // ── The cache key ─────────────────────────────────────────────────────

    @Test
    fun `an untouched portal keeps the cache key it always had`() {
        val repository = newRepository()

        assertEquals("", repository.stalkerCategorySelectionTag(portal, emptyList()))
        // A selection that belongs to another portal is not this portal's.
        assertEquals("", repository.stalkerCategorySelectionTag(portal, hidden("portal-b", "977")))
    }

    @Test
    fun `changing the selection changes the cache key`() {
        val repository = newRepository()

        val one = repository.stalkerCategorySelectionTag(portal, hidden(portal, "977"))
        val two = repository.stalkerCategorySelectionTag(portal, hidden(portal, "977", "958"))

        assertNotEquals("", one)
        assertNotEquals(one, two)
    }

    @Test
    fun `the cache key does not depend on the order the categories were hidden in`() {
        val repository = newRepository()

        assertEquals(
            repository.stalkerCategorySelectionTag(portal, hidden(portal, "977", "958")),
            repository.stalkerCategorySelectionTag(portal, hidden(portal, "958", "977"))
        )
    }
}
