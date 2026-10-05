# Library release calendar

Library has a Calendar tab on Android TV, phones/tablets and the web app. It uses a
borderless month grid with artwork in release days, a white selected day, and a
horizontal release strip beneath the month. Existing Library tabs remain available.

## Watchlists and dates

- ARVIO's own local/cloud watchlist works without a connected tracking account.
- Connected Trakt, SIMKL and MDBList watchlists can be combined or filtered by service.
  SIMKL also includes titles currently being watched.
- Matching titles are deduplicated by media type and TMDB identity, retaining every
  contributing service as provenance. Reading the calendar does not modify lists.
- TMDB supplies movie releases and episode dates/stills. Public Trakt episode
  metadata supplies confirmed air timestamps when available; no Trakt user login is
  needed for that metadata.
- Confirmed timestamps are converted to the device's timezone. Date-only releases
  retain their published date and display **Time TBA**. Movie release regions are
  labelled, including a US fallback when no release is listed for the chosen region.
- Missing metadata is reported as a partial result with a retry action. An artwork
  failure must not discard a confirmed release date.

## Interaction and responsiveness

The month starts on Monday. Previous/next month controls work across year
boundaries and keep the selected day where possible. On TV, left/right
moves one day, up/down moves a week, and OK enters the selected day's release strip.
OK on a release opens its details. Up returns from that strip to the selected day.

Phone layouts retain the month artwork and expose the larger selected-day cards
below it. Short landscape windows scroll vertically instead of clipping the month
or captions. The web grid supports arrows, Home/End and Page Up/Down, with a single
tab stop for the selected day.

Today has a marker independent of the selected date. An empty day offers **Next
release** when a later loaded release exists in the visible month and chosen source;
the calendar never changes the selected day just because data arrives. On TV, OK on
an empty day focuses this action (or Retry on a failed load), and Up returns to the
day. Dense six-week months retain their extra-release counts.

The Calendar uses the same full-size shared top bar as every other TV page. The
toolbar only contains month navigation, the watchlist filter and connected-service
branding; it has no Today button, timezone caption, refresh button or navigation
instruction footer. Retry remains available when a source fails. Phone date cells prioritize artwork
and release counts; complete titles and times remain in the selected-day strip.
Touch toolbar controls have 44 dp minimum targets. Loading states do not imply an
empty watchlist while sources are still being read.

### Service artwork

Calendar bundles official assets without recoloring: the purple Trakt favicon from
`https://app.trakt.tv/favicon.svg`, the MDBList homepage logo from
`https://mdblist.com/static/android-chrome-512x512.png`, and the white SIMKL wordmark
used by `https://docs.simkl.org/how-to-use-simkl`. The Trakt SVG paths are preserved
in an Android vector; PNG artwork retains its original colors and aspect ratio.

## Loading and isolation

Metadata requests use bounded concurrency and expiring caches. Android keeps public
metadata for 30 minutes in memory/on disk and restores a private month preview for
up to 24 hours, scoped to profile, connection identity, local watchlist membership,
language, region, month and timezone. Encoding, parsing and file access run off the
UI thread. The production Library route preloads the current month before the
Calendar tab opens. The web client also restores scoped month snapshots before
refreshing. Successfully removed source memberships are reconciled into the saved
preview even when an unrelated service fails; retained fallback data keeps its
original expiry. New, uncached remote data still requires a network response.

Completed titles appear progressively, before slower sources and optional logos finish.
Both loaders start titles as each watchlist arrives and publish confirmed episode
dates before optional air-time enrichment. Separate request limits prevent a
stalled tracker from hiding available ARVIO dates or blocking healthy artwork.
Optional time enrichment has a bounded budget including queue time. The web
coalesces progress updates while publishing the first available release promptly;
pending, empty and failed sources remain distinct. On Android, a private source
snapshot becomes reusable only after all source reads finish.
Historical season requests have their own bounded slots and start with recent
seasons, allowing newly arriving titles to publish dates without waiting for a
complete season walk. Metadata for an identical request is coalesced; no titles or
potentially relevant seasons are silently omitted.
Changing month, profile or language cancels/replaces the old request. Private
watchlists are scoped to the current account/profile; public metadata may be cached.
Android refreshes stale lists on tab entry/resume and reacts to cloud watchlist and
connection changes.

## Verification fixtures

`LibraryCalendarDeviceTest` renders the real Android Library shell and Calendar UI
using local artwork and synthetic October 2026 release dates. The web Calendar UI
fixture exercises the production component and loader with offline metadata
adapters at desktop, tablet and phone sizes, including ARVIO-only mode. These fixture
dates are for repeatable navigation and visual tests and are never production data.

Repository/mapping tests cover timezone boundaries, date-only releases, regional
movie types, provider deduplication/failures, profile isolation, caching and bounded
requests. Emulator screenshots are saved under `artifacts/calendar` during QA.

### Validation on 4 October 2026

- Android: 23 repository/mapping tests passed; the x86 sideload debug app and
  instrumentation APK assembled successfully.
- Emulator: all eight TV Calendar scenarios and both phone-layout scenarios
  passed. Phone checks ran at 390 × 780 dp on the same TV-system emulator and
  verified six-week scrolling and caption clearance; its TV display size was
  restored afterward. Two existing top-bar scenarios passed in the initial
  implementation check. No physical TV was used.
- Web: strict TypeScript and 43 focused unit tests passed on the refined version
  (18 Calendar and 25 translation/Library/TV regressions). Calendar, existing
  Library and translation browser checks passed at desktop, tablet and phone sizes.
- Live public TMDB responses parsed with the production models. A public Trakt
  smoke request received an HTML/403 challenge in this environment, so live exact
  times were not verified; the date-only fallback and timestamp mapping are tested.
- Two existing non-Calendar landscape scrolling scenarios hit Compose idle
  timeouts. One reproduced in isolation; those broader checks are not counted as
  passing and their relation to this change has not been established.

### Validation on 5 October 2026

- Android: 36 Calendar repository, mapping and persistent-cache unit tests passed.
  The final x86 sideload debug app and instrumentation APK assembled successfully.
  Regressions cover slow historical seasons, process recreation, cache isolation,
  healthy removals during provider outages, shared-title provenance and navigating
  cached months while a provider is unavailable.
- TV emulator: nine Calendar scenarios, two shared-topbar scenarios and the disk
  cache scenario passed at 1280 x 720. The Calendar topbar retains identical bounds
  when changing Library tabs. Five- and six-week month screenshots keep release
  artwork, captions and provenance visible. The removed controls are absent and
  all three official provider assets are displayed.
- Both phone-layout scenarios passed at 390 x 780 dp on the same TV-system emulator;
  the original TV display was restored. This verifies layout, not a separate phone
  operating-system image. No physical TV was used.
- Web: 26 focused unit tests and TypeScript checks passed. Browser fixtures cover
  TV 720p/1080p, desktop, tablet, phone, six-week months and cached releases while
  metadata is held pending. These use synthetic dates and controlled data adapters.
- Device cache timing measures restoration of 250 saved releases without network
  requests, not first-ever provider loading or complete artwork rendering. New
  remote metadata still requires network responses. Evidence, timing and build
  logs are in `artifacts/calendar-oct5`; web screenshots are in `artifacts/calendar`.
