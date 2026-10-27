# VEXO 0.23.0-dev tester

Selecting an online search result starts that song alone, with related autoplay fetched immediately when enabled. Search suggestions use the same path. Explicit playlist/category mix playback still queues that collection. The playback service changes the seed and queue together and cancels old recommendation work.

Song radio uses the original song’s music-radio endpoint. It expands through the currently playing song only when that song appears in the original station; unrelated manual songs or listening interests cannot redirect it. Sparse metadata falls back to verified same-artist songs. Familiarity, local feedback, repeat protection and artist diversity rank this related pool. Persistent mood preferences no longer inject a different search into ordinary song radio; explicitly started MindRadio/category mixes retain their scope.

Starting radio keeps the playing song, its position, pause state and manual queue, and removes only upcoming automatic picks before refilling.

Artwork now fits the entire image with its original proportions, including wide video thumbnails and portrait covers. The previous recovery, retry and metadata fallback fixes remain. The player has one queue shortcut; autoplay is in Up next, the mode preference is in Settings, and retry appears only when recommendations fail or run out. Unique Smart Experience, MindRadio, PureSong, crossfade, playlist and offline features remain. YouTube connection is optional.

Version code 32 upgrades public 0.22.0/code31 without uninstalling. This is an opt-in tester, not a stable update. Automated regression/layout and live discovery checks are required before release; physical-device audio/background testing remains for testers.
