package com.arflix.tv.ui.screens.player.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.inspector.FrameExtractor
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "SeekPreview"
private const val PREVIEW_INTERVAL_MS = 10_000L
private const val PREVIEW_WIDTH_PX = 320
private const val PREVIEW_MAX_HEIGHT_PX = 180
private const val DISK_CACHE_LIMIT_BYTES = 96L * 1024L * 1024L
private const val RANGE_CHUNK_BYTES = 512 * 1024
private const val RANGE_MEMORY_LIMIT_BYTES = 4 * 1024 * 1024
private const val MEDIA3_FRAME_TIMEOUT_MS = 2_500L

data class SeekPreviewSource(
    val url: String,
    val headers: Map<String, String>,
    val cacheIdentity: String,
    val durationMs: Long,
    val isLive: Boolean,
    val isAdaptive: Boolean,
)

data class SeekPreviewFrame(
    val bitmap: Bitmap,
    val positionMs: Long,
)

internal fun quantizeSeekPreviewPosition(positionMs: Long, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    if (positionMs >= durationMs) return durationMs
    val clamped = positionMs.coerceIn(0L, durationMs)
    val base = (clamped / PREVIEW_INTERVAL_MS) * PREVIEW_INTERVAL_MS
    val rounded = if (
        clamped - base >= PREVIEW_INTERVAL_MS / 2L &&
        base <= Long.MAX_VALUE - PREVIEW_INTERVAL_MS
    ) {
        base + PREVIEW_INTERVAL_MS
    } else {
        base
    }
    return rounded.coerceAtMost(durationMs)
}

internal fun acceleratedSeekPreviewStepMs(repeatCount: Int): Long = when {
    repeatCount >= 18 -> 60_000L
    repeatCount >= 8 -> 30_000L
    else -> 10_000L
}

internal fun fitSeekPreviewDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
        return maxWidth.coerceAtLeast(1) to maxHeight.coerceAtLeast(1)
    }
    val scale = minOf(
        maxWidth.toFloat() / sourceWidth.toFloat(),
        maxHeight.toFloat() / sourceHeight.toFloat(),
    )
    return (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
        (sourceHeight * scale).roundToInt().coerceAtLeast(1)
}

/**
 * Extracts sparse seek thumbnails on the device. The source URL and credentials never leave the
 * device; only small JPEG previews are retained in the app-private cache directory.
 */
class SeekPreviewFrameProvider(
    context: Context,
    playbackClient: OkHttpClient,
    memoryClassMb: Int,
) : Closeable {
    private val appContext = context.applicationContext
    private val cacheRoot = File(appContext.cacheDir, "seek_previews").apply { mkdirs() }
    private val previewClient = playbackClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "arvio-seek-preview").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val memoryCache = object : LruCache<String, Bitmap>(
        if (memoryClassMb <= 256) 3 * 1024 * 1024 else 8 * 1024 * 1024
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val diskCacheLock = Any()

    private var session: PreviewSession? = null
    @Volatile private var activeCache: ActiveCache? = null
    @Volatile private var closed = false

    init {
        executor.execute { pruneDiskCache() }
    }

    suspend fun configure(source: SeekPreviewSource?) {
        if (closed) return
        val usable = source?.takeIf { candidate ->
            !candidate.isLive &&
                candidate.durationMs > 0L &&
                candidate.url.isNotBlank() &&
                Uri.parse(candidate.url).scheme?.lowercase() in setOf("http", "https", "file", "content")
        }
        activeCache = usable?.let { candidate ->
            ActiveCache(
                keyPrefix = stableHash(candidate.cacheIdentity),
                durationMs = candidate.durationMs,
            )
        }
        withContext(dispatcher) {
            if (closed) return@withContext
            val old = session
            if (usable == null) {
                old?.close()
                session = null
                return@withContext
            }
            val signature = sourceSignature(usable)
            if (old?.signature == signature) {
                old.source = usable
                return@withContext
            }
            old?.close()
            session = PreviewSession(usable, signature)
            Log.i(TAG, "configured adaptive=${usable.isAdaptive} durationMs=${usable.durationMs}")
        }
    }

    /** Reads only app-private cache and never waits behind an active video decoder request. */
    suspend fun cachedFrameAt(positionMs: Long): SeekPreviewFrame? = withContext(Dispatchers.IO) {
        if (closed) return@withContext null
        val cache = activeCache ?: return@withContext null
        val bucket = quantizeSeekPreviewPosition(positionMs, cache.durationMs)
        val frameKey = "${cache.keyPrefix}_$bucket"
        memoryCache.get(frameKey)?.let { bitmap ->
            return@withContext SeekPreviewFrame(bitmap, bucket)
        }
        readDiskFrame(frameKey)?.let { bitmap ->
            memoryCache.put(frameKey, bitmap)
            return@withContext SeekPreviewFrame(bitmap, bucket)
        }
        null
    }

    suspend fun frameAt(positionMs: Long, cacheOnly: Boolean = false): SeekPreviewFrame? =
        withContext(dispatcher) {
            if (closed) return@withContext null
            val active = session ?: return@withContext null
            val bucket = quantizeSeekPreviewPosition(positionMs, active.source.durationMs)
            val frameKey = "${stableHash(active.source.cacheIdentity)}_$bucket"

            memoryCache.get(frameKey)?.let { bitmap ->
                return@withContext SeekPreviewFrame(bitmap, bucket)
            }
            readDiskFrame(frameKey)?.let { bitmap ->
                memoryCache.put(frameKey, bitmap)
                Log.d(TAG, "disk hit positionMs=$bucket")
                return@withContext SeekPreviewFrame(bitmap, bucket)
            }
            if (cacheOnly || System.currentTimeMillis() < active.retryAfterMs) {
                return@withContext null
            }

            val startedAt = System.currentTimeMillis()
            val bitmap = runCatching { active.extract(bucket) }
                .onFailure { failure ->
                    active.failureCount += 1
                    active.retryAfterMs = System.currentTimeMillis() +
                        if (active.failureCount >= 2) 30_000L else 5_000L
                    Log.w(TAG, "frame extraction failed attempt=${active.failureCount}: ${failure.javaClass.simpleName}")
                }
                .getOrNull()
                ?: return@withContext null

            active.failureCount = 0
            active.retryAfterMs = 0L
            memoryCache.put(frameKey, bitmap)
            synchronized(diskCacheLock) { writeDiskFrame(frameKey, bitmap) }
            Log.i(TAG, "frame ready positionMs=$bucket latencyMs=${System.currentTimeMillis() - startedAt}")
            SeekPreviewFrame(bitmap, bucket)
        }

    suspend fun rememberRenderedFrame(positionMs: Long, bitmap: Bitmap): SeekPreviewFrame? =
        withContext(Dispatchers.IO) {
            if (closed) {
                bitmap.recycle()
                return@withContext null
            }
            val cache = activeCache ?: run {
                bitmap.recycle()
                return@withContext null
            }
            val bucket = quantizeSeekPreviewPosition(positionMs, cache.durationMs)
            val frameKey = "${cache.keyPrefix}_$bucket"
            val normalized = normalizeFrame(bitmap)
            memoryCache.put(frameKey, normalized)
            synchronized(diskCacheLock) { writeDiskFrame(frameKey, normalized) }
            SeekPreviewFrame(normalized, bucket)
        }

    override fun close() {
        if (closed) return
        closed = true
        activeCache = null
        executor.execute {
            session?.close()
            session = null
            memoryCache.evictAll()
        }
        dispatcher.close()
        executor.shutdown()
    }

    private data class ActiveCache(
        val keyPrefix: String,
        val durationMs: Long,
    )

    private inner class PreviewSession(
        var source: SeekPreviewSource,
        val signature: String,
    ) : Closeable {
        private var media3Extractor: FrameExtractor? = null
        private var media3Unavailable = false
        private var retriever: MediaMetadataRetriever? = null
        private var rangeSource: HttpRangeMediaDataSource? = null
        private var usingPlatformHttp = false
        var retryAfterMs: Long = 0L
        var failureCount: Int = 0

        fun extract(positionMs: Long): Bitmap {
            // Signed debrid URLs often remain readable without the optional proxy headers. Try
            // Media3 first because it can extract frames from modern 4K/HDR codecs that Android's
            // legacy metadata retriever cannot decode, then retain the authenticated range source
            // below as the fallback for providers that truly require those headers.
            if (!media3Unavailable) {
                runCatching { extractWithMedia3(positionMs) }
                    .onSuccess { return it }
                    .onFailure {
                        media3Unavailable = true
                        media3Extractor?.close()
                        media3Extractor = null
                    }
            }
            val activeRetriever = retriever ?: createRetriever().also { retriever = it }
            return runCatching { extractScaled(activeRetriever, positionMs) }
                .recoverCatching { firstFailure ->
                    if (usingPlatformHttp || source.isAdaptive || !source.url.startsWith("http", ignoreCase = true)) {
                        throw firstFailure
                    }
                    // Some servers expose a seekable URL but reject byte ranges through a custom
                    // MediaDataSource. Android's native retriever is a useful final fallback.
                    retriever?.release()
                    rangeSource?.close()
                    rangeSource = null
                    usingPlatformHttp = true
                    MediaMetadataRetriever().also { fallback ->
                        fallback.setDataSource(source.url, source.headers)
                        retriever = fallback
                    }.let { fallback -> extractScaled(fallback, positionMs) }
                }
                .getOrThrow()
        }

        private fun extractWithMedia3(positionMs: Long): Bitmap {
            val extractor = media3Extractor ?: FrameExtractor.Builder(
                appContext,
                MediaItem.fromUri(source.url),
            )
                .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                .setEffects(listOf(Presentation.createForHeight(PREVIEW_MAX_HEIGHT_PX)))
                .build()
                .also { media3Extractor = it }
            val frame = extractor.getFrame(positionMs)
                .get(MEDIA3_FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            return normalizeFrame(frame.bitmap)
        }

        private fun createRetriever(): MediaMetadataRetriever {
            val uri = Uri.parse(source.url)
            return MediaMetadataRetriever().also { created ->
                when (uri.scheme?.lowercase()) {
                    "file", "content" -> created.setDataSource(appContext, uri)
                    "http", "https" -> {
                        if (source.isAdaptive) {
                            usingPlatformHttp = true
                            created.setDataSource(source.url, source.headers)
                        } else {
                            val randomAccess = HttpRangeMediaDataSource(
                                client = previewClient,
                                url = source.url,
                                headers = source.headers,
                            )
                            rangeSource = randomAccess
                            created.setDataSource(randomAccess)
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported preview source")
                }
            }
        }

        private fun extractScaled(retriever: MediaMetadataRetriever, positionMs: Long): Bitmap {
            val timeUs = positionMs.coerceAtLeast(0L) * 1_000L
            val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: 16
            val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: 9
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
                sourceHeight to sourceWidth
            } else {
                sourceWidth to sourceHeight
            }
            val (targetWidth, targetHeight) = fitSeekPreviewDimensions(
                sourceWidth = displayWidth,
                sourceHeight = displayHeight,
                maxWidth = PREVIEW_WIDTH_PX,
                maxHeight = PREVIEW_MAX_HEIGHT_PX,
            )

            val extracted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: throw IOException("No frame returned")
            return normalizeFrame(extracted, targetWidth, targetHeight)
        }

        override fun close() {
            media3Extractor?.close()
            media3Extractor = null
            retriever?.release()
            retriever = null
            rangeSource?.close()
            rangeSource = null
        }
    }

    private fun normalizeFrame(
        bitmap: Bitmap,
        requestedWidth: Int? = null,
        requestedHeight: Int? = null,
    ): Bitmap {
        val sourceWidth = bitmap.width.coerceAtLeast(1)
        val sourceHeight = bitmap.height.coerceAtLeast(1)
        val scale = minOf(
            PREVIEW_WIDTH_PX.toFloat() / sourceWidth.toFloat(),
            PREVIEW_MAX_HEIGHT_PX.toFloat() / sourceHeight.toFloat(),
            1f,
        )
        val targetWidth = requestedWidth ?: (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = requestedHeight ?: (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }
        return if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false).also { scaled.recycle() }
        } else {
            scaled
        }
    }

    private fun readDiskFrame(key: String): Bitmap? {
        val file = File(cacheRoot, "$key.jpg")
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
            ?.also { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun writeDiskFrame(key: String, bitmap: Bitmap) {
        val target = File(cacheRoot, "$key.jpg")
        if (target.isFile) return
        val temporary = File(cacheRoot, "$key.tmp")
        runCatching {
            temporary.outputStream().buffered().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }.onFailure {
            temporary.delete()
        }
    }

    private fun pruneDiskCache() {
        val files = cacheRoot.listFiles { file -> file.isFile && file.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        var retainedBytes = 0L
        files.forEach { file ->
            retainedBytes += file.length()
            if (retainedBytes > DISK_CACHE_LIMIT_BYTES) file.delete()
        }
        cacheRoot.listFiles { file -> file.extension == "tmp" }
            ?.forEach { file -> file.delete() }
    }
}

private class HttpRangeMediaDataSource(
    private val client: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>,
) : MediaDataSource() {
    private val chunks = LinkedHashMap<Long, ByteArray>(16, 0.75f, true)
    private var cachedBytes = 0
    private var resolvedSize: Long? = null
    private var rangeSupported = true
    @Volatile private var closed = false

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (closed || position < 0L || size <= 0) return -1
        val knownSize = resolvedSize
        if (knownSize != null && position >= knownSize) return -1

        var copied = 0
        var cursor = position
        while (copied < size) {
            val chunkStart = (cursor / RANGE_CHUNK_BYTES) * RANGE_CHUNK_BYTES
            val chunk = synchronized(chunks) { chunks[chunkStart] } ?: fetchChunk(chunkStart) ?: break
            val inChunk = (cursor - chunkStart).toInt()
            if (inChunk >= chunk.size) break
            val count = minOf(size - copied, chunk.size - inChunk)
            System.arraycopy(chunk, inChunk, buffer, offset + copied, count)
            copied += count
            cursor += count
            if (chunk.size < RANGE_CHUNK_BYTES) break
        }
        return if (copied > 0) copied else -1
    }

    override fun getSize(): Long {
        resolvedSize?.let { return it }
        if (closed) return -1L
        val request = Request.Builder()
            .url(url)
            .head()
            .applyHeaders(headers)
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
            }
        }.getOrNull()?.let { length ->
            resolvedSize = length
            return length
        }
        // Unknown length is valid for MediaDataSource; range reads can still discover EOF.
        return -1L
    }

    override fun close() {
        closed = true
        synchronized(chunks) {
            chunks.clear()
            cachedBytes = 0
        }
    }

    private fun fetchChunk(start: Long): ByteArray? {
        if (closed || (!rangeSupported && start > 0L)) return null
        val end = start + RANGE_CHUNK_BYTES - 1L
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=$start-$end")
            .applyHeaders(headers)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 416) {
                    resolvedSize = resolvedSize ?: start
                    return@use null
                }
                if (!response.isSuccessful) return@use null
                val partial = response.code == 206
                if (!partial && start > 0L) {
                    rangeSupported = false
                    return@use null
                }
                response.header("Content-Range")
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.let { resolvedSize = it }
                if (!partial) {
                    response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                        ?.let { resolvedSize = it }
                }
                val body = response.body
                val bytes = ByteArray(RANGE_CHUNK_BYTES)
                var total = 0
                body.byteStream().use { input ->
                    while (total < bytes.size) {
                        val read = input.read(bytes, total, bytes.size - total)
                        if (read < 0) break
                        total += read
                    }
                }
                if (total <= 0) return@use null
                if (total < RANGE_CHUNK_BYTES) {
                    resolvedSize = resolvedSize ?: (start + total)
                }
                bytes.copyOf(total).also { rememberChunk(start, it) }
            }
        }.getOrNull()
    }

    private fun rememberChunk(start: Long, bytes: ByteArray) {
        synchronized(chunks) {
            chunks.put(start, bytes)?.let { previous -> cachedBytes -= previous.size }
            cachedBytes += bytes.size
            val iterator = chunks.entries.iterator()
            while (cachedBytes > RANGE_MEMORY_LIMIT_BYTES && iterator.hasNext()) {
                cachedBytes -= iterator.next().value.size
                iterator.remove()
            }
        }
    }
}

private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
    headers.forEach { (name, value) ->
        if (!name.equals("Range", ignoreCase = true) && name.isNotBlank() && value.isNotBlank()) {
            header(name, value)
        }
    }
}

private fun sourceSignature(source: SeekPreviewSource): String = stableHash(
    buildString {
        append(source.url)
        append('|')
        source.headers.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (name, value) ->
            append(name.lowercase()).append('=').append(value).append(';')
        }
    }
)

private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
    .take(32)
