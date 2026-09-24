package com.arflix.tv.data.repository.sync

import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.ContinueWatchingItem
import com.arflix.tv.data.repository.MdbListRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MDBList implementation of [RemoteSyncProvider]. Thin adapter over
 * [MdbListRepository]; the watched-state mirror (mark watched/unwatched) is
 * driven from the sync service, not here.
 */
@Singleton
class MdbListRemoteProvider @Inject constructor(
    private val repository: MdbListRepository
) : RemoteSyncProvider {

    override val provider: SyncProvider = SyncProvider.MDBLIST

    override suspend fun isConnected(): Boolean = repository.isConnected()

    override suspend fun addToWatchlist(mediaType: MediaType, tmdbId: Int, isAnime: Boolean): Boolean =
        repository.addToWatchlist(mediaType, tmdbId)

    override suspend fun removeFromWatchlist(mediaType: MediaType, tmdbId: Int, isAnime: Boolean): Boolean =
        repository.removeFromWatchlist(mediaType, tmdbId)

    override suspend fun getWatchlist(): RemoteWatchlistResult = repository.getWatchlist()

    override suspend fun scrobbleStart(
        mediaType: MediaType, tmdbId: Int, progress: Float, season: Int?, episode: Int?, isAnime: Boolean
    ) = repository.scrobble("start", mediaType, tmdbId, progress, season, episode)

    override suspend fun scrobblePause(
        mediaType: MediaType, tmdbId: Int, progress: Float, season: Int?, episode: Int?, isAnime: Boolean
    ) = repository.scrobble("pause", mediaType, tmdbId, progress, season, episode)

    override suspend fun scrobbleProgress(
        mediaType: MediaType, tmdbId: Int, progress: Float, season: Int?, episode: Int?, isAnime: Boolean
    ) =
        // A pause, not a start. Verified against the live API: a start removes
        // the title from /sync/playback and only a pause writes a position back,
        // so a start heartbeat destroys the resume point every time it fires and
        // losing power mid-episode discarded the session. MDBList has no
        // "watching now" endpoint to read the title back from either, so the
        // paused record is the only thing keeping it in Continue Watching.
        repository.scrobble("pause", mediaType, tmdbId, progress, season, episode)

    override suspend fun scrobbleStop(
        mediaType: MediaType, tmdbId: Int, progress: Float, season: Int?, episode: Int?, isAnime: Boolean
    ) = repository.scrobble("stop", mediaType, tmdbId, progress, season, episode)

    override suspend fun getWatchedMovies(): Set<Int> = repository.getWatchedMovies()

    override suspend fun getWatchedEpisodes(): Set<String> = repository.getWatchedEpisodes()

    override suspend fun getContinueWatching(forceRefresh: Boolean): List<ContinueWatchingItem> =
        repository.getContinueWatching(forceRefresh)

    override suspend fun dismissContinueWatching(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): Boolean {
        repository.clearPlayback(mediaType, tmdbId, season, episode)
        return true
    }
}
