package com.arflix.tv.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** Uses the existing decoder output, never a second network connection. */
class PlaybackFrameRate {
    private val mutableRate = MutableStateFlow(0f)
    val rate = mutableRate.asStateFlow()
    private var previousUs: Long? = null
    private val intervals = ArrayList<Long>(48)
    private var candidateRate = 0f
    private var candidateFrames = 0

    @Synchronized
    fun reset() {
        previousUs = null
        intervals.clear()
        candidateRate = 0f
        candidateFrames = 0
        mutableRate.value = 0f
    }

    /** Seed AFR even on renderers that do not dispatch per-frame metadata callbacks. */
    @Synchronized
    fun onInputFormat(frameRate: Float) {
        if (mutableRate.value == 0f && frameRate.isFinite() && frameRate in 10f..120f) {
            mutableRate.value = stablePlaybackRate(frameRate)
        }
    }

    @Synchronized
    fun onFrame(timeUs: Long, declaredRate: Float) {
        if (declaredRate.isFinite() && declaredRate in 10f..120f) {
            val rate = stablePlaybackRate(declaredRate)
            if (mutableRate.value == 0f || rate == mutableRate.value) {
                mutableRate.value = rate
                candidateFrames = 0
            } else {
                // Adaptive formats may briefly disagree. Do not renegotiate HDMI per frame.
                candidateFrames = if (candidateRate == rate) candidateFrames + 1 else 1
                candidateRate = rate
                if (candidateFrames >= 48) {
                    mutableRate.value = rate
                    candidateFrames = 0
                }
            }
            previousUs = timeUs
            intervals.clear()
            return
        }
        candidateFrames = 0
        val delta = previousUs?.let { timeUs - it }
        previousUs = timeUs
        if (delta == null) return
        if (delta !in 8_000L..100_000L) {
            intervals.clear()
            return
        }
        intervals.add(delta)
        if (intervals.size < 48) return
        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2]
        // Do not infer a fixed refresh rate from variable-rate or discontinuous output.
        if (intervals.count { abs(it - median) <= median * 0.02 } >= 44) {
            mutableRate.value = stablePlaybackRate(1_000_000f / median)
        }
        intervals.clear()
    }
}

private val STANDARD_PLAYBACK_RATES = floatArrayOf(24000f / 1001f, 24f, 25f,
    30000f / 1001f, 30f, 48f, 50f, 60000f / 1001f, 60f, 100f, 120000f / 1001f, 120f)

internal fun stablePlaybackRate(fps: Float): Float {
    return STANDARD_PLAYBACK_RATES.minByOrNull { abs(it - fps) }
        ?.takeIf { abs(it - fps) <= 0.01f } ?: fps
}

internal fun matchingRefreshRateIndex(
    rates: List<Float>,
    fps: Float,
    activeIndex: Int? = null,
    allowFractionalFallback: Boolean = false,
): Int? {
    if (!fps.isFinite() || fps !in 10f..120f) return null
    val matches = rates.indices.filter { index ->
        val rate = rates[index]
        if (!rate.isFinite() || rate <= 0f) false else {
            val multiple = (rate / fps).roundToInt()
            multiple >= 1 && abs(rate / multiple - fps) <= 0.012f
        }
    }
    // Keep a compatible active mode (e.g. 120Hz for 24fps) instead of blanking HDMI.
    if (matches.isNotEmpty()) {
        return activeIndex?.takeIf { it in matches } ?: matches.minByOrNull { rates[it] }
    }
    if (!allowFractionalFallback) return null
    // Some HDMI mode lists expose only the integer counterpart (24 rather than 23.976).
    // Prefer that cadence to 60Hz pulldown, but never override an available exact match.
    val nearMatches = rates.indices.filter { index ->
        val rate = rates[index]
        val multiple = if (rate.isFinite() && rate > 0f) (rate / fps).roundToInt() else 0
        multiple >= 1 && abs(rate / multiple - fps) / fps <= 0.0011f
    }
    return activeIndex?.takeIf { it in nearMatches } ?: nearMatches.minByOrNull { rates[it] }
}
