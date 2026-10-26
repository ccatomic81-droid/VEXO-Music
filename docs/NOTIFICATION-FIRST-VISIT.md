# First-visit notification invitation

The first foreground visit after installing this change offers **Enable** and **Not now** when notifications are disabled. A saved name/profile screen and an existing update dialog finish first. Android 13+ requests `POST_NOTIFICATIONS` only after Enable; older Android versions open the app notification settings when notifications are disabled. Already-enabled users and users with a previous `notifications_asked` denial marker are skipped. The invitation is remembered across launches, activity recreation and later updates. Back/outside dismissal also counts as declining. Settings remains available for a later explicit choice.

The helper owns only its delayed callback and explanation dialog. Pausing cancels pending presentation; destroying the activity removes callbacks and dismisses its own dialog. It preserves music collections and all other settings. This change asks for the OS notification permission; it does not silently subscribe a user to push topics or enable separate marketing/update subscriptions.

## Integration and release state

This public source branch is based on the recoverable `feature/search-layout-polish` branch, commit `9b02c4852afd82dc4831cde47e4f6fa856bd5b9e` (0.13.1). The signed 0.15.0 APK and its prior verification reports survived a workspace reset, but the unpushed 0.14/0.15 source did not. Extracting readable classes from the APK is not sufficient evidence to replace that source safely. This branch is therefore a reviewable integration change, not a replacement for the 0.15 app or a stable update.

When the original 0.15 source is restored, reuse its existing `notificationPermission` launcher instead of declaring a second one, construct `NotificationPermissionPrompt` in `onCreate`, and keep the resume/pause/focus/render/destroy hooks from this change. Build with the original signer and a higher version code; rerun the 0.15 account, sync, push, playback and layout checks. Do not publish the 0.13.1 CI APK over the installed 0.15 app. No stable/test-host manifest has been promoted by this source publication.

`NotificationPermissionPromptTest` checks explicit Enable, Not now, back dismissal, already-granted/previously-denied states, activity recreation and startup/background deferral. The full Android build and existing service checks run through the pull request workflow.
