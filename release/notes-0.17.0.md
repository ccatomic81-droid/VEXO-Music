# VEXO Music 0.17.0 candidate

- Removed the dark bottom-navigation plate and Android navigation-bar scrim.
- Added Settings → YouTube taste: an optional, separate Google read-only connection for liked music.
- Added VEXO recommendations based on imported liked music, with a Home shelf after connection.
- Added manual refresh, cancellation, encrypted local metadata, expiry and disconnect/data-removal controls.

YouTube's exact personal Home feed is not available through its official API. This feature uses liked music to create VEXO suggestions. Live Google connection requires the project's YouTube Data API and OAuth consent configuration plus a physical-device check. This candidate does not change the stable update feed.
