package com.arflix.tv.util

import android.app.Activity
import android.media.MediaExtractor
import android.net.Uri
import android.os.Build
import android.view.Display
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.WeakHashMap

/**
 * Auto frame rate matching utility.
 * Switches the display refresh rate to match the video frame rate for judder-free playback.
 * Playback uses decoder metadata; the legacy extractor helpers are not on its hot path.
 */
object FrameRateUtils {

    private const val SWITCH_TIMEOUT_MS = 4000L
    private const val NTSC_FILM_FPS = 24000f / 1001f
    private const val CINEMA_24_FPS = 24f
    private const val MIN_VALID_FPS = 10f
    private const val MAX_VALID_FPS = 120f
    private const val POLL_INTERVAL_MS = 60L
    private const val STABLE_POLLS_REQUIRED = 2
    private const val DETECTION_CACHE_TTL_MS = 15 * 60_000L

    // Accessed on the main thread; never restore another activity's window preference.
    private val originalModeIds = WeakHashMap<Window, Int>()
    private val detectionCache = ConcurrentHashMap<String, CachedDetection>()

    data class FrameRateDetection(
        val raw: Float,
        val snapped: Float
    )

    private data class CachedDetection(
        val detection: FrameRateDetection,
        val createdAtMs: Long
    )

    private fun detectionCacheKey(sourceUrl: String): String {
        val uri = runCatching { Uri.parse(sourceUrl) }.getOrNull() ?: return sourceUrl.substringBefore('?')
        val scheme = uri.scheme.orEmpty()
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        return if (scheme.isNotBlank() && host.isNotBlank()) {
            "$scheme://$host$path"
        } else {
            sourceUrl.substringBefore('?')
        }
    }

    fun snapToStandardRate(fps: Float): Float {
        if (fps <= 0f) return fps
        return when {
            fps in 23.90f..23.988f -> NTSC_FILM_FPS
            fps in 23.988f..24.1f -> CINEMA_24_FPS
            fps in 24.9f..25.1f -> 25f
            fps in 29.90f..29.985f -> 30000f / 1001f
            fps in 29.985f..30.1f -> 30f
            fps in 49.9f..50.1f -> 50f
            fps in 59.9f..59.97f -> 60000f / 1001f
            fps in 59.97f..60.1f -> 60f
            else -> fps
        }
    }

    /**
     * Detect the video frame rate from a stream URL using MediaExtractor.
     * Returns null if detection fails or the URL is a live stream.
     */
    fun detectFrameRate(sourceUrl: String, headers: Map<String, String> = emptyMap()): FrameRateDetection? {
        val lower = sourceUrl.substringBefore('?').lowercase()
        if (lower.endsWith(".m3u8") || lower.contains("/hls") || lower.endsWith(".mpd")) return null

        val extractor = MediaExtractor()
        return try {
            val uri = Uri.parse(sourceUrl)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> extractor.setDataSource(sourceUrl, headers)
                else -> return null
            }

            var videoFormat: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoFormat = format
                    extractor.selectTrack(i)
                    break
                }
            }
            if (videoFormat == null) return null

            // Try declared frame rate first
            val declared = videoFormat
                .takeIf { it.containsKey(android.media.MediaFormat.KEY_FRAME_RATE) }
                ?.runCatching { getFloat(android.media.MediaFormat.KEY_FRAME_RATE) }
                ?.getOrNull()
            if (declared != null && declared in MIN_VALID_FPS..MAX_VALID_FPS) {
                return FrameRateDetection(raw = declared, snapped = snapToStandardRate(declared))
            }

            // Fall back to sample-based measurement
            val timestamps = ArrayList<Long>(400)
            while (timestamps.size < 350) {
                val ts = extractor.sampleTime
                if (ts < 0) break
                timestamps.add(ts)
                if (!extractor.advance()) break
            }
            if (timestamps.size < 34) return null

            val skip = 3
            var total = 0L
            for (i in (skip + 1) until timestamps.size) {
                total += (timestamps[i] - timestamps[i - 1])
            }
            val count = (timestamps.size - skip - 1).coerceAtLeast(1)
            val avgDuration = total.toFloat() / count
            if (avgDuration <= 0f) return null

            val measured = 1_000_000f / avgDuration
            if (measured !in MIN_VALID_FPS..MAX_VALID_FPS) return null

            FrameRateDetection(raw = measured, snapped = snapToStandardRate(measured))
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun detectFrameRateCached(sourceUrl: String, headers: Map<String, String> = emptyMap()): FrameRateDetection? {
        val key = detectionCacheKey(sourceUrl)
        val now = System.currentTimeMillis()
        detectionCache[key]?.let { cached ->
            if (now - cached.createdAtMs <= DETECTION_CACHE_TTL_MS) {
                return cached.detection
            }
            detectionCache.remove(key)
        }
        return detectFrameRate(sourceUrl, headers)?.also { detection ->
            detectionCache[key] = CachedDetection(detection, now)
        }
    }

    fun getCachedFrameRate(sourceUrl: String): FrameRateDetection? {
        val key = detectionCacheKey(sourceUrl)
        val now = System.currentTimeMillis()
        return detectionCache[key]?.let { cached ->
            if (now - cached.createdAtMs <= DETECTION_CACHE_TTL_MS) {
                cached.detection
            } else {
                detectionCache.remove(key)
                null
            }
        }
    }

    /**
     * Returns true when a compatible mode is active, pending, or newly requested.
     * A false result permits a surface-rate fallback, never a second competing request.
     */
    fun applyFrameRateMode(activity: Activity, frameRate: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (!frameRate.isFinite() || frameRate !in MIN_VALID_FPS..MAX_VALID_FPS) return false

        return try {
            val window = activity.window ?: return false
            val display = window.decorView.display ?: return false
            val activeMode = display.mode
            val sameSizeModes = display.supportedModes.filter {
                it.physicalWidth == activeMode.physicalWidth &&
                    it.physicalHeight == activeMode.physicalHeight
            }
            val index = matchingRefreshRateIndex(sameSizeModes.map { it.refreshRate }, frameRate,
                sameSizeModes.indexOfFirst { it.modeId == activeMode.modeId },
                allowFractionalFallback = true)
                ?: run {
                    android.util.Log.w("FrameRateMatch", "No compatible mode for ${frameRate}fps on ${Build.MODEL}; " +
                        "current=$activeMode supported=${display.supportedModes.contentToString()}")
                    restoreOriginalMode(activity)
                    return false
                }
            val best = sameSizeModes[index]
            val params = window.attributes
            // Display.getMode() can lag behind the requested mode during HDMI negotiation.
            if (params.preferredDisplayModeId == best.modeId) return true
            if (best.modeId == activeMode.modeId && params.preferredDisplayModeId == 0) return true
            if (!originalModeIds.containsKey(window)) {
                originalModeIds[window] = params.preferredDisplayModeId
            }
            params.preferredDisplayModeId = best.modeId
            window.attributes = params
            android.util.Log.i("FrameRateMatch", "Requested ${best.refreshRate}Hz for ${frameRate}fps (was ${activeMode.refreshRate}Hz)")
            true
        } catch (error: Exception) {
            android.util.Log.w("FrameRateMatch", "Display-mode request failed on ${Build.MODEL}", error)
            false
        }
    }

    /**
     * Switch the display to the best mode for the given frame rate.
     * Suspends until the switch stabilizes or times out; playback is never blocked.
     */
    suspend fun matchFrameRateAndWait(
        activity: Activity,
        frameRate: Float
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val target: Display.Mode = withContext(Dispatchers.Main) {
            if (!applyFrameRateMode(activity, frameRate)) return@withContext null
            runCatching {
                val window = activity.window ?: return@runCatching null
                val display = window.decorView.display ?: return@runCatching null
                val requestedId = window.attributes.preferredDisplayModeId
                if (requestedId == 0) display.mode
                else display.supportedModes.firstOrNull { it.modeId == requestedId }
            }.getOrNull()
        } ?: return false
        val confirmed = withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
            var stablePolls = 0
            while (stablePolls < STABLE_POLLS_REQUIRED) {
                val matches = withContext(Dispatchers.Main) {
                    runCatching {
                        val display = activity.window?.decorView?.display ?: return@runCatching false
                        val current = display.mode
                        current.modeId == target.modeId &&
                            current.physicalWidth == target.physicalWidth &&
                            current.physicalHeight == target.physicalHeight &&
                            kotlin.math.abs(current.refreshRate - target.refreshRate) <= 0.012f
                    }.getOrDefault(false)
                }
                stablePolls = if (matches) stablePolls + 1 else 0
                if (stablePolls < STABLE_POLLS_REQUIRED) delay(POLL_INTERVAL_MS)
            }
            true
        } == true
        if (!confirmed) {
            withContext(Dispatchers.Main) { restoreOriginalMode(activity) }
            android.util.Log.w("FrameRateMatch", "Display did not confirm ${frameRate}fps; released mode preference")
        } else {
            android.util.Log.i("FrameRateMatch", "Confirmed ${target.refreshRate}Hz for ${frameRate}fps on ${Build.MODEL}")
        }
        return confirmed
    }

    /**
     * Restore the original display mode that was active before frame rate matching.
     */
    fun restoreOriginalMode(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val window = activity.window ?: return
            val modeId = originalModeIds[window] ?: return
            val params = window.attributes
            params.preferredDisplayModeId = modeId
            window.attributes = params
            originalModeIds.remove(window)
        } catch (_: Exception) {}
    }

    fun clearOriginalMode() {
        originalModeIds.clear()
    }
}
