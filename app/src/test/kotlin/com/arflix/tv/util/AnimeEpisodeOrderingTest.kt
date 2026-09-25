package com.arflix.tv.util

import com.arflix.tv.data.model.AnimeStructuringStyle
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.EpisodeIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AnimeEpisodeOrderingTest {
    private fun episodes(season: Int, vararg numbers: Int) = numbers.map {
        Episode(id = season * 10000 + it, episodeNumber = it, seasonNumber = season, name = "Episode $it")
    }

    private suspend fun adjacent(seasons: Map<Int, List<Episode>>, season: Int, episode: Int, forward: Boolean) =
        adjacentTmdbEpisodeIdentity(EpisodeIdentity.canonical(season, episode), forward,
            loadEpisodes = { seasons[it].orEmpty() }, loadSeasonNumbers = { seasons.keys.toList() })

    @Test fun nextCrossesSeasonBoundary() = runTest {
        val seasons = mapOf(1 to episodes(1, 1, 12), 2 to episodes(2, 1, 2))
        assertEquals(EpisodeIdentity.canonical(2, 1), adjacent(seasons, 1, 12, true))
    }

    @Test fun previousCrossesSeasonBoundary() = runTest {
        val seasons = mapOf(1 to episodes(1, 1, 12), 2 to episodes(2, 1, 2))
        assertEquals(EpisodeIdentity.canonical(1, 12), adjacent(seasons, 2, 1, false))
    }

    @Test fun endsDoNotInventEpisodesOrEnterSpecials() = runTest {
        val seasons = mapOf(0 to episodes(0, 1), 1 to episodes(1, 1, 12))
        assertNull(adjacent(seasons, 1, 12, true))
        assertNull(adjacent(seasons, 1, 1, false))
    }

    @Test fun skipsNumberingGapsAndEmptySeasons() = runTest {
        val seasons = mapOf(1 to episodes(1, 1, 3), 2 to emptyList(), 4 to episodes(4, 9))
        assertEquals(EpisodeIdentity.canonical(1, 3), adjacent(seasons, 1, 1, true))
        assertEquals(EpisodeIdentity.canonical(4, 9), adjacent(seasons, 1, 3, true))
        assertEquals(EpisodeIdentity.canonical(1, 3), adjacent(seasons, 4, 9, false))
    }

    @Test fun standardKeepsAbsoluteNumbersAndCompletionAdvances() = runTest {
        val raw = episodes(22, 1089, 1090)
        val displayed = animeEpisodesForDisplay(raw, AnimeStructuringStyle.STANDARD, true, 22)
        assertSame(raw, displayed)
        assertEquals(1089, displayed.first().episodeNumber)
        assertEquals(EpisodeIdentity.canonical(22, 1090), adjacent(mapOf(22 to raw), 22, 1089, true))
        // The player's completion path compares its displayed episode against this same raw list.
        assertEquals(1090, raw.first { it.episodeNumber > displayed.first().episodeNumber }.episodeNumber)
    }

    @Test fun broadcastRetainsLocalNumberingAndCanonicalIdentity() {
        val displayed = animeEpisodesForDisplay(episodes(22, 1089, 1090), AnimeStructuringStyle.BROADCAST, true, 22)
        assertEquals(listOf(1, 2), displayed.map { it.episodeNumber })
        assertEquals(listOf(1089, 1090), displayed.map { it.tmdbEpisodeNumber })
    }

    @Test fun nonAnimeAndCanonicalBroadcastRemainUnchanged() {
        val raw = episodes(1, 1, 2)
        assertSame(raw, animeEpisodesForDisplay(raw, AnimeStructuringStyle.BROADCAST, true, 1))
        val absolute = episodes(2, 13, 14)
        assertSame(absolute, animeEpisodesForDisplay(absolute, AnimeStructuringStyle.BROADCAST, false, 2))
    }

    @Test fun metadataFailureAndMissingCurrentEpisodeAreSafe() = runTest {
        assertNull(adjacent(mapOf(1 to episodes(1, 1)), 1, 2, true))
        assertNull(adjacentTmdbEpisodeIdentity(EpisodeIdentity.canonical(1, 1), true,
            loadEpisodes = { throw IllegalStateException("offline") }, loadSeasonNumbers = { listOf(1) }))
    }

    @Test fun cancellationIsNotSwallowed() = runTest {
        try {
            adjacentTmdbEpisodeIdentity(EpisodeIdentity.canonical(1, 1), true,
                loadEpisodes = { throw CancellationException("cancelled") }, loadSeasonNumbers = { listOf(1) })
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: the caller can cancel when the player leaves this title.
        }
    }
}
