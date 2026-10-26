# VEXO Music 0.19.1

- Replace the 82%-height centred YouTube dialog with a full-height feature panel. A fixed title/close row remains visible when scrolling the imported music list.
- Apply the same full-height presentation to account, notifications, PureSong, standalone Smart and room panels. Preserve embedded Smart navigation and existing playback/queue screens.
- Apply status-bar, navigation-bar, cutout and keyboard insets exactly once. Remove fixed percentage sizing, rounded outer clipping and floating dialog margins.
- Use #1ED760 green, #121212 background, white text and neutral grey surfaces/borders. Remove the purple backdrop and purple selected states while preserving VEXO's own identity.
- Primary palette reference: Spotify's current official developer design colour asset, https://developer-assets.spotifycdn.com/images/guidelines/design/colors.svg (retrieved 2026-10-26), contains #1ED760, #121212, #7F7F7F and #EEEEEE. Guideline: https://developer.spotify.com/documentation/design. Other neutral greys support readable UI surfaces.

Automated checks cover a long connected music list, fixed close access, Android 8 three-button insets and keyboard dismissal. Physical phone layout acceptance remains necessary.
