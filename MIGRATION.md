# Standalone repository migration

Migrated on 25 October 2026 to https://github.com/ccatomic81-droid/VEXO-Music, branch main.

- Imported all 11 VEXO-only source snapshots from the old vexo-music-build branch, in their original order, with files moved from vexo/ to the project root.
- Original commit IDs, dates and their imported commit mappings are retained in .migration/source-history.json. Paths and parents changed, so imported commits have new IDs; the original private history remains available as a backup.
- Unrelated Dayspace files, commits, settings and other private projects were not copied.
- The root Android build workflow tests, lints, builds and checks app version 0.9.0/code 11. CI APKs use a runner key and remain test-only builds.
- The release workflow reuses the original signed APK and corresponding source ZIP. It checks pinned SHA-256 values before and after GitHub asset upload, then publishes the release.
- Version 0.9.0 release files are preserved byte-for-byte. The attached source ZIP is the original release snapshot and may contain historical repository references; the canonical working source and future changes live here.
- The built-in Cloudflare update endpoint, package ID and original signing certificate do not change. Installed apps do not need to reinstall because of this repository migration.
- Signing keys and temporary publishing credentials were not copied into GitHub. The existing private signing backup remains required for future upgrade APKs.

The old VEXO working folder is now a forwarding README. Its Git history, tag and release remain as a backup; the Dayspace project remains untouched.

## Verification completed

- All 161 distinct source blobs were copied with their original Git SHA values verified. App code and 100 original files remain unchanged; canonical documentation and workflow paths were adapted for the repository root.
- The standalone build, tests, lint, update-service tests, package checks and live music-metadata probe passed: https://github.com/ccatomic81-droid/VEXO-Music/actions/runs/40146601421.
- The signed 0.9.0 release was recreated successfully: https://github.com/ccatomic81-droid/VEXO-Music/releases/tag/vexo-v0.9.0. All three asset sizes and SHA-256 digests match the original release.
- Only the old VEXO working files and its two dedicated root workflows were retired. The nine other files in the legacy branch remain byte-for-byte unchanged. The old repository’s default branch was not modified.
- Installed apps and the public update service continue using the same package, certificate and endpoint. No app version bump or reinstall is required.

See .migration/verification.json for exact run URLs, artifact hashes and migration commit references.
