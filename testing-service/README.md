# VEXO Music tester channel

Tester download: https://vexo-music-testing.kavindukavishka785.workers.dev/

Current tester: **0.24.0-dev, version code 33**. App source `c1aa9ee53f005b610da95c7a5d8557ae2caf3787`; Android workflow run `40431610871`; PR #18. The non-debuggable release APK uses the existing signing certificate and updates 0.23.0-dev/code32, public 0.22.0/code31 and older compatible installations while retaining data. Download and choose **Update**; keep VEXO installed.

Artwork uses an undistorted album-style crop and removes detected symmetric black video borders. Home has compact filters, aligned playable cover cards, fewer duplicate radio controls and a slimmer mini-player. Now Playing has an artwork-based dark tint, clearer spacing and concise queue status. Existing loading recovery remains. See [visual changes](../docs/UI-POLISH-0.24.md).

Includes [0.23 song-radio fixes](../docs/SONG-RADIO-0.23.md): selecting a search song plays only that song, then related radio; starting radio preserves current playback and manual queue entries. Unique Smart Experience, MindRadio, PureSong, crossfade, playlists and offline features remain. YouTube connection stays optional.

Validation: 210 Android tests, zero failures/errors, ten skipped integration tests (200 passed); both lint/build variants; dependency audit and source guard; seven live discovery checks passed. Native Home renders inspected at 320, 360 and 393 px, plus standard and large-text player layouts. Six tester-host and three public update-host tests passed. The signed payload matches the exact CI candidate; original certificate, package, version and non-debuggable flag verified. Public download checksum, HEAD, ranges, archived 0.23 route, closed writes and stable isolation passed. See [verification](../verification/tester-0.24.0-dev.json).

On your phone, test a search selection and Up next, cover crops, manual queue preservation, saved data, crossfade, Bluetooth and background playback. Physical-device audio and upgrade acceptance testing remains required. Report issues through Settings → Report a problem.

The built-in updater still checks stable **0.22.0/code31**. This opt-in tester uses this download link; the next update needs a version code above 33. Current files use `testing/0.24.0-dev/`; archived APK addresses remain available. The deployed Worker has only its R2 binding and no publication routes or credentials.
