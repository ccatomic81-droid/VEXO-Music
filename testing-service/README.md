# Opt-in VEXO Music test release host

Public tester page: https://vexo-music-testing.kavindukavishka785.workers.dev/

This separate Worker serves the original-key-signed 0.11.0-dev APK (code 13), checksum file and `testing.json`. It links to the corresponding source already publicly available on GitHub at `b095c291da27f150b65657bec69c78d3da76b6c1`. No existing application users are automatically enrolled. The app currently reads only its existing stable feed. Testers download the APK directly and install it as an update while keeping the old app installed. This is a public opt-in test link, not an authenticated closed testing group.

The app's private data is not uploaded here. The APK is a debuggable development build, not a production release. Cloud MindRadio and live rooms are connected. Licensed lyric search, comprehensive transliteration and full catalogue metadata remain pending. Existing saved content remains, and new online offline saves are disabled without authorization. Device checks are still needed.

## Isolation

Only the exact public artifact names/checksums in `artifacts.mjs` can be read. No source archive is uploaded to this host. All objects use `testing/0.11.0-dev/` in the existing release bucket. The stable Worker, stable objects and `stable.json` are not modified. Read-only `service.mjs` is the deployed final entrypoint. Files are streamed with correct content types, resumable ranges, ETags and content lengths. The test page is noindex and shows development limitations.

`publisher.mjs` is a temporary deployment entrypoint. It requires a short-lived capability, verifies hashes and sizes against the reviewed artifacts, refuses overwrite with differing bytes and requires all downloads before publishing the manifest. It cannot address any stable object. After publication, redeploy `service.mjs` with only the R2 binding and remove all publishing bindings/token material. Never commit a token, key or signing password.

`node --test testing-service/service.test.mjs update-service/service.test.mjs` covers public download/range behavior, manifest routing, closed write routes, authentication/expiry, checksum rejection, publish ordering and stable isolation. Cloud verification checks exact served bytes/certificate and confirms the stable manifest is unchanged.

No paid plan or subscription is enabled by this configuration. It reuses the existing release bucket; existing account quotas still apply.

Future releases offered to devices that install this candidate must use version code 14 or higher; the current test candidate uses code 13.

The original 0.10.0-dev APK URL remains available at its original immutable object. Android verification: 117 tests, zero failures/errors, five optional skips; lint and APK build passed in run 40170936665. See `release/verification-0.11.0-dev.json` and `cloud-service/live-verification.json`.
