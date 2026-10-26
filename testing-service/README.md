# VEXO Music tester channel

Tester download: https://vexo-music-testing.kavindukavishka785.workers.dev/

Current tester build: **0.21.0-dev, version code 30**. Source commit: `b4c8a3b724d561552ed1fe24b9f49091199a5ae6`; Android Actions run `40332296821`; implementation PR #16. The non-debuggable release APK uses the existing signing certificate and updates 0.20.0-dev/code 29 or older compatible VEXO installations while retaining data.

This update adds short-lived session recommendations from audible VEXO listening, manual queue priority, shuffle-safe Play next, queue recommendation labels, recent-history radio protection and less unrelated station drift. YouTube account connection stays optional. MindRadio, PureSong, Smart Experience, crossfade, offline music and the simpler Home/Search remain. See [behavior and fixes](../docs/SESSION-RADIO-QUEUE-0.21.md).

Download and choose **Update**; keep VEXO installed. Check music playback, saved data, guest recommendations, manual and shuffled queues, radio continuity, crossfade, background listening and Bluetooth on a physical phone. Report issues through Settings → Report a problem.

This is an opt-in tester download. The built-in updater still checks stable; stable remains 0.19.3/code 28. Android will require a version code above 30 for the next upgrade.

Validation: 198 Android tests, zero failures/errors, eight skipped integration tests (190 passed); debug/release lint and builds; dependency and source security checks; live discovery probe. Host tests cover streaming, range/HEAD/cache, immutable objects, closed writes, stable isolation and archived routes including 0.20.0-dev. Phone audio/background acceptance testing remains required.

The APK certificate was checked and the signed archive contents were compared with the exact verified unsigned CI candidate. Current public files use `testing/0.21.0-dev/`; older APK addresses are preserved. The deployed Worker has only its R2 binding and no publication routes. No private signing or publication credentials belong in this repository.
