# Changelog

## 0.25.1 — Smart discovery, offline saving and update progress (27 October 2026)

- Animated in-app update downloads with real percentage/MB progress, a distinct verification phase, Cancel and Retry.
- Keep Cancel visible with small screens and large text; abort activity-owned transfers on destruction and clean partial files.
- Restore automatic offline song saving and its Settings switch within the existing 500 MB limit.
- Add TikTok-inspired Trending Music, personalized mood/Daily/New to you mixes and Tune My Mix.
- Learn local listening interests and refill mix queues while preserving manual queue priority and current playback.
- Publish with the original certificate and increasing version code 37 for public and tester upgrades.

## 0.24.0 — Artwork, player and song-related radio (27 October 2026)

- Fill artwork frames without distortion and remove detected symmetric black video borders.
- Compact Home filters, aligned covers, slimmer mini-player and artwork-tinted Now Playing.
- Play only the selected search song, followed by related station recommendations.
- Preserve playback position and manual queue when starting radio; reduce duplicate controls.
- Publish with the original certificate and version code34, upgrading public and tester installs.
- Record Trending Music, interest learning, personalized mood/daily mixes and Tune My Mix as future plans.

## 0.22.0 — Artwork recovery and public experience update (26 October 2026)

- Reload the same cover after Refresh & repair and restore cached artwork views on attachment.
- Retry temporary artwork failures twice; stale retries cannot replace a newly bound song.
- Fall back to the same song’s public thumbnail when artwork metadata is missing or stale.
- Keep clipping backgrounds separate from mutable loading placeholders.
- Publish simpler Home/Search, VEXO session learning, manual queue priority and shuffle-safe Play next.
- Preserve optional YouTube connection, existing features, app identity and signing certificate.

## 0.16.0 — Notification invitation and source recovery (26 October 2026)

- Ask once to enable notifications after startup, with Enable / Not now and persistent refusal handling.
- Retain Aurora Home, optional Google/email accounts, explicit favourites/playlist sync and release-alert controls.
- Preserve the existing package, signing identity, encrypted collections, playback, search and Smart Experience.
- Recover and publish maintainable source after the unpushed 0.14/0.15 project was lost.

## 0.9.0 — Refresh and repair (25 October 2026)

- Morning begins at 04:00; local-minute, clock/time-zone and resume updates keep greetings current.
- New greeting card, responsive shelves, improved quick links, rounded mini-player with progress and large-text height.
- Visible Home Refresh, Settings Refresh & repair, and optional next-launch recovery after a recorded crash.
- Local, bounded diagnostics with a reviewable email report to ccatomic81@gmail.com and clipboard fallback.
- Bounded artwork workers/queue, weak view references, low-memory cache release and stale-result protection.
- Fresh Home retries now work during a request; generation checks protect repaired caches from old results.
- Stream-address expiry uses a monotonic clock; future-dated Home caches refresh after clock corrections.
- Same package and private signing identity; built-in public release feed retained.

## 0.8.0 — Your Home (25 October 2026)

- First-open name setup, optional private profile photo and profile editing.
- Home greets you by name using the phone’s local time.
- Built-in public update service and signed APK delivery; removed the update-source field.
- Larger source thumbnails, high-resolution URL requests with fallback, and sharper bitmap decoding.
- Existing discovery, search suggestions, playlists, offline saves, crossfade and signing identity retained.

## 0.7.0 — Discovery and playlists (24 October 2026)

- Real playable song matches while typing, with artwork, artists/channels, direct play and cached matches.
- Compact search layout that leaves room for results above the keyboard.
- New Home with collection shortcuts, music shelves, personal playlists, regional picks and six mood mixes.
- Background metadata loading for the first three mixes; other mixes load on demand.
- Full playlist pages with Play all, randomized queue, save mix, add songs, rename, remove and delete.
- Persisted playlist navigation across activity recreation; existing library and signer retained.

## 0.6.0 — 24 October 2026

- Search suggestions while typing, localized by the selected music region, with a 300 ms debounce and stale-response protection.
- Up to 20 recent searches stored on-device; tap to search, fill the field, remove one, or clear all.
- Suggestions are capped, cached by query/region and use a short network timeout; normal search still works when suggestions fail.
- What’s new after an app update and a permanent Settings summary.
- Optional once-daily foreground update checks, manual checks, version-code comparison, release summaries and user-triggered browser downloads.
- Public HTTPS feed configuration, bounded responses and HTTPS-only redirects. No embedded GitHub credentials or silent installation.
- Same Android package and signing certificate for installation over previous versions.

## 0.5.0 — Crossfade (24 October 2026)

Added Settings → Crossfade with Off or 1–12 seconds, default 4 seconds. A second prepared ExoPlayer overlaps song endings using equal-power volume curves while a stable MediaSession-facing player preserves the queue and controls. A single audio-focus owner handles both decks. Pause/seek/stop/noisy/focus loss cancel outgoing audio; unready tracks use normal playback. Short tracks shorten the fade, Shuffle keeps its actual successor and Repeat One bypasses crossfade. Package and signer remain update-compatible.

# 0.4.2 — Settings privacy and branding (24 October 2026)

Hide downloading/saved song titles from the Settings offline summary, including live download-status updates. Keep usage and count, and retain the Offline library. Rename the installed app to VEXO Music and add Developer: Kavindu Kavishka in About and the Settings footer. Keep the package and signer compatible with v0.4.0/v0.4.1 Radio.

# 0.4.1 — Player alignment and icons (24 October 2026)

Rebuilt the actual Now Playing view with a measured square artwork frame, rounded clipping, fixed header, aligned title/favourite area, equal tool slots, clear Autoplay switch and separate radio buttons. Icons now use a stable optical size and cleaner queue/repeat/timer/heart geometry. Added player renders and checks for narrow screens, landscape, enlarged text, control spacing and icon sizing. Package and signing certificate remain compatible with v0.4.0 Radio.

# 0.4.0 — Song Radio (24 October 2026)

Added seed-based Song Radio, Balanced/Familiar/Discover tuning, local artist-affinity ranking, explicit negative feedback/reset, alternate-upload deduplication, artist variety and bounded multi-source candidate gathering. Now Playing and Up Next expose radio controls/status and a manual retry. Queue restoration, pause/repeat/shuffle guards and offline saving remain.

This delivery uses a new signing certificate and package `space.vexo.music.radio` because the previous signing key is unavailable after a workspace reset. It installs alongside the prior app; its private library does not transfer automatically.

# 0.3.0

- Icon-only navigation with accessibility descriptions and tooltips.
- Song Play buttons open Now Playing; mini-player Play opens the full player.
- Live Up Next screen with reorder, Play Next, remove, clear-upcoming and clear-queue actions.
- Related-song Autoplay, duplicate filtering, shuffle-aware order and stale-request protection.
- Saved queue/position restored paused; restored Play prepares and resumes correctly.
- Remembered playback speed controls from 0.75× to 2×.

# 0.2.0

- Repair small-screen control overflow and align headings, song rows, action buttons and larger-text layouts.
- Load region-aware Home music charts with a labelled regional-search fallback, refresh, cached lists and manual region selection.
- Automatically save complete listened-to audio for offline playback, with a 500 MiB budget, an Offline library and storage controls.
- Keep the original application ID and signing certificate for an in-place update.
