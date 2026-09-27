# IPTV VOD investigation — 22 September 2026

The reported source was IPTV VOD, with Loki supplied as the example. The earlier successful Sintel test covered an addon source, not IPTV. It did not establish that IPTV playback worked on the user's device.

## Findings and changes

- IPTV movie and episode sources discarded the configured player headers used for catalogue access. Sources now retain those headers for playback preparation and relay fallback.
- Episode discovery selected only the first matching series. Providers can store 4K and HD versions as separate series; an unavailable episode in the first version could hide a valid alternative. Discovery now checks all matching series IDs, deduplicates them and limits lookups to two at a time. A failed or missing variant does not hide the others.
- The production desktop test played Loki S2E3 directly before the new changes, at 3840 pixels and a 53:35 duration. Forcing that source through the relay subsequently stalled. IPTV VOD therefore keeps the subscriber's direct connection first and uses the relay as a fallback. It does not invent a live HLS variant for an on-demand file. Browser repackaging can still use the relay when needed.
- The updated production source list exposed both the 4K variant and a previously hidden HD variant for the tested episode.

## Validation and limits

82 targeted playback tests and TypeScript passed, including the actual fallback ladder, header preservation, duplicate series IDs, a failed variant, and a missing episode in the first variant. Deployment runs the complete web regression suite and checks the published build stamp.

The local preview initially used an origin not allowed by the existing relay; that local configuration failure is not evidence of a provider outage. Temporary local environment configuration was removed and the development server stopped.

The user subsequently identified the failing device as an iPad. The tested Loki S2E3 source is MKV. Desktop Chromium can play this file directly; the iPad route requires browser repackaging. The prepared remux path previously used only the relay. It now probes the subscriber URL first, without the relay's upstream headers, and falls back to the prepared relay when the direct probe fails (for example because of CORS). Each failed worker is terminated before the next attempt. Cancellation suppresses fallback; unsupported codecs do not retry the same file through another URL; playback errors still reach normal recovery.

All 702 web tests pass, including direct success, failed direct probe then relay, both routes failing, cancellation, unsupported codecs, and actual player route selection. The deployed c79e1dbe5 build also played Loki S2E3 directly at 3840px, readyState 4 and advancing playback. No real iPad is available here: these tests verify routing and lifecycle, not Safari decoding or successful iPad playback. Direct remux still requires provider CORS/range support, and relay playback still depends on provider access. No Android/TV native-player changes were made.

## Release

- `f0b3affe6`: preserve VOD headers and discover all matching series variants.
- `c79e1dbe5`: direct-first IPTV VOD with header-aware relay fallback.
- Deployment: https://github.com/ProdigyV21/ARVIO/actions/runs/35732746291

## Broader iPad follow-up — 23 September

User reports most sources work on Windows but fail in both Chrome and Safari on an iPad running the newest iPadOS (exact version and error behaviour not supplied).

Fixed three confirmed code issues:
- HEVC input codec strings from Mediabunny can start with `hev1`, while its MP4 muxer writes `hvc1`. Remux probes now advertise the output sample entry, preserving profile/level and the existing Dolby Vision safety checks.
- Remux startup awaited `loadeddata` before the caller requested playback. It now returns after `loadedmetadata`, avoiding a circular wait in browsers that defer decoding until playback is requested.
- Direct startup treated the absence of playable frames after `NotAllowedError` as a network timeout. Permission rejection now stops those watchdogs and shows a Play button. A user Play event rearms startup monitoring; remux also exposes Play instead of silently swallowing permission rejection.

Validation: 706 web tests and TypeScript pass, including metadata-only startup, HEVC output labelling, and direct/remux autoplay permission recovery. A synthetic HEVC Main10 file played at readyState 4 and sought to 65 seconds in the desktop Chromium fixture. This is regression evidence, not proof of physical iPad playback. Browser/provider/network limits still apply. No native APK changes.
