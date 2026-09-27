package com.arflix.tv.ui.focus

/**
 * TV D-pad section navigation for Details-style screens (Issue 5, Layer 1).
 *
 * Extracted verbatim from DetailsScreen's private `FocusSection` enum and
 * `handleLeft` / `handleRight` helpers plus its inline up/down transition
 * tables, so the rules are unit-testable and reusable by
 * CategoryViewAllScreen / CollectionDetailsScreen without copy-paste.
 *
 * All functions here are pure: they take the focused section, per-section
 * indices and a [DetailsSectionContent] snapshot, and return the outcome.
 * Compose state ownership stays in the screen; this file owns the rules.
 *
 * Canonical vertical order (both directions skip unavailable sections):
 * BUTTONS, SEASONS, EPISODES, RATINGS, CAST, REVIEWS, COLLECTION, SIMILAR.
 */
enum class FocusSection {
    BUTTONS, EPISODES, SEASONS, RATINGS, CAST, REVIEWS, SIMILAR, COLLECTION
}

/** Per-section cursor positions, mirroring the screen's index vars. */
data class DetailsFocusIndices(
    val buttonIndex: Int = 0,
    val episodeIndex: Int = 0,
    val ratingsIndex: Int = 0,
    val seasonIndex: Int = 0,
    val castIndex: Int = 0,
    val reviewIndex: Int = 0,
    val similarIndex: Int = 0,
    val collectionIndex: Int = 0
)

/**
 * Content-availability snapshot driving bounds and section skipping.
 * [ratingsPageCount] is the 12-per-page pagination of episode ratings;
 * [maxButtonIndex] is 5 when a collection CTA exists, else 4.
 */
data class DetailsSectionContent(
    val episodeCount: Int = 0,
    val totalSeasons: Int = 1,
    val ratingsPageCount: Int = 0,
    val castCount: Int = 0,
    val reviewCount: Int = 0,
    val similarCount: Int = 0,
    val collectionCount: Int = 0,
    val maxButtonIndex: Int = 4,
    val isTv: Boolean = true,
    val hasRatingsSection: Boolean = false
) {
    fun itemCount(section: FocusSection): Int = when (section) {
        FocusSection.BUTTONS -> maxButtonIndex + 1
        FocusSection.EPISODES -> episodeCount
        FocusSection.SEASONS -> totalSeasons
        FocusSection.RATINGS -> ratingsPageCount
        FocusSection.CAST -> castCount
        FocusSection.REVIEWS -> reviewCount
        FocusSection.SIMILAR -> similarCount
        FocusSection.COLLECTION -> collectionCount
    }

    fun isAvailable(section: FocusSection): Boolean = when (section) {
        FocusSection.BUTTONS -> true
        FocusSection.SEASONS -> isTv && totalSeasons > 1
        FocusSection.EPISODES -> isTv && episodeCount > 0
        FocusSection.RATINGS -> hasRatingsSection
        FocusSection.CAST -> castCount > 0
        FocusSection.REVIEWS -> reviewCount > 0
        FocusSection.SIMILAR -> similarCount > 0
        FocusSection.COLLECTION -> collectionCount > 0
    }
}

fun indexInSection(section: FocusSection, indices: DetailsFocusIndices): Int = when (section) {
    FocusSection.BUTTONS -> indices.buttonIndex
    FocusSection.EPISODES -> indices.episodeIndex
    FocusSection.RATINGS -> indices.ratingsIndex
    FocusSection.SEASONS -> indices.seasonIndex
    FocusSection.CAST -> indices.castIndex
    FocusSection.REVIEWS -> indices.reviewIndex
    FocusSection.SIMILAR -> indices.similarIndex
    FocusSection.COLLECTION -> indices.collectionIndex
}

/** True when the cursor is on the first item — the host routes Left to the sidebar. */
fun isAtLeftmost(section: FocusSection, indices: DetailsFocusIndices): Boolean =
    indexInSection(section, indices) == 0

fun handleLeft(
    section: FocusSection,
    buttonIdx: Int, episodeIdx: Int, ratingsIdx: Int, seasonIdx: Int, castIdx: Int, reviewIdx: Int, similarIdx: Int,
    collectionIdx: Int,
    setButton: (Int) -> Unit, setEpisode: (Int) -> Unit, setRatings: (Int) -> Unit, setSeason: (Int) -> Unit,
    setCast: (Int) -> Unit, setReview: (Int) -> Unit, setSimilar: (Int) -> Unit,
    setCollection: (Int) -> Unit
): Boolean {
    when (section) {
        FocusSection.BUTTONS -> if (buttonIdx > 0) setButton(buttonIdx - 1)
        FocusSection.EPISODES -> if (episodeIdx > 0) setEpisode(episodeIdx - 1)
        FocusSection.RATINGS -> if (ratingsIdx > 0) setRatings(ratingsIdx - 1)
        FocusSection.SEASONS -> if (seasonIdx > 0) setSeason(seasonIdx - 1)
        FocusSection.CAST -> if (castIdx > 0) setCast(castIdx - 1)
        FocusSection.REVIEWS -> if (reviewIdx > 0) setReview(reviewIdx - 1)
        FocusSection.SIMILAR -> if (similarIdx > 0) setSimilar(similarIdx - 1)
        FocusSection.COLLECTION -> if (collectionIdx > 0) setCollection(collectionIdx - 1)
    }
    return true
}

fun handleRight(
    section: FocusSection,
    buttonIdx: Int, episodeIdx: Int, ratingsIdx: Int, seasonIdx: Int, castIdx: Int, reviewIdx: Int, similarIdx: Int,
    collectionIdx: Int,
    maxButtonIndex: Int,
    episodeCount: Int,
    ratingsPageCount: Int,
    seasonCount: Int,
    castCount: Int,
    reviewCount: Int,
    similarCount: Int,
    collectionCount: Int,
    setButton: (Int) -> Unit, setEpisode: (Int) -> Unit, setRatings: (Int) -> Unit, setSeason: (Int) -> Unit,
    setCast: (Int) -> Unit, setReview: (Int) -> Unit, setSimilar: (Int) -> Unit,
    setCollection: (Int) -> Unit
): Boolean {
    when (section) {
        FocusSection.BUTTONS -> {
            if (buttonIdx < maxButtonIndex) setButton(buttonIdx + 1)
        }
        FocusSection.EPISODES -> if (episodeIdx < episodeCount - 1) setEpisode(episodeIdx + 1)
        FocusSection.RATINGS -> {
            if (ratingsIdx < ratingsPageCount - 1) setRatings(ratingsIdx + 1)
        }
        FocusSection.SEASONS -> if (seasonIdx < seasonCount - 1) setSeason(seasonIdx + 1)
        FocusSection.CAST -> if (castIdx < castCount - 1) setCast(castIdx + 1)
        FocusSection.REVIEWS -> if (reviewIdx < reviewCount - 1) setReview(reviewIdx + 1)
        FocusSection.SIMILAR -> if (similarIdx < similarCount - 1) setSimilar(similarIdx + 1)
        FocusSection.COLLECTION -> if (collectionIdx < collectionCount - 1) setCollection(collectionIdx + 1)
    }
    return true
}

/**
 * Section reached by DPAD_UP. BUTTONS maps to itself — the host additionally
 * moves focus into the sidebar (side effect, kept at the call site).
 */
fun moveUpSection(section: FocusSection, content: DetailsSectionContent): FocusSection {
    val hasEpisodes = content.episodeCount > 0
    val hasCast = content.castCount > 0
    return when (section) {
        FocusSection.BUTTONS -> FocusSection.BUTTONS
        FocusSection.SEASONS -> FocusSection.BUTTONS
        FocusSection.EPISODES -> {
            if (content.totalSeasons > 1) FocusSection.SEASONS else FocusSection.BUTTONS
        }
        FocusSection.CAST -> {
            if (content.isTv) {
                when {
                    content.hasRatingsSection -> FocusSection.RATINGS
                    hasEpisodes -> FocusSection.EPISODES
                    content.totalSeasons > 1 -> FocusSection.SEASONS
                    else -> FocusSection.BUTTONS
                }
            } else FocusSection.BUTTONS
        }
        FocusSection.RATINGS -> {
            when {
                hasEpisodes -> FocusSection.EPISODES
                content.totalSeasons > 1 -> FocusSection.SEASONS
                else -> FocusSection.BUTTONS
            }
        }
        FocusSection.REVIEWS -> if (hasCast) FocusSection.CAST else FocusSection.BUTTONS
        FocusSection.SIMILAR -> {
            if (content.collectionCount > 0) FocusSection.COLLECTION
            else if (content.reviewCount > 0) FocusSection.REVIEWS
            else if (hasCast) FocusSection.CAST
            else FocusSection.BUTTONS
        }
        FocusSection.COLLECTION -> {
            if (content.reviewCount > 0) FocusSection.REVIEWS
            else if (hasCast) FocusSection.CAST
            else FocusSection.BUTTONS
        }
    }
}

/**
 * Section reached by DPAD_DOWN. A section with nothing below it maps to
 * itself (focus stays); BUTTONS with no content below also stays.
 */
fun moveDownSection(section: FocusSection, content: DetailsSectionContent): FocusSection {
    val hasEpisodes = content.episodeCount > 0
    val hasSeasons = content.totalSeasons > 1
    val hasCast = content.castCount > 0
    val hasReviews = content.reviewCount > 0
    val hasSimilar = content.similarCount > 0
    val hasCollection = content.collectionCount > 0
    return when (section) {
        FocusSection.BUTTONS -> {
            if (content.isTv && hasSeasons) FocusSection.SEASONS
            else if (content.isTv && hasEpisodes) FocusSection.EPISODES
            else if (content.hasRatingsSection) FocusSection.RATINGS
            else if (hasCast) FocusSection.CAST
            else if (hasReviews) FocusSection.REVIEWS
            else if (hasCollection) FocusSection.COLLECTION
            else if (hasSimilar) FocusSection.SIMILAR
            else FocusSection.BUTTONS
        }
        FocusSection.SEASONS -> {
            if (hasEpisodes) FocusSection.EPISODES
            else if (content.hasRatingsSection) FocusSection.RATINGS
            else if (hasCast) FocusSection.CAST
            else if (hasReviews) FocusSection.REVIEWS
            else if (hasCollection) FocusSection.COLLECTION
            else if (hasSimilar) FocusSection.SIMILAR
            else FocusSection.SEASONS
        }
        FocusSection.EPISODES -> {
            if (content.hasRatingsSection) FocusSection.RATINGS
            else if (hasCast) FocusSection.CAST
            else if (hasReviews) FocusSection.REVIEWS
            else if (hasCollection) FocusSection.COLLECTION
            else if (hasSimilar) FocusSection.SIMILAR
            else FocusSection.EPISODES
        }
        FocusSection.RATINGS -> {
            if (hasCast) FocusSection.CAST
            else if (hasReviews) FocusSection.REVIEWS
            else if (hasCollection) FocusSection.COLLECTION
            else if (hasSimilar) FocusSection.SIMILAR
            else FocusSection.RATINGS
        }
        FocusSection.CAST -> {
            if (hasReviews) FocusSection.REVIEWS
            else if (hasCollection) FocusSection.COLLECTION
            else if (hasSimilar) FocusSection.SIMILAR
            else FocusSection.CAST
        }
        FocusSection.REVIEWS -> {
            if (hasCollection) FocusSection.COLLECTION
            else if (hasSimilar) FocusSection.SIMILAR
            else FocusSection.REVIEWS
        }
        FocusSection.COLLECTION -> {
            if (hasSimilar) FocusSection.SIMILAR else FocusSection.COLLECTION
        }
        FocusSection.SIMILAR -> FocusSection.SIMILAR
    }
}
