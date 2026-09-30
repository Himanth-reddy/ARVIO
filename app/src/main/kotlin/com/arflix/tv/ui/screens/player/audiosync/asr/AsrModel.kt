package com.arflix.tv.ui.screens.player.audiosync.asr

import android.content.Context
import android.net.ConnectivityManager
import com.arflix.tv.ui.screens.player.audiosync.SpeechModelActions
import com.arflix.tv.ui.screens.player.audiosync.SpeechModelState
import com.arflix.tv.ui.screens.player.audiosync.SubtitleSyncStatus
import com.arflix.tv.ui.screens.player.audiosync.SyncLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The English recognition model (about 74 MB), kept in app storage. It is downloaded the first
 * time the audio sync needs it.
 * Progress and errors are published to [SubtitleSyncStatus.speechModel].
 */
internal object AsrModel {
    const val ENCODER = "encoder.int8.onnx"
    const val DECODER = "decoder.int8.onnx"
    const val JOINER = "joiner.int8.onnx"
    const val TOKENS = "tokens.txt"
    const val DOWNLOAD_MB = 74

    private const val VERSION = "gigaspeech-2023-12-12"
    /** The model's published files (sherpa-onnx, Apache-2.0). */
    private const val SOURCE = "https://huggingface.co/csukuangfj/sherpa-onnx-zipformer-gigaspeech-2023-12-12/resolve/main/"

    /** Local name to (published name, expected size in bytes). */
    private val FILES = listOf(
        (ENCODER to "encoder-epoch-30-avg-1.int8.onnx") to 72_850_738L,
        (DECODER to "decoder-epoch-30-avg-1.int8.onnx") to 540_688L,
        (JOINER to "joiner-epoch-30-avg-1.int8.onnx") to 259_417L,
        (TOKENS to "tokens.txt") to 5_020L,
    )
    private val TOTAL_BYTES = FILES.sumOf { it.second }

    private val downloading = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    /** Registers the Settings actions and publishes the current state. Call once at app start. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        SubtitleSyncStatus.modelActions = object : SpeechModelActions {
            override fun download() {
                val ctx = appContext ?: return
                Thread({ download(ctx) }, "AudioSyncModelDownload").apply { isDaemon = true }.start()
            }

            override fun delete() {
                val ctx = appContext ?: return
                if (downloading.get()) return
                directory(ctx).deleteRecursively()
                publish(ctx)
            }
        }
        publish(context)
    }

    fun directory(context: Context): File = File(context.filesDir, "audiosync/asr/$VERSION")

    fun isReady(context: Context): Boolean {
        val dir = directory(context)
        return FILES.all { (names, size) -> File(dir, names.first).length() == size }
    }

    fun isUnmetered(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return !manager.isActiveNetworkMetered
    }

    private fun publish(context: Context, progress: Float = 0f, error: String? = null) {
        SubtitleSyncStatus.publishSpeechModel(
            SpeechModelState(
                supported = true,
                sizeMb = DOWNLOAD_MB,
                downloaded = isReady(context),
                downloading = downloading.get(),
                progress = progress,
                error = error,
            ),
        )
    }

    /**
     * Downloads missing files. Blocking; call from a background thread. Returns true when the model
     * is ready afterwards.
     */
    fun download(context: Context): Boolean {
        if (isReady(context)) {
            publish(context)
            return true
        }
        if (!downloading.compareAndSet(false, true)) return false
        var error: String? = null
        try {
            publish(context)
            val dir = directory(context).apply { mkdirs() }
            var doneBytes = 0L
            for ((names, size) in FILES) {
                val (local, published) = names
                val file = File(dir, local)
                if (file.length() != size) {
                    val onBytes: (Long) -> Unit = { bytes ->
                        publish(context, progress = (doneBytes + bytes).toFloat() / TOTAL_BYTES)
                    }
                    val failure = fetch(SOURCE + published, file, size, onBytes)
                    if (failure != null) {
                        error = failure
                        return false
                    }
                }
                doneBytes += size
            }
            return isReady(context).also { if (!it) error = "downloaded files are incomplete" }
        } finally {
            downloading.set(false)
            publish(context, error = error)
        }
    }

    /** Returns null on success, otherwise a short reason. */
    private fun fetch(url: String, target: File, expectedSize: Long, onBytes: (Long) -> Unit): String? {
        val partial = File(target.path + ".part")
        return try {
            var connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            var redirects = 0
            // HttpURLConnection does not follow cross-host redirects (GitHub release -> CDN).
            while (connection.responseCode in 300..399 && redirects < 5) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                connection = URL(URL(url), location).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                redirects++
            }
            if (connection.responseCode != 200) {
                val code = connection.responseCode
                connection.disconnect()
                SyncLog.w("model download HTTP $code for $url")
                return "HTTP $code"
            }
            var written = 0L
            var lastReport = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (written - lastReport >= 512 * 1024) {
                            lastReport = written
                            onBytes(written)
                        }
                    }
                }
            }
            connection.disconnect()
            if (partial.length() != expectedSize) {
                partial.delete()
                return "size ${partial.length()} != $expectedSize"
            }
            if (!partial.renameTo(target)) return "could not save file"
            null
        } catch (failure: Exception) {
            SyncLog.w("model download failed for $url: ${failure.message}")
            partial.delete()
            failure.message ?: failure.javaClass.simpleName
        }
    }
}
