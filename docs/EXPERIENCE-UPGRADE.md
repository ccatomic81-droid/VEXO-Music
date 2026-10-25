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
