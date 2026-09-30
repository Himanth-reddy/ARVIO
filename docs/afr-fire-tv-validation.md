# AFR / Fire TV validation

## Scope

The report does not yet identify the exact Fire Stick model, Fire OS version,
AFR setting, or failure symptom. These fixes address reproducible problems in
ARVIO's refresh-rate selection and request lifecycle; they are not a claim that
the reporter's HDMI setup has been tested.

- Use one display-switch mechanism at a time. Try a compatible same-resolution
  display mode, verify it, then use the Android 12+ surface hint only if needed.
- Do not repeat a pending window request on surface recreation.
- Keep an already-compatible active refresh rate, including integer multiples.
- Cancel an obsolete pending preference when the next video needs another rate.
- Canonicalize small timestamp rounding differences without confusing 23.976
  with 24, or 59.94 with 60. Require stable declared-rate changes after startup.
- Restore the original preference per window, including after a switch timeout.
- No additional stream probing or provider connection is introduced.
- Off and Seamless only retain their existing meanings.

## Automated checks

`PlaybackFrameRateTest` covers timestamp estimation, unstable metadata, seeks,
source resets, fractional rates, and compatible active display modes.

`FrameRateUtilsTest` uses simulated Android displays to cover pending requests,
source changes, resolution preservation, independent window restoration,
rejected requests, delayed confirmation, and the four-second timeout. The timeout
is asynchronous; it does not pause playback or block the main thread.

## Required hardware follow-up

On the affected Fire Stick, record the model, Fire OS version, TV/AVR model,
ARVIO AFR setting, and Fire TV's frame-rate matching setting. Test known
23.976, 24, 25, 29.97, 50, and 59.94 fps files with Off, Seamless only, and Always.

Confirm the HDMI refresh rate with the TV/AVR information display. Check start,
pause/resume, seek, next episode, returning home, and switching to another app.
Verify no repeated blanking, no stuck playback, and restoration on player exit.
Capture `adb logcat -s FrameRateMatch` alongside the symptom. An emulator cannot
verify an actual Fire TV HDMI mode change.

## References

- [Amazon display-mode APIs](https://developer.amazon.com/docs/fire-tv/4k-apis-for-hdmi-mode-switch.html)
- [Android frame-rate API guidance](https://developer.android.com/media/optimize/performance/frame-rate)
