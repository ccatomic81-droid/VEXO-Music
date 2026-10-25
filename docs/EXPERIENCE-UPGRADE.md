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
- An offline manifest signer checks the original certificate and reads credentials only from environment. A small source credential guard is included; it is not a complete historical secret scan or dependency audit.

The live backend and stable feed are not changed by these source edits. 0.12 clients still use their existing update path. Before a future public release, deploy the compatible backend changes and publish original-key authenticated metadata using `update-service/sign-manifest.py`. Its output must match the actual verified production APK. Do not rotate the app signing identity or publish private key files.

## Status and next phases

Security tests and Android Actions validation must pass before considering this foundation ready. On-device encryption/upgrade/installer and external-controller behavior still require physical-device checks. User account creation, Firebase/Google configuration, notifications, read-only YouTube authorization and cloud sync are not provisioned by this phase.

Next: actual listening signals and improved next-song ranking; real category search/results; then permitted PureSong markers and the top Home Skip intros ON/OFF control. Preserve FairPlay fairness, manual queues and crossfade; automatic intro jumps must not count as dislikes. Complete future integration phases against the master plan's source permissions and privacy gates.

Credential custody, repository account multifactor status, full dependency vulnerability coverage, hardware-backed Keystore behavior, signing-key rotation compatibility and paid/free account billing status have not been independently audited here. No claim of full penetration testing or complete security certification is made.
