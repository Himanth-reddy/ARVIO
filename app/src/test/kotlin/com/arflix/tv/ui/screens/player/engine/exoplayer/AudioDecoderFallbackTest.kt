package com.arflix.tv.ui.screens.player.engine.exoplayer

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AudioDecoderFallbackTest {
    private val audio = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
        .setChannelCount(6)
        .setSampleRate(48000)
        .build()
    private val crashed = MediaCodecInfo.newInstance(
        "crashed", MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3,
        null, true, false, true, false, false
    )
    private val alternate = MediaCodecInfo.newInstance(
        "alternate", MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3,
        null, false, true, false, false, false
    )
    private val delegate = MediaCodecSelector { _, _, _ -> listOf(crashed, alternate) }

    private fun capabilities(support: Int): RendererCapabilities = mockk {
        every { supportsFormat(any()) } returns RendererCapabilities.create(support)
    }

    private fun availableNames(fallback: AudioDecoderFallback): List<String> =
        fallback.wrapSelector(delegate).getDecoderInfos(MimeTypes.AUDIO_E_AC3, false, false).map { it.name }

    @Test
    fun supportedTrackBlocksOnlyTheCrashedDecoderAndOnlyOnce() {
        val software = capabilities(C.FORMAT_HANDLED)
        val fallback = AudioDecoderFallback().apply { softwareCapabilities = listOf(software) }
        assertThat(availableNames(fallback)).containsExactly("crashed", "alternate").inOrder()
        assertThat(fallback.blockIfSupported("crashed", audio)).isTrue()
        assertThat(availableNames(fallback)).containsExactly("alternate")
        assertThat(fallback.blockIfSupported("crashed", audio)).isFalse()
        verify { software.supportsFormat(audio) }
    }

    @Test
    fun missingSoftwareRendererLeavesHardwareAvailable() {
        val fallback = AudioDecoderFallback()
        assertThat(fallback.blockIfSupported("crashed", audio)).isFalse()
        assertThat(availableNames(fallback)).containsExactly("crashed", "alternate").inOrder()
    }

    @Test
    fun unsupportedOrOverCapacityTrackDoesNotBlacklistHardware() {
        for (support in listOf(C.FORMAT_UNSUPPORTED_TYPE, C.FORMAT_UNSUPPORTED_SUBTYPE,
            C.FORMAT_UNSUPPORTED_DRM, C.FORMAT_EXCEEDS_CAPABILITIES)) {
            val fallback = AudioDecoderFallback().apply { softwareCapabilities = listOf(capabilities(support)) }
            assertThat(fallback.blockIfSupported("crashed", audio)).isFalse()
            assertThat(availableNames(fallback)).containsExactly("crashed", "alternate").inOrder()
        }
    }

    @Test
    fun protectedTrackNeverDisablesItsHardwareDecoder() {
        val software = capabilities(C.FORMAT_HANDLED)
        val fallback = AudioDecoderFallback().apply { softwareCapabilities = listOf(software) }
        val encrypted = audio.buildUpon().setCryptoType(C.CRYPTO_TYPE_FRAMEWORK).build()
        assertThat(fallback.blockIfSupported("crashed", encrypted)).isFalse()
        assertThat(availableNames(fallback)).containsExactly("crashed", "alternate").inOrder()
        verify(exactly = 0) { software.supportsFormat(any()) }
    }

    @Test
    fun missingFormatAndVideoErrorsDoNotBlockAudioDecoders() {
        val software = capabilities(C.FORMAT_HANDLED)
        val fallback = AudioDecoderFallback().apply { softwareCapabilities = listOf(software) }
        assertThat(fallback.blockIfSupported("crashed", null)).isFalse()
        assertThat(fallback.blockIfSupported("crashed", Format.Builder().build())).isFalse()
        assertThat(fallback.blockIfSupported("crashed", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build())).isFalse()
        assertThat(fallback.blockIfSupported("", audio)).isFalse()
        verify(exactly = 0) { software.supportsFormat(any()) }
    }

    @Test
    fun failedCapabilityQueryDoesNotBlockHardwareOrThrow() {
        val broken = mockk<RendererCapabilities> { every { supportsFormat(any()) } throws IllegalStateException("Unavailable") }
        val fallback = AudioDecoderFallback().apply { softwareCapabilities = listOf(broken) }
        assertThat(fallback.blockIfSupported("crashed", audio)).isFalse()
        assertThat(availableNames(fallback)).containsExactly("crashed", "alternate").inOrder()
    }

    @Test
    fun anotherWorkingSoftwareRendererCanStillRecoverAfterQueryFailure() {
        val broken = mockk<RendererCapabilities> { every { supportsFormat(any()) } throws IllegalStateException("Unavailable") }
        val fallback = AudioDecoderFallback().apply {
            softwareCapabilities = listOf(broken, capabilities(C.FORMAT_UNSUPPORTED_SUBTYPE), capabilities(C.FORMAT_HANDLED))
        }
        assertThat(fallback.blockIfSupported("crashed", audio)).isTrue()
    }

    @Test
    fun unsupportedAttemptDoesNotConsumeTheSupportedRetry() {
        val fallback = AudioDecoderFallback().apply { softwareCapabilities = listOf(capabilities(C.FORMAT_UNSUPPORTED_SUBTYPE)) }
        assertThat(fallback.blockIfSupported("crashed", audio)).isFalse()
        fallback.softwareCapabilities = listOf(capabilities(C.FORMAT_HANDLED))
        assertThat(fallback.blockIfSupported("crashed", audio)).isTrue()
    }

    @Test
    fun selectorPreservesSecureAndTunnelingArguments() {
        val selector = mockk<MediaCodecSelector> {
            every { getDecoderInfos(MimeTypes.AUDIO_E_AC3, true, true) } returns listOf(crashed)
        }
        val fallback = AudioDecoderFallback()
        assertThat(fallback.wrapSelector(selector).getDecoderInfos(MimeTypes.AUDIO_E_AC3, true, true)).containsExactly(crashed)
        verify(exactly = 1) { selector.getDecoderInfos(MimeTypes.AUDIO_E_AC3, true, true) }
    }
}
