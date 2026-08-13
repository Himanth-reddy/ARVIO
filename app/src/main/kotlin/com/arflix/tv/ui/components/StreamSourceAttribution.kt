package com.arflix.tv.ui.components

import com.arflix.tv.data.model.StreamSource
import java.util.Locale

private object AttributionRegexes {
    val attributionSeparators = Regex("[\\r\\n|\\u2022\\u00B7]+")
    val attributionUrl = Regex("(?i)(?:https?://|magnet:)\\S+")
    val attributionSize = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:TB|GB|MB|KB)\\b")
    val attributionTechnicalToken = Regex(
        """(?i)(?<![\p{L}\p{N}])(?:8K|4K|4320P|2160P|1080P|720P|576P|480P|UHD|FHD|SD|REMUX|BLU[ ._-]?RAY|BDRIP|WEB[ ._-]?DL|WEBRIP|HDTV|CAM|TELESYNC|HEVC|H[ ._-]?265|X265|AV1|AVC|H[ ._-]?264|X264|HDR10\+?|HDR|DOLBY[ ._-]?VISION|DV|ATMOS|TRUEHD|DTS(?:[ ._-]?HD)?|DDP|DD\+|E[ ._-]?AC3|AC3|AAC|7[ .]?1|5[ .]?1)(?![\p{L}\p{N}])"""
    )
    val attributionEmptyWrappers = Regex("""\(\s*\)|\[\s*]|\{\s*\}""")
    val attributionWhitespace = Regex("\\s+")
    val attributionNonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
}

internal fun sourceAttributionLabels(
    stream: StreamSource,
    addonLabel: String
): List<String> {
    val hints = stream.behaviorHints
    val structuredProvider = cleanSourceAttribution(hints?.provider)
    val fallbackProvider = cleanSourceAttribution(stream.rawLabel)
    val provider = structuredProvider ?: fallbackProvider
    val source = cleanSourceAttribution(hints?.sourceLabel)
    val providerCode = cleanSourceAttribution(hints?.providerCode)
    val indexer = cleanSourceAttribution(hints?.indexer)
        ?: cleanSourceAttribution(hints?.indexerCode)

    return buildList {
        addAttributionLabel(provider, addonLabel)

        val sourceIsProviderCode = source != null && providerCode != null &&
            normalizedAttribution(source) == normalizedAttribution(providerCode)
        val sourceIsAbbreviation = source != null && provider != null &&
            source.length <= 3 && source.all { !it.isLetter() || it.isUpperCase() }
        if (!sourceIsProviderCode && !sourceIsAbbreviation) {
            addAttributionLabel(source, addonLabel)
        }

        addAttributionLabel(indexer, addonLabel)
    }.distinctBy(::normalizedAttribution)
}

internal fun cleanSourceAttribution(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw
        .replace(AttributionRegexes.attributionUrl, " ")
        .replace(AttributionRegexes.attributionSeparators, " ")
        .replace(AttributionRegexes.attributionSize, " ")
        .replace(AttributionRegexes.attributionTechnicalToken, " ")
        .replace(AttributionRegexes.attributionEmptyWrappers, " ")
        .replace(AttributionRegexes.attributionWhitespace, " ")
        .trim(' ', '-', '_', '/', ',', ':')
    return cleaned.takeIf { it.length >= 2 }?.take(48)
}

private fun MutableList<String>.addAttributionLabel(label: String?, addonLabel: String) {
    if (label.isNullOrBlank()) return
    val normalizedLabel = normalizedAttribution(label)
    val normalizedAddon = normalizedAttribution(addonLabel)
    if (normalizedLabel.isBlank()) return
    if (normalizedAddon == normalizedLabel || normalizedAddon.contains(normalizedLabel)) return
    if (any { existing ->
            val normalizedExisting = normalizedAttribution(existing)
            normalizedExisting == normalizedLabel ||
                normalizedExisting.contains(normalizedLabel) ||
                normalizedLabel.contains(normalizedExisting)
        }
    ) return
    add(label)
}

private fun normalizedAttribution(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(AttributionRegexes.attributionNonAlphanumeric, "")
