package androidx.media3.decoder.ffmpeg

import androidx.media3.common.Format

/**
 * The Play flavor ships no FFmpeg extension: the audio sync never gets a software decoder here and
 * relies on the device's own (MediaCodec) decoders, or on the player's decoded audio.
 * The sideload flavor has the real one (src/sideload).
 */
@Suppress("UNUSED_PARAMETER")
internal class AudioSyncFfmpegDecoder(format: Format) {
    init {
        throw UnsupportedOperationException("no FFmpeg audio decoder in this build")
    }

    val name: String get() = ""
    val channelCount: Int get() = 0
    val sampleRate: Int get() = 0

    fun queue(data: ByteArray, timeUs: Long): Boolean = false

    fun drain(onPcm: (java.nio.ByteBuffer, Long) -> Unit) = Unit

    fun flush() = Unit

    fun release() = Unit

    companion object {
        fun supports(mimeType: String): Boolean = false
    }
}
