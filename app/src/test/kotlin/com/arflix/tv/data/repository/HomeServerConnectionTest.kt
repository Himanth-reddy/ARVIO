package com.arflix.tv.data.repository

import android.app.Application
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.testing.IsolatedSettingsStoreRule
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeServerConnectionTest {
    @get:Rule val settingsStore = IsolatedSettingsStoreRule()
    private val requests = mutableListOf<Request>()
    private val timeouts = mutableListOf<Long>()
    private val profiles = mockk<ProfileManager> {
        every { activeProfileId } returns MutableStateFlow("connection-test")
        coEvery { getProfileId() } returns "connection-test"
        every { profileStringKeyFor(any(), any()) } answers {
            stringPreferencesKey("${firstArg<String>()}_${secondArg<String>()}")
        }
    }

    private fun repository(response: (Request) -> Pair<Int, String> = ::success): HomeServerRepository {
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(call: Call) { timeouts += call.timeout().timeoutNanos() }
        }).addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val (code, body) = response(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return HomeServerRepository(RuntimeEnvironment.getApplication(), client, profiles)
    }

    private fun success(request: Request): Pair<Int, String> = when {
        request.url.encodedPath.endsWith("/System/Info/Public") ->
            200 to """{"Id":"server","ServerName":"Home","ProductName":"Emby Server"}"""
        request.url.encodedPath.endsWith("/Users/AuthenticateByName") ->
            200 to """{"AccessToken":"token","ServerId":"server","User":{"Id":"member","Name":"Member"}}"""
        request.url.encodedPath.endsWith("/Users/member/Views") ->
            200 to """{"Items":[{"Id":"films","Name":"Films","CollectionType":"movies"}]}"""
        else -> error("Unexpected endpoint: ${request.url.encodedPath}")
    }

    @Test fun `normal local Emby login authenticates and persists its libraries`() = runBlocking {
        val repo = repository()
        val result = repo.connect("http://192.168.1.50:8096", "Member", "secret").getOrThrow()
        assertEquals(HomeServerKind.EMBY, result.serverKind)
        assertEquals("films", repo.currentConnections().single().collections.single().id)
        assertEquals(listOf("/System/Info/Public", "/Users/AuthenticateByName", "/Users/member/Views"), requests.map { it.url.encodedPath })
        assertEquals("token", requests.last().header("X-Emby-Token"))
        assertEquals(listOf(12L, 30L, 12L).map { TimeUnit.SECONDS.toNanos(it) }, timeouts)
    }

    @Test fun `local HTTP server accepts passwordless login from a pasted dashboard address`() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        val paths = listOf("/emby/System/Info/Public", "/emby/Users/AuthenticateByName", "/emby/Users/member/Views")
        for (path in paths) {
            val (status, json) = success(Request.Builder().url("http://localhost$path").build())
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
        }
        server.start()
        try {
            val base = server.url("/emby").toString()
            val repo = HomeServerRepository(RuntimeEnvironment.getApplication(), client, profiles)
            val connection = repo.connect("$base/web/index.html#!/home", "Member", "").getOrThrow()
            assertEquals(base, connection.serverUrl)
            assertEquals("films", connection.collections.single().id)
            val received = List(3) { server.takeRequest(1, TimeUnit.SECONDS)!! }
            assertEquals(paths, received.map { it.path })
            assertEquals("POST", received[1].method)
            assertEquals("", JsonParser.parseString(received[1].body.readUtf8()).asJsonObject["Pw"].asString)
            assertEquals("token", received[2].getHeader("X-Emby-Token"))
            assertEquals(3, server.requestCount)
            assertEquals(connection, repo.currentConnections().single())
        } finally {
            server.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun `dashboard URL becomes the API base without losing its reverse proxy prefix`() = runBlocking {
        val repo = repository()
        val result = repo.connect("http://192.168.1.50:8096/emby/web/index.html?serverId=old#!/home", "Member", "secret").getOrThrow()
        assertEquals("http://192.168.1.50:8096/emby", result.serverUrl)
        assertTrue(requests.all { it.url.encodedPath.startsWith("/emby/") && it.url.query == null && it.url.fragment == null })
    }

    @Test fun `passwordless account is authenticated by Emby with an empty Pw`() = runBlocking {
        val repo = repository { request ->
            if (request.method == "POST") {
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                val body = JsonParser.parseString(buffer.readUtf8()).asJsonObject
                assertEquals("Member", body["Username"].asString)
                assertEquals("", body["Pw"].asString)
            }
            success(request)
        }
        assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "").isSuccess)
        assertEquals(3, requests.size)
    }

    @Test fun `server still rejects a missing or incorrect required password`() = runBlocking {
        val repo = repository { request ->
            if (request.method == "POST") 401 to "{}" else success(request)
        }
        assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "").isFailure)
        assertTrue(repo.currentConnections().isEmpty())
        assertEquals(2, requests.size)
    }

    @Test fun `valid login survives library failure and can recover its libraries later`() = runBlocking {
        var unavailable = true
        val repo = repository { request ->
            if (request.url.encodedPath.endsWith("/Views") && unavailable) 503 to "{}" else success(request)
        }
        val result = repo.connect("http://192.168.1.50:8096", "Member", "secret").getOrThrow()
        assertTrue(result.isUsable)
        assertTrue(result.collections.isEmpty())
        assertEquals("token", repo.currentConnections().single().accessToken)
        unavailable = false
        repo.refreshMissingLibraries()
        assertEquals("films", repo.currentConnections().single().collections.single().id)
        assertEquals(1, requests.count { it.method == "POST" })
    }

    @Test fun `library timeout does not discard an authenticated connection`() = runBlocking {
        val repo = repository { request ->
            if (request.url.encodedPath.endsWith("/Views")) throw SocketTimeoutException("fixture")
            success(request)
        }
        assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "secret").getOrThrow().isUsable)
        assertEquals(1, repo.currentConnections().size)
    }

    @Test fun `revoked token is not saved as a successful connection`() = runBlocking {
        val repo = repository { request ->
            if (request.url.encodedPath.endsWith("/Views")) 401 to "{}" else success(request)
        }
        assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "secret").isFailure)
        assertTrue(repo.currentConnections().isEmpty())
    }

    @Test fun `reconnecting same account preserves cached libraries during outage`() = runBlocking {
        var unavailable = false
        val repo = repository { request ->
            if (request.url.encodedPath.endsWith("/Views") && unavailable) 503 to "{}" else success(request)
        }
        repo.connect("http://192.168.1.50:8096", "Member", "secret").getOrThrow()
        unavailable = true
        val result = repo.connect("http://192.168.1.50:8096", "Member", "new-secret").getOrThrow()
        assertEquals("films", result.collections.single().id)
        assertEquals(1, repo.currentConnections().size)
    }

    @Test fun `another account cannot inherit cached libraries when discovery fails`() = runBlocking {
        val repo = repository { request ->
            if (request.url.encodedPath.endsWith("/Views")) 503 to "{}" else success(request)
        }
        val old = HomeServerConnection(serverUrl = "http://192.168.1.50:8096", userId = "other", accessToken = "old-token",
            serverKind = HomeServerKind.EMBY, serverId = "server", collections = listOf(HomeServerCollection("private", "Private", "movies")))
        repo.importCloudConnectionsJsonForProfile("connection-test", Gson().toJson(listOf(old)))
        val result = repo.connect("http://192.168.1.50:8096", "Member", "secret").getOrThrow()
        assertTrue(result.collections.isEmpty())
        assertEquals("member", repo.currentConnections().single().userId)
    }

    @Test fun `unreachable server stops after the first transport failure`() = runBlocking {
        val repo = repository { throw SocketTimeoutException("fixture") }
        assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "secret").exceptionOrNull() is SocketTimeoutException)
        assertEquals(1, requests.size)
        assertTrue(repo.currentConnections().isEmpty())
    }

    @Test fun `TLS verification failure is not bypassed by protocol fallback`() = runBlocking {
        val repo = repository { throw SSLHandshakeException("fixture certificate failure") }
        assertTrue(repo.connect("https://emby.example", "Member", "secret").exceptionOrNull() is SSLHandshakeException)
        assertEquals(1, requests.size)
    }

    @Test fun `unavailable or rate limited discovery does not probe or post credentials`() = runBlocking {
        for (status in listOf(429, 503)) {
            requests.clear()
            val repo = repository { status to "{}" }
            assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "secret").isFailure)
            assertEquals(1, requests.size)
            assertEquals("GET", requests.single().method)
        }
    }

    @Test fun `unsupported public endpoint still permits authenticated compatible servers`() = runBlocking {
        val repo = repository { request ->
            when (request.url.encodedPath) {
                "/System/Info/Public", "/identity" -> 404 to "{}"
                else -> success(request)
            }
        }
        assertTrue(repo.connect("http://192.168.1.50:8096", "Member", "secret").isSuccess)
        assertEquals(4, requests.size)
    }

    @Test fun `Plex discovery still uses identity when Emby endpoint is absent`() = runBlocking {
        val repo = repository { request ->
            when (request.url.encodedPath) {
                "/System/Info/Public" -> 404 to "{}"
                "/identity" -> 200 to """<MediaContainer machineIdentifier="plex-server" friendlyName="Plex"/>"""
                else -> error("Must require a Plex token before further requests")
            }
        }
        assertTrue(repo.connect("http://192.168.1.50:32400", "", "").isFailure)
        assertEquals(listOf("/System/Info/Public", "/identity"), requests.map { it.url.encodedPath })
    }

    @Test fun `cancellation during discovery login or library load never saves a connection`() = runBlocking {
        for (path in listOf("/System/Info/Public", "/Users/AuthenticateByName", "/Users/member/Views")) {
            val repo = repository { request ->
                if (request.url.encodedPath == path) throw CancellationException("fixture")
                success(request)
            }
            try {
                repo.connect("http://192.168.1.50:8096", "Member", "secret")
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(repo.currentConnections().isEmpty())
            }
        }
    }

    @Test fun `normalization preserves custom API bases IPv6 and explicit ports`() {
        val cases = mapOf(
            " 192.168.1.50:8096/ " to "http://192.168.1.50:8096",
            "https://emby.example:8920/emby/web/index.html#!/home" to "https://emby.example:8920/emby",
            "https://emby.example/emby/web/home.html?x=1" to "https://emby.example/emby",
            "http://[::1]:8096/web/" to "http://[::1]:8096",
            "https://emby.example/compat/" to "https://emby.example/compat",
            "https://emby.example/media%20server/web/index.html" to "https://emby.example/media%20server",
            "https://emby.example/web/api" to "https://emby.example/web/api",
            "" to ""
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, normalizeHomeServerUrl(input)) }
    }
}
