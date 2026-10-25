# Experience upgrade implementation

Developer: Kavindu Kavishka. Baseline: public 0.12.0 (code 14). Development candidate: 0.13.0-dev (code 15). Implements the approved combined plan in phases while retaining the solid VEXO design and existing data/playback.

## Security foundation

Implemented in this development branch:

- Future newer updates must contain an original-key RSA signature over the release fields. Unsigned current/older legacy feeds remain readable only to report that no update is available; they are never installable by this path.
- Signed fields bind app ID, version, notes, exact allowed download URL, size, SHA-256, original certificate and public source commit. Public verification key is recovered from the verified 0.12 APK; no private signing material is added to source.
- In-app downloads are bounded, reject redirects and verify exact signed bytes. Package identity/version/non-debuggable state/certificate are checked before narrowly scoped FileProvider handoff to Android. The user must approve installation and Android unknown-source permission. Failed verification preserves the installed app/data. Existing cached APKs are rechecked.
- Advanced room/queue/settings session commands require the VEXO application UID. Trusted external controllers retain a limited set of normal playback commands and cannot inject arbitrary media items.
- Existing library/profile metadata and the profile photo migrate to app-level encrypted storage. Original collections are removed only after encryption, durable persistence and readback succeed. Failed initial migration permits read-only recovery of the legacy data; no new plaintext writes. An unreadable existing encrypted vault never silently falls back to stale plaintext.
- Cloud backend source adds shared IP/day and IP/minute budgets alongside replaceable device identities, including socket connection attempts. No raw IP is persisted in the budget keys. Anonymous abuse remains possible; quotas are not a substitute for identity/attestation or account security.
- An offline manifest signer checks the original certificate and reads credentials only from environment. A small source credential guard is included; it is not a complete historical secret scan. CI inventories resolved Maven dependencies and queries OSV for known advisories; unknown/unlisted packages and new vulnerabilities are not guaranteed covered. GitHub's dependency review could not run because Dependency Graph is disabled, so the independent inventory check replaces that unavailable check.

The live backend and stable feed are not changed by these source edits. 0.12 clients still use their existing update path. Before a future public release, deploy the compatible backend changes and publish original-key authenticated metadata using `update-service/sign-manifest.py`. Its output must match the actual verified production APK. Do not rotate the app signing identity or publish private key files.

## Status and next phases

Security tests and Android Actions validation must pass before considering this foundation ready. On-device encryption/upgrade/installer and external-controller behavior still require physical-device checks. User account creation, Firebase/Google configuration, notifications, read-only YouTube authorization and cloud sync are not provisioned by this phase.

Next: actual listening signals and improved next-song ranking; real category search/results; then permitted PureSong markers and the top Home Skip intros ON/OFF control. Preserve FairPlay fairness, manual queues and crossfade; automatic intro jumps must not count as dislikes. Complete future integration phases against the master plan's source permissions and privacy gates.

Credential custody, repository account multifactor status, full dependency vulnerability coverage, hardware-backed Keystore behavior, signing-key rotation compatibility and paid/free account billing status have not been independently audited here. No claim of full penetration testing or complete security certification is made.

Initial CI exposed a process-static offline cache retaining the previous application context during encrypted-storage UI tests. The cache now closes/rebinds when the application context changes, avoiding stale storage handles. Cloud API and room connection clients also disable redirects so credentials and music commands stay on the configured service.

The initial OSV inventory found two known advisories for transitive `org.jsoup:jsoup:1.22.2`: GHSA-65r4-943x-97jj and GHSA-pmhh-3w7g-xqp8. A dependency constraint now selects the maintainer's patched 1.23.2 release. The audit remains fail-closed for known findings, unavailable responses or pagination it cannot completely examine. This is not a claim that VEXO's particular parser use was exploitable. Maintainer source: https://jsoup.org/news/ and https://github.com/jhy/jsoup/security/advisories/GHSA-pmhh-3w7g-xqp8 .


## Listening, categories and PureSong phase

Implemented on `feature/listening-categories-puresong`, stacked on the security foundation:

- Next-song ranking reads a bounded encrypted local taste model. Monotonic audible time adds modest song/artist preference; manual early skips add a weak negative, playback failures and pause/seek distance add none. One signal per song per 30 minutes, 30-day decay and a 300-song cap limit feedback loops. Explicit feedback, anti-repeat, favourites and artist variety still apply. This only ranks future automatic candidates; it never reorders manual queues or FairPlay requests.
- Listening learning can be disabled or reset from Smart recommendation controls. Pausing listening history suppresses learning. Clearing history clears learned taste; a play already underway cannot repopulate a just-cleared model. Memories, favourites, intro markers and playlists remain separate.
- Search has 14 native category cards, scoped Sinhala/Tamil/English/Any search, category results, pagination using the existing provider result cursor and a Play category mix action. Typed title/artist clues remain in the query. These are search collections and language hints, not measured audio genre/energy/popularity labels. Autoplay from category results keeps the chosen category query; explicitly starting song radio clears it. No provider or new subscription is introduced.
- Home has a distinct, accessible Skip intros ON/OFF control with a 48dp touch target. Hold it for PureSong controls. Smart, Settings and song menus open the same saved controls. Solid charcoal/forest surfaces and lime accents preserve VEXO branding; navigation remains Home, Search, Library, Settings, Smart.
- PureSong stores only user-confirmed start markers for imported content URIs, bound to URI and matching duration. At a fresh playing track start it can make one permitted seek; a prepared crossfade deck applies the same validated marker before fading in. OFF, unknown/mismatched markers, restored non-start positions and shared rooms do not auto-seek. Full version cancels the armed seek and starts at zero. Same-track intro seeks are never skip dislikes. No online cuts, audio speech detection or platform-ad blocking are claimed.
- Optional Prefer official audio adds a small title-based preference to future ranked candidates. It does not certify a source, replace a manual choice, or strip sponsors from online content.

The final installer recheck also runs off the UI thread before Android handoff.

Tests cover outcome weights/cooldown/decay, error/pause/seek handling, privacy/reset, explicit-feedback priority, local marker eligibility/mismatches, off/full-version/shared-room protections, category queries, saved Home state and 320dp native layouts. CI must pass before this phase is ready; physical-device playback, large libraries, installation and room compatibility still need device testing.

## External setup remains separate

Update push notifications, optional Google/email sign-in, an authorized YouTube library subset and cross-device account sync need an owner-selected/configured project, app registration and appropriate OAuth/notification configuration. No working account or push setup is fabricated. YouTube account sign-in alone does not authorize private library access or grant streaming/offline rights. Cloud quota and service availability remain relevant even with free tiers; no paid resources are provisioned. Existing cloud-radio and room features keep their current explicit consent and configured backend.
