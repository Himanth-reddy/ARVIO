# IPTV navigation investigation (2026-09-05)

## Scope and plan

1. Reproduce TV/mobile scrolling and actions, keeping the user's profiles and playlists intact.
2. Keep one continuous lazy channel list. Page additional records from SQLite without changing existing row keys or resetting scroll/focus. Only query EPG for a bounded visible window.
3. Preserve stored favorites order regardless of channel sorting. Filter hidden/locked groups before database pagination and before rendering. Keep hidden groups available in settings, not as shortcuts in the guide.
4. Give menu, channel-list and programme navigation distinct Back/focus ownership. Restore focus to a neighboring channel when removing the focused favorite.
5. Share EPG request limits across overlapping jobs and stop stale retries when changing channels. Retain cached channels after refresh failures without an automatic reload loop.
6. Run JVM regressions, remote/touch tests and actual TV checks, then install a release-signed build.

## Root causes corrected

- The guide rendered a moving 18-42 row window. Changing/appending that window reset the lazy list to item zero; state and index-based keys also discarded focus.
- Favorites were sorted again by the global channel-order setting, undoing manual moves.
- Hidden groups were intentionally rendered in a separate guide section; the paged fallback could bypass the visibility filter.
- A delayed channel ID could disagree with the object actually focused when a user opened the channel menu.
- The guide's Back handler could run while the channel popup remained open. Subsequent input then acted on the wrong layer.
- Search could perform two focus moves for one Down key. Category structure refreshes could reclaim focus, and index-based category keys changed when reordering groups.
- Programme navigation tried to focus the next lazy row before it existed.
- Empty-result retention kept removed favorites visible. Stale favorite IDs inflated the guide count and triggered repeated paging, sometimes leaving an empty EPG query window.
- Rapid Down repeats grew the pending SQLite page repeatedly (144 to 2,640 rows in the device reproduction). Repeated requests now share one pending 192-row increment, and page requests no longer fight the viewport for ownership of the EPG window.
- EPG concurrency was 64 per batch rather than a shared provider budget. Failed short-guide requests could fall through to additional endpoints. Cached playlist failures could trigger another forced reload, and delayed player retries outlived their original channel.

## Verification recorded

### Local tests

The focused JVM suite covers:

- GuideNavigationTest (5, including repeated page request coalescing)
- IptvGuideRequestBudgetTest (3)
- IptvProviderOrderTest (7)
- LiveTvStartupTest (22)
- LiveTvResponsiveLayoutTest (5)

The budget tests verify at most two simultaneous guide requests per origin, 250 ms start spacing, shared limits between batches, and cooldown behavior for rejected/rate-limited requests.

### Physical TV and mobile emulator

Device: TCL Smart TV Pro, Android 14, 192 MiB app heap growth limit. Screenshots use its 1920x1080 display override.

- Synthetic 55,000-channel dataset: 170 Down presses crossed the initial 144-row page boundary; 20 Up presses returned to channel 150 without reset or lost focus.
- Touch scrolling at a mobile viewport: appending rows and refreshing metadata preserved scroll position; a subsequent swipe advanced the list. Also passed on an Android 14 phone emulator.
- Reordering a favorite retained focus on that channel.
- Programme-row navigation and Back/menu isolation passed on the physical TV (two tests, 54.115 seconds).
- The full seven-test physical TV suite passed in 174.048 seconds, including focused-favorite removal and touch category hiding. Final mobile emulator touch scrolling and category hiding passed (two tests, 8.351 seconds).

### Actual configured playlist

The existing account's enabled playlist was an aggregator, not the separately supplied Xtream endpoint. Its SQLite index contained 108,145 channels. The optimized build displayed this total and played NPO 1 with current/future guide blocks.

Recorded device log intervals on the cached path:

- TV page data initialization 14:18:20.393; first-paint data 14:18:20.606 (213 ms). This is data preparation, not a measured cold-launch-to-screen time.
- Favorites request 14:19:14.077; indexed EPG available 14:19:14.711 (634 ms including state/focus work); the database guide query itself took 37 ms.
- A later two-channel query took 64 ms. Screenshots confirmed NPO 1 guide blocks and continuous live playback during menu operations.
- Actual long-press menu: move NPO 1 above and below the temporary second favorite worked; focus stayed on NPO 1. The temporary added favorite was removed afterward.
- Final-build cached entry at 14:52:57.839 reached first-paint data at 14:52:57.930 (91 ms). NPO 1 indexed guide was visible at 14:52:58.784 (945 ms after data initialization).

The first rapid-scroll release measurement still recorded 8.02% deadline-missed frames (p50 15 ms, p95 36 ms). It exposed the repeated page-request bug above. This is not a claim of perfectly smooth 60 fps; the subsequent paging correction requires another device measurement.

## Provider test limitation

The explicitly supplied Xtream playlist was tested in a separate temporary profile. The server returned HTTP 403 (access denied). Further requests to that endpoint were not hammered or used to bypass the rejection. The temporary profile was removed and the original profile restored; no app data was cleared.

This does NOT prove why another subscriber was blocked. The old request fan-out/retry paths were a concrete risk, but provider logs/account limits are required to attribute that block. The request-budget fix cannot guarantee every provider accepts every request rate.

No claim is made that all 55,000 channels have instantaneous EPG. A cached visible guide is fast; absent upstream schedules, first imports, rejected credentials and network latency remain external constraints. Some 24/7 entries in the actual aggregator playlist had no matched guide, unlike NPO 1.

## Signing

Sideload release keeps version 1.9.996 (311). No uninstall or data clear was used. Required signer SHA-256:

`9778d7533d4bc1aee80c1d2d7043fb22cba3b79cd11911d3b131c1316d5f17c1`

Credentials and raw provider URLs are excluded from this document and test fixtures.
