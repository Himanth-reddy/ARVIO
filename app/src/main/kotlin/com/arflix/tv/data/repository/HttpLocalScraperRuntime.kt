package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonBehaviorHints
import com.arflix.tv.data.model.AddonManifest
import com.arflix.tv.data.model.AddonResource
import com.arflix.tv.data.model.ProxyHeaders
import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class HttpLocalScraperInstallCandidate(
    val name: String,
    val version: String,
    val description: String,
    val logo: String?,
    val manifest: AddonManifest,
    val transportUrl: String
)

@Singleton
class HttpLocalScraperRuntime @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tmdbApi: TmdbApi
) {
    private val gson = Gson()
    private val manifestCache = mutableMapOf<String, HttpScraperManifest>()
    private val tmdbIdCache = mutableMapOf<String, Int?>()

    suspend fun fetchInstallCandidate(
        url: String,
        customName: String?
    ): HttpLocalScraperInstallCandidate? = withContext(Dispatchers.IO) {
        val manifestUrl = manifestUrlFor(url)
        val manifest = fetchManifest(manifestUrl) ?: return@withContext null
        val httpScrapers = manifest.scrapers.filter { it.isHttpOnlyEnabled() }
        if (httpScrapers.isEmpty()) return@withContext null

        val stableId = "http.local.${shortHash(manifestUrl)}"
        val addonManifest = AddonManifest(
            id = stableId,
            name = sanitizeProviderLabel(customName?.trim()?.takeIf { it.isNotBlank() } ?: manifest.name),
            version = manifest.version,
            description = "HTTP local scraper bundle (${httpScrapers.size} HTTP providers)",
            types = listOf("movie", "series"),
            resources = listOf(
                AddonResource(
                    name = "stream",
                    types = listOf("movie", "series"),
                    idPrefixes = listOf("tt")
                )
            ),
            behaviorHints = AddonBehaviorHints(p2p = false)
        )
        HttpLocalScraperInstallCandidate(
            name = addonManifest.name,
            version = manifest.version,
            description = addonManifest.description,
            logo = httpScrapers.firstNotNullOfOrNull { it.logo?.takeIf(String::isNotBlank) },
            manifest = addonManifest,
            transportUrl = manifestUrl.substringBeforeLast('/', missingDelimiterValue = manifestUrl)
        )
    }

    fun canHandle(addon: Addon): Boolean {
        val manifestId = addon.manifest?.id ?: return false
        return manifestId.startsWith(HTTP_LOCAL_MANIFEST_PREFIX) ||
            manifestId.startsWith(LEGACY_LOCAL_MANIFEST_PREFIX)
    }

    suspend fun resolveMovieStreams(
        addon: Addon,
        imdbId: String,
        title: String,
        year: Int?
    ): List<StreamSource> {
        val manifest = addon.url?.let { fetchManifest(manifestUrlFor(it)) } ?: return emptyList()
        val tmdbId = resolveTmdbId(imdbId, mediaType = "movie") ?: return emptyList()
        return resolveHttpStreams(
            addon = addon,
            manifest = manifest,
            tmdbId = tmdbId,
            mediaType = "movie",
            season = null,
            episode = null,
            fallbackTitle = title,
            fallbackYear = year
        )
    }

    suspend fun resolveEpisodeStreams(
        addon: Addon,
        imdbId: String,
        season: Int,
        episode: Int,
        tmdbId: Int?,
        title: String
    ): List<StreamSource> {
        val manifest = addon.url?.let { fetchManifest(manifestUrlFor(it)) } ?: return emptyList()
        val resolvedTmdbId = tmdbId ?: resolveTmdbId(imdbId, mediaType = "tv") ?: return emptyList()
        return resolveHttpStreams(
            addon = addon,
            manifest = manifest,
            tmdbId = resolvedTmdbId,
            mediaType = "tv",
            season = season,
            episode = episode,
            fallbackTitle = title,
            fallbackYear = null
        )
    }

    private suspend fun resolveHttpStreams(
        addon: Addon,
        manifest: HttpScraperManifest,
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<StreamSource> = coroutineScope {
        val providers = manifest.scrapers
            .filter { it.isHttpOnlyEnabled() }
            .map { it.id.lowercase(Locale.US) }
            .toSet()

        val jobs = buildList {
            if ("multivid" in providers || "videasy" in providers) {
                add(async(Dispatchers.IO) { resolveVidEasy(tmdbId, mediaType, season, episode, fallbackTitle, fallbackYear) })
            }
            if ("multivid" in providers || "vidlink" in providers) {
                add(async(Dispatchers.IO) { resolveVidLink(tmdbId, mediaType, season, episode) })
            }
            if ("multivid" in providers || "vidsrc" in providers || "vixsrc" in providers) {
                add(async(Dispatchers.IO) { resolveVidSrc(tmdbId, mediaType, season, episode) })
            }
        }
        jobs.awaitAll()
            .flatten()
            .filter { it.url.startsWith("http://", ignoreCase = true) || it.url.startsWith("https://", ignoreCase = true) }
            .filterNot { it.url.startsWith("magnet:", ignoreCase = true) || it.url.contains("btih:", ignoreCase = true) }
            .distinctBy { it.url }
            .take(50)
            .map { stream -> stream.toStreamSource(addon) }
    }

    private suspend fun resolveVidEasy(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?,
        fallbackTitle: String,
        fallbackYear: Int?
    ): List<HttpResolvedStream> {
        val details = fetchTmdbDetails(tmdbId, mediaType, fallbackTitle, fallbackYear)
        val servers = listOf(
            "Neon" to "https://api.videasy.net/myflixerzupcloud/sources-with-title",
            "Yoru" to "https://api.videasy.net/cdn/sources-with-title",
            "Cypher" to "https://api.videasy.net/moviebox/sources-with-title",
            "Reyna" to "https://api.videasy.net/primewire/sources-with-title",
            "Omen" to "https://api.videasy.net/onionplay/sources-with-title",
            "Breach" to "https://api.videasy.net/m4uhd/sources-with-title",
            "Ghost" to "https://api.videasy.net/primesrcme/sources-with-title",
            "Sage" to "https://api.videasy.net/1movies/sources-with-title",
            "Vyse" to "https://api.videasy.net/hdmovie/sources-with-title",
            "Raze" to "https://api.videasy.net/superflix/sources-with-title"
        )
        return coroutineScope {
            servers.map { (name, endpoint) ->
                async(Dispatchers.IO) {
                    runCatching {
                        var url = "$endpoint?title=${details.title.urlEncode()}" +
                            "&mediaType=${details.mediaType}&year=${details.year.orEmpty()}" +
                            "&tmdbId=$tmdbId&imdbId=${details.imdbId.orEmpty()}"
                        if (mediaType == "tv") {
                            if (name == "Yoru") return@runCatching emptyList<HttpResolvedStream>()
                            url += "&seasonId=${season ?: 1}&episodeId=${episode ?: 1}"
                        }
                        val encrypted = getText(url, VIDEASY_HEADERS).takeIf { it.length > 20 && !it.startsWith("<!") }
                            ?: return@runCatching emptyList()
                        val decrypted = postJson(
                            url = "https://enc-dec.app/api/dec-videasy",
                            body = """{"text":${gson.toJson(encrypted)},"id":"$tmdbId"}"""
                        )
                        val result = decrypted?.getObject("result") ?: decrypted
                        (result?.getArray("sources")?.toList().orEmpty()).mapNotNull { source: JsonElement ->
                            val obj = source.asJsonObjectOrNull() ?: return@mapNotNull null
                            val streamUrl = obj.string("url") ?: return@mapNotNull null
                            HttpResolvedStream(
                                provider = "VIDEASY $name",
                                title = "VIDEASY $name ${obj.string("quality").orEmpty()}".trim(),
                                url = streamUrl,
                                quality = obj.string("quality") ?: "Auto",
                                headers = mapOf(
                                    "Referer" to "https://player.videasy.net/",
                                    "Origin" to "https://player.videasy.net",
                                    "User-Agent" to USER_AGENT
                                )
                            )
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun resolveVidLink(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): List<HttpResolvedStream> = runCatching {
        val encrypted = getJson("https://enc-dec.app/api/enc-vidlink?text=${tmdbId.toString().urlEncode()}")
            ?.string("result")
            ?: return@runCatching emptyList()
        val url = if (mediaType == "tv") {
            "https://vidlink.pro/api/b/tv/$encrypted/${season ?: 1}/${episode ?: 1}?multiLang=0"
        } else {
            "https://vidlink.pro/api/b/movie/$encrypted?multiLang=0"
        }
        val payload = getJson(url, VIDLINK_HEADERS) ?: return@runCatching emptyList()
        val playlist = payload.getObject("stream")?.string("playlist") ?: return@runCatching emptyList()
        listOf(
            HttpResolvedStream(
                provider = "VidLink",
                title = "VidLink Primary",
                url = playlist,
                quality = "Auto",
                headers = mapOf("Referer" to "https://vidlink.pro/", "Origin" to "https://vidlink.pro")
            )
        )
    }.getOrDefault(emptyList())

    private suspend fun resolveVidSrc(
        tmdbId: Int,
        mediaType: String,
        season: Int?,
        episode: Int?
    ): List<HttpResolvedStream> = runCatching {
        val meta = fetchTmdbDetails(tmdbId, mediaType, "", null)
        val imdbId = meta.imdbId ?: return@runCatching emptyList<HttpResolvedStream>()
        val embedUrl = if (mediaType == "tv") {
            "https://vsrc.su/embed/tv?imdb=$imdbId&season=${season ?: 1}&episode=${episode ?: 1}"
        } else {
            "https://vsrc.su/embed/$imdbId"
        }
        val embedHtml = getText(embedUrl)
        val iframeSrc = IFRAME_SRC_REGEX.find(embedHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return@runCatching emptyList<HttpResolvedStream>()
        val iframeUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
        val iframeHtml = getText(iframeUrl, mapOf("Referer" to "https://vsrc.su/"))
        val prorcpSrc = PRORCP_SRC_REGEX.find(iframeHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?: return@runCatching emptyList<HttpResolvedStream>()
        val cloudUrl = URL(URL("https://cloudnestra.com/"), prorcpSrc).toString()
        val cloudHtml = getText(cloudUrl, mapOf("Referer" to "https://cloudnestra.com/"))
        val divMatch = DIV_MATCH_REGEX.find(cloudHtml) ?: return@runCatching emptyList<HttpResolvedStream>()
        val decrypted = postJson(
            url = "https://enc-dec.app/api/dec-cloudnestra",
            body = """{"text":${gson.toJson(divMatch.groupValues[2])},"div_id":${gson.toJson(divMatch.groupValues[1])}}"""
        )
        (decrypted?.getArray("result")?.toList().orEmpty()).mapIndexedNotNull { index: Int, element: JsonElement ->
            val streamUrl = element.asStringOrNull() ?: return@mapIndexedNotNull null
            HttpResolvedStream(
                provider = "VidSrc",
                title = "VidSrc Server ${index + 1}",
                url = streamUrl,
                quality = "Auto",
                headers = mapOf(
                    "Referer" to "https://cloudnestra.com/",
                    "Origin" to "https://cloudnestra.com"
                )
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun fetchTmdbDetails(
        tmdbId: Int,
        mediaType: String,
        fallbackTitle: String,
        fallbackYear: Int?
    ): HttpScraperTmdbDetails {
        return runCatching {
            val type = if (mediaType == "tv") "tv" else "movie"
            val payload = getJson(
                "https://api.themoviedb.org/3/$type/$tmdbId?api_key=${Constants.TMDB_API_KEY}&append_to_response=external_ids"
            )
            val title = payload?.string(if (type == "tv") "name" else "title")
                ?: fallbackTitle
            val date = payload?.string(if (type == "tv") "first_air_date" else "release_date")
            val year = date?.take(4)?.takeIf { it.all(Char::isDigit) } ?: fallbackYear?.toString()
            val imdbId = payload?.getObject("external_ids")?.string("imdb_id")
                ?: payload?.string("imdb_id")
            HttpScraperTmdbDetails(tmdbId.toString(), title, year, imdbId, type)
        }.getOrElse {
            HttpScraperTmdbDetails(tmdbId.toString(), fallbackTitle, fallbackYear?.toString(), null, mediaType)
        }
    }

    private suspend fun resolveTmdbId(imdbId: String, mediaType: String): Int? {
        val clean = imdbId.trim().takeIf { it.matches(IMDB_ID_REGEX) } ?: return null
        val key = "$mediaType:$clean"
        synchronized(tmdbIdCache) {
            if (tmdbIdCache.containsKey(key)) return tmdbIdCache[key]
        }
        val resolved = runCatching {
            val find = tmdbApi.findByExternalId(clean, Constants.TMDB_API_KEY)
            if (mediaType == "tv") find.tvResults.firstOrNull()?.id else find.movieResults.firstOrNull()?.id
        }.getOrNull()
        synchronized(tmdbIdCache) { tmdbIdCache[key] = resolved }
        return resolved
    }

    private suspend fun fetchManifest(manifestUrl: String): HttpScraperManifest? {
        synchronized(manifestCache) {
            manifestCache[manifestUrl]?.let { return it }
        }
        val parsed = runCatching {
            val json = getText(manifestUrl)
            gson.fromJson(json, HttpScraperManifest::class.java)
        }.getOrNull()?.takeIf { it.name.isNotBlank() && it.scrapers.isNotEmpty() }
        if (parsed != null) {
            synchronized(manifestCache) { manifestCache[manifestUrl] = parsed }
        }
        return parsed
    }

    private suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(okhttp3.Headers.headersOf(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.string().orEmpty()
        }
    }

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject? {
        return runCatching { gson.fromJson(getText(url, headers), JsonObject::class.java) }.getOrNull()
    }

    private suspend fun postJson(url: String, body: String): JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            runCatching { gson.fromJson(response.body?.string().orEmpty(), JsonObject::class.java) }.getOrNull()
        }
    }

    private fun manifestUrlFor(url: String): String {
        val clean = url.trim().substringBefore('#').trimEnd('/')
        return if (clean.endsWith("/manifest.json", ignoreCase = true)) clean else "$clean/manifest.json"
    }

    private fun shortHash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    private fun HttpScraperEntry.isHttpOnlyEnabled(): Boolean {
        if (!enabled) return false
        val normalizedFormats = formats.map { it.lowercase(Locale.US) }.toSet()
        if (normalizedFormats.any { it in P2P_FORMATS }) return false
        return normalizedFormats.isEmpty() || normalizedFormats.any { it in HTTP_FORMATS }
    }

    private fun HttpResolvedStream.toStreamSource(addon: Addon): StreamSource {
        val cleanHeaders = headers
            .mapKeys { it.key.trim() }
            .mapValues { it.value.trim() }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        return StreamSource(
            source = title.ifBlank { provider },
            addonName = "${sanitizeProviderLabel(addon.name)} - $provider",
            addonId = addon.id,
            quality = normalizeQuality(quality),
            size = "",
            sizeBytes = null,
            url = url,
            infoHash = null,
            fileIdx = null,
            behaviorHints = cleanHeaders
                .takeIf { it.isNotEmpty() }
                ?.let { StreamBehaviorHints(proxyHeaders = ProxyHeaders(request = it)) },
            subtitles = emptyList(),
            sources = emptyList()
        )
    }

    private fun normalizeQuality(value: String): String {
        val text = value.lowercase(Locale.US)
        return when {
            "2160" in text || "4k" in text -> "4K"
            "1440" in text -> "1440p"
            "1080" in text -> "1080p"
            "720" in text -> "720p"
            "480" in text -> "480p"
            "360" in text -> "360p"
            else -> "Auto"
        }
    }

    private fun sanitizeProviderLabel(value: String): String {
        return value.replace(NUVIO_REGEX, "HTTP").trim()
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
        .replace("+", "%20")

    private fun JsonObject.string(name: String): String? = get(name)?.asStringOrNull()
    private fun JsonObject.getObject(name: String): JsonObject? = get(name)?.asJsonObjectOrNull()
    private fun JsonObject.getArray(name: String): JsonArray? = get(name)?.asJsonArrayOrNull()
    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonElement.asJsonArrayOrNull(): JsonArray? = if (isJsonArray) asJsonArray else null
    private fun JsonElement.asStringOrNull(): String? = runCatching {
        if (isJsonNull) null else asString
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private data class HttpScraperManifest(
        val name: String = "",
        val version: String = "1.0.0",
        val scrapers: List<HttpScraperEntry> = emptyList()
    )

    private data class HttpScraperEntry(
        val id: String = "",
        val name: String = "",
        val enabled: Boolean = false,
        val formats: List<String> = emptyList(),
        val logo: String? = null
    )

    private data class HttpScraperTmdbDetails(
        val id: String,
        val title: String,
        val year: String?,
        val imdbId: String?,
        val mediaType: String
    )

    private data class HttpResolvedStream(
        val provider: String,
        val title: String,
        val url: String,
        val quality: String,
        val headers: Map<String, String> = emptyMap()
    )

    companion object {
        private val IFRAME_SRC_REGEX = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val PRORCP_SRC_REGEX = Regex("""src:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        private val DIV_MATCH_REGEX = Regex(
            """<div id="([^"]+)"[^>]*style=["']display\s*:\s*none;?["'][^>]*>([a-zA-Z0-9:/.,{}\-_=+ ]+)</div>""",
            RegexOption.IGNORE_CASE
        )
        private val IMDB_ID_REGEX = Regex("tt\\d{5,}")
        private val NUVIO_REGEX = Regex("nu" + "vio", RegexOption.IGNORE_CASE)
        private const val HTTP_LOCAL_MANIFEST_PREFIX = "http.local."
        private const val LEGACY_LOCAL_MANIFEST_PREFIX = "nu" + "vio.local."
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        private val HTTP_FORMATS = setOf("mp4", "mkv", "m3u8", "hls", "dash")
        private val P2P_FORMATS = setOf("torrent", "magnet", "p2p", "infohash")
        private val VIDEASY_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Origin" to "https://player.videasy.net",
            "Referer" to "https://player.videasy.net/"
        )
        private val VIDLINK_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json,*/*",
            "Referer" to "https://vidlink.pro/",
            "Origin" to "https://vidlink.pro"
        )
    }
}
