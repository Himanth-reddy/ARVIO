package com.arflix.tv.data.repository

import com.arflix.tv.data.api.StalkerApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class IptvAccountInfoRepositoryTest {
    @Test fun `account lookup uses playlist credentials rather than separate EPG account`() = runTest {
        var requestedHost: String? = null
        var requestedUser: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedHost = chain.request().url.host
            requestedUser = chain.request().url.queryParameter("username")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("""{"user_info":{"auth":1,"status":"Active","exp_date":0}}""".toResponseBody())
                .build()
        }.build()
        val repository = repository(client)
        repository.fetchAccountInfo(IptvPlaylistEntry(
            id = "p1", name = "TV", m3uUrl = "https://playlist.example/get.php?username=tv&password=test",
            epgUrl = "https://epg.example/xmltv.php?username=guide&password=test"
        ))
        assertEquals("playlist.example", requestedHost)
        assertEquals("tv", requestedUser)
    }

    @Test fun `plain M3U never looks up the separate EPG subscription`() = runTest {
        var requestCount = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requestCount++
            throw java.io.IOException("No request expected")
        }.build()
        val info = repository(client).fetchAccountInfo(IptvPlaylistEntry(
            id = "p1", name = "TV", m3uUrl = "https://playlist.example/channels.m3u",
            epgUrl = "https://epg.example/xmltv.php?username=guide&password=test"
        ))
        assertEquals(IptvAccountInfo.STATUS_UNAVAILABLE, info?.status)
        assertEquals(0, requestCount)
    }

    @Test fun `failed account request does not replace the playback session`() = runTest {
        val repository = repository()
        val portal = StalkerPortalEntry("stalker1", "TV", "https://portal.example", "00:1A:79:AA:BB:CC")
        val oldApi = mockk<StalkerApi>()
        coEvery { oldApi.getAccountInfoBody() } returns null
        IptvRepository::class.java.getDeclaredField("cachedStalkerApis").apply {
            isAccessible = true
            set(repository, mapOf(portal.id to oldApi))
        }
        mockkConstructor(StalkerApi::class)
        try {
            coEvery { anyConstructed<StalkerApi>().handshake() } returns true
            coEvery { anyConstructed<StalkerApi>().getProfile() } returns true
            coEvery { anyConstructed<StalkerApi>().getAccountInfoBody() } returns
                """{"js":{"end_date":"2030-12-31"}}"""
            repeat(3) { assertNull(repository.fetchAccountInfo(portal)) }
            coVerify(exactly = 0) { anyConstructed<StalkerApi>().handshake() }
            coVerify(exactly = 3) { oldApi.getAccountInfoBody() }
            assertSame(oldApi, repository.cachedStalkerApi)
            coEvery { oldApi.getAccountInfoBody() } returns """{"js":{"end_date":"2030-12-31"}}"""
            assertNotNull(repository.fetchAccountInfo(portal))
            assertSame(oldApi, repository.cachedStalkerApi)
        } finally {
            unmockkConstructor(StalkerApi::class)
        }
    }

    @Test fun `new session is initialized once and shared by concurrent account requests`() = runTest {
        val repository = repository()
        val portal = StalkerPortalEntry("stalker1", "TV", "https://portal.example", "00:1A:79:AA:BB:CC")
        mockkConstructor(StalkerApi::class)
        try {
            coEvery { anyConstructed<StalkerApi>().handshake() } coAnswers { delay(25); true }
            coEvery { anyConstructed<StalkerApi>().getProfile() } returns true
            coEvery { anyConstructed<StalkerApi>().getAccountInfoBody() } returns
                """{"js":{"end_date":"2030-12-31"}}"""
            val results = List(2) { async { repository.fetchAccountInfo(portal) } }.awaitAll()
            assertTrue(results.all { it?.status == IptvAccountInfo.STATUS_ACTIVE })
            assertNotNull(repository.cachedStalkerApi)
            coVerify(exactly = 1) { anyConstructed<StalkerApi>().handshake() }
            coVerify(exactly = 1) { anyConstructed<StalkerApi>().getProfile() }
            coVerify(exactly = 2) { anyConstructed<StalkerApi>().getAccountInfoBody() }
            coVerifyOrder {
                anyConstructed<StalkerApi>().handshake()
                anyConstructed<StalkerApi>().getProfile()
                anyConstructed<StalkerApi>().getAccountInfoBody()
            }
            val sharedApi = repository.cachedStalkerApi
            assertNotNull(repository.fetchAccountInfo(portal))
            assertSame(sharedApi, repository.cachedStalkerApi)
            coVerify(exactly = 1) { anyConstructed<StalkerApi>().handshake() }
        } finally {
            unmockkConstructor(StalkerApi::class)
        }
    }

    private fun repository(client: OkHttpClient = OkHttpClient()) = IptvRepository(
        mockk(relaxed = true), client, mockk(relaxed = true), mockk(relaxed = true)
    )
}
