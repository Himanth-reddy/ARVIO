import type { CatalogConfig } from "./types";

export const defaultCatalogs: CatalogConfig[] = [
  // The profile's favorite IPTV channels (see favoriteTv.ts), not a TMDB list.
  { id: "favorite_tv", name: "Favorite TV", sourceType: "preinstalled", mediaType: "tv", enabled: true, isPreinstalled: true },
  { id: "trending_movies", name: "Trending in Movies", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/snoak/trending-movies", enabled: true, isPreinstalled: true },
  { id: "trending_tv", name: "Trending in Shows", sourceType: "mdblist", mediaType: "tv", sourceUrl: "https://mdblist.com/lists/snoak/trakt-s-trending-shows", enabled: true, isPreinstalled: true },
  { id: "trending_anime", name: "Trending in Anime", sourceType: "mdblist", mediaType: "tv", sourceUrl: "https://mdblist.com/lists/snoak/trending-anime-shows", enabled: true, isPreinstalled: true },
  { id: "top10_movies_today", name: "Top 10 Movies Today", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/snoak/top-10-movies-of-the-day", enabled: true, isPreinstalled: true },
  { id: "top10_shows_today", name: "Top 10 Shows Today", sourceType: "mdblist", mediaType: "tv", sourceUrl: "https://mdblist.com/lists/snoak/top-10-shows-of-the-day", enabled: true, isPreinstalled: true },
  { id: "just_added", name: "Just Added", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/snoak/latest-movies-digital-release", enabled: true, isPreinstalled: true },
  { id: "latest_tv", name: "Latest Airing", sourceType: "mdblist", mediaType: "tv", sourceUrl: "https://mdblist.com/lists/snoak/latest-tv-shows", enabled: true, isPreinstalled: true },
  { id: "top_movies_week", name: "Top Movies This Week", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/linaspurinis/top-watched-movies-of-the-week", enabled: true, isPreinstalled: true },
  { id: "new_kdramas", name: "New in K-Dramas", sourceType: "mdblist", mediaType: "tv", sourceUrl: "https://mdblist.com/lists/snoak/latest-kdrama-shows", enabled: true, isPreinstalled: true },
  { id: "coming_soon", name: "Coming Soon", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/snoak/upcoming-movies", enabled: true, isPreinstalled: true },
  { id: "action", name: "Popular Action", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/action-movies", enabled: true, isPreinstalled: true },
  { id: "comedy", name: "Popular Comedy", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/comedy-movies", enabled: true, isPreinstalled: true },
  { id: "scifi", name: "Popular Sci-Fi", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/science-fiction-movies", enabled: true, isPreinstalled: true },
  { id: "thriller", name: "Popular Thriller", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/thriller-movies", enabled: true, isPreinstalled: true },
  { id: "drama", name: "Popular Drama", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/drama-movies", enabled: true, isPreinstalled: true },
  { id: "horror", name: "Popular Horror", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/horror-movies", enabled: true, isPreinstalled: true },
  { id: "documentary", name: "Popular Documentary", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/popular-documentary-movies", enabled: true, isPreinstalled: true },
  { id: "romance", name: "Popular Romance", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/popular-romance-movies", enabled: true, isPreinstalled: true },
  { id: "animated", name: "Popular Animated", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/snoak/animationanime-movies", enabled: true, isPreinstalled: true },
  { id: "family", name: "Popular Family", sourceType: "mdblist", mediaType: "all", sourceUrl: "https://mdblist.com/lists/familytv133/family-kids-english-movies-rated-g-pg", enabled: true, isPreinstalled: true },
  { id: "bond", name: "James Bond Collection", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/hdlists/james-bond-movies", enabled: true, isPreinstalled: true },
  { id: "harry_potter", name: "Harry Potter Collection", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/thebirdod/harry-potter-collection", enabled: true, isPreinstalled: true },
  { id: "matrix", name: "The Matrix Collection", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/andyhawks/universe-the-matrix", enabled: true, isPreinstalled: true },
  { id: "lotr", name: "Lord of the Rings and Hobbit Collection", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/spudhead15/lord-of-the-rings-and-hobbit-collection", enabled: true, isPreinstalled: true },
  { id: "jurassic", name: "Jurassic Park Collection", sourceType: "mdblist", mediaType: "movie", sourceUrl: "https://mdblist.com/lists/purple_smurf/jurassic-park", enabled: true, isPreinstalled: true },
  { id: "tmdb_popular_movies", name: "Popular Movies", sourceType: "tmdb", mediaType: "movie", endpoint: "discover/movie", params: { sort_by: "popularity.desc" }, enabled: true, isPreinstalled: true },
  { id: "tmdb_popular_tv", name: "Popular Series", sourceType: "tmdb", mediaType: "tv", endpoint: "discover/tv", params: { sort_by: "popularity.desc" }, enabled: true, isPreinstalled: true }
];

function isValidCatalog(catalog: CatalogConfig | null | undefined): catalog is CatalogConfig {
  if (!catalog || typeof catalog !== "object") return false;
  if (!String(catalog.id ?? "").trim()) return false;
  if (!String(catalog.name ?? catalog.title ?? "").trim()) return false;
  if (!String(catalog.sourceType ?? "").trim()) return false;
  return true;
}

function normalizedSourceType(value: unknown): CatalogConfig["sourceType"] {
  const raw = String(value ?? "").trim().toLowerCase().replace(/_/g, "-");
  if (raw === "preinstalled") return "preinstalled";
  if (raw === "trakt") return "trakt";
  if (raw === "simkl") return "simkl";
  if (raw === "mdblist" || raw === "mdb-list") return "mdblist";
  if (raw === "addon") return "addon";
  if (raw === "home-server" || raw === "homeserver") return "home-server";
  if (raw === "template") return "template";
  if (raw === "tmdb") return "tmdb";
  return "preinstalled";
}

function normalizedLayout(catalog: CatalogConfig): CatalogConfig["layout"] {
  const shape = String(catalog.collectionTileShape ?? "").trim().toLowerCase();
  if (shape === "poster") return "poster";
  return catalog.layout ?? "landscape";
}

function normalizedCatalog(catalog: CatalogConfig): CatalogConfig {
  return {
    ...catalog,
    id: String(catalog.id).trim(),
    name: String(catalog.name ?? catalog.title).trim(),
    title: String(catalog.name ?? catalog.title).trim(),
    sourceType: normalizedSourceType(catalog.sourceType),
    layout: normalizedLayout(catalog),
    enabled: catalog.enabled !== false
  };
}

// Old web builds seeded per-service mdblist rows (garycrawfordgc lists) that are
// now dead or stale, and those entries were synced into user clouds. The real
// service rows are the APK's collection catalogs — drop the legacy ones anywhere
// they appear so services never show twice.
function isLegacyServiceCatalog(catalog: CatalogConfig) {
  if (String(catalog.sourceUrl ?? "").includes("garycrawfordgc")) return true;
  return catalog.sourceType === "mdblist" && ["netflix", "disney", "prime", "hbo", "apple_tv", "hulu", "paramount"].includes(catalog.id);
}

export function mergeCatalogs(saved: CatalogConfig[] | undefined, hiddenIds: string[] = [], hiddenAddonIds: string[] = []) {
  // A saved empty list is intentional, just as it is on Android. Only a new
  // installation without a saved list should receive the defaults.
  const cleaned = (saved ?? defaultCatalogs)
    .filter(isValidCatalog)
    .map(normalizedCatalog)
    .filter((catalog) => !(
      ["sports", "popular_live_tv"].includes(catalog.id) &&
      catalog.sourceType === "preinstalled" &&
      !catalog.sourceUrl?.trim() && !catalog.sourceRef?.trim() && !catalog.addonId?.trim()
    ))
    .filter((catalog) => !isLegacyServiceCatalog(catalog));
  const hidden = new Set(hiddenIds);
  const hiddenAddons = new Set(hiddenAddonIds);
  const seen = new Set<string>();
  return cleaned.filter(catalog => {
    if (seen.has(catalog.id)) return false;
    seen.add(catalog.id);
    return true;
  }).map(catalog => ({
    ...catalog,
    enabled: catalog.enabled !== false && !hidden.has(catalog.id) &&
      !(catalog.sourceType === "addon" && hiddenAddons.has(catalog.id)) &&
      !(String(catalog.kind).toUpperCase() === "COLLECTION_RAIL" &&
        hidden.has(`collection_row_${String(catalog.collectionRailKey || catalog.collectionGroup).toLowerCase()}`))
  }));
}

export function updateHiddenCatalogIds(catalogs: CatalogConfig[], hiddenIds: string[] = []) {
  const present = new Set(catalogs.map(catalog => catalog.id));
  for (const catalog of catalogs) {
    const key = catalog.collectionRailKey || catalog.collectionGroup;
    if (key && String(catalog.kind).toUpperCase() === "COLLECTION_RAIL") {
      present.add(`collection_row_${key.toLowerCase()}`);
    }
  }
  // Keep tombstones for deleted/unavailable catalogs so discovery on another
  // device cannot restore them after an unrelated reorder or rename.
  return [...new Set([
    ...hiddenIds.filter(id => !present.has(id)),
    ...catalogs.filter(catalog => !catalog.enabled).map(catalog => catalog.id)
  ])];
}
