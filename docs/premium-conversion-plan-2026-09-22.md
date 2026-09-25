# Premium playback and conversion improvement plan — 22 September 2026

## Baseline and success criteria
Use 17–21 September UTC as the last complete five-day baseline: 62 trials started, no recorded trial-start failures, 30 account-day checkout opens, three confirmed new memberships. Playback recorded 34 request account-days, 26 failure account-days and 19 start account-days; these overlap and are not an attempt success rate. Website pageviews rose to 6,859. Do not infer that payment processing caused the conversion gap.

## 1. Make trials demonstrate useful playback
- Audit source discovery, browser transport selection, startup timeout, stalled playback, recovery and external-player fallback.
- Fix reproducible failures and bounded retries; preserve progress and selection when changing source.
- Distinguish recoverable transport errors, terminal failures and user-paused playback. Avoid retry loops and misleading failure counts.
- Detect usable, enabled streaming addons and home servers; catalogs alone must not suppress source setup guidance.
- Offer focused setup and recovery actions, using the existing app design and translations.
- Verify synthetic playable media and fault fixtures, then real installed source discovery/playback using the authorized account. Do not publish credentials or media URLs. Record browser/provider limitations honestly.

## 2. Measure the journey without overstating attribution
- Count Premium landing views and membership/web handoffs with bounded first-party events; never block navigation for analytics.
- Preserve campaign/source through sign-in and trial, and distinguish link clicks from payment attempts.
- Match only confirmed server-side Ko-fi subscription events to authenticated accounts and access checks. Direct Ko-fi purchases with no known journey remain unattributed.
- Add useful anonymous failure dimensions and comparable complete-day cohorts. No email, source URL, title, device fingerprint or provider credential in marketing events.
- Test forged paid events, hostile metadata, deduplication, origin restrictions, disabled storage, network errors and existing membership activation/linking.

## 3. Validate, release and review
- Run the relevant web and auth suites and a production build; test desktop and narrow touch layouts, safe retries, source setup and membership actions.
- Publish backend first, then website and webapp; verify production endpoints/pages and preserve a rollback reference.
- Do not charge a test payment, send promotional messages, or change billing providers/prices.
- Schedule comparison after seven and fourteen complete days. Separate new memberships from renewals, compare mature cohorts and flag measurement gaps. Today’s promotion changes cannot be judged using earlier traffic.

## Deliverables
Code and regression tests, aggregate baseline/report evidence, deployment references, documented browser checks and an automatic follow-up review. Real payment approval remains unverified unless an authorized payment is completed by the user.

## Implemented and verified
- Fixed bounded stalled-stream recovery, live timeline changes, failure-before-teardown position capture, refreshed-link resume and Retry/Sources actions.
- Corrected catalog-only source detection, inline onboarding, direct settings destinations and trial-first membership UI. Added translations across all 56 non-English dictionaries.
- Real account testing exposed addon donation/Discord/no-result entries masquerading as streams; definite informational entries are excluded from playback and automatic selection.
- Selected sources that declare browser-controlled request headers now use the existing configured media relay. HLS and file playback retain their normal path; MKV can still be repackaged. DASH remains direct because the relay does not rewrite MPD manifests. Unknown custom headers are preserved rather than silently dropped.
- Real production testing exposed repeated update reloads: the client build stamp was newer than its stale public manifest. The manifest now builds with the client, older stamps cannot trigger reloads, repeat attempts are guarded and open players defer updates.
- Added privacy-limited first-party navigation attribution and server-only payment/access observations. Reports distinguish unknown attribution, renewals, account-days and provider errors.
- Validation: 694 web tests and 97 backend tests passed; responsive setup and mock membership flows checked at 320/390/1024/1440px, including Spanish, Dutch and Arabic. Full player UI checks verified failed-source Retry, replacement with HLS, pause and seek. The final web CI run is linked below.
- Auth deployment: `6ab270d4d3fad4c876efa94c`. Production testing caught and fixed a Netlify Blob context compatibility issue in the public measurement endpoint; it now returns HTTP 200. The protected report confirmed the QA landing journey and one paid-access observation. Reports for 5/30/90 days returned in 2.2–2.7 seconds and reproduced the complete five-day baseline. Historical paid-access observation is unmeasured, not failed access.
- Verified the deployed Spanish Premium page, the direct Ko-fi membership link and ARVIO-only journey handoff. The production report recorded one QA membership click, one web click and one connected account; no journey identifier is sent to Ko-fi.
- After deployment, the previously failing PenguPlay 720p Cinejoy source played the actual *Sintel* film (1280px video, 14:49 duration). Pausing, skipping forward 30 seconds and resuming worked; video time advanced beyond 1:11 with enough buffered media. This confirms real addon playback in the production desktop browser, not just synthetic fixtures.
- Final production source-list check: zero observed donation/no-result/community notices remained; 55 browser-playback candidates remained available. QA campaign traffic is excluded from the scheduled marketing analysis.
- Follow-up reviews scheduled for 30 September and 7 October at 10:00 Amsterdam time, after seven/fourteen complete post-release days.

## Test limits
No real payment was charged. Synthetic playable MP4 and HLS verified the player itself. Live testing used the authorized account and the freely released film *Sintel*. Before the final relay fix, some PenguPlay sources failed to fetch or exhausted HLS recovery; the 720p Cinejoy source passed after deployment. HDHub returned no-result and donation notices; MovieBox returned a 21-second installation advertisement instead of the film. That advertisement is not a successful movie-playback test. Provider CORS, availability and codec restrictions can still prevent an individual third-party stream from playing; working recovery cannot make every source compatible. No claim is made that the whole film was watched or that every physical device/browser was tested.

## Release references
- `7b6b1edb5`: playback recovery, source setup, Premium measurement and update-loop fixes.
- `0d414296e`: production measurement compatibility fix.
- `0b78619bb` and `2b587b150`: selected-source header relay, DASH guard and remaining donation notice filtering.
- `5387c074c`: exclude the observed HDHub donation webpage; final production build stamp `1790080287014` verified by CI and browser checks.
- Final web production build and verification: https://github.com/ProdigyV21/ARVIO/actions/runs/35727524046
- Aggregate evidence and private test logs remain under `artifacts/`, outside published site roots.
