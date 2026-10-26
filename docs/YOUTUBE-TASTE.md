# Optional YouTube taste (0.17)

Settings → YouTube taste → Connect YouTube opens a separate Google authorization flow requesting only `youtube.readonly`. VEXO sign-in does not trigger it. A user can decline, cancel, refresh, or disconnect. Guests can use the connection too. Successful imports add a Home shelf; no shelf or authorization appears before consent. Home Focus mode hides the shelf.

The official API does not expose a user's personalized YouTube Home feed. This feature imports a bounded sample of liked music and creates **VEXO** recommendations from related-song metadata. It does not claim to reproduce YouTube recommendations. No watch-history, full YouTube Music library, or private mix support is promised.

Up to four pages of 50 liked videos are checked; up to 100 non-live music-category tracks of 30–900 seconds are retained. Invalid IDs, unsafe artwork, repeated pages, and oversized responses are rejected. Read requests use a fixed HTTPS Google API origin, bearer headers, no redirects, and bounded timeouts. Up to three distinct liked artists seed related-song lookup. Existing music ranking removes duplicates and liked songs and balances artists. Discovery failures preserve the successful liked-music import with a visible retry state.

Imported metadata and picks stay in the encrypted `youtube_taste` collection, separately from favourites, playlists, listening history, and account sync. VEXO does not save OAuth access/refresh tokens or ask for a password. Google Play services manage authorization. Metadata expires after 28 days or is deleted on disconnect. Pending work cannot repopulate the collection after cancellation/disconnect/activity destruction. VEXO sign-out also clears this feature. Disconnect is local; the screen links to Google account connections to revoke Google's permissions explicitly.

## Google project setup and device verification

The existing Android OAuth client must match `space.vexo.music.radio` and the original release certificate. Enable **YouTube Data API v3** in the same Google Cloud project and configure `https://www.googleapis.com/auth/youtube.readonly` in the OAuth consent screen. Sensitive-scope access may require Google's verification before general public use; testing projects must include appropriate test users. No new paid service is required by the code. These console settings are external prerequisites, not provisioned by this source change.

Verify on a physical device: choose account, grant/deny consent, refresh, expired/revoked permission, offline import, empty liked-music sample, rotate during Google consent, cancel during discovery, disconnect, and sign out. Fixture tests do not prove real Google authorization, account configuration, or live playback.

## Navigation

The bottom tab container is transparent with no elevation. The page backdrop continues around the icons. The Android navigation bar is transparent, contrast scrims are disabled where supported, and explicit system/keyboard insets retain safe touch targets on older Android versions.

## Primary references

- https://developer.android.com/identity/authorization
- https://developers.google.com/youtube/data/reference/rest/v3/videos/list
- https://developers.google.com/youtube/data/reference/rest/v3/errors
