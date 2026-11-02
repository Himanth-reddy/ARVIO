package com.arflix.tv.data.repository

import com.arflix.tv.data.model.CollectionGroupKind
import com.arflix.tv.data.model.CollectionSourceConfig
import com.arflix.tv.data.model.CollectionSourceKind
import com.arflix.tv.data.model.CollectionTileShape
import java.util.Locale

/** ARVIO-authored queries. Artwork is TMDB metadata, not an imported creator's pack. */
private object ArvioCollectionsRegexes {
    val NON_ALPHA_NUM_REGEX = Regex("[^a-z0-9]+")
}

internal object ArvioCollections {
    const val DISABLED_MARKER = "collection_defaults_disabled"
    private const val SCIENCE = "/8sNiAPPYU14PUepFNeSNGUTiHW.jpg"
    private const val COMEDY = "/jK65srQczOKTpW62wPxwwKztGgE.jpg"
    private const val FAMILY = "/eCynaAOgYYiw5yN5lBwz3IxqvaW.jpg"
    private const val CRIME = "/tSPT36ZKlP2WVHJLM4cQPLSzv3b.jpg"
    private const val HORROR = "/AmR3JG1VQVxU8TfAvljUhfSFUOx.jpg"
    private const val DOCUMENTARY = "/z2uuQasY4gQJ8VDAFki746JWeQJ.jpg"
    private const val ANIMATION = "/mMtUybQ6hL24FXo0F3Z4j2KG7kZ.jpg"
    private const val ACTION = "/gqrnQA6Xppdl8vIb2eJc58VC1tW.jpg"
    private const val THRILLER = "/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg"
    private const val FANTASY = "/zRKQW58MBEY078AxkHxEJzUskCl.jpg"
    private const val ADVENTURE = "/uRNgkJSkNBFbbn9fPsEjDIy8Sh3.jpg"

    val entries: List<CollectionTemplateEntry> = buildList {
        add(entry("Audience favourites", CollectionGroupKind.FEATURED, SCIENCE,
            "Highly rated films with at least 1,000 audience votes.", listOf(
                discover("movie", "vote_average.desc", "vote_count.gte" to "1000"))))
        add(entry("Acclaimed series", CollectionGroupKind.FEATURED, THRILLER,
            "Standout television, ranked by viewers with at least 500 votes.", listOf(
                discover("series", "vote_average.desc", "vote_count.gte" to "500"))))
        add(entry("Under two hours", CollectionGroupKind.FEATURED, COMEDY,
            "Well-rated films for an evening, all between 60 and 120 minutes.", listOf(
                discover("movie", "popularity.desc", "with_runtime.gte" to "60", "with_runtime.lte" to "120",
                    "vote_average.gte" to "7", "vote_count.gte" to "300"))))
        add(entry("Japanese animation", CollectionGroupKind.FEATURED, ANIMATION,
            "Animated films and series from Japan, from intimate stories to imaginative adventures.",
            listOf("movie", "series").map {
                discover(it, "popularity.desc", "with_genres" to "16", "with_original_language" to "ja")
            }))

        add(service("Netflix", 8, "/rK1KljqmbvO9HQa1PBFLILWah72.png"))
        add(service("Disney+", 337, "/5eZ872CghnHFLB1j8grszbrx0dx.png"))
        add(service("Apple TV+", 350, "/9icYBfYFcwgCbky5VdGUIKJ4C5i.png"))
        add(service("Prime Video", 9, "/gMZdpavHmxFNnLpMHwVxfqeux2g.png"))
        add(service("HBO Max", 1899, "/skypuy7SXuugIQeYg0IglmzoKaS.png"))
        add(service("Hulu", 15, "/44uAnmSqvA4yBOdbPWN8YgQHjWm.png"))
        add(service("Paramount+", 2303, "/4N4BMd0Mm0kHAmF7RZgL5lW3cwc.png", 2616))
        add(service("Peacock", 386, "/a1UIdq5BrkcAxnxcUhFsNbXnxeu.png"))
        add(service("Starz", 43, "/h25xjouKmiSmFiiqw0aDXbxGZo7.png"))
        add(service("Shudder", 99, "/58O6yqUFM6qoOiBNAddJs7xNKc.png"))
        add(service("MGM+", 34, "/q63Uzpu7JAs566vA2G23Lk7LcID.png"))
        add(service("Discovery+", 520, "/tHseJEgZaUdVlLtBpOGmKPfzbQ8.png"))
        add(service("Crunchyroll", 283, "/uFL3c4Cq8M6WoLymlC5Y8bmGytV.png"))

        add(genre("Action", 28, 10759, ACTION, "High stakes, daring escapes and larger-than-life adventures."))
        add(genre("Adventure", 12, 10759, ADVENTURE, "Journeys to unfamiliar places and stories of discovery."))
        add(genre("Comedy", 35, 35, COMEDY, "Sharp comedies, familiar favourites and stories with a lighter touch."))
        add(genre("Crime", 80, 80, CRIME, "Investigations, criminal empires and lives on the wrong side of the law."))
        add(genre("Documentary", 99, 99, DOCUMENTARY, "Real stories and remarkable people, places and ideas."))
        add(genre("Family", 10751, 10751, FAMILY, "Films and series to discover together."))
        add(genre("Fantasy", 14, 10765, FANTASY, "Mythical worlds, extraordinary powers and imagined possibilities."))
        add(genre("Horror", 27, null, HORROR, "Unsettling mysteries, supernatural stories and cinematic nightmares."))
        add(genre("Science Fiction", 878, 10765, SCIENCE, "New worlds, speculative futures and journeys beyond our own."))
        add(genre("Thriller", 53, null, THRILLER, "Tense stories built on uncertainty, intrigue and suspense."))

        add(franchise("Star Wars", 10, "/iY2ujEY2m68OTTlPFTiHub9joHS.jpg", "The Skywalker films, in release order."))
        add(franchise("Lord of the Rings", 119, "/bccR2CGTWVVSZAG0yqmy3DIvhTX.jpg", "The journey through Middle-earth, in release order."))
        add(franchise("Harry Potter", 1241, "/4gV0rKUjB1nLUdZB4zIltLvNZZr.jpg", "The Harry Potter films, from the first year to the final battle."))
        add(franchise("The Matrix", 2344, "/bRm2DEgUiYciDw3myHuYFInD7la.jpg", "Return to the Matrix, with the films arranged by release."))
        add(franchise("Pirates of the Caribbean", 295, "/wxgD3fB5lQ2sGJLog0rvXW049Pf.jpg", "The adventures of Captain Jack Sparrow and the pirates of the Caribbean."))
        add(franchise("Hunger Games", 131635, "/Ipp7cegtub4t0mu7xaKLQkYoGc.jpg", "The films of Panem, including its prequels, in release order."))
        add(franchise("Fast & Furious", 9485, "/z5A5W3WYJc3UVEWljSGwdjDgQ0j.jpg", "The main Fast & Furious films, from street racing to globe-spanning action."))
        add(franchise("Jurassic Park", 328, "/njFixYzIxX8jsn6KMSEtAzi4avi.jpg", "Jurassic Park and Jurassic World, in release order."))
        add(franchise("Terminator", 528, "/sCnBEw2Yu6foEjs4Xb4eMddYHRo.jpg", "The Terminator films and their changing futures, in release order."))
        add(franchise("The Dark Knight", 263, "/xyhrCEdB4XRkelfVsqXeUZ6rLHi.jpg", "Christopher Nolan's three-film Batman story."))

        for ((year, art) in listOf(1970 to CRIME, 1980 to HORROR, 1990 to FAMILY,
            2000 to ADVENTURE, 2010 to SCIENCE, 2020 to FANTASY)) {
            add(entry("${year}s", CollectionGroupKind.DECADE, art,
                "Explore the films of $year to ${year + 9}, ranked by audience rating.", listOf(
                    discover("movie", "vote_average.desc", "primary_release_date.gte" to "$year-01-01",
                        "primary_release_date.lte" to "${year + 9}-12-31", "vote_count.gte" to "300"))))
        }
    }

    fun preserveDisabledDefaults(hidden: Set<String>): Set<String> {
        val legacyRails = setOf("collection_rail_service", "collection_rail_genre", "collection_rail_franchise")
        val serviceIds = entries.filter { it.group == CollectionGroupKind.SERVICE }.map { it.id }
        // The old global switch hid every tile as well as its rail. Hiding just rails is not enough.
        val disabled = DISABLED_MARKER in hidden ||
            (hidden.containsAll(legacyRails) && hidden.containsAll(serviceIds))
        return if (disabled) hidden + DISABLED_MARKER + entries.map { it.id } +
            entries.map { "collection_rail_${it.group.name.lowercase(Locale.US)}" } else hidden
    }

    private fun entry(title: String, group: CollectionGroupKind, art: String, description: String,
        sources: List<CollectionSourceConfig>) = CollectionTemplateEntry(
        id = "collection_${group.name.lowercase(Locale.US)}_${slug(title)}",
        title = title, group = group, coverImageUrl = "https://image.tmdb.org/t/p/w780$art",
        tileShape = CollectionTileShape.LANDSCAPE, hideTitle = false, heroVideoUrl = null,
        sources = sources, listMetadata = emptyList(), description = description,
        heroImageUrl = if (group == CollectionGroupKind.SERVICE) null else "https://image.tmdb.org/t/p/w1280$art"
    )

    private fun service(title: String, provider: Int, logo: String, secondProvider: Int? = null): CollectionTemplateEntry =
        entry(title, CollectionGroupKind.SERVICE, logo,
            "Movies and series available through $title in the US catalog. Availability varies by region.",
            listOfNotNull(provider, secondProvider).flatMap { id -> listOf("movie", "series").map { type ->
                CollectionSourceConfig(kind = CollectionSourceKind.TMDB_WATCH_PROVIDER, mediaType = type,
                    tmdbWatchProviderId = id, watchRegion = "US", sortBy = "popularity.desc")
            } })

    private fun genre(title: String, movie: Int, series: Int?, art: String, description: String) =
        entry(title, CollectionGroupKind.GENRE, art, description, listOfNotNull(
            CollectionSourceConfig(kind = CollectionSourceKind.TMDB_GENRE, mediaType = "movie", tmdbGenreId = movie,
                sortBy = "popularity.desc"),
            series?.let { CollectionSourceConfig(kind = CollectionSourceKind.TMDB_GENRE, mediaType = "series",
                tmdbGenreId = it, sortBy = "popularity.desc") }
        ))

    private fun franchise(title: String, id: Int, art: String, description: String) =
        entry(title, CollectionGroupKind.FRANCHISE, art, description, listOf(
            CollectionSourceConfig(kind = CollectionSourceKind.TMDB_COLLECTION, mediaType = "movie", tmdbCollectionId = id)))

    private fun discover(type: String, sort: String, vararg filters: Pair<String, String>) =
        CollectionSourceConfig(kind = CollectionSourceKind.TMDB_DISCOVER, mediaType = type, sortBy = sort,
            discoverParams = mapOf("include_adult" to "false") + filters.toMap())

    private fun slug(title: String) = title.lowercase(Locale.US).replace("+", "plus")
        .replace("&", "and").replace(ArvioCollectionsRegexes.NON_ALPHA_NUM_REGEX, "_").trim('_')
}
