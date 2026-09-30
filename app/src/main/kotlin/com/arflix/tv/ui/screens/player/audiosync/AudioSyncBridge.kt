@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.arflix.tv.ui.screens.player.audiosync

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import com.arflix.tv.ui.screens.player.subtitles.SubtitleSyncMatcher
import java.io.IOException

/**
 * The audio sync's access to subtitles: download and parsing, backed by the subtitle loader and
 * parser the match scan uses.
 */
internal object AudioSyncBridge {

    /** The subtitle's decoded text; throws when it can't be downloaded. */
    suspend fun downloadSubtitleBody(url: String, headers: Map<String, String>): String =
        SubtitleSyncMatcher.loadRaw(url) ?: throw IOException("subtitle download failed: $url")

    /** [raw] parsed into timed cues, one cue per subtitle line group. */
    @Suppress("UNUSED_PARAMETER")
    fun parseCues(raw: String, url: String): List<CuesWithTiming> =
        toCuesWithTiming(SubtitleSyncMatcher.parseCues(raw))

    fun toCuesWithTiming(cues: List<SubtitleSyncMatcher.TimedCue>): List<CuesWithTiming> =
        cues.filter { it.endMs > it.startMs }.map { cue ->
            CuesWithTiming(
                listOf(Cue.Builder().setText(cue.text).build()),
                cue.startMs * 1_000L,
                (cue.endMs - cue.startMs) * 1_000L
            )
        }

    fun toTimedCues(cues: List<CuesWithTiming>): List<SubtitleSyncMatcher.TimedCue> =
        cues.mapNotNull { entry ->
            if (entry.startTimeUs == androidx.media3.common.C.TIME_UNSET) return@mapNotNull null
            val durationUs = when {
                entry.durationUs != androidx.media3.common.C.TIME_UNSET -> entry.durationUs
                entry.endTimeUs != androidx.media3.common.C.TIME_UNSET -> entry.endTimeUs - entry.startTimeUs
                else -> 2_000_000L
            }
            SubtitleSyncMatcher.TimedCue(
                entry.startTimeUs / 1_000L,
                (entry.startTimeUs + durationUs) / 1_000L,
                entry.cues.joinToString("\n") { it.text?.toString().orEmpty() }
            )
        }
}

/** Hearing-impaired markup removal from plain subtitle text. */
internal object SubtitleSdhFilter {
    private val squareBrackets = Regex("\\[[^]]*][ \\t]*")
    // ">>" marks a speaker change and ">>>" a topic change in CEA-608 style captions.
    private val speakerChevrons = Regex("[<>]{2,}[ \t]*")
    private val parentheses = Regex(
        "(?:\\((?=[A-Za-z0-9 '#.,\\\"\\\\\\-\\r\\n]*\\))(?![0-9]*\\))[^)]*\\)|" +
            "（(?=[A-Za-z0-9 '#.,\\\"\\\\\\-\\r\\n]*）)(?![0-9]*）)[^）]*）)[ \\t]*"
    )
    private val speakerLabel = Regex(
        "(?m)^([ \\t]*-[ \\t]*)?(?:[A-Za-z0-9 ()'#.,]+|\\[[^]\\r\\n]*]):(?=\\s|$)[ \\t]*"
    )

    fun filterPlainText(text: String): String? {
        var filtered = speakerChevrons.replace(text, "")
        filtered = speakerLabel.replace(filtered) { match -> match.groups[1]?.value.orEmpty() }
        filtered = squareBrackets.replace(filtered, "")
        filtered = parentheses.replace(filtered, "")
        return filtered.lines()
            .filter { line -> line.any { !it.isWhitespace() && it != '-' } }
            .joinToString("\n")
            .takeIf(String::isNotBlank)
    }
}
