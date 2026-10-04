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

The month starts on Monday. Previous/next month and Today controls work across year
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

Refresh is available directly in the toolbar. Phone date cells prioritize artwork
and release counts; complete titles and times remain in the selected-day strip.
Touch toolbar controls have 44 dp minimum targets. Loading states do not imply an
empty watchlist while sources are still being read.

## Loading and isolation

Metadata requests use bounded concurrency and expiring in-memory caches. Completed
titles appear progressively, before slower sources and optional logos finish.
Both loaders start titles as each watchlist arrives and publish confirmed episode
dates before optional air-time enrichment. Separate request limits prevent a
stalled tracker from hiding available ARVIO dates or blocking healthy artwork.
Optional time enrichment has a bounded budget including queue time. The web
coalesces progress updates while publishing the first available release promptly;
pending, empty and failed sources remain distinct. On Android, a private source
snapshot becomes reusable only after all source reads finish.
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
