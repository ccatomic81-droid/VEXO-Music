# VEXO Music 0.19.0

- Restore Search browse and active suggestions when returning to the cached tab. Preserve the field, query and submitted results.
- Separate foreground search work from Home imports/refreshes; cancel superseded search tasks and deduplicate paginated songs.
- Replace the dropping artwork queue with Glide 5.0.9: shared requests, view cancellation, memory/disk caches, bounded network timeouts, size-aware covers and original-URL fallback. Memory pressure trims the cache without blanking visible covers.
- Replace green highlights, selected backgrounds, platform controls and launcher mark with a matching violet accent.
- Add public YouTube Music WEB_REMIX catalogue search with songs-first filtering, music-video fallback, robust metadata extraction, podcast/unavailable row exclusion, country context and provider-specific continuation. Preserve NewPipe fallback and existing playback.
- Recognize only explicitly labelled opening intro chapters from playback metadata. Unknown tracks remain full length. Confirmed private starts can now be saved for individual YouTube songs as well as imported files. Confirmed starts win, duration changes reject stale timing, shared-room playback is unchanged, and a paused start remains armed until playback begins.

Reference reviewed: MetrolistGroup/Metrolist at 876e355181fd3b04e7063b10442fef1b721d53b3. See docs/MUSIC-SEARCH-0.19.md. Physical device artwork/network/playback and frame-rate acceptance still require testing; chapter labels are metadata, not audio fingerprint recognition.
