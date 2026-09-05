# Build and feature notes

## Video player lint fix

### Error
The Android CI lint stage failed with two `MissingPermission` errors in `VideoActivity.kt`. The video player calls the Android vibration API for the long-press speed gesture and uses audio-volume/audio-effect APIs.

### Fix
Added the required normal Android permissions to `AndroidManifest.xml`:

- `android.permission.VIBRATE` — authorizes the long-press haptic feedback.
- `android.permission.MODIFY_AUDIO_SETTINGS` — authorizes programmatic music-stream volume/audio-setting changes used by the video player.

The fix keeps the feature behavior intact instead of suppressing the lint checks.

### Why the feature exists
The video player provides touch controls so playback can be adjusted without opening menus:

- Long-press for playback-speed adjustment.
- Vertical swipe on the right side for volume.
- Vertical swipe on the left side for brightness.
- Haptic feedback when the long-press speed gesture activates.

### How it works
`VideoActivity` receives touch events from the `PlayerView`. A three-second long press activates the speed gesture and uses `Vibrator`/`VibratorManager` for haptic feedback. Vertical movement is mapped to volume or screen-brightness changes depending on which half of the video was touched. Playback speed is constrained to the supported 1x/2x/3x/4x steps.

## CI policy
For future changes, commit messages will use:

**Title:** short description of the change

**Description:** what changed, why it was needed, the problem/error addressed, and how the implementation works.

Feature additions will also document the feature purpose and implementation behavior in the project notes when appropriate.
