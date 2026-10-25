# VEXO Music 0.9.0 — issue review

Recipient for user-initiated app reports: ccatomic81@gmail.com.

| Finding | Correction | Evidence |
| --- | --- | --- |
| Screenshot showed Good night at 04:59 | Morning now begins at 04:00 using device-local time | Hour boundaries and foreground time/time-zone/resume UI checks |
| Android 8 receiver registration depended on an app-private compatibility permission | Register protected system time broadcasts directly on older Android; use explicit flags on newer Android | Full Android 8 UI check |
| Clock changes could leave greeting stale until polling | Foreground system minute/time/time-zone broadcasts; refresh on resume | Existing Home instance updates without rebuilding the screen |
| Artwork tasks used an unlimited queue and held screen views | Two workers, bounded 48-item queue, weak view references, generation checks | Queue saturation and repair cancellation check |
| Low-memory callback did not evict artwork | Release replaceable image cache and pending work | Application low-memory handling; image fallback remains |
| Old requests could refill caches after repair | Home/mix cache writes follow generation and region checks | Request code review; existing stale-result suites retained |
| Refresh was ignored while Home was already loading | Forced refresh cancels the previous future and starts a new generation | Request code review |
| Clock moved backward could keep old Home data fresh | Reject future cache timestamps; stream expiry now uses elapsed time | Cache/clock code review |
| Mini-player fixed height could clip large text | Content-based height, rounded artwork, progress line and fixed touch targets | Narrow and large-text native render checks |
| Mini-player Play/Pause also opened the player | Play/Pause acts directly; row opens Now Playing | Control wiring review |
| Device errors had no reviewable report flow | Local sanitized diagnostics, email preview, copy fallback | Privacy/bounds, email-intent and support-UI checks |

## Repair limits

Refresh & repair preserves all user collections, profile data, queue and completed offline audio. Automatic recovery runs once on the next process start after a recorded uncaught exception, when enabled. Playback failures clear stale stream addresses for the next explicit retry. The app does not silently restart or install updates, change Android permissions, or guarantee recovery from every device/provider problem.

Reports stay on the phone until the user chooses to share them. Exception messages and user media/profile/search data are excluded. The optional description is sent as entered. An email draft is not proof that an email was sent or delivered.

## Verification scope

Host-side Robolectric tests and native renders do not establish physical Infinix/OEM behavior, audible streaming quality, delivery of an email through a user's mail app, or repair of all Android crashes. Live publication checks establish release-feed/download integrity, not source-provider audio availability. Final test totals and package/signature checks are in VERIFICATION.md.
