package com.arflix.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for [IptvRepository.inferQuality], [IptvRepository.inferQualityFrom]
 * and [IptvRepository.vodQualityRank] (marked `internal` so tests can call them
 * directly, same convention as the Stalker EPG helpers and `activePlaylists`).
 *
 * Root cause: IPTV providers mark their variants of one title with WORDS - a
 * catalog holds "UHD - The Gentlemen", "FHD - The Gentlemen" and "The Gentlemen"
 * side by side. Only the live-TV path (`inferQualityLabel`) ever knew those words;
 * the VOD path matched digits and "4k" alone, so every one of them fell through to
 * the bare "VOD" badge. A tester with three variants of the same episode saw three
 * identical rows in the source list and could not pick one.
 *
 * The second half of the bug is where the marker sits: for a series it is on the
 * SERIES name, while the source row is built from the EPISODE title ("Episode 2"),
 * which names no resolution at all - hence [IptvRepository.inferQualityFrom], which
 * asks the series name first and keeps the episode title as the fallback.
 */
class IptvVodQualityTest {

    @Test fun `provider quality words are read, not just digits`() {
        val repository = newRepository()
        assertEquals("4K", repository.inferQuality("UHD - The Gentlemen (2024)"))
        assertEquals("4K", repository.inferQuality("The Gentlemen 4K"))
        assertEquals("4K", repository.inferQuality("The Gentlemen 2160p"))
        assertEquals("1080p", repository.inferQuality("FHD - The Gentlemen (2024)"))
        assertEquals("1080p", repository.inferQuality("The Gentlemen 1080p"))
        assertEquals("720p", repository.inferQuality("The Gentlemen 720p"))
        assertEquals("480p", repository.inferQuality("The Gentlemen 480p"))
        assertEquals("SD", repository.inferQuality("SD - The Gentlemen"))
    }

    /**
     * "HD" stays "HD" instead of being promoted to a number: providers use it for
     * both 720p and 1080i, so either number would be a guess printed as a fact.
     */
    @Test fun `bare HD is reported as HD and never upgraded to a resolution`() {
        val repository = newRepository()
        assertEquals("HD", repository.inferQuality("HD - The Gentlemen"))
        assertEquals("HD", repository.inferQuality("The Gentlemen [HD]"))
    }

    /**
     * The whole point of the word list is that the more specific token wins.
     * Matching "HD" as a plain substring would read every "FHD"/"UHD" entry as
     * plain HD and undo the fix.
     */
    @Test fun `FHD and UHD are never mistaken for the HD inside them`() {
        val repository = newRepository()
        assertEquals("4K", repository.inferQuality("UHD"))
        assertEquals("1080p", repository.inferQuality("FHD"))
        assertEquals("4K", repository.inferQuality("UHD - FHD Collection"))
    }

    @Test fun `quality words inside a longer word do not count`() {
        val repository = newRepository()
        assertEquals("VOD", repository.inferQuality("Neuhduell"))
        assertEquals("VOD", repository.inferQuality("Sdirekt"))
        assertEquals("VOD", repository.inferQuality("The Gentlemen"))
        assertEquals("VOD", repository.inferQuality(""))
    }

    /**
     * The old digit matcher read "4k" anywhere in the text, so a run-together
     * name still produced a badge. Tightening the word boundaries must not take
     * that away - these four all worked before this change and have to keep
     * working.
     */
    @Test fun `a run-together provider name still resolves`() {
        val repository = newRepository()
        assertEquals("4K", repository.inferQuality("The Gentlemen UHDRemux"))
        assertEquals("4K", repository.inferQuality("The Gentlemen 4KHDR"))
        assertEquals("1080p", repository.inferQuality("The Gentlemen FHDRip"))
        assertEquals("4K", repository.inferQuality("The Gentlemen 4Kremux2160"))
    }

    /**
     * "HDR" is a colour range, not a resolution. It must NOT be read as HD -
     * that would put a wrong fact on screen, and it is exactly why HD and SD
     * keep the strict boundary that 4K/UHD/FHD are allowed to drop.
     */
    @Test fun `HDR is not read as HD`() {
        val repository = newRepository()
        assertEquals("VOD", repository.inferQuality("The Gentlemen HDR"))
        assertEquals("VOD", repository.inferQuality("The Gentlemen HDR10"))
        // ...but a real resolution in the same name still wins.
        assertEquals("4K", repository.inferQuality("The Gentlemen UHD HDR10"))
    }

    /**
     * A letter right in front of a NUMBER must not block it: "1920x1080" is the
     * common way a provider spells a resolution, and the digit-only guard is what
     * keeps it matching.
     */
    @Test fun `a resolution written as a pixel pair is still read`() {
        val repository = newRepository()
        assertEquals("1080p", repository.inferQuality("The Gentlemen 1920x1080"))
        assertEquals("4K", repository.inferQuality("The Gentlemen 3840x2160"))
        // ...but a longer number that merely contains the digits is not a resolution.
        assertEquals("VOD", repository.inferQuality("Episode 10804"))
    }

    @Test fun `the series name is asked first and the episode title is the fallback`() {
        val repository = newRepository()
        // The reported case: marker on the series, nothing on the episode.
        assertEquals(
            "4K",
            repository.inferQualityFrom("UHD - The Gentlemen (2024)", "The Gentlemen (2024) - S02E02 - Episode 2")
        )
        // The other way round: provider labels the episode instead.
        assertEquals(
            "1080p",
            repository.inferQualityFrom("The Gentlemen", "The Gentlemen S02E02 1080p")
        )
        // The series name wins when both say something.
        assertEquals("4K", repository.inferQualityFrom("UHD - The Gentlemen", "The Gentlemen 720p"))
        // Neither says anything: unchanged behaviour, the plain VOD badge.
        assertEquals("VOD", repository.inferQualityFrom("The Gentlemen", "Episode 2"))
        assertEquals("VOD", repository.inferQualityFrom(null, "", "   "))
        assertEquals("VOD", repository.inferQualityFrom())
    }

    /**
     * Sorting has to learn the same words, otherwise a "UHD" entry is recognised
     * in the badge and still sorted to the bottom of the list.
     */
    @Test fun `sorting ranks the word variants in the right order`() {
        val repository = newRepository()
        val ranked = listOf("SD", "UHD", "The Gentlemen", "HD", "FHD", "720p", "480p")
            .sortedByDescending { repository.vodQualityRank(it) }
        assertEquals(listOf("UHD", "FHD", "720p", "HD", "480p", "SD", "The Gentlemen"), ranked)
    }

    private fun newRepository(): IptvRepository {
        val context = io.mockk.mockk<android.content.Context>(relaxed = true)
        val okHttpClient = io.mockk.mockk<okhttp3.OkHttpClient>(relaxed = true)
        val profileManager = io.mockk.mockk<ProfileManager>(relaxed = true)
        val invalidationBus = io.mockk.mockk<CloudSyncInvalidationBus>(relaxed = true)
        return IptvRepository(context, okHttpClient, profileManager, invalidationBus)
    }
}
