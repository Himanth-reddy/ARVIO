package com.arflix.tv.data.model

/**
 * Episode structuring / ordering style for anime content.
 *
 * [BROADCAST] structures episodes into official broadcast seasons / cours
 * (mapped via Kitsu / Anime Relations Mapping to TMDB canonical episodes).
 *
 * [STANDARD] retains TMDB's canonical Western season structuring.
 */
enum class AnimeStructuringStyle(val id: String) {
    BROADCAST("broadcast"),
    STANDARD("standard");

    companion object {
        const val PREFERENCE_KEY = "anime_episode_structuring"

        // Compatibility aliases
        val JAPANESE = BROADCAST
        val WESTERN = STANDARD

        fun fromId(id: String?): AnimeStructuringStyle = when (id?.trim()?.lowercase()) {
            STANDARD.id, "western" -> STANDARD
            else -> BROADCAST
        }
    }
}
