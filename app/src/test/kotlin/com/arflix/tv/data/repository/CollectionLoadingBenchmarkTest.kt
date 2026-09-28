package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbExternalIds
import com.arflix.tv.data.api.TmdbMovieDetails
import com.arflix.tv.data.api.TmdbPublicListResponse
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.CollectionSourceConfig
import com.arflix.tv.data.model.CollectionSourceKind
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Opt in with ARVIO_LIVE_COLLECTION_BENCHMARK=1 and run :app:testSideloadDebugUnitTest
 * --tests com.arflix.tv.data.repository.CollectionLoadingBenchmarkTest (ensure the test task reruns).
 * artifacts/run-collection-benchmark.ps1 -Live forces only the test task, not all dependencies.
 * Cold means a fresh repository and HTTP client, not a cold OS DNS cache or JVM.
 * Plain JVM Android org.json stubs may prevent IMDb parsing: see imdbRated in output.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionLoadingBenchmarkTest {
    @Test fun controlledColdAndWarmCollection() = runTest {
        val ids = listOf(108, 102, 107, 101, 106, 103, 105, 104)
        val calls = linkedMapOf<String, Int>()
        fun count(kind: String) { calls[kind] = (calls[kind] ?: 0) + 1 }
        val api = mockk<TmdbApi>()
        // Deserialize realistic metadata so the same fixture exercises future card seeding.
        val listJson = """{"page":1,"total_pages":1,"items":[${ids.joinToString { id ->
            """{"id":$id,"media_type":"movie","title":"Movie $id","poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg","overview":"Fixture","release_date":"2020-01-01","vote_average":7.5,"genre_ids":[28]}"""
        }}]}"""
        coEvery { api.getPublicList(1, any(), any(), any()) } coAnswers {
            count("metadata"); delay(100)
            Gson().fromJson(listJson, TmdbPublicListResponse::class.java)
        }
        coEvery { api.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            count("details"); delay(200)
            TmdbMovieDetails(id = firstArg(), title = "Movie ${firstArg<Int>()}", posterPath = "/poster.jpg")
        }
        coEvery { api.getMovieExternalIds(any(), any()) } coAnswers {
            count("externalIds"); delay(400)
            TmdbExternalIds(imdbId = "tt${firstArg<Int>()}")
        }
        // Only the public rating boundary is simulated; collection/details/cache logic is real.
        val repository = spyk(repository(api, mockk()))
        coEvery { repository.getImdbRating(any(), any(), any()) } coAnswers {
            count("imdbSimulated"); delay(300); "8.0"
        }
        val catalog = catalog(1)
        repeat(2) { index ->
            calls.clear()
            val start = testScheduler.currentTime
            val wallStart = System.nanoTime()
            val page = repository.loadCollectionCatalogPage(catalog, 0, 8, MediaType.MOVIE)
            println("COLLECTION_BENCH controlled ${if (index == 0) "cold" else "warm"} " +
                "virtualMs=${testScheduler.currentTime - start} wallMs=${elapsedMs(wallStart)} " +
                "requests=${calls.values.sum()} kinds=$calls orderedIds=${page.items.map { it.id }}")
            assertEquals(ids, page.items.map { it.id })
            if (index == 1) {
                assertTrue("Warm page should not perform API/rating requests", calls.isEmpty())
                assertEquals(0L, testScheduler.currentTime - start)
            }
        }
        println("COLLECTION_BENCH controlled delaysMs metadata=100 details=200 externalIds=400 imdbSimulated=300; " +
            "legacy six-wide eight-card critical path=100+2*(max(200,400)+300)=1500ms")
    }

    @Test fun liveKaptainColdAndWarmCollections() {
        assumeTrue("Opt-in live traffic only", System.getenv("ARVIO_LIVE_COLLECTION_BENCHMARK") == "1")
        assertTrue("TMDB key must be configured", Constants.TMDB_API_KEY.isNotBlank())
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .firstOrNull { File(it, "collections/kaptain-arvio-trimmed.json").isFile }
            ?: error("Cannot locate collections/kaptain-arvio-trimmed.json")
        val document = File(root, "collections/kaptain-arvio-trimmed.json").reader().use {
            JsonParser.parseReader(it)
        }
        val sources = objects(document).filter { it.get("tmdbSourceType")?.asString == "LIST" }.toList()
        for (title in listOf("The Saga: Alien Collection", "The Visionary: Denis Villeneuve")) {
            val source = sources.firstOrNull { it.get("title")?.asString == title }
                ?: error("Kaptain fixture source not found: $title")
            val listId = source.get("tmdbId").asInt
            val counter = CountingInterceptor()
            val client = OkHttpClient.Builder().addInterceptor(counter)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS).build()
            try {
                val api = Retrofit.Builder().baseUrl("https://api.themoviedb.org/3/")
                    .client(client).addConverterFactory(GsonConverterFactory.create()).build()
                    .create(TmdbApi::class.java)
                val repository = repository(api, client)
                runBlocking {
                    var coldIds = emptyList<Int>()
                    repeat(2) { index ->
                        counter.reset()
                        val start = System.nanoTime()
                        val page = repository.loadCollectionCatalogPage(catalog(listId), 0, 8, MediaType.MOVIE)
                        val elapsed = elapsedMs(start)
                        val ids = page.items.map { it.id }
                        println("COLLECTION_BENCH live ${if (index == 0) "cold" else "warm"} " +
                            "listId=$listId title=$title elapsedMs=$elapsed ${counter.summary(start)} " +
                            "orderedIds=$ids imdbRated=${page.items.count { it.imdbRating.isNotBlank() }} " +
                            "nextOffset=${page.nextOffset} hasMore=${page.hasMore}")
                        assertTrue("Live collection must return cards (see sanitized HTTP status summary)", ids.isNotEmpty())
                        if (index == 0) {
                            coldIds = ids
                            assertEquals("Preserve upstream movie order", counter.movieIds.distinct().take(8), ids)
                        } else {
                            assertEquals(coldIds, ids)
                            assertEquals("Warm collection should use repository caches", 0, counter.size())
                        }
                    }
                }
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
        println("COLLECTION_BENCH live caveat: IMDb HTTP requests are real, but plain JVM org.json " +
            "stubs can reject successful responses and trigger fallback; this is not Android UI/image-render time. " +
            "Per-kind summedMs includes body read and overlaps concurrent requests; lastEndMs is relative to page start.")
    }

    private fun catalog(listId: Int) = CatalogConfig(
        id = "benchmark-$listId", title = "Benchmark", sourceType = CatalogSourceType.PREINSTALLED,
        kind = CatalogKind.COLLECTION, collectionRailKey = "imported",
        collectionSources = listOf(CollectionSourceConfig(CollectionSourceKind.TMDB_LIST, tmdbListId = listId))
    )

    private fun repository(api: TmdbApi, client: OkHttpClient) = MediaRepository(
        mockk(relaxed = true), api, mockk(relaxed = true), mockk(relaxed = true),
        client, mockk(relaxed = true), mockk(relaxed = true)
    )

    private fun objects(element: JsonElement): Sequence<JsonObject> = sequence {
        when {
            element.isJsonObject -> {
                yield(element.asJsonObject)
                element.asJsonObject.entrySet().forEach { yieldAll(objects(it.value)) }
            }
            element.isJsonArray -> element.asJsonArray.forEach { yieldAll(objects(it)) }
        }
    }

    private class CountingInterceptor : Interceptor {
        private data class Sample(val kind: String, val status: Int, val start: Long, val end: Long)
        private val samples = Collections.synchronizedList(mutableListOf<Sample>())
        val movieIds = Collections.synchronizedList(mutableListOf<Int>())
        fun reset() { samples.clear(); movieIds.clear() }
        fun size() = samples.size
        fun summary(pageStart: Long): String = synchronized(samples) {
            "requests=${samples.size} kinds=" + samples.groupBy { it.kind }.toSortedMap().map { (kind, rows) ->
                "$kind(count=${rows.size},summedMs=${rows.sumOf { (it.end - it.start) / 1_000_000 }}," +
                    "lastEndMs=${(rows.maxOf { it.end } - pageStart) / 1_000_000},statuses=${rows.groupingBy { it.status }.eachCount()})"
            }.joinToString(";")
        }
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val kind = when {
                request.url.host != "api.themoviedb.org" -> if (request.url.host.contains("cinemeta")) "imdbCinemeta" else "imdbFallback"
                request.url.encodedPath.contains("/list/") -> "metadata"
                request.url.encodedPath.endsWith("/external_ids") -> "externalIds"
                else -> "details"
            }
            val start = System.nanoTime()
            var status = -1
            try {
                val response = chain.proceed(request)
                status = response.code
                val body = response.peekBody(4L * 1024 * 1024).string()
                if (kind == "metadata" && response.isSuccessful) {
                    JsonParser.parseString(body).asJsonObject.getAsJsonArray("items")?.forEach {
                        val item = it.asJsonObject
                        if (item.get("media_type")?.asString != "tv") movieIds.add(item.get("id").asInt)
                    }
                }
                return response
            } catch (error: Exception) {
                // Never propagate a URL/query/API key in a benchmark exception message.
                throw IOException("Benchmark $kind failed: ${error.javaClass.simpleName}")
            } finally {
                samples.add(Sample(kind, status, start, System.nanoTime()))
            }
        }
    }

    companion object {
        private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000
    }
}
