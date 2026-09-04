package com.arflix.tv.ui.screens.tv.live

import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.PlaylistGroupKey
import com.arflix.tv.data.repository.IptvConfig
import com.arflix.tv.data.repository.IptvPlaylistEntry
import com.arflix.tv.data.repository.orderXtreamChannelsByProviderCategories
import com.arflix.tv.ui.screens.tv.syncSignature
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveCategoryIndexTest {

    @Test
    fun channelsForKeepsFavoriteOrderAndUsesStaticBuckets() {
        val channels = listOf(
            channel("1", "NL News HD", "NL | News"),
            channel("2", "US Sports 4K", "US | Sports"),
            channel("3", "Kids SD", "Kids"),
        ).mapIndexed { index, channel -> channel.enrich(index + 100) }

        val index = buildCategoryIndex(channels)

        assertThat(index.channelsFor("fav", favorites = listOf("2", "1"), recents = emptyList()).map { it.id })
            .containsExactly("2", "1")
            .inOrder()
        assertThat(index.channelsFor("g-sports", favorites = emptyList(), recents = emptyList()).map { it.id })
            .containsExactly("2")
        assertThat(index.channelsFor("NL-news", favorites = emptyList(), recents = emptyList()).map { it.id })
            .containsExactly("1")
    }

    @Test
    fun channelsForReturnsNewestRecentFirst() {
        val channels = listOf(
            channel("1", "One", "General"),
            channel("2", "Two", "General"),
            channel("3", "Three", "General"),
        ).mapIndexed { index, channel -> channel.enrich(index + 100) }
        val recents = linkedSetOf("1", "3", "2")

        val index = buildCategoryIndex(channels)

        assertThat(index.channelsFor("recent", favorites = emptyList(), recents = recents).map { it.id })
            .containsExactly("2", "3", "1")
            .inOrder()
    }

    @Test
    fun lockedGroupCannotLeakThroughAllFavoritesOrItsCategoryBeforePinUnlock() {
        val channels = listOf(
            channel("list_1:1", "Public", "General"),
            channel("list_1:2", "Restricted", "Premium"),
        ).mapIndexed { index, channel -> channel.enrich(index + 1) }
        val restrictedGroup = PlaylistGroupKey.build("list_1", "Premium")
        val restrictedCategory = playlistGroupCategoryId("list_1", "Premium")

        val lockedIndex = buildCategoryIndex(channels, restrictedGroups = setOf(restrictedGroup))

        assertThat(lockedIndex.channelsFor("all", emptyList(), emptyList()).map { it.id })
            .containsExactly("list_1:1")
        assertThat(lockedIndex.channelsFor("fav", listOf("list_1:2"), emptyList())).isEmpty()
        assertThat(lockedIndex.channelsFor(restrictedCategory, emptyList(), emptyList())).isEmpty()

        val unlockedIndex = buildCategoryIndex(channels)
        assertThat(unlockedIndex.channelsFor(restrictedCategory, emptyList(), emptyList()).map { it.id })
            .containsExactly("list_1:2")
    }

    @Test
    fun normalPagedCategoriesNeverMoveFavoritesAheadOfProviderOrder() {
        val providerWindow = listOf(
            channel("list:1", "Provider First", "News"),
            channel("list:2", "Provider Second", "News"),
            channel("list:3", "Provider Third", "News"),
        )
        val favorites = listOf(providerWindow[2])

        val allChannels = selectPagedChannelsInProviderOrder(
            categoryId = "all",
            providerWindow = providerWindow,
            favoriteChannels = favorites,
            recentChannels = emptyList(),
            limit = 100,
        )
        val providerGroup = selectPagedChannelsInProviderOrder(
            categoryId = "grp:list:news",
            providerWindow = providerWindow,
            favoriteChannels = favorites,
            recentChannels = emptyList(),
            limit = 100,
        )

        assertThat(allChannels.map { it.id }).containsExactly("list:1", "list:2", "list:3").inOrder()
        assertThat(providerGroup.map { it.id }).containsExactly("list:1", "list:2", "list:3").inOrder()
    }

    @Test
    fun favoritesCategoryStillUsesSavedFavoriteOrder() {
        val providerWindow = listOf(
            channel("list:1", "Provider First", "News"),
            channel("list:2", "Provider Second", "News"),
            channel("list:3", "Provider Third", "News"),
        )

        val result = selectPagedChannelsInProviderOrder(
            categoryId = "fav",
            providerWindow = providerWindow,
            favoriteChannels = listOf(providerWindow[2], providerWindow[0]),
            recentChannels = emptyList(),
            limit = 100,
        )

        assertThat(result.map { it.id }).containsExactly("list:3", "list:1").inOrder()
    }

    @Test
    fun pagedChannelSelectionIsDetachedFromMutableBackingList() {
        val providerWindow = MutableList(100) { index ->
            channel("list:$index", "Channel $index", "General")
        }

        val result = selectPagedChannelsInProviderOrder(
            categoryId = "all",
            providerWindow = providerWindow,
            favoriteChannels = emptyList(),
            recentChannels = emptyList(),
            limit = 48,
        )
        providerWindow.clear()

        assertThat(result).hasSize(48)
        assertThat(result.first().id).isEqualTo("list:0")
        assertThat(result.last().id).isEqualTo("list:47")
    }

    @Test
    fun categoryTreeKeepsProviderFirstOccurrenceOrder() {
        val channels = listOf(
            channel("list:9", "Nine", "Z Last alphabetically"),
            channel("list:2", "Two", "A First alphabetically"),
            channel("list:7", "Seven", "Middle"),
            channel("list:8", "Eight", "Z Last alphabetically"),
        )

        val state = buildFastStartupChannelState(
            channels = channels,
            favorites = emptySet(),
            recents = emptySet(),
        )

        assertThat(state.tree.global.categories.map { it.label })
            .containsExactly("Z Last alphabetically", "A First alphabetically", "Middle")
            .inOrder()
    }

    @Test
    fun channelNumberSortUsesProviderNumbersAndKeepsTiesStable() {
        val channels = listOf(
            channel("list:20", "Twenty", "General").copy(providerChannelNumber = "20"),
            channel("list:none", "No number", "General"),
            channel("list:3a", "Three A", "General").copy(providerChannelNumber = "3"),
            channel("list:invalid", "Invalid number", "General").copy(providerChannelNumber = "HD"),
            channel("list:3b", "Three B", "General").copy(providerChannelNumber = "3.0"),
        ).mapIndexed { index, channel -> channel.enrich(index + 100) }

        val result = sortChannelsByConfiguredOrder(channels, "number")

        assertThat(result.map { it.id })
            .containsExactly("list:3a", "list:3b", "list:20", "list:none", "list:invalid")
            .inOrder()
    }

    @Test
    fun providerSortKeepsTheOriginalChannelList() {
        val channels = listOf(
            channel("list:z", "Zulu", "General"),
            channel("list:a", "Alpha", "General"),
        ).mapIndexed { index, channel -> channel.enrich(index + 1) }

        assertThat(sortChannelsByConfiguredOrder(channels, "provider")).isSameInstanceAs(channels)
    }

    @Test
    fun configSignatureChangesWhenPlaylistOrderChanges() {
        val first = IptvPlaylistEntry("first", "First", "https://example.test/first.m3u")
        val second = IptvPlaylistEntry("second", "Second", "https://example.test/second.m3u")

        val original = IptvConfig(playlists = listOf(first, second)).syncSignature()
        val reordered = IptvConfig(playlists = listOf(second, first)).syncSignature()

        assertThat(reordered).isNotEqualTo(original)
    }

    @Test
    fun xtreamCategoryOrderSurvivesTvIndexing() {
        val globalStreamResponse = listOf(
            "news" to channel("xtream:20", "News Twenty", "News").copy(xtreamStreamId = 20),
            "sports" to channel("xtream:30", "Sports Thirty", "Sports").copy(xtreamStreamId = 30),
            "sports" to channel("xtream:10", "Sports Ten", "Sports").copy(xtreamStreamId = 10),
        )
        val merged = orderXtreamChannelsByProviderCategories(
            categoryIdsInProviderOrder = listOf("sports", "news"),
            categorizedChannels = globalStreamResponse,
        )
            .map { it.copy(id = "list_1:${it.id}") }

        val state = buildFastStartupChannelState(
            channels = merged,
            favorites = emptySet(),
            recents = emptySet(),
        )
        val sportsCategory = state.tree.global.categories.single { it.label == "Sports" }

        assertThat(state.all.map { it.id })
            .containsExactly("list_1:xtream:30", "list_1:xtream:10", "list_1:xtream:20")
            .inOrder()
        assertThat(state.tree.global.categories.map { it.label })
            .containsExactly("Sports", "News")
            .inOrder()
        assertThat(state.index.channelsFor(sportsCategory.id, emptyList(), emptyList()).map { it.id })
            .containsExactly("list_1:xtream:30", "list_1:xtream:10")
            .inOrder()
    }

    @Test
    fun stalkerPortalsWithSameGroupRemainSeparateCategories() {
        val state = buildFastStartupChannelState(
            channels = listOf(
                channel("stalker:stalker1:10", "Portal One News", "News"),
                channel("stalker:stalker2:10", "Portal Two News", "News"),
            ),
            favorites = emptySet(),
            recents = emptySet(),
        )

        val newsCategories = state.tree.global.categories.filter { it.label == "News" }
        assertThat(newsCategories.map { it.playlistId })
            .containsExactly("stalker1", "stalker2")
            .inOrder()
        assertThat(state.index.channelsFor(newsCategories[0].id, emptyList(), emptyList()).map { it.id })
            .containsExactly("stalker:stalker1:10")
        assertThat(state.index.channelsFor(newsCategories[1].id, emptyList(), emptyList()).map { it.id })
            .containsExactly("stalker:stalker2:10")
    }

    private fun channel(id: String, name: String, group: String): IptvChannel =
        IptvChannel(
            id = id,
            name = name,
            streamUrl = "https://example.test/$id.m3u8",
            group = group,
        )
}
