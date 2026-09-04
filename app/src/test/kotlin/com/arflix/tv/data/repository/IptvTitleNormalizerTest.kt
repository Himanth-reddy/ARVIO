package com.arflix.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the title normalization used for Xtream VOD/series matching.
 *
 * The reported bug (#398) was that every letter outside a-z was replaced by a
 * space, which split the word apart instead of transliterating it: "Doğu" became
 * "do u" and never matched the provider catalog again.
 */
class IptvTitleNormalizerTest {

    @Test
    fun `letters unicode can decompose are transliterated instead of dropped`() {
        assertEquals("dogu", IptvTitleNormalizer.normalize("Doğu"))
        assertEquals("cukur", IptvTitleNormalizer.normalize("Çukur"))
        assertEquals("amelie", IptvTitleNormalizer.normalize("Amélie"))
        assertEquals("morderische spiele", IptvTitleNormalizer.normalize("Mörderische Spiele"))
        assertEquals("fur alle falle", IptvTitleNormalizer.normalize("Für alle Fälle"))
        assertEquals("lodz", IptvTitleNormalizer.normalize("Łódź"))
    }

    @Test
    fun `letters without a unicode decomposition are transliterated too`() {
        // NFD alone leaves these intact, so the a-z filter would still drop them.
        assertEquals("strasse der traume", IptvTitleNormalizer.normalize("Straße der Träume"))
        assertEquals("weissensee", IptvTitleNormalizer.normalize("Weißensee"))
        assertEquals("die grosste show", IptvTitleNormalizer.normalize("Die Größte Show"))
        // The dotless "ı" from the issue report's own example.
        assertEquals("kirmizi oda", IptvTitleNormalizer.normalize("Kırmızı Oda"))
        assertEquals("odegaard", IptvTitleNormalizer.normalize("Ødegaard"))
        assertEquals("aeon flux", IptvTitleNormalizer.normalize("Æon Flux"))
    }

    @Test
    fun `every non decomposable letter has an ascii replacement in both cases`() {
        assertEquals("ss ss", IptvTitleNormalizer.normalize("ß ẞ"))
        assertEquals("i i", IptvTitleNormalizer.normalize("ı İ"))
        assertEquals("o o", IptvTitleNormalizer.normalize("ø Ø"))
        assertEquals("ae ae", IptvTitleNormalizer.normalize("æ Æ"))
        assertEquals("oe oe", IptvTitleNormalizer.normalize("œ Œ"))
        assertEquals("d d", IptvTitleNormalizer.normalize("đ Đ"))
        assertEquals("d d", IptvTitleNormalizer.normalize("ð Ð"))
        assertEquals("l l", IptvTitleNormalizer.normalize("ł Ł"))
        assertEquals("th th", IptvTitleNormalizer.normalize("þ Þ"))
    }

    @Test
    fun `plain ascii titles keep the previous normalization`() {
        assertEquals("breaking bad", IptvTitleNormalizer.normalize("Breaking Bad"))
        assertEquals("the walking dead s01e05", IptvTitleNormalizer.normalize("The Walking Dead S01E05"))
        assertEquals("game of thrones", IptvTitleNormalizer.normalize("Game of Thrones (2011)"))
        assertEquals("law order svu", IptvTitleNormalizer.normalize("Law & Order: SVU"))
        assertEquals("marvel s agents of s h i e l d", IptvTitleNormalizer.normalize("Marvel's Agents of S.H.I.E.L.D."))
        assertEquals("stranger things", IptvTitleNormalizer.normalize("Stranger Things [Multi]"))
        assertEquals("the office", IptvTitleNormalizer.normalize("The Office 1080p WEB-DL"))
        assertEquals("dark", IptvTitleNormalizer.normalize("  Dark  "))
    }

    @Test
    fun `letter folding leaves ascii input untouched`() {
        listOf(
            "Breaking Bad",
            "The Walking Dead S01E05",
            "Game of Thrones (2011)",
            "Law & Order: SVU",
            "Marvel's Agents of S.H.I.E.L.D.",
            "Stranger Things [Multi]"
        ).forEach { title ->
            assertEquals(title, IptvTitleNormalizer.foldLetters(title))
        }
    }

    @Test
    fun `season episode and release markers are still stripped`() {
        assertEquals("mord mit aussicht", IptvTitleNormalizer.normalize("Mord mit Aussicht S02 1080p"))
        assertEquals("tatort", IptvTitleNormalizer.normalize("Tatort [DE] Season 3 Episode 12 WEB-DL"))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", IptvTitleNormalizer.normalize(""))
        assertEquals("", IptvTitleNormalizer.normalize("   "))
    }

    @Test
    fun `transcribed umlauts collapse onto the transliterated spelling`() {
        // Providers write "Für" either as "Fur" or as "Fuer". The transliterated
        // form is what normalize() produces, so the transcribed one gets folded
        // onto it to give catalog entries a second index key.
        val query = IptvTitleNormalizer.normalize("Für alle Fälle")
        val transliterated = IptvTitleNormalizer.normalize("Fur alle Falle")
        val transcribed = IptvTitleNormalizer.normalize("Fuer alle Faelle")

        assertEquals(query, transliterated)
        assertEquals(query, IptvTitleNormalizer.foldUmlautTranscription(transcribed))
        assertEquals(query, IptvTitleNormalizer.foldUmlautTranscription(transliterated))
    }

    @Test
    fun `umlaut folding only rewrites strings that carry a transcription`() {
        listOf("breaking bad", "dark", "tatort", "das boot").forEach { title ->
            assertEquals(title, IptvTitleNormalizer.foldUmlautTranscription(title))
        }
        // Documents why this fold is applied to catalog keys only, never to a
        // query: it would rewrite plain ASCII words as well.
        assertEquals("blu velvet", IptvTitleNormalizer.foldUmlautTranscription("blue velvet"))
        assertTrue(IptvTitleNormalizer.foldUmlautTranscription("goethe") == "gothe")
    }

    @Test
    fun `folding is idempotent so aliasing an alias changes nothing`() {
        val once = IptvTitleNormalizer.foldUmlautTranscription("fuer alle faelle")
        assertEquals(once, IptvTitleNormalizer.foldUmlautTranscription(once))
    }
}
