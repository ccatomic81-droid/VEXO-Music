# VEXO Music 0.8.0 — verification

Checked 25 October 2026.

| Check | Result |
| --- | --- |
| Normal host suite | 84 passed, 5 opt-in live checks skipped (89 total) |
| Clean build | testDebugUnitTest lintDebug assembleDebug passed |
| Lint | 0 errors; warnings listed in verification/lint-results-debug.txt |
| Update service contract | 2 Node tests passed |
| Package/version | space.vexo.music.radio / 0.8.0 / code 10; actual APK manifest checked |
| Signing | Same v2 signing certificate as v0.4.x–v0.7.x |
| Profile | Required name, Unicode, persistence, draft recreation, photo copying/cropping and invalid-file preservation passed |
| Android 8 compatibility | Photo import and existing Home layout checks passed |
| Artwork | Small images keep resolution; large images bound to 1024; known-host URL upgrades with fallback |
| UI | First-open profile and personalized Home rendered and inspected at 320×640; existing small/large-text suites passed |
| Public delivery | Live 0.8.0 feed and complete APK/source downloads verified against local SHA-256 |
| Physical phone listening | Not verified here |

The suite uses Robolectric and controlled metadata, not an Android emulator or physical phone. Rendered Home fixtures use placeholders; they do not represent a bundled music catalogue. These checks establish layout, persistence, image decoding and request logic, not physical-device audio or source-image availability. Existing search, playlist, radio, offline and crossfade tests passed.

The optional live discovery probe requests real music metadata and artwork URLs; it does not listen to audio. Earlier live-network reports are historical. Source availability can still prevent playback. The app requests larger covers where supported but cannot improve detail absent from the source.

Updates now use the compile-time public service with no configurable URL field. The delivered APK has the original signer; runner-signed CI artifacts remain clean-install test builds. The publication tool uploads files before the manifest and checks checksums. After publication, the temporary publishing deployment is replaced with the read-only service and public downloads are checked against the local hashes. See UPDATES.md and update-service/.

Certificate SHA-256: `0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`

APK SHA-256: `0e32923143a7ac7e02d1af3d445b0d0bb24a023afba85eefb240e342ca29b19f`

Install over the existing app to retain data. Signing keys, publishing tokens, SDK files, build outputs and Gradle caches are excluded from source.

## Public release verification

The live feed returned the correct app ID, version code 10, version name 0.8.0, signed APK URL, source URL, byte count, checksum and certificate identity field. Complete public APK and source downloads matched the local originals exactly. HEAD, byte-range resume (206), ETag revalidation (304), and rejection of public writes (405) passed. The production deployment has only the RELEASES R2 binding; the temporary token/hash and publishing entrypoint were removed.

GitHub Actions run 40142408723 completed successfully for the app code, Android checks, update-service checks and package verification.
