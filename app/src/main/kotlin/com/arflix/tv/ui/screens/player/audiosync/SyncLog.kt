package com.arflix.tv.ui.screens.player.audiosync

import android.util.Log

/**
 * Audio subtitle sync's log. Links are reduced to their host, since stream and subtitle URLs can
 * carry account tokens; the log holds timings and decisions, never audio or text.
 */
internal object SyncLog {
    private const val TAG = "AudioSync"
    private val url = Regex("""https?://([^/\s?#]+)[^\s]*""")

    fun i(message: String) {
        Log.i(TAG, redact(message))
    }

    fun w(message: String) {
        Log.w(TAG, redact(message))
    }

    fun d(message: String) {
        Log.d(TAG, redact(message))
    }

    private fun redact(message: String): String = url.replace(message) { "<${it.groupValues[1]}>" }
}
