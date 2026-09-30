package com.arflix.tv.ui.screens.player.audiosync.asr

import java.io.File

/**
 * The Play build ships no speech recognition: the audio sync (it downloads a speech model at
 * runtime, which Play does not allow) exists only in the sideload build, and BuildConfig
 * .AUDIO_SYNC_AVAILABLE keeps it from ever starting here. The sideload build has the real one.
 */
@Suppress("UNUSED_PARAMETER")
internal class SherpaSpeechToText(modelDir: File, threads: Int) : SpeechToText, AutoCloseable {
    init {
        throw UnsupportedOperationException("no speech recognition in this build")
    }

    override fun transcribe(samples: FloatArray): List<Pair<Double, String>> = emptyList()

    override fun close() = Unit
}
