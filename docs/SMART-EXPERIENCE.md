# VEXO Music smart experience upgrade

Developer: Kavindu Kavishka. Inspected baseline: `c23abe77f3c94b1f0875cd7717682f6a2cef181f` (0.9.0, code 11), 25 October 2026. Development branch: `feature/smart-experience`.

## Architecture and preservation

This is the existing Java Android app, not a rewrite. Native programmatic views, Android 8+ (API 26), target/compile SDK 35, Java 17, AGP 8.9.2 / Gradle 8.13. Media3 1.8.0 supplies playback and MediaSessionService; CrossfadePlayer wraps the existing two-player crossfade engine. OkHttp 4.12 and NewPipeExtractor 0.26.5 supply YouTube search, related candidates, stream resolution and autocomplete. There is no Spotify API, audio-feature API, licensed lyrics API, server database, user authentication, cloud music account, or AI endpoint in this repository. UserProfile is an on-device name/avatar, not authenticated identity.

Existing app ID `space.vexo.music.radio`, all library/preferences keys, queue persistence, autoplay, crossfade, settings, profile, greetings and artwork remain. No reset, schema replacement, user-data deletion, or stable-branch merge is part of this PR. New local capabilities are connected to the real player and existing library.

`android.yml` runs unit tests, lint, builds an APK, checks the update-service contract and probes live discovery (the probe is advisory). `release.yml` only republishes the already-signed 0.9.0 migration assets; it does not build future releases. The stable update feed is the existing Cloudflare Worker/R2 URL. The stable signing certificate is recorded in `.migration/verification.json`; CI debug APKs use a different key and must not be offered to installed users as upgrades.

## Feature assessment and implementation status

| # | Feature | Implemented in this branch | Remaining dependency / precise limitation |
|---|---|---|---|
| 1 | MindRadio | Descriptive command entry, mood/energy/genre/language/artist search hints, real candidate lookup, local recommendation ranking and playback | **Partial.** No cloud AI conversation or semantic audio inference. Needs a server-side model endpoint, authenticated access, quotas and consent before sending any personal data. Mood/energy are search terms, not measured audio attributes. |
| 2 | Anti-repeat | Configurable 0–720 hour exclusion by song identity, alternate-upload suppression, artist-aware playlist shuffle, forgotten favourites and discovery ranking | Locally implemented. Explicit manual selection and repeat-one/all remain user overrides. Sparse pools can produce no eligible recommendations rather than secretly ignoring restrictions. Legacy plays without dates cannot satisfy time-based exclusions. |
| 3 | Forgotten search | Timestamped play log, date range, title/artist clues, Sinhala/Tamil/English matching, limited aliases and spelling tolerance; older history retained with unknown dates | **Partial.** No semantic recollection of unrecorded context or lyric index. Licensed lyric search and possibly a semantic index are needed for those capabilities. |
| 4 | Recommendation controls | Genre, language, mood, energy, artist search hints, discovery weight and repeat window saved locally and used by discovery/radio | **Partial.** Popularity and true audio-feature filters require licensed catalogue metadata. UI explicitly describes this instead of inventing popularity scores. |
| 5 | Song memories | Private notes, playback moments, chronological timeline, playback/seek and deletion | Locally implemented, no cloud sync. |
| 6 | Playlist Time Machine | Automatic before-edit/delete/rename snapshots, restore as copy, explicit replace, single-track recovery, undo by restoring the before-operation version | Locally implemented. Version history starts now; data deleted before this upgrade cannot be recovered. |
| 7 | FairPlay | Persisted pass-the-phone requests, per-person round robin, deduplicated queue, remove requests, playback | **Partial.** Multi-device sessions and synchronized listening are blocked on authentication/realtime backend, authorization rules, invitation expiry, host control, abuse limits and a permitted playback model. Local participant names are not verified identities. |
| 8 | Smart feedback | More/less, 7-day break, never recommend, reset; future candidate filtering/ranking consumes feedback | Locally implemented, metadata-based. Explicitly queued tracks are respected. |
| 9 | Multilingual search | Original Unicode query preserved; common Sinhala/Tamil Romanized aliases, spelling tolerance in local history, script alternatives in autocomplete, alternate-query fallback for empty remote searches | **Partial.** Not a comprehensive transliterator or language model. Broad Sinhala/Tamil spelling/transliteration coverage needs a maintained transliteration service/dataset. Existing remote autocomplete remains active. |
| 10 | Connection recovery | Validated-network callback, bounded retries on network failures, same-item/position resume, pause/item-change cancellation, existing low-data selection and imported local audio | **Partial.** New online offline saves are disabled without explicit provider authorization. Existing saved files remain. Retry does not bypass rate limits, authentication, geo restrictions, DRM or provider denial. Physical-device interruption/background tests remain necessary. |
| 11 | Discovery adventure | New-artist trail excludes known artists, independent/hidden-gem searches, Sri Lankan/Tamil regional journeys, playable results, forgotten favourites | Locally implemented as transparent metadata/search-based discovery. “Hidden gems” is not a verified popularity classification. |
| 12 | Customizable Home | Saved section order, hide/show modules, pinned playlists, focus mode retaining shortcuts/pins; existing section UI reused | Locally implemented. UI supports narrow layouts and large text with scrolling. |

## Phases and next integration gates

1. Repository inspection and feasibility: architecture, data, source, workflows, signing and update dependencies identified.
2. Device-private foundation: Keystore encryption, bounded history/memories/feedback/revisions; non-destructive library hooks.
3. Local experience: recommendation controls, real search/playback integration, recovery screens, fair local queue, home layout.
4. Reliability and verification: connection recovery, authorized-save gate, unit/UI regression tests, CI APK. Results must be checked against the exact commit.
5. External integrations: server-side AI, licensed lyrics/audio metadata, authenticated realtime sessions and authorized offline provider. These are explicitly incomplete until working endpoints and entitlements exist.
6. Release: same signing key, device tests, review/merge, signed candidate and manifest verification. Never promote a runner debug APK to the stable feed.

## Privacy and storage

New smart data is split into independent collections so settings do not decrypt or rewrite the full history. Each collection is AES-256-GCM encrypted with random IVs and fixed authenticated context; the key is generated in AndroidKeyStore and is never hardcoded or exported. Unreadable/tampered vaults are retained and reported; no plaintext fallback. History is at most 1,000 events; memories 200; feedback 500 entries; playlist snapshots 20 per name and 100 total. The pre-upgrade library stays in its existing app-private storage; old history is retained without fabricated dates. Backups/device transfer remain excluded by the existing manifest rules. New timestamp history can be paused and both timestamped/old history cleared explicitly. Memories can be deleted individually. No smart data is uploaded to any backend. Search phrases are sent to the existing music source only when the user initiates discovery/search; do not place private notes or whole history into those queries.

Encryption protects at rest, not against an unlocked compromised app/device. Device-key loss makes the smart vault unreadable; never uninstall to fix it because private app data is removed. Playlist deletion/edit fails rather than proceeding without the required recovery snapshot when the vault is unavailable. There is no new cloud restore/export feature.

## External services, credentials, rights and cost assessment

No new paid service, secret, project, database or subscription was created. Local features introduce no API billing. Existing source availability/rate limits remain outside VEXO's control.

- **AI:** supply/select a server-side model provider and billing project, request budget, HTTPS gateway with authentication/rate limits, and privacy consent. Never ship a provider secret in the APK or send it in chat. Model and hosting costs depend on provider, requests and token usage; no fixed price is assumed.
- **Shared sessions:** choose a realtime backend and identity provider; configure server authorization and session-specific permissions before multi-device access. Hosting, database and realtime usage may incur costs; no backend has been provisioned here.
- **Lyrics, popularity and audio features:** provider agreement and credentials with rights to search/display/store the relevant fields. Fees and territorial coverage require the selected provider's actual terms.
- **Music rights/offline:** repository code licensing does not license songs. NewPipeExtractor is an extraction dependency, not evidence of playback/download rights. Existing extraction playback is preserved, not expanded or declared authorized. YouTube's published API policies restrict offline download and audio separation; switching to an official API alone does not grant a music catalogue or offline rights. New online download saves are gated off. Licensed provider integration or rights-cleared user-owned content is the release dependency for authorized downloads.
- **Signing/release:** configure the original private keystore through GitHub Actions secrets, verify the certificate against `0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`, and keep signing material out of source/ZIPs/logs. A newly generated key would break update continuity.

Primary references: [Android cryptography](https://developer.android.com/privacy-and-security/cryptography), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies), [YouTube policy guide](https://developers.google.com/youtube/terms/developer-policies-guide). Applicability to the existing extractor is a source/provider compatibility concern, not a claim that API approval exists.

## Validation and release status

Local update-service contract tests: 2 passed. `git diff --check`: passed. Local Gradle bootstrap is blocked by the runtime Java network/proxy setup; Android SDK is absent. GitHub Actions is the authoritative compilation/unit/lint/APK check for this branch. This report will be updated with the exact CI result.

No stable merge, stable release, or update-feed publication has occurred. No claim is made that all 12 features are production complete. Physical Infinix/Android testing, Keystore on-device testing, long playback/crossfade testing, network interruption testing, installation-over-0.9.0 validation and all external-service integrations remain release gates.

### Prepared release path

`candidate-release.yml` is manual-only and prepares a **draft prerelease** after tests, lint, signed release compilation and exact certificate verification. Configure `VEXO_KEYSTORE_BASE64`, `VEXO_KEYSTORE_PASSWORD`, `VEXO_KEY_ALIAS`, `VEXO_KEY_PASSWORD` in repository Actions secrets using the original signing identity. Do not put these in source or messages. The workflow removes its temporary keystore, packages only tracked source, and never writes the stable update feed. It must be available on the repository default branch before manual dispatch from the GitHub UI. Release notes and a clearly named, unpublished update-manifest template are under `release/`. The template URL must serve the verified APK before any publication; it is not an existing download.
