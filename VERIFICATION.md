# VEXO Music 0.6.0 — verification

Checked 24 October 2026.

| Check | Result |
| --- | --- |
| APK assembly | Passed |
| Lint | 0 errors, 26 non-fatal warnings |
| Normal host suite | 67 passed; 4 opt-in network checks skipped (71 total) |
| New search/update checks | 8 passed in the normal suite |
| Search suggestions UI | Rendered and inspected at 320×568; navigation remains visible |
| Playback session regression | 3 passed on API 34 |
| Existing UI coverage | Android 8 / API 26 and Android 15 / API 35 |
| Live suggestion request | Unverified: environment proxy tunnel timed out |
| Package/version | space.vexo.music.radio / 0.6.0 / code 8 |
| Signing | v2 signature; same certificate as v0.4.x/v0.5.0 |
| Public update feed | Not connected; configurable in Settings or at build time |
| Physical phone listening | Not verified in this environment |

`testDebugUnitTest lintDebug assembleDebug` passed after the final production changes. Consolidated suite results are under `verification/main-tests/`. A separate opt-in live autocomplete test failed during proxy tunnel establishment; that is recorded under `verification/live-network/Suggestions-probe-0.6.0.xml`. No live autocomplete success is claimed. Normal feature tests cover the real suggestion controller against simulated responses, including debouncing, cancellation, stale delivery, cache country separation, provider failure and privacy controls. UI checks use synthetic music fixtures. Update transport tests use an in-process HTTP interceptor rather than a public server.

The first full-suite attempt hit a Robolectric 4.17 Android 15 loudness-codec/Bluetooth simulation error in an existing playback regression test. The Media3 wrapper/session tests now run on API 34; production target remains API 35 and UI coverage remains API 26/35. All 3 session tests pass. This does not verify Bluetooth playback or audible crossfade on a phone.

The app-update feature shows a local What's new summary once per installed version and supports optional daily foreground checks of a public HTTPS feed. The source repository is private, so public delivery is not yet connected. No automatic background push or silent installation is claimed. See `UPDATES.md` for configuration and upgrade-signing requirements.

APK certificate SHA-256:
`0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`

APK SHA-256:
`b6d44419efde067e96721153e07d64d8201b2b25a3f90f5cb5e4033437532b97`

Install over the existing VEXO Music app to keep local data. The signing key and machine-specific SDK files are excluded from source.
