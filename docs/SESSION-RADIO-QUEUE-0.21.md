# VEXO 0.21.0-dev: recommendations and queue fixes

The tester update builds on the simpler 0.20 interface and preserves MindRadio, PureSong, Smart Experience, crossfade, offline music and optional account features.

## Listening recommendations without a YouTube account

VEXO uses its own favourites, playlists, listening history, explicit feedback and audible-time learning. Optional YouTube likes and playlists can contribute but are not a requirement. An unreadable optional import no longer prevents local discovery or radio feedback from working; the unreadable import remains intact.

A bounded encrypted session record keeps up to twelve song observations, valid for two hours. Successful listening of at least thirty seconds provides a short-lived artist preference; a deliberate skip between two and twenty seconds provides negative evidence. Pauses, seeks within a song, automatic early transitions, buffering and failed playback are not dislikes. Session evidence has a thirty-minute half-life. Up to two recent positively heard artists can lead discovery candidate requests, while existing favourites remain available. Explicit negative feedback still wins. No tempo, genre, mood or language is inferred from audio.

Pause history and learning opt-out disable session use. Reset learning and clear history remove session evidence. A completed listen is persisted on a worker, with privacy/reset checks repeated at persistence time.

## Radio relevance and variety

Radio retains its original seed, current-song recommendations, source relevance, artist variety, upload deduplication and hidden-song exclusions. Additional taste-seed expansion is limited to sparse candidate pools to reduce station drift. The final selector now protects the last twenty history entries by default in addition to the existing timed repeat window. Setting the repeat window to zero still disables this extra history exclusion. It never overrides manual queue selections.

## Queue defects addressed

- Add to queue previously appended after automatic recommendations. It now goes before the first upcoming automatic pick, after existing manual selections.
- Play next previously disabled shuffle. It now preserves shuffle and the existing relative order of other entries, and does not seek or resume a paused song.
- Automatic and explicit entries were visually indistinguishable. Queue rows now say Recommended or In your queue, and retain this source information across queue restoration. Explicitly playing a collection converts its selected items into manual entries.
- Optional import failure previously bypassed local radio exclusions in the fallback path. Local feedback now remains active independently of that import.
- Listening encryption previously ran on the playback callback thread. Completed samples now use the worker queue.

Start radio still preserves the playing track's position and existing manual queue. Manual queue insertion and promotion use a service command with capacity and stale-item checks.

## Validation

`SessionQueueTest` covers guest/session recommendations, session expiry and future timestamps, privacy/reset handling, neutral/failed/skip evidence, corrupt optional imports, source restoration, shuffled insertion, promotion, pause and playback-position preservation. Existing radio, playback, queue, UI and security checks remain part of Android CI.

Physical-phone audible playback, background behavior and recommendation satisfaction still require tester feedback. This is an original, metadata-based recommender; it does not reproduce Spotify's private models or use Spotify listener data.

## Research informing the design

- https://www.spotify.com/uk/safetyandprivacy/understanding-recommendations
- https://engineering.atspotify.com/2021/11/the-rise-and-lessons-learned-of-ml-models-to-personalize-content-on-home-part-i
- https://research.atspotify.com/2021/04/contextual-and-sequential-user-embeddings-for-music-recommendation
- https://research.atspotify.com/publications/exploiting-sequential-music-preferences-via-optimisation-based-sequencing
