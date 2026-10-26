# First-visit notification invitation

The first foreground visit after installing this change offers **Enable** and **Not now** when notifications are disabled. A saved name/profile screen and an existing update dialog finish first. Android 13+ requests `POST_NOTIFICATIONS` only after Enable; older Android versions open the app notification settings when notifications are disabled. Already-enabled users and users with a previous `notifications_asked` denial marker are skipped. The invitation is remembered across launches, activity recreation and later updates. Back/outside dismissal also counts as declining. Settings remains available for a later explicit choice.

The helper owns only its delayed callback and explanation dialog. Pausing cancels pending presentation; destroying the activity removes callbacks and dismisses its own dialog. It preserves music collections and all other settings. This change asks for the OS notification permission; it does not silently subscribe a user to push topics or enable separate marketing/update subscriptions.

## Integration and release state

The invitation is integrated with the recovered 0.16 project and reuses the account/notification permission launcher. Recovery and release verification are recorded in [RECOVERY-0.16.md](RECOVERY-0.16.md). The earlier standalone PR #9 served as the verified implementation baseline before the latest account, sync and UI features were restored.
