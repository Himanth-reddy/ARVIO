package com.arflix.tv.data.model

import com.arflix.tv.data.api.StremioMetaPreview
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import java.time.Instant

/** Artwork only. Addon status/times never overwrite the user's channel schedule. */
data class SportsEventArtwork(val title: String, val background: String, val genres: List<String>,
    val startsAt: Long? = null,
    val homeBadge: String? = null, val awayBadge: String? = null,
    val homeTeam: String? = null, val awayTeam: String? = null,
    val source: String? = null, val fixture: SportsFixture? = null) {
    val key: String = sportsArtworkKey(title)
}

private object SportsArtworkRegexes {
    val marks = Regex("\\p{M}+")
    val livePrefix = Regex("^(live\\s*[:|-]\\s*|live\\s+)")
    val sportPrefix = Regex("^(football|soccer|basketball|baseball|tennis|ice hockey|american football|boxing|mma|cricket)\\s*:\\s*")
    val versus = Regex("\\b(vs\\.?|versus|v\\.)\\s+")
    val punctuation = Regex("[^\\p{L}\\p{N}]+")
    val cosmeticTags = Regex("\\s*[\\[(](?:live|hd|fhd|uhd|4k)[\\])]\\s*", RegexOption.IGNORE_CASE)
    val matchupSeparator = Regex("\\s+(?:vs?\\.?|versus|at|[-–—])\\s+", RegexOption.IGNORE_CASE)
    val qualifierKey = Regex("\\b(women(?:s|'s)?|youth|u\\d{2}|under[ -]?\\d{2})\\b")
    val womenPrefix = Regex("^women.*")
    val hyphenSpace = Regex("[ -]")
}

fun sportsArtworkKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFD)
    .replace(SportsArtworkRegexes.marks, "").lowercase(Locale.ROOT)
    .replace(SportsArtworkRegexes.livePrefix, "").replace(SportsArtworkRegexes.sportPrefix, "")
    .replace(SportsArtworkRegexes.versus, "vs ").replace(SportsArtworkRegexes.punctuation, " ").trim()

/** Only cosmetic title differences are ignored. Age/gender/round qualifiers remain. */
fun sportsEventIdentity(title: String): String {
    val plain = title.replace(SportsArtworkRegexes.cosmeticTags, " ")
    val matchup = plain.substringAfterLast(':').substringBefore(',').trim()
    val normalized = sportsArtworkKey((if (SportsArtworkRegexes.matchupSeparator.containsMatchIn(matchup)) matchup else plain)
        .replace(SportsArtworkRegexes.matchupSeparator, " vs "))
    val sides = normalized.split(" vs ")
    return if (sides.size == 2 && sides.all { it.length >= 3 }) sides.sorted().joinToString(" vs ") else normalized
}

fun sportsQualifierKey(text: String): String = SportsArtworkRegexes.qualifierKey
    .findAll(text.lowercase(Locale.ROOT)).map { it.value.replace(SportsArtworkRegexes.womenPrefix, "women").replace("under", "u").replace(SportsArtworkRegexes.hyphenSpace, "") }
    .toSet().sorted().joinToString("|")

fun safeSportsImage(image: String?): String? = image?.takeIf { it.length <= 2048 && !it.contains("_UTC", true) }?.let {
    val uri = try { URI(it) } catch (e: Exception) { if (e is kotlin.coroutines.cancellation.CancellationException) throw e; null }
    it.takeIf { uri?.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") && !uri?.host.isNullOrBlank() }
}

fun StremioMetaPreview.toSportsEventArtwork(): SportsEventArtwork? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    if (id?.startsWith("leaf:") == true) return null // Channel-recording covers are not match artwork.
    // Posters may contain UTC times. Backgrounds are the addon's untimed landscape assets.
    val image = background?.takeIf { it.isNotBlank() && !it.contains("_UTC", ignoreCase = true) } ?: return null
    val uri = try { URI(image) } catch (e: Exception) { if (e is kotlin.coroutines.cancellation.CancellationException) throw e; null } ?: return null
    if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
    return SportsEventArtwork(title, image, genres.orEmpty(), released?.let { try { Instant.parse(it).toEpochMilli() } catch (e: Exception) { if (e is kotlin.coroutines.cancellation.CancellationException) throw e; null } })
}
