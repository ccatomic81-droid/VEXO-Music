# Opt-in VEXO Music test release host

Public tester page: https://vexo-music-testing.kavindukavishka785.workers.dev/

Current candidate: **0.13.1-dev, version code 16**, signed with VEXO's original certificate and built with debug mode disabled. Its non-signature APK contents exactly match the tested CI release candidate from `03b4c9d44a2c8583c9d7196bf22ebc14ca1edec8` (Android Actions 40193399178). The public stable release stays at 0.12.0/code 14. No stable branch merge or automatic enrolment is part of this test publication.

## Test installation

1. Open the tester page on an Android 8+ phone and download the APK.
2. Keep the existing VEXO app installed; choose **Update** when Android opens the APK. Android may ask to allow installation from the browser.
3. Check existing playlists, favourites, profile, memories and queue. Do not uninstall or clear app data.
4. Test automatic next songs, category/language search, local intro markers with crossfade and full-version override, network interruption recovery, Bluetooth controls and background playback. Test rooms on two phones.
5. Report problems through **Settings → Report a problem**. Test installations wait for a newer test/public build; an in-place downgrade to 0.12 is not provided. The next public update must use code 17 or higher.

This is a public opt-in link, not an authenticated closed-testing group. The installed app still reads the stable feed; testers must use this link to install this candidate. No new update push notification or sign-in system is claimed.

## Scope and limits

The candidate adds private audible-time listening learning, category/language search, Home Skip intros controls, shared PureSong settings, encrypted legacy library/profile migration, authenticated future updates and safer media controls. Existing VEXO solid colors and music features remain. Intro markers apply only to confirmed imported audio; online songs remain uncut. Accounts, push notifications and authorized YouTube library sync need project configuration. Licensed lyrics, complete transliteration, popularity/audio features and newly authorized online offline playback remain external dependencies. Physical Android acceptance testing is still required.

## Hosting and isolation

Only exact checksum-pinned APK/manifest/checksum names in `artifacts.mjs` can be read. New objects are immutable under `testing/0.13.1-dev/` in the existing release bucket. The 0.10, 0.11 and 0.13.0 test APK addresses remain supported at their original immutable prefixes. No source ZIP is uploaded here; corresponding source links the already-public GitHub commit.

`service.mjs` is the final read-only entrypoint with the R2 binding alone. It streams downloads, supports ranges/HEAD/ETags and noindexes the test page. The temporary publisher uses a short-lived capability, checks hashes/sizes, rejects differing overwrites, and publishes the manifest only after all artifacts exist. It cannot address stable objects. After publication, remove the publisher entrypoint and publishing bindings. Never commit tokens, signing keys or passwords.

Tests cover ranges/cache, closed writes, authentication/expiry, checksum validation, publication ordering, stable isolation and archived APK routing. Live verification independently downloads and checks the served APK and original certificate, compares the stable manifest before/after, checks HEAD/ranges, and confirms write routes are closed. See `verification-0.13.1-dev.json`, `../release/verification-0.13.1-dev.json` and `../release/update-0.13.1-dev.json`.

No paid plan or new paid resource is enabled. This reuses the existing release bucket and test Worker, subject to existing account quotas.

The 0.13.1 update specifically cleans Search: compact language/source controls, horizontal recent queries, uniform category widths and spacing, matched mini-player/content margins and reduced navigation glyphs. Native checks include 393dp, 320dp, keyboard-visible and 160% text layouts.
