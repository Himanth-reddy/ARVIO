package com.arflix.tv.data.model

import java.time.Instant

/**
 * IPTV channel parsed from an M3U playlist.
 */
data class IptvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String,
    val logo: String? = null,
    val epgId: String? = null,
    val rawTitle: String = name,
    val xtreamStreamId: Int? = null,
    val catchupType: String? = null,
    val catchupDays: Int = 0,
    val catchupSource: String? = null
)

/**
 * Compact now/next program slice for a channel.
 */
data class IptvNowNext(
    val now: IptvProgram? = null,
    val next: IptvProgram? = null,
    val later: IptvProgram? = null,
    val upcoming: List<IptvProgram> = emptyList(),
    val recent: List<IptvProgram> = emptyList()  // Programs that ended within the past ~60-90 min
)

/**
 * EPG program row.
 */
data class IptvProgram(
    val title: String,
    val description: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long
) {
    fun isLive(atUtcMillis: Long): Boolean = atUtcMillis in startUtcMillis until endUtcMillis
    fun startsInMinutes(atUtcMillis: Long): Long = ((startUtcMillis - atUtcMillis) / 60_000L).coerceAtLeast(0L)
}

/**
 * Loaded IPTV snapshot used by UI.
 */
data class IptvSnapshot(
    val channels: List<IptvChannel> = emptyList(),
    val grouped: Map<String, List<IptvChannel>> = emptyMap(),
    val nowNext: Map<String, IptvNowNext> = emptyMap(),
    val favoriteGroups: List<String> = emptyList(),
    val favoriteChannels: List<String> = emptyList(),
    val hiddenGroups: List<String> = emptyList(),
    val groupOrder: List<String> = emptyList(),
    val epgWarning: String? = null,
    val loadedAt: Instant = Instant.now()
)

fun IptvChannel.supportsCatchup(): Boolean {
    if (streamUrl.contains("/live/") && streamUrl.substringAfter("/live/").contains("/")) {
        return true
    }
    if (!catchupType.isNullOrBlank() || !catchupSource.isNullOrBlank() || catchupDays > 0) {
        return true
    }
    return false
}

fun IptvChannel.getCatchupUrl(program: IptvProgram, nowMillis: Long): String? {
    val xtreamRegex = Regex("""^(https?://[^/]+)/live/([^/]+)/([^/]+)/(\d+)(?:\.[a-zA-Z0-9]+)?$""")
    val match = xtreamRegex.find(streamUrl)
    if (match != null) {
        val host = match.groupValues[1]
        val username = match.groupValues[2]
        val password = match.groupValues[3]
        val streamId = match.groupValues[4]
        val zonedDateTime = java.time.Instant.ofEpochMilli(program.startUtcMillis)
            .atZone(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        val startStr = zonedDateTime.format(formatter)
        return "$host/timeshift.php?username=$username&password=$password&stream=$streamId&start=$startStr"
    }

    val sourceTemplate = catchupSource
    if (!sourceTemplate.isNullOrBlank()) {
        val startSec = program.startUtcMillis / 1000
        val endSec = program.endUtcMillis / 1000
        val nowSec = nowMillis / 1000
        val durationSec = (program.endUtcMillis - program.startUtcMillis) / 1000
        val offsetSec = (nowMillis - program.startUtcMillis) / 1000

        var resolved = sourceTemplate
            .replace("{utc}", startSec.toString())
            .replace("\${start}", startSec.toString())
            .replace("{lutc}", nowSec.toString())
            .replace("\${now}", nowSec.toString())
            .replace("{end}", endSec.toString())
            .replace("\${end}", endSec.toString())
            .replace("{duration}", durationSec.toString())
            .replace("\${duration}", durationSec.toString())
            .replace("{offset}", offsetSec.toString())
            .replace("\${offset}", offsetSec.toString())

        val zonedDateTime = java.time.Instant.ofEpochMilli(program.startUtcMillis)
            .atZone(java.time.ZoneId.systemDefault())
        resolved = resolved
            .replace("{start-year}", zonedDateTime.year.toString())
            .replace("{start-mon}", String.format("%02d", zonedDateTime.monthValue))
            .replace("{start-day}", String.format("%02d", zonedDateTime.dayOfMonth))
            .replace("{start-hour}", String.format("%02d", zonedDateTime.hour))
            .replace("{start-min}", String.format("%02d", zonedDateTime.minute))

        return resolved
    }

    val type = catchupType?.lowercase()
    if (type == "default" || type == "append") {
        val startSec = program.startUtcMillis / 1000
        val nowSec = nowMillis / 1000
        val separator = if (streamUrl.contains("?")) "&" else "?"
        return "${streamUrl}${separator}utc=${startSec}&lutc=${nowSec}"
    }

    return null
}

