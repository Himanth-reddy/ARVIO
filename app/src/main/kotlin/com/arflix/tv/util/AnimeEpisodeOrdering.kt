package com.arflix.tv.util

import com.arflix.tv.data.model.AnimeStructuringStyle
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.EpisodeIdentity
import kotlinx.coroutines.CancellationException

internal fun animeEpisodesForDisplay(
    episodes: List<Episode>,
    style: AnimeStructuringStyle,
    isAnime: Boolean,
    displaySeason: Int,
): List<Episode> {
    if (style == AnimeStructuringStyle.STANDARD || !isAnime ||
        episodes.isEmpty() || episodes.first().episodeNumber == 1
    ) return episodes
    return episodes.mapIndexed { index, episode ->
        episode.copy(
            episodeNumber = index + 1,
            seasonNumber = displaySeason,
            identity = EpisodeIdentity(displaySeason, index + 1, episode.seasonNumber, episode.episodeNumber),
        )
    }
}

/** Resolve actual TMDB neighbors, including gaps and season boundaries; never invent an episode. */
internal suspend fun adjacentTmdbEpisodeIdentity(
    current: EpisodeIdentity,
    forward: Boolean,
    loadEpisodes: suspend (Int) -> List<Episode>,
    loadSeasonNumbers: suspend () -> List<Int>,
): EpisodeIdentity? {
    try {
        fun ordered(season: Int, episodes: List<Episode>) = episodes
            .filter { it.tmdbSeasonNumber == season && it.tmdbEpisodeNumber > 0 }
            .sortedBy { it.tmdbEpisodeNumber }
        fun canonical(episode: Episode) = EpisodeIdentity.canonical(episode.tmdbSeasonNumber, episode.tmdbEpisodeNumber)
        val episodes = ordered(current.tmdbSeason, loadEpisodes(current.tmdbSeason))
        if (episodes.none { it.tmdbEpisodeNumber == current.tmdbEpisode }) return null
        val neighbor = if (forward) episodes.firstOrNull { it.tmdbEpisodeNumber > current.tmdbEpisode }
            else episodes.lastOrNull { it.tmdbEpisodeNumber < current.tmdbEpisode }
        if (neighbor != null) return canonical(neighbor)

        val seasons = loadSeasonNumbers().distinct().filter {
            it > 0 && if (forward) it > current.tmdbSeason else it < current.tmdbSeason
        }.sorted().let { if (forward) it else it.reversed() }
        for (season in seasons) {
            val candidates = ordered(season, loadEpisodes(season))
            val next = if (forward) candidates.firstOrNull() else candidates.lastOrNull()
            if (next != null) return canonical(next)
        }
        return null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return null
    }
}
