# VEXO Music tester channel

Tester download: https://vexo-music-testing.kavindukavishka785.workers.dev/

Current tester: **0.23.0-dev, version code 32**. App source `6034934319352df4fcd5af844f640512e110d455`; Android workflow run `40372224321`; PR #18. The non-debuggable release APK uses the existing signing certificate and updates public 0.22.0/code31 and older compatible installations while retaining data. Download and choose **Update**; keep VEXO installed.

Search playback starts only the selected song, followed by recommendations from that song’s music radio. Original-station candidates guide continuation; unrelated search results, persistent mood queries and unrelated taste seeds no longer redirect ordinary song radio. Explicit mixes and MindRadio retain their own scope. Starting radio refreshes only automatic upcoming picks while keeping current playback and manual queue entries. Artwork fits the full image, and repeated radio/player controls have been removed. YouTube connection remains optional. See [behavior and fixes](../docs/SONG-RADIO-0.23.md).

Validation: 207 Android tests, zero failures/errors, ten skipped integration tests (197 passed); both lint/build variants; dependency audit and source guard; seven live discovery checks passed, including real artwork decode and a selected-song station producing ten distinct tracks across ten artists. Six tester-host tests passed. The signed payload matches the exact CI candidate, and its original certificate, package, version and non-debuggable flag were verified. Public download checksum, HEAD, ranges, archived 0.21 route, closed writes and stable isolation were checked.

On your phone, test a search selection and Up next, image fit, manual queue preservation, saved data, crossfade, Bluetooth and background playback. Physical-device audio and upgrade acceptance testing remains required. Report issues through Settings → Report a problem.

The built-in updater still checks stable **0.22.0/code31**. This opt-in tester uses this download link; the next update needs a version code above 32. Current files use `testing/0.23.0-dev/`; archived APK addresses remain available. The deployed Worker has only its R2 binding and no publication routes or credentials.
