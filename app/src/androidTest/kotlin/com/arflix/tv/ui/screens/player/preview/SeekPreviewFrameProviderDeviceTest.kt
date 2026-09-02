package com.arflix.tv.ui.screens.player.preview

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeekPreviewFrameProviderDeviceTest {
    @Test
    fun localVideoReturnsDifferentFramesAcrossTimeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceFile = File(context.cacheDir, "seek_preview_device_test.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("seek_preview_device_test.mp4").use { input ->
            sourceFile.outputStream().use(input::copyTo)
        }
        val provider = SeekPreviewFrameProvider(
            context = context,
            playbackClient = OkHttpClient(),
            memoryClassMb = 256,
        )

        try {
            provider.configure(
                SeekPreviewSource(
                    url = Uri.fromFile(sourceFile).toString(),
                    headers = emptyMap(),
                    cacheIdentity = "device-test-three-scenes",
                    durationMs = 30_000L,
                    isLive = false,
                    isAdaptive = false,
                )
            )

            val openingFrame = provider.frameAt(2_000L)
            val endingFrame = provider.frameAt(22_000L)

            assertNotNull("The opening preview frame should be decoded", openingFrame)
            assertNotNull("The ending preview frame should be decoded", endingFrame)
            assertFalse(
                "Timeline previews must change as the scrub position changes",
                openingFrame!!.bitmap.sameAs(endingFrame!!.bitmap),
            )
            assertTrue(
                "A decoded frame must be immediately available from memory",
                provider.memoryFrameAt(2_000L)?.bitmap === openingFrame.bitmap,
            )
        } finally {
            provider.close()
            sourceFile.delete()
        }
    }

    @Test
    fun rangedHttpVideoReturnsDifferentFramesAcrossTimeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoBytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open("seek_preview_device_test.mp4").use { it.readBytes() }
        val port = ServerSocket(0).use { it.localPort }
        val server = RangeVideoServer(videoBytes, port).apply {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
        val provider = SeekPreviewFrameProvider(
            context = context,
            playbackClient = OkHttpClient(),
            memoryClassMb = 256,
        )

        try {
            provider.configure(
                SeekPreviewSource(
                    url = "http://127.0.0.1:$port/video.mp4",
                    headers = mapOf("X-Preview-Test" to "range"),
                    cacheIdentity = "device-test-range-http-$port",
                    durationMs = 30_000L,
                    isLive = false,
                    isAdaptive = false,
                )
            )

            val openingFrame = provider.frameAt(2_000L)
            val endingFrame = provider.frameAt(22_000L)

            assertNotNull("The ranged opening frame should be decoded", openingFrame)
            assertNotNull("The ranged ending frame should be decoded", endingFrame)
            assertFalse(
                "Ranged HTTP previews must change with the scrub position",
                openingFrame!!.bitmap.sameAs(endingFrame!!.bitmap),
            )
            assertTrue("Playback authentication headers must reach the media server", server.sawAuthHeader.get())
            assertTrue("Progressive previews must use byte-range requests", server.sawRangeRequest.get())
        } finally {
            provider.close()
            server.stop()
        }
    }

    private class RangeVideoServer(
        private val videoBytes: ByteArray,
        port: Int,
    ) : NanoHTTPD(port) {
        val sawAuthHeader = AtomicBoolean(false)
        val sawRangeRequest = AtomicBoolean(false)

        override fun serve(session: IHTTPSession): Response {
            if (session.headers["x-preview-test"] == "range") {
                sawAuthHeader.set(true)
            }
            if (session.method == Method.HEAD) {
                return newFixedLengthResponse(Response.Status.OK, "video/mp4", "").apply {
                    addHeader("Content-Length", videoBytes.size.toString())
                    addHeader("Accept-Ranges", "bytes")
                }
            }
            val requestedRange = session.headers["range"]
                ?.removePrefix("bytes=")
                ?.substringBefore(',')
            if (requestedRange != null) {
                sawRangeRequest.set(true)
                val start = requestedRange.substringBefore('-').toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                if (start >= videoBytes.size) {
                    return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                }
                val requestedEnd = requestedRange.substringAfter('-', "").toLongOrNull()
                val end = (requestedEnd ?: videoBytes.lastIndex.toLong())
                    .coerceIn(start, videoBytes.lastIndex.toLong())
                val length = (end - start + 1L).toInt()
                return newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    "video/mp4",
                    ByteArrayInputStream(videoBytes, start.toInt(), length),
                    length.toLong(),
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes $start-$end/${videoBytes.size}")
                }
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                "video/mp4",
                ByteArrayInputStream(videoBytes),
                videoBytes.size.toLong(),
            ).apply { addHeader("Accept-Ranges", "bytes") }
        }
    }
}
