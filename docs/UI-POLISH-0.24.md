# VEXO 0.24.0-dev visual tester

Addresses the supplied 0.23 Home and Now Playing screenshots. Covers now remove conservative symmetric near-black letterboxing from non-square thumbnails before an undistorted center crop. Square album artwork keeps its original composition. The transformation has a new Glide disk-cache key, so previous fitted/letterboxed cached results are not reused.

Home has a smaller avatar/name row, compact flat filter pills, low-emphasis region and PureSong controls, no duplicated large radio CTA, and clean playable artwork tiles without overlay buttons. Shelf widths follow the measured viewport rather than screen configuration, keeping two cards aligned in split-screen and inset layouts.

The mini-player is flatter, shorter, and gives song titles more space. The player has an artwork-based dark tint, a smaller white primary play button, better cover/title/seek spacing, concise queue status and no redundant online-source footer. Existing profile, settings, Smart Experience, MindRadio, PureSong, playlists, offline, queue and radio functions remain. YouTube connection stays optional.

Version 0.24.0-dev / code33 updates 0.23.0-dev/code32 and earlier compatible installs. Full Android tests, lint/build variants, live discovery, native rendered layouts and signed/downloaded APK checks are required before tester publication. Stable stays 0.22.0/code31.
