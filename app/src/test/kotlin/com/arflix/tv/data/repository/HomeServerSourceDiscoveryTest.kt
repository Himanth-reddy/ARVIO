package com.arflix.tv.data.repository

import android.app.Application
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.testing.IsolatedSettingsStoreRule
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeServerSourceDiscoveryTest {
    @get:Rule val settingsStore = IsolatedSettingsStoreRule()

    @Test fun `Silo connects a non admin profile through its compatible base path`() = runBlocking {
        val repository = repository(HomeServerKind.JELLYFIN) { request ->
            when (request.url.encodedPath) {
                "/compat/System/Info/Public" -> 200 to """{"ProductName":"Silo","ServerName":"Shared Silo","Id":"silo"}"""
                "/compat/Users/AuthenticateByName" -> {
                    val buffer = okio.Buffer()
                    request.body!!.writeTo(buffer)
                    val payload = com.google.gson.JsonParser.parseString(buffer.readUtf8()).asJsonObject
                    assertEquals("member@example.test#Family", payload["Username"].asString)
                    assertEquals("secret#1234", payload["Pw"].asString)
                    200 to """{"AccessToken":"member-token","ServerId":"silo","User":{"Id":"profile-id","Name":"Family","Policy":{"IsAdministrator":false}}}"""
                }
                "/compat/Users/profile-id/Views" -> {
                    assertEquals("member-token", request.header("X-Emby-Token"))
                    200 to """{"Items":[{"Id":"movies","Name":"Shared movies","CollectionType":"movies"}]}"""
                }
                else -> error("Unexpected or administrator-only request: ${request.url.encodedPath}")
            }
        }
        val result = repository.connect("https://example.invalid/compat", "member@example.test#Family", "secret#1234").getOrThrow()
        assertEquals(HomeServerKind.JELLYFIN, result.serverKind)
        assertEquals("profile-id", result.userId)
        assertEquals("movies", result.collections.single().id)
    }

    private suspend fun repository(kind: HomeServerKind, response: (Request) -> Pair<Int, String>): HomeServerRepository {
        val profile = "sources-${UUID.randomUUID()}"
        val profiles = mockk<ProfileManager> {
            every { activeProfileId } returns MutableStateFlow(profile)
            coEvery { getProfileId() } returns profile
            every { getProfileIdSync() } returns profile
            every { profileStringKeyFor(any(), any()) } answers { stringPreferencesKey("${firstArg<String>()}_${secondArg<String>()}") }
        }
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            val (code, body) = response(request)
            mockk<okhttp3.Call> { every { timeout() } returns okio.Timeout()
                every { execute() } returns Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build() }
        }
        val repository = spyk(HomeServerRepository(RuntimeEnvironment.getApplication(), client, profiles))
        val connection = HomeServerConnection(connectionId = "shared", serverKind = kind,
            serverUrl = "https://example.invalid", userId = "test", accessToken = "share-only",
            collections = listOf(HomeServerCollection("library", "Shared library", if (kind == HomeServerKind.PLEX) "movie" else "tvshows")))
        coEvery { repository.currentConnections() } returns listOf(connection)
        return repository
    }

    @Test fun `shared Plex falls back from forbidden GUID search to matching section title`() = runBlocking {
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val repository = repository(HomeServerKind.PLEX) { request ->
            requests.add(request.url.toString())
            assertEquals("share-only", request.header("X-Plex-Token"))
            val url = request.url
            when {
                url.queryParameter("guid") != null -> 403 to "{}"
                url.encodedPath == "/search" -> 200 to """{"MediaContainer":{"Metadata":[{"ratingKey":"wrong","type":"movie","title":"Shared Film Documentary"}]}}"""
                url.encodedPath.endsWith("/all") -> 200 to """{"MediaContainer":{"Metadata":[{"ratingKey":"correct","title":"Shared Film","type":"movie","librarySectionID":"library"}]}}"""
                url.encodedPath == "/library/metadata/correct" -> 200 to """{"MediaContainer":{"Metadata":[{"ratingKey":"correct","title":"Shared Film","type":"movie","Media":[{"id":"m","width":1920,"height":1080,"container":"mp4","Part":[{"id":"p","key":"/library/parts/p/file.mp4","container":"mp4"}]}]}]}}"""
                else -> error("Unexpected request ${url.encodedPath}")
            }
        }
        val sources = repository.resolveMovieSources("tt42", "Shared Film", null, 42)
        assertEquals(requests.joinToString("\n"), 1, sources.size)
        assertEquals("1080p", sources.single().quality)
        assertTrue(sources.single().url!!.contains("/library/parts/p/"))
    }

    @Test fun `Emby searches both HD and UHD series and keeps their episode versions`() = runBlocking {
        val repository = repository(HomeServerKind.EMBY) { request ->
            val path = request.url.encodedPath
            when {
                path == "/Users/test/Items" -> 200 to """{"Items":[{"Id":"hd","Name":"Example Show","Type":"Series","ProviderIds":{"Tmdb":"42","Imdb":"tt42"}},{"Id":"uhd","Name":"Example Show UHD","Type":"Series","ProviderIds":{"Tmdb":"42"}},{"Id":"wrong","Name":"Example Show","Type":"Series","ProviderIds":{"Tmdb":"99"}}]}"""
                path.startsWith("/Shows/") -> {
                    val id = path.split('/')[2]
                    assertNotEquals("wrong", id)
                    200 to """{"Items":[{"Id":"episode-$id","Name":"Pilot","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1}]}"""
                }
                path.endsWith("/PlaybackInfo") -> {
                    val height = if ("uhd" in path) 2160 else 1080
                    200 to """{"MediaSources":[{"Id":"v$height","Name":"$height","Container":"mkv","Path":"/media/episode.mkv","MediaStreams":[{"Type":"Video","Width":${height * 16 / 9},"Height":$height}]}]}"""
                }
                else -> error("Unexpected request $path")
            }
        }
        val sources = repository.resolveEpisodeSources("tt42", "Example Show", 1, 1, 42, null)
        assertEquals(setOf("1080p", "4K"), sources.map { it.quality }.toSet())
        assertEquals(2, sources.size)
    }

    @Test fun `Plex permission denial does not manufacture sources`() = runBlocking {
        val repository = repository(HomeServerKind.PLEX) { 403 to "{}" }
        assertTrue(repository.resolveMovieSources("tt42", "Shared Film", 2024, 42).isEmpty())
    }
}
