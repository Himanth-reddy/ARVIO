package com.arflix.tv.data.repository

import com.arflix.tv.data.api.DeviceCodeRequest
import com.arflix.tv.di.AppModule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TraktAuthQueueTest {
    @Test fun activationDoesNotWaitBehindBusyMetadataQueue() = runBlocking {
        val occupied = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequests = 1; maxRequestsPerHost = 1 })
            .addInterceptor { chain ->
                if (chain.request().url.encodedPath == "/busy") {
                    occupied.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(
                        """{"device_code":"test-device","user_code":"TEST","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}"""
                            .toResponseBody("application/json".toMediaType())).build()
            }.build()
        client.newCall(Request.Builder().url("https://api.trakt.tv/busy").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { finished.countDown() }
            override fun onResponse(call: Call, response: Response) { response.close(); finished.countDown() }
        })
        try {
            assertTrue(occupied.await(2, TimeUnit.SECONDS))
            val auth = AppModule.provideTraktAuthApi(client)
            val code = withTimeout(2_000) { auth.getDeviceCode(DeviceCodeRequest("fixture")) }
            assertEquals("TEST", code.userCode)
            assertEquals("Metadata request must still be blocked", 1L, release.count)
        } finally {
            release.countDown()
            finished.await(2, TimeUnit.SECONDS)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
