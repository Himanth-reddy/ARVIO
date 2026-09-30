package com.arflix.tv.ui.screens.player.audiosync.asr

import java.io.File

/**
 * The initial audio-sync rollout is limited to sideload builds. AUDIO_SYNC_AVAILABLE keeps
 * recognition disabled here and the Play artifact does not include the native speech engine.
 */
@Suppress("UNUSED_PARAMETER")
internal class SherpaSpeechToText(modelDir: File, threads: Int) : SpeechToText, AutoCloseable {
    init {
        throw UnsupportedOperationException("no speech recognition in this build")
    }

    override fun transcribe(samples: FloatArray): List<Pair<Double, String>> = emptyList()

    override fun close() = Unit
}
