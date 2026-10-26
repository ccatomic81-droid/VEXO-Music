# Optional YouTube taste (0.17)

Settings → YouTube taste → Connect YouTube opens a separate Google authorization flow requesting only `youtube.readonly`. VEXO sign-in does not trigger it. A user can decline, cancel, refresh, or disconnect. Guests can use the connection too. Successful imports add a Home shelf; no shelf or authorization appears before consent. Home Focus mode hides the shelf.

The official API does not expose a user's personalized YouTube Home feed. This feature imports a bounded sample of liked music and creates **VEXO** recommendations from related-song metadata. It does not claim to reproduce YouTube recommendations. No watch-history, full YouTube Music library, or private mix support is promised.

Up to four pages of 50 liked videos are checked; up to 100 non-live music-category tracks of 30–900 seconds are retained. Invalid IDs, unsafe artwork, repeated pages, and oversized responses are rejected. Read requests use a fixed HTTPS Google API origin, bearer headers, no redirects, and bounded timeouts. Up to three distinct liked artists seed related-song lookup. Existing music ranking removes duplicates and liked songs and balances artists. Discovery failures preserve the successful liked-music import with a visible retry state.

Imported metadata and picks stay in the encrypted `youtube_taste` collection, separately from favourites, playlists, listening history, and account sync. VEXO does not save OAuth access/refresh tokens or ask for a password. Google Play services manage authorization. Metadata expires after 28 days or is deleted on disconnect. Pending work cannot repopulate the collection after cancellation/disconnect/activity destruction. VEXO sign-out also clears this feature. Disconnect is local; the screen links to Google account connections to revoke Google's permissions explicitly.

## Google project setup and device verification

The existing Android OAuth client must match `space.vexo.music.radio` and the original release certificate. Enable **YouTube Data API v3** in the same Google Cloud project and configure `https://www.googleapis.com/auth/youtube.readonly` in the OAuth consent screen. Sensitive-scope access may require Google's verification before general public use; testing projects must include appropriate test users. No new paid service is required by the code. These console settings are external prerequisites, not provisioned by this source change.

The current project's public YouTube API check returned `403 accessNotConfigured / SERVICE_DISABLED` on 2026-10-26. This confirms the API is disabled, rather than a successful live import. Android OAuth client registration and sensitive-scope approval were not verified.

| Setup field | Existing app value |
| --- | --- |
| Google project | `vexo-music-kavindu-2026` (number `241670791889`) |
| Android package | `space.vexo.music.radio` |
| Original signing SHA-1 | `9C:1B:E5:91:2B:4D:63:B8:7F:96:EF:F2:CA:B9:CA:D4:96:D0:67:76` |
| Read-only scope | `https://www.googleapis.com/auth/youtube.readonly` |

Owner setup:

1. [Enable YouTube Data API v3 in the existing project](https://console.cloud.google.com/apis/library/youtube.googleapis.com?project=vexo-music-kavindu-2026).
2. In Google Auth Platform, check the Android OAuth client uses the package and SHA-1 above. Keep the existing web client for VEXO sign-in.
3. Add `youtube.readonly` under Data Access. Configure the consent screen, audience/test users, and any verification Google requires for the intended audience.
4. Install the signed app on a phone, open Settings → YouTube taste, and test grant/deny, import, refresh and disconnect. Do not describe the optional account connection as live before these prerequisites are complete. Public music search and music radio operate separately from this optional connection.

Verify on a physical device: choose account, grant/deny consent, refresh, expired/revoked permission, offline import, empty liked-music sample, rotate during Google consent, cancel during discovery, disconnect, and sign out. Fixture tests do not prove real Google authorization, account configuration, or live playback.

## Navigation

The bottom tab container is transparent with no elevation. The page backdrop continues around the icons. The Android navigation bar is transparent, contrast scrims are disabled where supported, and explicit system/keyboard insets retain safe touch targets on older Android versions.

## Primary references

- https://developer.android.com/identity/authorization
- https://developers.google.com/youtube/data/reference/rest/v3/videos/list
- https://developers.google.com/youtube/data/reference/rest/v3/errors
