package com.arflix.tv.data.repository

import com.arflix.tv.data.api.MdbListApi
import com.arflix.tv.data.api.MdbTokenResponse
import com.arflix.tv.data.api.MdbUser
import com.arflix.tv.data.repository.sync.SyncProvider
import com.arflix.tv.data.repository.sync.SyncProviderStore
import com.arflix.tv.util.Constants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class MdbListAuthLifecycleTest {

    private val api = mockk<MdbListApi>()
    private val store = mockk<SyncProviderStore>(relaxed = true)
    private val profileManager = mockk<ProfileManager>(relaxed = true)

    private lateinit var repository: MdbListRepository

    @Before
    fun setUp() {
        mockkObject(Constants)
        every { Constants.MDBLIST_CLIENT_ID } returns "test-client-id"
        every { profileManager.getProfileIdSync() } returns "default-profile"

        repository = MdbListRepository(
            api = api,
            store = store,
            profileManager = profileManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun httpException(code: Int, json: String): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://api.mdblist.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .build()
        val body = json.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(body, raw))
    }

    @Test
    fun `token renewal is serialized and mutex prevents duplicate concurrent API calls`() = runTest {
        var currentToken = "old-access-token"
        val existingOAuth = SyncProviderStore.MdbListCredential.OAuth(
            accessToken = currentToken,
            refreshToken = "refresh-token-123",
            expiresAt = System.currentTimeMillis() - 60_000L // Expired
        )

        coEvery { store.getMdbListCredential("test-profile") } answers {
            SyncProviderStore.MdbListCredential.OAuth(
                accessToken = currentToken,
                refreshToken = "refresh-token-123",
                expiresAt = if (currentToken == "old-access-token") {
                    System.currentTimeMillis() - 60_000L
                } else {
                    System.currentTimeMillis() + 7200_000L
                }
            )
        }

        coEvery {
            api.refreshToken(
                grantType = "refresh_token",
                refreshToken = "refresh-token-123",
                clientId = "test-client-id"
            )
        } coAnswers {
            delay(50)
            MdbTokenResponse(
                accessToken = "renewed-access-token",
                refreshToken = "new-refresh-token",
                expiresIn = 7200
            )
        }

        coEvery {
            store.setMdbListOAuthTokens(any(), any(), any(), any())
        } answers {
            currentToken = firstArg()
        }

        // Launch 4 concurrent callers needing renewal
        val deferredList = (1..4).map {
            async {
                repository.resolveAuth("test-profile", forceRefresh = false)
            }
        }

        val results = deferredList.awaitAll()

        // All concurrent callers should receive the renewed token
        results.forEach { auth ->
            assertTrue(auth is MdbListRepository.MdbListAuth.OAuth)
            assertEquals("renewed-access-token", (auth as MdbListRepository.MdbListAuth.OAuth).accessToken)
        }

        // The renewal endpoint should only be called once thanks to the mutex and double-check
        coVerify(exactly = 1) {
            api.refreshToken("refresh_token", "refresh-token-123", "test-client-id")
        }
    }

    @Test
    fun `renewal preserves existing refresh token when new token is null or blank`() = runTest {
        val existingCred = SyncProviderStore.MdbListCredential.OAuth(
            accessToken = "access-1",
            refreshToken = "keep-this-refresh-token",
            expiresAt = System.currentTimeMillis() - 1000L
        )
        coEvery { store.getMdbListCredential("p1") } returns existingCred

        // Case 1: refreshToken is null in response
        coEvery {
            api.refreshToken(any(), any(), any())
        } returns MdbTokenResponse(
            accessToken = "new-access-1",
            refreshToken = null,
            expiresIn = 7200
        )

        repository.resolveAuth("p1", forceRefresh = true)

        coVerify {
            store.setMdbListOAuthTokens(
                accessToken = "new-access-1",
                refreshToken = "keep-this-refresh-token",
                expiresInSeconds = 7200,
                profileId = "p1"
            )
        }

        // Case 2: refreshToken is blank in response
        coEvery {
            api.refreshToken(any(), any(), any())
        } returns MdbTokenResponse(
            accessToken = "new-access-2",
            refreshToken = "   ",
            expiresIn = 3600
        )

        repository.resolveAuth("p1", forceRefresh = true)

        coVerify {
            store.setMdbListOAuthTokens(
                accessToken = "new-access-2",
                refreshToken = "keep-this-refresh-token",
                expiresInSeconds = 3600,
                profileId = "p1"
            )
        }
    }

    @Test
    fun `disconnect clears both OAuth tokens and legacy API key credentials`() = runTest {
        repository.disconnect("profile-target")

        coVerify(exactly = 1) { store.clearAllMdbListCredentials("profile-target") }
        coVerify(exactly = 1) { store.onProviderDisconnected(SyncProvider.MDBLIST) }
    }

    @Test
    fun `OAuth credentials use strictly Bearer header and no apikey query parameter`() = runTest {
        coEvery { store.getMdbListCredential("oauth-profile") } returns SyncProviderStore.MdbListCredential.OAuth(
            accessToken = "test-oauth-token",
            refreshToken = "ref",
            expiresAt = System.currentTimeMillis() + 3600_000L
        )

        coEvery {
            api.getUser(authHeader = any(), apiKey = any())
        } returns MdbUser(username = "oauth_tester")

        val username = repository.fetchUsername("oauth-profile")

        assertEquals("oauth_tester", username)
        coVerify(exactly = 1) {
            api.getUser(
                authHeader = "Bearer test-oauth-token",
                apiKey = null
            )
        }
    }

    @Test
    fun `ApiKey credentials use strictly apikey query parameter and no Bearer header`() = runTest {
        coEvery { store.getMdbListCredential("key-profile") } returns SyncProviderStore.MdbListCredential.ApiKey(
            apiKey = "legacy-secret-key"
        )

        coEvery {
            api.getUser(authHeader = any(), apiKey = any())
        } returns MdbUser(username = "key_tester")

        val username = repository.fetchUsername("key-profile")

        assertEquals("key_tester", username)
        coVerify(exactly = 1) {
            api.getUser(
                authHeader = null,
                apiKey = "legacy-secret-key"
            )
        }
    }

    @Test
    fun `validateKey sends apikey in query only without Authorization header`() = runTest {
        coEvery {
            api.getUser(authHeader = null, apiKey = "probe-key")
        } returns MdbUser(username = "valid_user")

        val isValid = repository.validateKey("probe-key")

        assertTrue(isValid)
        coVerify(exactly = 1) {
            api.getUser(authHeader = null, apiKey = "probe-key")
        }
    }

    @Test
    fun `401 HTTP response on OAuth call triggers token refresh and retries once`() = runTest {
        var currentToken = "stale-access-token"
        coEvery { store.getMdbListCredential("p1") } answers {
            SyncProviderStore.MdbListCredential.OAuth(
                accessToken = currentToken,
                refreshToken = "ref-123",
                expiresAt = if (currentToken == "stale-access-token") {
                    System.currentTimeMillis() + 36_000_000L
                } else {
                    System.currentTimeMillis() + 7200_000L
                }
            )
        }

        coEvery {
            api.getUser(authHeader = "Bearer stale-access-token", apiKey = null)
        } throws httpException(401, """{"error":"invalid_token"}""")

        coEvery {
            api.refreshToken("refresh_token", "ref-123", "test-client-id")
        } returns MdbTokenResponse(
            accessToken = "refreshed-access-token",
            refreshToken = "ref-123",
            expiresIn = 3600
        )

        coEvery {
            store.setMdbListOAuthTokens("refreshed-access-token", "ref-123", 3600, "p1")
        } answers {
            currentToken = "refreshed-access-token"
        }

        coEvery {
            api.getUser(authHeader = "Bearer refreshed-access-token", apiKey = null)
        } returns MdbUser(username = "retried_user")

        val username = repository.fetchUsername("p1")

        assertEquals("retried_user", username)
        coVerify(exactly = 1) { api.getUser(authHeader = "Bearer stale-access-token", apiKey = null) }
        coVerify(exactly = 1) { api.refreshToken("refresh_token", "ref-123", "test-client-id") }
        coVerify(exactly = 1) { api.getUser(authHeader = "Bearer refreshed-access-token", apiKey = null) }
    }

    @Test
    fun `refresh rejection with HTTP 401 or 400 clears credentials and disconnects safely`() = runTest {
        coEvery { store.getMdbListCredential("p-reject") } returns SyncProviderStore.MdbListCredential.OAuth(
            accessToken = "bad-access",
            refreshToken = "revoked-refresh",
            expiresAt = System.currentTimeMillis() - 1000L
        )

        coEvery {
            api.refreshToken(any(), any(), any())
        } throws httpException(401, """{"error":"invalid_grant"}""")

        val result = repository.resolveAuth("p-reject", forceRefresh = true)

        assertNull(result)
        coVerify(exactly = 1) { store.clearAllMdbListCredentials("p-reject") }
        coVerify(exactly = 1) { store.onProviderDisconnected(SyncProvider.MDBLIST) }
    }

    @Test
    fun `transient refresh failure HTTP 500 returns non-expired token without wiping credentials`() = runTest {
        val validExpiry = System.currentTimeMillis() + 600_000L
        coEvery { store.getMdbListCredential("p-transient") } returns SyncProviderStore.MdbListCredential.OAuth(
            accessToken = "still-valid-token",
            refreshToken = "ref",
            expiresAt = validExpiry
        )

        coEvery {
            api.refreshToken(any(), any(), any())
        } throws httpException(500, """{"error":"server_error"}""")

        val result = repository.resolveAuth("p-transient", forceRefresh = true)

        assertNotNull(result)
        assertEquals("still-valid-token", (result as MdbListRepository.MdbListAuth.OAuth).accessToken)
        coVerify(exactly = 0) { store.clearAllMdbListCredentials(any()) }
    }

    @Test
    fun `parseMdbListDeviceError parses RFC 8628 codes accurately with single read of errorBody`() {
        // authorization_pending
        val pendingEx = httpException(400, """{"error": "authorization_pending"}""")
        assertEquals(MdbListDeviceError.AUTHORIZATION_PENDING, parseMdbListDeviceError(pendingEx))

        // slow_down via 400 JSON
        val slowDown400Ex = httpException(400, """{"error": "slow_down"}""")
        assertEquals(MdbListDeviceError.SLOW_DOWN, parseMdbListDeviceError(slowDown400Ex))

        // slow_down via HTTP 429
        val slowDown429Ex = httpException(429, "")
        assertEquals(MdbListDeviceError.SLOW_DOWN, parseMdbListDeviceError(slowDown429Ex))

        // access_denied via 400 JSON
        val denied400Ex = httpException(400, """{"error": "access_denied"}""")
        assertEquals(MdbListDeviceError.ACCESS_DENIED, parseMdbListDeviceError(denied400Ex))

        // access_denied via HTTP 403
        val denied403Ex = httpException(403, "")
        assertEquals(MdbListDeviceError.ACCESS_DENIED, parseMdbListDeviceError(denied403Ex))

        // expired_token via 400 JSON
        val expiredEx = httpException(400, """{"error": "expired_token"}""")
        assertEquals(MdbListDeviceError.EXPIRED_TOKEN, parseMdbListDeviceError(expiredEx))

        // other unexpected error
        val otherEx = httpException(500, """{"error": "internal_error"}""")
        assertEquals(MdbListDeviceError.OTHER, parseMdbListDeviceError(otherEx))

        // non-HttpException
        assertEquals(MdbListDeviceError.OTHER, parseMdbListDeviceError(IllegalStateException("offline")))
    }

    @Test
    fun `profile scoping - credentials and operations are isolated by profileId`() = runTest {
        val tokenResponse = MdbTokenResponse(
            accessToken = "prof-b-access",
            refreshToken = "prof-b-refresh",
            expiresIn = 3600
        )

        repository.saveTokens(tokenResponse, profileId = "profile-b")

        coVerify(exactly = 1) {
            store.setMdbListOAuthTokens(
                accessToken = "prof-b-access",
                refreshToken = "prof-b-refresh",
                expiresInSeconds = 3600,
                profileId = "profile-b"
            )
        }
        coVerify(exactly = 0) {
            store.setMdbListOAuthTokens(
                accessToken = any(),
                refreshToken = any(),
                expiresInSeconds = any(),
                profileId = "default-profile"
            )
        }
    }
}
