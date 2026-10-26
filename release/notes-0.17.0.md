# VEXO Music 0.17.0

- Removed the dark bottom-navigation plate and Android navigation-bar scrim.
- Start radio preserves the currently playing song, position, pause state and manually queued songs.
- Radio prefers YouTube Music music-radio queues, with fallback to existing discovery when unavailable.
- Added Settings → YouTube taste: an optional, separate Google read-only connection for liked music.
- Added VEXO recommendations based on imported liked music, with a Home shelf after connection.
- Added manual refresh, cancellation, encrypted local metadata, expiry and disconnect/data-removal controls.

YouTube's exact personal Home feed is not available through its official API. The optional connection uses liked music to create VEXO suggestions. Live Google connection requires the project's YouTube Data API and OAuth consent configuration plus a physical-device check. Public music search and music-radio discovery do not require this Google connection.
