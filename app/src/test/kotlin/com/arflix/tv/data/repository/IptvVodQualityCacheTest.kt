package com.arflix.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvVodQualityCacheTest {
    @Test fun `576p is not reported as 480p`() {
        val repository = newRepository(mockk(relaxed = true))
        assertEquals("576p", repository.inferQuality("Example Movie 576p"))
        assertEquals("576p", repository.inferQuality("Example Movie 720x576"))
        assertEquals("480p", repository.inferQuality("Example Movie 720x480"))
        assertEquals("1080p", repository.inferQuality("Example Movie 2160x1080"))
        assertEquals("480p", repository.inferQuality("Example Movie 480p"))
        assertEquals(repository.vodQualityRank("576p"), repository.vodQualityRank("720x576"))
        val ranked = listOf("480p", "HD", "576p", "720p")
            .sortedByDescending { repository.vodQualityRank(it) }
        assertEquals(listOf("720p", "HD", "576p", "480p"), ranked)
    }

    @Test fun `persisted series quality survives a cold resolver fast path`() {
        val now = System.currentTimeMillis()
        val provider = "review-provider"
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val stored = mapOf(
            "series_binding_map_v2" to """{"items":{"$provider|tmdb:123":[7]}}""",
            "series_info_${"$provider|7".hashCode()}" to
                """{"savedAtMs":$now,"episodes":[{"id":77,"season":1,"episode":2,"title":"Episode 2","containerExtension":"mp4"}]}""",
            "catalog_${provider.hashCode()}" to
                """{"formatVersion":1,"createdAtMs":$now,"entries":[{"seriesId":7,"name":"UHD - The Gentlemen","normalizedName":"the gentlemen","canonicalTitleKey":"the gentlemen","titleTokens":["gentlemen"],"tmdb":"123"}]}""",
            "resolved_episode_map" to
                """{"items":{"$provider|123||the gentlemen|1|2":{"streamId":77,"seriesId":7,"title":"Episode 2","seriesName":"UHD - The Gentlemen","savedAtMs":$now,"confidence":1.0,"method":"tmdb_id"}}}"""
        )
        every { prefs.getString(any(), any()) } answers { stored[firstArg<String>()] }
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences("iptv_series_resolver_cache_v1", Context.MODE_PRIVATE) } returns prefs
        val repository = newRepository(context)
        val resolver = resolver(repository)
        val fastPath = resolver.javaClass.declaredMethods.first { it.name == "tryFastResolveEpisodeFromCache" }
            .apply { isAccessible = true }
        val results = fastPath.invoke(resolver, provider, "The Gentlemen", 1, 2, 123, null) as List<*>
        assertEquals(1, results.size)
        val result = results.single()!!
        val seriesName = result.javaClass.getDeclaredField("seriesName").apply { isAccessible = true }.get(result) as String?
        assertEquals("4K", repository.inferQualityFrom(seriesName, "Episode 2"))
        fastPath.invoke(resolver, provider, "The Gentlemen", 1, 2, 123, null)
        verify(exactly = 1) { prefs.getString("catalog_${provider.hashCode()}", null) }
    }

    @Test fun `saved name lookups are provider scoped and read only once including misses`() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString("catalog_${"first".hashCode()}", null) } returns catalog("UHD - The Gentlemen")
        every { prefs.getString("catalog_${"second".hashCode()}", null) } returns catalog("FHD - The Gentlemen")
        val resolver = resolver(repositoryWithPrefs(prefs))
        repeat(3) {
            assertEquals("UHD - The Gentlemen", savedName(resolver, "first", 7))
            assertEquals("FHD - The Gentlemen", savedName(resolver, "second", 7))
            assertEquals(null, savedName(resolver, "first", 999))
        }
        verify(exactly = 1) { prefs.getString("catalog_${"first".hashCode()}", null) }
        verify(exactly = 1) { prefs.getString("catalog_${"second".hashCode()}", null) }
    }

    @Test fun `missing and malformed catalogs leave episode quality fallback available`() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getString("catalog_${"broken".hashCode()}", null) } returns "not-json"
        val repository = repositoryWithPrefs(prefs)
        val resolver = resolver(repository)
        for (provider in listOf("missing", "broken")) {
            repeat(2) {
                val name = savedName(resolver, provider, 7)
                assertEquals(null, name)
                assertEquals("1080p", repository.inferQualityFrom(name, "Episode 2 1080p"))
            }
            verify(exactly = 1) { prefs.getString("catalog_${provider.hashCode()}", null) }
        }
    }

    @Test fun `clearing resolver caches discards saved name snapshot`() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns catalog("UHD - The Gentlemen")
        val resolver = resolver(repositoryWithPrefs(prefs))
        assertEquals("UHD - The Gentlemen", savedName(resolver, "provider", 7))
        resolver.javaClass.getDeclaredMethod("clearAll").apply { isAccessible = true }.invoke(resolver)
        every { prefs.getString(any(), any()) } returns catalog("FHD - The Gentlemen")
        assertEquals("FHD - The Gentlemen", savedName(resolver, "provider", 7))
        verify(exactly = 2) { prefs.getString("catalog_${"provider".hashCode()}", null) }
    }

    private fun catalog(name: String) =
        """{"formatVersion":1,"createdAtMs":1,"entries":[{"seriesId":7,"name":"$name"}]}"""

    private fun repositoryWithPrefs(prefs: SharedPreferences): IptvRepository {
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences("iptv_series_resolver_cache_v1", Context.MODE_PRIVATE) } returns prefs
        return newRepository(context)
    }

    private fun resolver(repository: IptvRepository): Any =
        IptvRepository::class.java.getDeclaredMethod("getSeriesResolver")
            .apply { isAccessible = true }.invoke(repository)!!

    private fun savedName(resolver: Any, provider: String, seriesId: Int): String? =
        resolver.javaClass.getDeclaredMethod("cachedSeriesName", String::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(resolver, provider, seriesId) as String?

    private fun newRepository(context: Context) = IptvRepository(
        context,
        mockk<OkHttpClient>(relaxed = true),
        mockk<ProfileManager>(relaxed = true),
        mockk<CloudSyncInvalidationBus>(relaxed = true)
    )
}
