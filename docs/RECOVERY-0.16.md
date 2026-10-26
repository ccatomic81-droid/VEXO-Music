# VEXO Music 0.16 recovery and release

The signed 0.15.0-dev APK survived a workspace reset, while its unpushed 0.14/0.15 source did not. The recoverable public 0.13.1 project is the source baseline. The missing account/session, Google chooser, manual sync, push, shared styling and Home UI code was recovered from the signed APK and reviewed in Java source. Existing source is retained for playback, queues, search, radio, Cloud MindRadio, live listening, recovery, encrypted storage, recommendations and Smart tools. Generated-code control flow was not substituted for those existing algorithms.

This is a recovered project, not a claim that the lost original source archive was found. Account/session wire formats, the encrypted vault namespace and collection keys are retained. No user data is uploaded as part of recovery. Manual sync transfers online track metadata and playlist names only; local audio, photos, listening history and Smart memories remain local. Existing deletions are not propagated. Atomic merges preserve local content and Time Machine checkpoints. A switched account or destroyed screen cannot apply a pending result.

Firebase client configuration was recovered from the same APK's public resources. The dedicated Google account backend source was read back from its deployed Supabase Edge Function. Its signature/project/provider/verified-email checks and bounded session exchange are preserved. Administrative credentials remain server environment values. No signing material or administrative token is in the repository.

0.16 uses the stable push topic. Existing opted-in test users are unsubscribed from the other topic during restoration. Fresh installs are not subscribed automatically, and granting Android's notification permission does not silently opt a listener into push announcements. Release announcements must use data-only messages; arbitrary notification campaigns bypass application foreground validation and are not part of this release process. No push/email campaign is sent by publication.

The first-visit invitation waits for profile setup, the What's new dialog and foreground focus. Enable requests Android's permission; Not now/back dismissal is remembered. Existing notification grants and recorded refusals are skipped. Settings can still request an explicit later change. The invitation is shown once for this cohort, not after every future version.

## Verification

The full Android test suite, debug/release lint, unsigned production assembly, resolved dependency inventory/advisory check and service tests are required before signing. Recovery tests cover encrypted sessions/sign-out, incomplete/oversized Google responses, two-device metadata merge with local-file preservation, rejection of unsafe metadata, push consent/channel/version filtering, rounded-cover clipping and native account/Home/notification routes. Existing playback, search, queue, radio, recovery and Smart tests remain part of the suite.

Native Home and account layouts are rendered at 320dp and compared to the saved 0.15 verification renders. Existing large-font and older-Android layout tests remain. Google's successful native account chooser and actual FCM delivery still require a physical device; host tests use local fixtures and do not create accounts or send messages. Real-time audio synchronization remains best effort and depends on each phone's buffering and power management.

The release must keep package `space.vexo.music.radio`, use version code 20 and verify the original certificate `0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3`. Release metadata is signed with that identity. Verified immutable APK bytes are published before the stable feed, and temporary publishing permissions are removed afterward. GitHub source and release verification records provide a durable checkpoint.

Final results and public state are recorded under `release/verification-0.16.0.json` and `verification/public-release-0.16.0.json` after completion. Pending tasks must not be described as completed by this document.
