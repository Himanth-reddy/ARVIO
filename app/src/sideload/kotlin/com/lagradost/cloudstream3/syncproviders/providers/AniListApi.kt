@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

open class AniListApi : SyncAPI(
    name = "AniList",
    key = "anilist",
    mainUrl = "https://anilist.co",
    iconUrl = null,
    requiresLogin = true,
    syncIdName = SyncIdName.AniList
) {
    data class Title(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
        val userPreferred: String? = null
    )

    data class CoverImage(
        val extraLarge: String? = null,
        val large: String? = null,
        val medium: String? = null,
        val color: String? = null
    )

    data class MediaCoverImage(
        val extraLarge: String? = null,
        val large: String? = null,
        val medium: String? = null
    )

    data class MediaTitle(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null
    )

    data class SeasonNextAiringEpisode(
        val episode: Int? = null,
        val airingAt: Long? = null,
        val timeUntilAiring: Long? = null
    )

    data class RecommendationConnection(
        val nodes: List<Recommendation>? = null,
        val edges: List<RecommendationEdge>? = null
    )

    data class RecommendationEdge(
        val node: Recommendation? = null
    )

    data class Recommendation(
        val mediaRecommendation: RecommendedMedia? = null
    )

    data class RecommendedMedia(
        val id: Int? = null,
        val title: Title? = null,
        val coverImage: CoverImage? = null
    )

    data class LikePageInfo(
        val total: Int? = null,
        val perPage: Int? = null,
        val currentPage: Int? = null,
        val lastPage: Int? = null,
        val hasNextPage: Boolean? = null
    )
}
