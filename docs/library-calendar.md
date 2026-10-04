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
boundaries. The source picker also provides refresh on Android. On TV, left/right
moves one day, up/down moves a week, and OK enters the selected day's release strip.
OK on a release opens its details. Up returns from that strip to the selected day.

Phone layouts retain the month artwork and expose the larger selected-day cards
below it. Short landscape windows scroll vertically instead of clipping the month
or captions. The web grid supports arrows, Home/End and Page Up/Down, with a single
tab stop for the selected day.

## Loading and isolation

Metadata requests use bounded concurrency and expiring in-memory caches. Completed
titles appear progressively, before slower sources and optional logos finish.
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

- Android: 16 repository/mapping tests passed; the x86 sideload debug app and
  instrumentation APK assembled successfully.
- Emulator: four TV Calendar scenarios and two existing top-bar scenarios passed.
  A separate Calendar phone-layout scenario passed at 390 × 780 dp on the same
  emulator; its TV display size was restored afterward. No physical TV was used.
- Web: strict TypeScript and 48 focused unit tests passed. Calendar, existing
  Library and translation browser checks passed at desktop, tablet and phone sizes.
- Live public TMDB responses parsed with the production models. A public Trakt
  smoke request received an HTML/403 challenge in this environment, so live exact
  times were not verified; the date-only fallback and timestamp mapping are tested.
- Two existing non-Calendar landscape scrolling scenarios hit Compose idle
  timeouts. One reproduced in isolation; those broader checks are not counted as
  passing and their relation to this change has not been established.
