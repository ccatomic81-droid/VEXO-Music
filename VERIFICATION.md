# VEXO Music 0.7.0 — verification

Checked 24 October 2026.

| Check | Result |
| --- | --- |
| Normal host suite | 77 passed, 5 opt-in live checks skipped (82 total) |
| APK build | Passed; clean rebuilt and packaged version code 9 verified |
| Lint | 0 errors, 30 non-fatal warnings |
| New UI | Home, playlist, live-match layout, keyboard space and large text inspected |
| Layout sizes | 360×800, 320×568, 320×640 with 1.6× text |
| Playlist persistence | Save mix, deduplicate, add, rename, removal safety and activity recreation passed |
| Package/version | space.vexo.music.radio / 0.7.0 / code 9 |
| Signing | Same v2 signing certificate as v0.4.x–v0.6.0 |
| Live discovery provider | Optional GitHub probe; see workflow run/artifact |
| Physical Android listening | Not verified here |
| Public app-update feed | Still disconnected |

`testDebugUnitTest lintDebug assembleDebug` passed after the last production changes. A final clean assembly corrected stale incremental manifest metadata; the actual APK manifest was verified as version 0.7.0/code 9, along with the new discovery classes and existing signer. XML results are under `verification/main-tests/`. The normal suite uses synthetic tracks and controlled sources for deterministic checks. It verifies stale asynchronous results, country-specific caches, playlist metadata safety, native controls and navigation, the keyboard's reserved space, and retaining the field while songs arrive. No Android emulator or physical phone was used.

A separate opt-in `MusicDiscoveryLiveTest` requests real playable music metadata and cover URLs for search and a regional mix. GitHub Actions runs this after the normal checks and publishes a separate artifact. That network-dependent probe can fail independently of the normal suite. It does not download or listen to audio; no physical playback quality claim is made. Earlier `verification/live-network` captures are historical results from prior versions.

Home prefetches the first three VEXO mix metadata lists with a two-worker pool and short request timeouts. Remaining mixes load when opened. A previously populated list is retained on refresh failure; an empty failed mix shows Retry. Background artwork updates do not rebuild the Home screen. Search supplies cached matches immediately, then up to six debounced online matches. Artist/channel names come from song metadata rather than Spotify profiles.

Play all and one-shot shuffle both start a fresh collection queue using the existing player and its queue-size limit. Shuffle randomizes a copy, preserving stored order. Public update delivery remains unconfigured because source is private; What's new still works offline. See UPDATES.md.

Certificate SHA-256: `0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`

APK SHA-256: `79977832d41bb4ff2f2bae744beaad964f3340f62a746ee5736da86b40afe54c`

Install over the existing app to retain data. Signing keys, local SDK files, build outputs and Gradle caches are excluded from the source package.
