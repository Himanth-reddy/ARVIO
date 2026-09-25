export type ParityStatus = "complete" | "partial" | "blocked";

export interface ParityFeature {
  id: string;
  area: string;
  androidSource: string;
  webSource: string;
  status: ParityStatus;
  browserDifference?: string;
}

export const parityFeatures: ParityFeature[] = [
  {
    id: "home",
    area: "Home",
    androidSource: "HomeScreen + HomeViewModel + MediaRepository",
    webSource: "HomeScreen + store + tmdb/catalogs",
    status: "partial",
    browserDifference: "Configurable catalog ordering, collection sources and home-server rows are implemented. Browser layout and remote behavior differ from Android."
  },
  {
    id: "details",
    area: "Details",
    androidSource: "DetailsScreen + DetailsViewModel",
    webSource: "DetailsDrawer + tmdb/addons",
    status: "partial",
    browserDifference: "Details, episodes, cast, reviews and related titles are available. Play automatically selects browser sources; Sources exposes manual and external actions."
  },
  {
    id: "player",
    area: "Player",
    androidSource: "PlayerScreen + PlayerViewModel + ExoPlayer",
    webSource: "PlayerOverlay + hls.js/html5 video",
    status: "partial",
    browserDifference: "Direct, HLS, DASH, transport streams, supported remux and provider conversion are implemented with automatic source recovery. Codec and conversion support still depend on browser, device and provider."
  },
  {
    id: "profiles",
    area: "Profiles",
    androidSource: "ProfileSelectionScreen + ProfileRepository",
    webSource: "ProfileSelectionScreen + profiles/cloud",
    status: "partial",
    browserDifference: "Profiles, avatars and PIN locking are implemented. Device-specific profile behavior differs from Android."
  },
  {
    id: "cloud",
    area: "ARVIO Cloud",
    androidSource: "AuthRepository + CloudSyncRepository",
    webSource: "auth + cloud + store",
    status: "partial",
    browserDifference: "Settings and addon edits have durable retry queues. Other sync scopes still require separate conflict and offline handling."
  },
  {
    id: "trakt",
    area: "Trakt",
    androidSource: "TraktRepository + TraktSyncService",
    webSource: "trakt + store",
    status: "partial",
    browserDifference: "Device auth, watchlist, playback, and scrobble exist; full two-way watched/history/outbox sync is not complete."
  },
  {
    id: "addons",
    area: "Addons",
    androidSource: "StreamRepository + AddonRuntimeAggregator",
    webSource: "addons",
    status: "partial",
    browserDifference: "Stremio-compatible manifests and direct streams exist; Android-only Cloudstream/runtime plugins are blocked in browser."
  },
  {
    id: "livetv",
    area: "Live TV",
    androidSource: "IptvRepository + TvViewModel + LiveTvScreen",
    webSource: "iptv + LiveTvScreen",
    status: "partial",
    browserDifference: "M3U, Xtream, guide grid, catchup and favorites are implemented. Stalker supports channel discovery, temporary playback links and now/next; a real portal account is still needed for provider validation."
  },
  {
    id: "homeserver",
    area: "Home Server",
    androidSource: "HomeServerRepository",
    webSource: "homeserver",
    status: "partial",
    browserDifference: "Plex/Jellyfin/Emby/Silo libraries, episodes and playback negotiation are implemented. Server permissions, reachability and conversion capability remain provider-specific."
  },
  {
    id: "telegram",
    area: "Telegram",
    androidSource: "TelegramRepository + TelegramSourceResolver",
    webSource: "telegram + telegram-stream service worker",
    status: "partial",
    browserDifference: "Native Telegram account connection and source streaming are implemented; service-worker and codec support depend on the browser."
  }
];

export function paritySummary() {
  const total = parityFeatures.length;
  const complete = parityFeatures.filter((feature) => feature.status === "complete").length;
  const partial = parityFeatures.filter((feature) => feature.status === "partial").length;
  const blocked = parityFeatures.filter((feature) => feature.status === "blocked").length;
  return { total, complete, partial, blocked };
}
