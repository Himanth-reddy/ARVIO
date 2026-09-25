# Browser reliability and parity update — 25 September 2026

## Changes

- Play selects browser-compatible sources, ranks direct playback first, skips uncached/external-only sources and automatically recovers from preparation timeouts and runtime failures. Progressive sources can arrive before exhaustion is reported. Sources retains explicit external-player actions. Playback position is retained across fallback.
- Addon edits are written to a durable account-scoped outbox. Failed cloud saves remain pending and retry on reconnect/periodically; pending deletions and newer edits survive refresh and concurrent saves. Local storage failures cannot produce a false saved result.
- Search has a discovery landing page, movie/TV, genre/year/sort controls, paginated results, retry feedback and URL-preserved filters.
- Stalker has portal discovery, MAC/token authentication, complete paginated live-channel loading, temporary stream links and now/next guide parsing. Only the required MAC cookie is forwarded; credentials are stripped on cross-origin redirects.
- Android-only playback/network controls are grouped and identified separately from browser settings.
- Browser Back/Forward and reload restore sections, search queries and shareable movie/TV details.
- Downloads has managed file progress, pause/resume/retry, completion verification and open-file actions where the File System Access API is supported. Browser/VLC handoffs are labelled separately and never presented as completed managed downloads.
- New UI phrases are included across the existing language dictionaries; newly seeded translations are machine translations.

## Validation

- TypeScript and optimized Next.js build passed.
- Full regression suite, including source failures/timeouts, late source discovery, durable addon edits, portal protocol parsing, search pagination, route round-trips, download truncation/resume and proxy header controls.
- Local browser checks with real metadata at desktop, phone (390 × 844), and tablet (1180 × 820) sizes.
- Actual browser playback: a deliberately broken 1080p URL fell through to a generated H.264/AAC test video, which advanced to 11 seconds with no video error. VLC was selected as the manual player preference and was not launched.

## Practical limits

An unavailable source cannot be made playable. If every browser-compatible source fails, ARVIO gives a Sources recovery message. Codec, DRM, provider conversion permissions, expired links and network availability remain external constraints. Real iPad and Stalker-provider validation were unavailable. Managed downloads require browser file-system support; on other browsers, the browser or VLC owns transfer progress and offline files. This does not add general offline operation for the entire webapp.
