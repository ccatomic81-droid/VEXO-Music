# VEXO 0.24.0-dev visual tester

Addresses the supplied 0.23 Home and Now Playing screenshots. Covers now remove conservative symmetric near-black letterboxing from non-square thumbnails before an undistorted center crop. Square album artwork keeps its original composition. The transformation has a new Glide disk-cache key, so previous fitted/letterboxed cached results are not reused.

Home has a smaller avatar/name row, compact flat filter pills, low-emphasis region and PureSong controls, no duplicated large radio CTA, and clean playable artwork tiles without overlay buttons. Shelf widths follow the measured viewport rather than screen configuration, keeping two cards aligned in split-screen and inset layouts.

The mini-player is flatter, shorter, and gives song titles more space. The player has an artwork-based dark tint, a smaller white primary play button, better cover/title/seek spacing, concise queue status and no redundant online-source footer. Existing profile, settings, Smart Experience, MindRadio, PureSong, playlists, offline, queue and radio functions remain. YouTube connection stays optional.

Version 0.24.0-dev / code33 updates 0.23.0-dev/code32 and earlier compatible installs. Full Android tests, lint/build variants, live discovery, native rendered layouts and signed/downloaded APK checks are required before tester publication. Stable stays 0.22.0/code31.

Published opt-in tester: https://vexo-music-testing.kavindukavishka785.workers.dev/ . App commit `c1aa9ee53f005b610da95c7a5d8557ae2caf3787`, workflow `40431610871`: 200 Android tests passed and ten skipped; seven live provider checks passed; both lint/build variants and dependency/security checks passed. Native renders reviewed at small/standard/large phone widths and large player text. Signed APK payload matches CI, uses the original certificate, and has debug mode disabled. Download hash, HEAD/range, archived version and closed publication routes verified. Physical-phone acceptance testing remains outstanding.
