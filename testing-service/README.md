# VEXO Music tester channel

Tester page: https://vexo-music-testing.kavindukavishka785.workers.dev/

Current tester build: **0.20.0-dev, version code 29**. The non-debuggable release APK uses the original app certificate and can update public 0.19.3/code 28 while retaining app data. Source: `7d1ff89de54d03d698e45c6db0065217b094972c`; Android Actions run `40324049550`.

This build simplifies Home and Search while preserving all five navigation destinations, MindRadio, Pure Song, Smart Experience, personalization, accounts, crossfade, offline music and playlists.

Install from the tester page and choose **Update**. Keep VEXO installed. Check music playback, saved data, search suggestions, radio continuity, intro controls, crossfade, background listening and Bluetooth on a physical phone. Report issues through Settings → Report a problem.

This is a public opt-in tester link. The built-in updater continues to check the stable feed. The next upgrade must have a code above 29; Android does not install an older stable APK over this test build. Stable remains 0.19.3/code 28.

Validation: 193 Android tests, 0 failures/errors, 8 skipped integration tests; debug/release lint; debug/release builds; dependency and source security checks; 21 service checks. The release host has six checks for streaming, range/HEAD/cache, exact checksums, closed writes, stable isolation and archived APK routing. Physical-phone audio and device behavior still require acceptance testing.

The APK was signed locally using the protected original key, certificate verified, and its non-signature archive contents compared with the exact CI candidate. Immutable files use `testing/0.20.0-dev/`; previous tester APK URLs remain supported. Only APK, manifest and checksums are exposed. The final Worker has only its R2 binding and no publication routes. No signing or publishing credential belongs in this repository.
