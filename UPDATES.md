# App updates and releases

Current public release: **0.29.0 / code 47**. It updates public 0.28.1 in place with the original signing identity. Adds a dedicated Artists library, independent artist search, followed artists, and pages filtered by provider artist credits. The manifest is signed. Use Settings → Check for updates; Android still requires approval to install. See `release/public-0.29.0.json` and `release/verification-0.29.0.json`.

In 0.25.1, Get update opens an animated foreground download panel showing real percentage and MB transferred. Verification is a separate phase. Cancel stops the HTTP transfer and removes its partial file; Retry starts a new attempt after a connection or verification failure. A cached, already verified APK can be reused. Cancel remains visible while large-text content scrolls. Destroying the activity cancels its transfer. Older installed apps retain their existing update UI until this version is installed.

## Built-in delivery

The public feed identifies the latest published VEXO release. Signed APK/source downloads are verified after each publication, including exact file checksums and resumable downloads. The production Worker has only its R2 binding and accepts GET/HEAD; temporary publishing access has been removed. Users do not configure an update address.

VEXO Music 0.8.0 and later use this public feed without account credentials or URL setup:

https://vexo-music-updates.kavindukavishka785.workers.dev/stable.json

Settings → Check for updates fetches it immediately. Optional automatic checks run at most once per 24 hours when the app opens. A current or older version code does not prompt an update. Dismissing a remote release snoozes that version; a manual check can show it again. What's new also works offline for the installed release.

Get update downloads and verifies the signed APK inside VEXO after a user tap. Android controls installation. This is foreground checking, not background push or silent installation. Version codes must increase; 0.9.0 uses code 11. The JSON response is limited to 64 KiB and release summaries to 12,000 characters. App identity and HTTPS URLs are validated, redirects must stay HTTPS, and requests time out. Failed automatic checks do not interrupt listening; manual failures offer retry.

The public service serves release metadata and versioned APK/source downloads from Cloudflare R2. Its production Worker accepts GET/HEAD only, supports resumable downloads and caches immutable versioned files. It does not receive profile names, pictures or listening history. Standard hosting request logs may include IP address and request metadata.

## Publish a future release

The service source, HTTP contract tests and deployment configuration are in `update-service/`. Build and verify a new APK with the same private signing key. Temporarily deploy `publisher.mjs` with an independently generated short-lived publishing token hash, upload the signed APK and source using `publish-release.py`, and publish the manifest last. The publisher validates file checksums, app identity, own-host URLs and the expected certificate field. Verify the actual APK signature separately; metadata is not a substitute for Android signing.

After publication, deploy `service.mjs` with only its R2 binding, removing the publishing module and token hash. Verify public reads and rejected writes, then destroy the private temporary token. Never commit tokens or signing keys. `wrangler.jsonc` targets this read-only production configuration.

## Preserve app data and upgrade signing

The delivered APK retains the package and backed-up development signing certificate used by the v0.4.x–v0.7.x line:

`0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`

Install over the existing app to retain its data. Uninstalling removes private data. The key is absent from source control and source ZIPs.

GitHub Actions creates a test APK with the runner’s debug key. It cannot update the delivered signed installation until the existing key is configured securely. Do not publish the runner-signed APK as an upgrade. The workflow does not publish releases automatically.

## Search privacy

Typing at least two characters fetches suggestions and playable song metadata from YouTube through NewPipe Extractor. Home fetches its first three mixes. The selected country is supplied. At most 20 recent terms and 12 artist shortcuts are stored privately on the device. Successful online song searches record up to 3 distinct artists from the first 6 music results; selecting a search song prioritizes its artist. Artist circles retain separate provider artist identities and fetch exact artist results from public YouTube Music for profile photos. Song covers are not used for artist portraits. Portrait metadata is cached for 7 days (6 hours after an unavailable lookup); Data saver skips new lookups, and Home Refresh & repair retries them. Legacy combined labels use exact full-name matching first, with recovery of individual collaboration names when no exact artist exists. Removing a recent term removes that reference; artists linked to another search remain. Clear recent searches clears both terms and artist shortcuts and invalidates pending portrait responses, including if the same term is searched again. Local phone-music searches do not request remote suggestions. No analytics backend is used.

Primary suggestion API reference: https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/suggestion/SuggestionExtractor.html

## Default alerts and crossfade (0.28.1)

On a new install, or when a setting is absent after upgrading, update alerts and push release alerts default to ON and crossfade defaults to 4 seconds. Explicitly saved Off choices and custom crossfade durations are kept.

Android notification permission remains a separate user choice. The app does not create a new Firebase messaging registration until notifications are allowed. Denying permission leaves the saved alert preference selected and shows a permission-needed status. Granting permission through the app or Android Settings connects release alerts automatically; revoking permission pauses delivery. Users can turn either alert option off under Settings → Notifications.

Update alerts are local notifications for a newer authenticated release detected during an update check. Push release alerts receive announcements from Firebase Cloud Messaging; a separately authenticated sender is required to broadcast them. Publishing a release does not itself send a push broadcast. Both kinds open the update review flow and leave installation to the user and Android.
