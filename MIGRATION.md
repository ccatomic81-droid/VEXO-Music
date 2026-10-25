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

The old VEXO location will become a forwarding README after the new build and release are verified. Its Git history, tag and release will remain as a backup; the Dayspace project remains untouched.
