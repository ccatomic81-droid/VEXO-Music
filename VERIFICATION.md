# VEXO Music 0.9.0 — verification

Checked 25 October 2026.

| Check | Result |
| --- | --- |
| Normal host suite | 90 passed, 5 opt-in live checks skipped (95 total) |
| Build | Clean test/lint/build passed; final checks passed after artwork queue capacity adjustment |
| Lint | 0 errors, 30 non-fatal warnings |
| Update service contracts | 2 Node tests passed |
| Package/version | space.vexo.music.radio / 0.9.0 / code 11; actual APK manifest checked |
| Signing | Original v2 certificate retained |
| Greeting | 04:00 morning boundary, foreground clock/time-zone broadcasts and resume update passed |
| Android 8 | Home compatibility and photo import checks passed |
| Recovery | Crash marker consumed once; automatic-recovery opt-out respected; temporary caches cleared |
| Data retention | Profile, playlists, favourites/history, recent searches, queue position and offline audio preserved in recovery checks |
| Diagnostics | 12-record bound; exception messages and user data excluded; report addressed to requested mailbox |
| Report UI | Preview and clipboard action passed; email intent validated |
| Artwork queue | 100-request stress check stays within 48 queued tasks and repair drops pending work |
| UI | Native renders inspected at 360×800 and 320×640 with 1.6× text, including mini-player |
| Device playback | Physical Infinix/OEM behavior and audible streaming not verified here |
| Email delivery | Opens a user-reviewed email draft; actual mail-client sending/delivery not verified here |

These are Robolectric host-side checks and synthetic native renders, not physical-device tests. Placeholders in fixture renders do not represent missing production artwork or a bundled music catalogue. Actual covers still depend on the music provider. Existing search, playlist, offline, radio and crossfade checks passed.

The final APK was verified with aapt and apksigner. The update feed is built in and public release files use the same signing identity as previous delivered versions. The CI APK uses the runner's different debug signer and remains a clean-install test artifact.

Warnings include existing resource/localization, custom-view tooling and drawing-allocation suggestions, plus diagnostic storage-space guidance. The synchronous preference commit in fatal-error recording is intentional so the crash marker persists before Android terminates the process.

Automatic repair refreshes only replaceable discovery, stream-address and artwork caches. It does not delete private collections/audio, restart silently, reset Android permissions, or guarantee repair of provider/network/OS problems. Reports remain local until a user chooses to share them. See ISSUES-FIXED.md for findings and limits and UPDATES.md for release publication.

Certificate SHA-256: `0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`

APK SHA-256: `4b8a86d6b3dd68a1ea8294ebdef988c69e6a583e71d939248802a7b412eea026`

Install over the existing app to retain data. Private signing/publishing keys, SDK files, Gradle caches and build outputs are excluded from the source archive.

## Published release

The public stable feed returned the correct application ID, version 0.9.0/code 11, APK/source URLs, byte count, APK SHA-256 and certificate field. Full public downloads matched the local APK/source hashes. HEAD, byte-range resume (206), ETag revalidation (304), and rejected public publishing writes (405) passed. The production Worker retains only its R2 binding; the temporary publishing token and hash were removed.

GitHub Actions run 40144401512 passed the Android test/lint/build, update-service contracts, APK package verification, and optional live music-metadata probe. Metadata checks do not establish audible playback or physical-device reliability.
