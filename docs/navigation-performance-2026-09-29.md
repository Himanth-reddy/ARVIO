# Home and Settings navigation verification

## Scope

Preserve the existing visual design, artwork resolution, controls and animation
durations. Optimize rendering and preference work, not the feature set.

- Home crossfade uses per-draw alpha for its single image. Static scrims are
  cached separately instead of repainting a combined full-screen image layer.
- Catalog observers ignore unrelated preference writes before parsing JSON.
- Settings playlist decryption, integration reads and reconciliation run off
  the input thread. Catalog observation no longer repeatedly reconciles defaults.
- TV catalog settings virtualize off-screen rows with stable keys. Row layout
  preferences are collected together, and unchanged rows have a separate
  composable boundary and cached drawing layer.

## Physical TV measurements

Android 14, 60 Hz, approximately 2.34 GiB accessible RAM. Signed, minified
sideload release 2.0.0 (317), ARM64/ARMv7, no debug or emulator ABI payload.
Home scrolling was followed by Settings navigation in the same app session.

The baseline already contained the initial Settings background-work and
virtualization fixes. These comparisons measure the additional rendering work,
not the full difference from an older published release.

| Scenario | Inputs | Baseline jank | Final jank | Baseline median/p95 | Final median/p95 |
| --- | ---: | ---: | ---: | --- | --- |
| Home (warm baseline) | 78 | 43.78% | 12.16% | 61/85 ms | 30/48 ms |
| Settings section sidebar | 60 | 21.86% | 21.60% | 15/57 ms | 17/61 ms |
| Settings Playback content | 44 | 0.98% | 0.98% | 14/15 ms | 14/15 ms |
| Settings Catalogs content | 96 | 32.34% | 30.75% | 21/38 ms | 20/36 ms |

Final rendered frame counts: Home 1324, sidebar 338, Playback 204, Catalogs 735.
An earlier candidate independently measured Home at 12.14% jank. Catalog and
sidebar differences are small; do not interpret them as a confirmed meaningful
improvement. Section switching and catalog scrolling still need further work.

Run `scripts/measure-tv-navigation.ps1` with the device serial, ADB path and
scenario. Position focus first on a Home card, the Accounts sidebar entry, or
the first content row for the two content scenarios. The driver sends direction
keys only, waits 250 ms after each ADB call, resets gfxinfo and saves raw frame
statistics plus a JSON summary. Effective input spacing includes ADB overhead.

Measurements exclude app startup and screenshots. These are Android gfxinfo
frame metrics, not input-to-photon latency or an FPS measurement. One TV and
short deterministic runs cannot establish smoothness on every device. Screenshots
and traces remain local because they contain personal account/library data.

## Verification

- 27 unit tests passed: catalog observation/deletion, profile-specific layout
  preferences, Home focus state and Settings scrolling geometry.
- 10 Android device tests passed on an Android 14 emulator with 1536 MB RAM:
  single-press activation, held input, picker focus, large IPTV category state,
  300-catalog navigation/actions and shrinking catalog lists.
- Release installed on the physical TV using `adb install -r`. The signing
  certificate, original install timestamp and data inode were preserved.
- Installed APK SHA-256 matched the built artifact:
  `c265c2c20d4d193a7892610ffce6b6a4dae6d8cf5c498b53373a6d05dd1e2c79`.
- Package flags confirmed a non-debuggable build. No AndroidRuntime crash was
  observed during the final Home-to-Settings run. Account connections remained.

Adding separate layers to ordinary Settings controls did not produce a benefit
and was removed before the final release build. No animation was disabled or
shortened to obtain the Home improvement.
