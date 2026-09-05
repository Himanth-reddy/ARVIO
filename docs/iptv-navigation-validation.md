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
- Hiding the focused category could hand focus to the channel grid while leaving the drawer open. The sidebar now restores focus after that category is actually removed.
- Programme navigation tried to focus the next lazy row before it existed.
- Empty-result retention kept removed favorites visible. Stale favorite IDs inflated the guide count and triggered repeated paging, sometimes leaving an empty EPG query window.
- Rapid Down repeats grew the pending SQLite page repeatedly (144 to 2,640 rows in the device reproduction). Repeated requests now share one pending 192-row increment, and page requests no longer fight the viewport for ownership of the EPG window.
- Remote row reveal restarted a long default scrolling spring, and border animation rebuilt channel content per frame. Reveal now scrolls only the clipped distance in 100 ms; border animation state is read in the drawing phase.
- EPG concurrency was 64 per batch rather than a shared provider budget. Failed short-guide requests could fall through to additional endpoints. Cached playlist failures could trigger another forced reload, and delayed player retries outlived their original channel.

## Verification recorded

### Local tests

42 focused JVM tests passed on the latest-main integration build:

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
- The full eight-test physical TV suite passed in 178.306 seconds, including focused-favorite removal, touch category hiding, and remote focus restoration after hiding a category. Mobile emulator touch scrolling and category hiding passed (two tests, 7.881 seconds).
- After the final clipped-distance scrolling and drawing-phase border correction, all eight physical TV tests passed again in 133.153 seconds. Both mobile touch tests passed again in 7.995 seconds, and the latest-main release build plus all 42 JVM tests passed. Test-suite duration is not a frame-rate benchmark.

### Actual configured playlist

The existing account's enabled playlist was an aggregator, not the separately supplied Xtream endpoint. Its SQLite index contained 108,145 channels. The optimized build displayed this total and played NPO 1 with current/future guide blocks.

Recorded device log intervals on the cached path:

- TV page data initialization 14:18:20.393; first-paint data 14:18:20.606 (213 ms). This is data preparation, not a measured cold-launch-to-screen time.
- Favorites request 14:19:14.077; indexed EPG available 14:19:14.711 (634 ms including state/focus work); the database guide query itself took 37 ms.
- A later two-channel query took 64 ms. Screenshots confirmed NPO 1 guide blocks and continuous live playback during menu operations.
- Actual long-press menu: move NPO 1 above and below the temporary second favorite worked; focus stayed on NPO 1. The temporary added favorite was removed afterward.
- Actual category menu: hid the small cartoon category, confirmed its removal from the guide, then restored it using Settings > TV > playlist categories. The user's original hidden groups were left unchanged.
- Final-build cached entry at 14:52:57.839 reached first-paint data at 14:52:57.930 (91 ms). NPO 1 indexed guide was visible at 14:52:58.784 (945 ms after data initialization).

After page-request coalescing, 160 actual Down presses reached channel 161 and 20 Up presses returned to channel 141 with live playback continuing. Only 336 rows were loaded, instead of the previous repeated growth to 2,640. The subsequent 80 Down presses also continued without resetting.

Frame measurements before the final drawing/animation correction were not yet smooth enough: 20.02% deadline-missed frames (p50 16 ms, p95 73 ms) during the first paced scroll and 19.83% in a repeat using native input injection. The stationary live-player baseline was 2.73%. These are device-run observations, not an isolated benchmark or a claim of 60 fps.

The final release again reached channel 161 after 160 Down presses, with live playback still running. Its measurement was 21.25% deadline-missed frames (p50 21 ms, p95 69 ms, p99 101 ms; 3,883 frames). Therefore the final animation correction improved deterministic navigation/test duration but did NOT demonstrate an overall frame-rate improvement. The super-smooth performance target remains unpassed.

Final cached TV entry: data initialization 15:54:41.720, first-paint data 15:54:41.967 (247 ms), NPO 1 indexed EPG 15:54:42.813 (1,093 ms after initialization; 12 ms for its query).

## Remaining performance gate

Before calling this a fully approved performance release, collect a system/frame trace while scrolling with and without the mini-player, separate key-injection overhead from app work, and identify the main-thread composition/layout/allocation cost. Optimize the measured hot path, then repeat the same channel sequence on the TCL and a mobile device. The functioning cached-guide path and action regressions are verified; perfectly smooth scrolling and complete EPG coverage are not.

## Provider test limitation

The explicitly supplied Xtream playlist was tested in a separate temporary profile. The server returned HTTP 403 (access denied). Further requests to that endpoint were not hammered or used to bypass the rejection. The temporary profile was removed and the original profile restored; no app data was cleared.

This does NOT prove why another subscriber was blocked. The old request fan-out/retry paths were a concrete risk, but provider logs/account limits are required to attribute that block. The request-budget fix cannot guarantee every provider accepts every request rate.

No claim is made that all 55,000 channels have instantaneous EPG. A cached visible guide is fast; absent upstream schedules, first imports, rejected credentials and network latency remain external constraints. Some 24/7 entries in the actual aggregator playlist had no matched guide, unlike NPO 1.

## Signing

Sideload release keeps version 1.9.996 (311). No uninstall or data clear was used. Required signer SHA-256:

`9778d7533d4bc1aee80c1d2d7043fb22cba3b79cd11911d3b131c1316d5f17c1`

Credentials and raw provider URLs are excluded from this document and test fixtures.

## Latest-main integration

The IPTV changes were also applied to `14543b97b` (including the newly merged player/details PRs). The signed sideload release and the 42-test focused JVM suite built successfully; a second assemble confirmed the final sources were up to date. Uncommitted player-preview work in the original worktree was left intact, not overwritten or mixed into this IPTV commit.

## Follow-up hardening and measurement

Changes on top of `5ab0c13db`:

- Each profile/provider/category retains an independent lazy-list position and loaded-page limit, bounded to 16 category windows. Touch category selection uses the same lock checks as remote selection.
- Retain up to 160 indexed channel schedules across neighboring windows; an empty/failed refresh does not erase already displayed guide data. Always include the playing channel in the indexed query, even outside the browsed category.
- Re-evaluate the mini-player's current/next programme against the clock. Previously a completed programme could remain labelled NOW until a network refresh.
- Resolve a saved last channel from SQLite when it lies outside the first loaded page; respect hidden/restricted groups.
- Handle an expired HLS live window by seeking to the live edge, once per minute, without applying this recovery to catchup.
- Emit a terminal empty-guide result for large playlists too, instead of leaving successfully completed lookups marked pending.
- Make XML parsing and retry spooling cancellable. Avoid parsing dates/descriptions for unrelated XMLTV channels. Visible XMLTV requests now share the provider budget; authorization failures no longer provoke a second request with another user agent.
- Correct the SAX fallback's empty `localName` handling. With namespaces disabled, the parser supplies `qName` and an empty `localName`; the previous null-only fallback silently recognized no XML elements. Regression tests now exercise real XML and cancellation.
- Move the live indicator's alpha updates to the graphics layer, avoiding composition for each pulse frame.

### Regression results

- Final focused JVM suite: **60 passed**, zero failures (previous 42 plus repository optimization, XML parser/cancellation, clock/cache/recovery and saved-channel tests).
- Physical TCL suite: **9 passed in 160.084 seconds**. Programme navigation now uses actual populated programme rows, not empty placeholders. Includes category A -> B -> A independent scroll restoration.
- Android 14 phone emulator: **3 passed in 11.754 seconds**: touch scrolling across appended pages, hiding a category, and independent category scroll restoration. This is emulator coverage, not a claim of physical-phone performance.
- Release compilation, R8 and vital lint completed successfully. Device navigation tests ran against a debug-signed-with-release-key build; performance observations used the optimized release APK.
- After the external-focus correction, the complete **10-test TCL suite passed in 164.431 seconds**, including the new regression that restoring focus to a visible row must not move it to the top. An earlier run while the TV was asleep could not find Compose hierarchies and was discarded; no production crash was recorded. The TV was awakened for the repeat and its screensaver setting restored afterward.

### Physical performance observations

The committed opt-in `GuideScrollDriver` injects 160 Down and 160 Up key pairs with 150 ms pacing, without spawning a shell for each key. It requires a manually prepared, focused guide; it never changes account/playlist configuration. Capture `dumpsys gfxinfo com.arvio.tv reset` before the run, then `dumpsys gfxinfo com.arvio.tv` immediately afterward. Perfetto can run alongside it.

For the real 108,145-channel catalog, starting at channel 1 with the same mini-player stream:

| Build/run | Frames | Missed deadlines | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `5ab0c13db`, native driver | 3,331 | 25.22% | 34 ms | 57 ms | 77 ms |
| Follow-up release, native driver | 3,540 | 20.11% | 34 ms | 53 ms | 73 ms |
| Follow-up repeat with tracing | 3,699 | 11.14% | 31 ms | 48 ms | 65 ms |
| Final build, NPO 1 FHD playing | 3,206 | 33.72% | 38 ms | 65 ms | 93 ms |

The repeat encountered an upstream HTTP 502 playback retry, so it is **not** an equivalent uninterrupted-video comparison and must not be used to promise an 11% outcome. Neither run demonstrates consistently smooth 60 fps. Both completed all 320 actions and returned to channel 1; the list and mini-player remained mounted. The first follow-up run is a modest improvement, not a performance pass.

The final run used a different, Full-HD stream (NPO 1) and missed more deadlines; it also completed all 320 actions. This is a residual performance failure, not hidden as a passing result. Low-overhead profiling of simultaneous Full-HD playback and scrolling remains necessary. A later external-focus correction avoids pinning an already visible selected row to the top when returning from the drawer; it does not address the Full-HD frame-rate limitation.

At 18:28 the final release resumed NPO 1 automatically after installation/restart and showed its cached guide. At 18:31 screenshots confirmed the NOW programme had advanced from NOS Sportjournaal (18:15-18:30) to EenVandaag (18:30-19:05) while browsing All Channels. A physical category round-trip also retained the selected channel 31 rather than resetting to channel 1.

Two 35-second system traces also show background allocation pressure: before, the hottest worker used 13.303 CPU seconds and GC used about 2.9 seconds across nine events; after, GC used about 0.48 seconds across two events. These traces contain different warm-cache/background work, so they identify remaining costs rather than isolate one patch's effect. Main/render work remains significant and requires further profiling for the smoothness target.

The fully automated Macrobenchmark launch attempt could not reliably open the production account/profile flow on this TCL. It was not counted as a passing test and was replaced by the tested explicit opt-in input driver. No unattended login flow or credentials are committed.
