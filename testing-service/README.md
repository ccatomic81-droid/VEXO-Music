# VEXO Music tester channel

Tester download: https://vexo-music-testing.kavindukavishka785.workers.dev/

Current tester: **0.25.0-dev / code35**. App source `78ef2666e16618560793ad707eac7d4bae311264`; Android workflow `40465642903`; PR #19. Uses the original certificate and a non-debuggable release APK. It updates public 0.24.0/code34 and prior testers without uninstalling.

Includes TikTok-inspired Trending Music from public music playlists/search, personalized mood mixes, on-device listening-interest learning, Daily Mix, New to you, Tune My Mix and continuous smart mix queues integrated with VEXO Home/player. Tuning keeps the playing song, its position/pause/repeat state and manually queued songs. YouTube account connection stays optional. Unique MindRadio, PureSong, Smart Experience, crossfade, offline music, accounts and playlists remain accessible.

Trending is curated discovery from public music playlists/search with source labels and refresh times, not an official TikTok chart. New to you means unplayed in retained VEXO data rather than verified new releases. See [implementation details and limitations](../docs/DISCOVERY-MIXES-0.25.md).

Validation: **216 Android tests passed, eleven skipped, zero failures/errors**; **eight live provider checks passed**, including actual daily/mood/trending/new-to-you songs and existing radio, artwork and catalogue continuation. Both lint/build variants, dependency audit and source security guard passed. Six tester-host plus three public update-host tests passed. Native Home, mix page, Trending and standard/large-text tuning layouts were visually reviewed. The signed payload matches the CI candidate; package, version, original certificate and non-debuggable flag verified. Public checksum, HEAD/range, archived 0.24 tester, closed writes and unchanged public 0.24 feed verified. See [verification](../verification/tester-0.25.0-dev.json).

Install as **Update**, keeping VEXO installed. Test a mood/Daily/Trending selection, tuning while playing and paused, manual queue order, saved playlists, crossfade, Bluetooth and background playback on your phone. Physical-device audio and upgrade acceptance are still needed. Report through Settings → Report a problem.

The built-in updater checks public **0.24.0/code34**. This opt-in tester uses its download link; the next build needs a version code above35. Current files use `testing/0.25.0-dev/`, with earlier APK addresses preserved. The deployed tester has only its R2 binding and no publication routes or credentials.
