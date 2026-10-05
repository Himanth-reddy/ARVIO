package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.data.model.CalendarRelease
import com.arflix.tv.data.model.CalendarReleaseKind
import com.arflix.tv.data.model.MediaItem
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class CalendarMonthPreview(
    val entries: List<CalendarRelease>,
    val sourceCounts: Map<String, Int>,
    val refreshedAt: Long = System.currentTimeMillis()
)

/** Disposable, app-private cache; never backed up or used as an authoritative watchlist. */
@Singleton
class ReleaseCalendarCache internal constructor(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, "release-calendar-v1"))

    private val gson = Gson()
    private val fileMutex = Mutex()
    private var writesSinceTrim = 31

    // A wrapper permits both object responses and primitive/list metadata without reflection.
    internal suspend fun <T> readValue(key: String, type: Type): T? = withContext(Dispatchers.IO) {
        read("metadata:$key", METADATA_TTL)?.let {
            try { gson.fromJson<T>(it.get("value"), type) } catch (_: Exception) { null }
        }
    }

    internal suspend fun writeValue(key: String, value: Any): Unit = withContext(Dispatchers.IO) {
        write("metadata:$key", JsonObject().apply { add("value", gson.toJsonTree(value)) })
    }

    internal suspend fun readMonth(key: String): CalendarMonthPreview? = withContext(Dispatchers.IO) {
      read("month:$key", MONTH_TTL)?.let { json ->
        try {
            val refreshedAt = json.get("refreshedAt").asLong
            if (now() - refreshedAt !in 0 until MONTH_TTL) return@let null
            CalendarMonthPreview(json.getAsJsonArray("entries").map { element ->
                val item = element.asJsonObject
                CalendarRelease(
                    id = item.get("id").asString,
                    media = gson.fromJson(item.get("media"), MediaItem::class.java),
                    date = LocalDate.parse(item.get("date").asString),
                    releaseInstant = item.get("instant")?.asString?.let(Instant::parse),
                    kind = CalendarReleaseKind.valueOf(item.get("kind").asString),
                    seasonNumber = item.get("season")?.asInt,
                    episodeNumber = item.get("episode")?.asInt,
                    episodeTitle = item.get("episodeTitle")?.asString,
                    sourceIds = item.getAsJsonArray("sources").map { it.asString }.toSet(),
                    logoUrl = item.get("logo")?.asString,
                    region = item.get("region")?.asString
                )
            }, json.getAsJsonObject("counts").entrySet().associate { it.key to it.value.asInt }, refreshedAt)
        } catch (_: Exception) { null }
      }
    }

    internal suspend fun writeMonth(key: String, preview: CalendarMonthPreview): Unit = withContext(Dispatchers.IO) {
      write("month:$key", JsonObject().apply {
        addProperty("refreshedAt", preview.refreshedAt)
        add("counts", JsonObject().apply { preview.sourceCounts.forEach { (id, count) -> addProperty(id, count) } })
        add("entries", JsonArray().apply { preview.entries.forEach { entry ->
            add(JsonObject().apply {
                addProperty("id", entry.id)
                add("media", gson.toJsonTree(entry.media))
                addProperty("date", entry.date.toString())
                entry.releaseInstant?.let { addProperty("instant", it.toString()) }
                addProperty("kind", entry.kind.name)
                entry.seasonNumber?.let { addProperty("season", it) }
                entry.episodeNumber?.let { addProperty("episode", it) }
                entry.episodeTitle?.let { addProperty("episodeTitle", it) }
                add("sources", JsonArray().apply { entry.sourceIds.forEach { add(it) } })
                entry.logoUrl?.let { addProperty("logo", it) }
                entry.region?.let { addProperty("region", it) }
            })
        } })
      })
    }

    internal suspend fun clearMetadata(): Unit = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            directory.listFiles()?.filter { it.name.startsWith("metadata-") }?.forEach { it.delete() }
        }
    }

    private suspend fun read(key: String, ttl: Long): JsonObject? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            try {
                val file = file(key)
                val age = now() - file.lastModified()
                if (!file.isFile || age !in 0 until ttl) return@withLock null
                JsonParser.parseString(file.readText()).asJsonObject
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
        }
    }

    private suspend fun write(key: String, value: JsonObject): Unit = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            try {
                directory.mkdirs()
                val target = file(key)
                val temporary = File(directory, "${target.name}.tmp")
                temporary.writeText(gson.toJson(value))
                // Readers share the same lock, so neither partial JSON nor a half-replaced file is visible.
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) temporary.delete()
                target.setLastModified(now())
                if (++writesSinceTrim >= 32) {
                    writesSinceTrim = 0
                    directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
                        ?.drop(768)?.forEach { it.delete() }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Cache failures must never fail a calendar load. */ }
        }
    }

    private fun file(key: String) = File(directory, "${key.substringBefore(':')}-${calendarCacheDigest(key)}.json")

    private companion object {
        const val METADATA_TTL = 30 * 60_000L
        const val MONTH_TTL = 24 * 60 * 60_000L
    }
}

internal fun calendarCacheDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
