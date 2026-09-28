# Imported collections

Import a Nuvio collection document in Settings > Catalogs, using a URL or JSON.
Each collection becomes a Home row, with a tile for each supported folder.
The existing Home layout and built-in artwork are unchanged.

Imports are stored in the active profile's catalogs and use the existing account
sync on Android and web. Re-importing the same URL replaces that pack's contents
while keeping existing titles and ordering. Removing a pack removes its rows and
folders. Built-in collection visibility uses the same profile visibility settings.

Supported sources: installed add-on catalogs (including genre), TMDB collection,
public TMDB list, TMDB discover/company/network, person cast and director credits,
public Trakt lists and public MDBList lists. Add-ons must already be installed.
Private lists and unavailable upstream services cannot be made available by importing.
Additional discover filters on person/director credit sources are rejected rather
than silently ignored. Other unsupported source providers are skipped; imports
without any supported folders are rejected. Imports are limited to 2 million
characters, 100 rows and 500 folders; duplicate identifiers are rejected.

The web collection view supports poster/landscape covers, keyboard navigation,
missing-artwork fallback and retry. Animated hero videos are not played in the web
collection dialog; their metadata is retained when syncing back to Android.

## Android loading

Collection cards reuse the titles and artwork already returned by TMDB lists,
discover, franchise and person-credit endpoints. ID-only sources fetch lightweight
details with a shared six-request limit. The initial page does not wait for IMDb
ratings; visible cards receive full details and ratings afterward, two at a time.
Partial pages keep source order so TV focus does not jump as requests finish.
Imported collections fetch one upstream page initially and expand on scroll;
movie/series filters, pagination and the collection's contents are preserved.

Run the collection regression tests with:

```powershell
./gradlew.bat :app:testSideloadDebugUnitTest --tests '*Collection*'
```

`CollectionLoadingBenchmarkTest` includes a deterministic cold/warm request-count
benchmark. Set `ARVIO_LIVE_COLLECTION_BENCHMARK=1` to additionally measure two public
lists from the bundled Kaptain pack (requires the configured TMDB key). JVM timings
exclude image decoding and UI rendering. The opt-in Android instrumentation test
`CollectionLoadingDeviceTest`, with argument `collectionLive=true`, measures actual
repository loading, poster downloads/decoding, warm reloads and pagination.
