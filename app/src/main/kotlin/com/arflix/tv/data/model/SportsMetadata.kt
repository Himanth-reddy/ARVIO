package com.arflix.tv.data.model

import com.google.gson.JsonParser
import kotlin.coroutines.cancellation.CancellationException

data class SportsBroadcaster(val name: String, val country: String, val startsAt: Long)
data class SportsFixture(
    val id: String, val league: String?, val qualifier: String?, val venue: String?, val round: String?,
    val status: String, val observedAt: Long, val homeScore: Int?, val awayScore: Int?,
    val broadcasters: List<SportsBroadcaster>,
)

/** Public metadata DTO; the provider API key exists only in the backend. */
fun parseSportsMetadata(body: String): List<SportsEventArtwork> {
    val root = try {
        JsonParser.parseString(body).asJsonObject
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    } ?: return emptyList()

    val version = try { root.get("version")?.asInt } catch (e: Exception) { if (e is CancellationException) throw e; null }
    if (version != 1) return emptyList()

    val events = try { root.getAsJsonArray("events") } catch (e: Exception) { if (e is CancellationException) throw e; null } ?: return emptyList()
    return events.take(6000).mapNotNull { value ->
        try {
            val item = value.asJsonObject
            fun text(key: String) = item.get(key)?.takeUnless { it.isJsonNull }?.asString
            val title = text("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sport = text("sport")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val start = try { item.get("startsAt")?.asLong?.takeIf { it > 0 } } catch (e: Exception) { if (e is CancellationException) throw e; null } ?: return@mapNotNull null
            val background = safeSportsImage(text("background"))
            val home = safeSportsImage(text("homeBadge"))
            val away = safeSportsImage(text("awayBadge"))
            val catalogueEnabled = try { root.get("catalogueEnabled")?.asBoolean == true } catch (e: Exception) { if (e is CancellationException) throw e; false }

            val fixture = if (catalogueEnabled && text("id")?.matches(idRegex) == true) {
                SportsFixture(
                    id = text("id")!!, league = text("league"), qualifier = text("qualifier"), venue = text("venue"), round = text("round"),
                    status = text("status") ?: "scheduled", observedAt = try { item.get("observedAt")?.asLong ?: 0L } catch (e: Exception) { if (e is CancellationException) throw e; 0L },
                    homeScore = text("homeScore")?.toIntOrNull(), awayScore = text("awayScore")?.toIntOrNull(),
                    broadcasters = try {
                        item.getAsJsonArray("broadcasters")?.take(1500)?.mapNotNull { raw ->
                            try {
                                val b = raw.asJsonObject
                                SportsBroadcaster(b.get("name").asString, b.get("country").asString, b.get("startsAt").asLong)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                null
                            }
                        }.orEmpty()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        emptyList()
                    },
                )
            } else null
            if (fixture == null && background == null && (home == null || away == null)) return@mapNotNull null
            SportsEventArtwork(title, background.orEmpty(), listOf(sport), start,
                homeBadge = if (away != null) home else null, awayBadge = if (home != null) away else null,
                homeTeam = text("homeTeam"), awayTeam = text("awayTeam"), source = "TheSportsDB", fixture = fixture)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}

private val idRegex = Regex("\\d+")
