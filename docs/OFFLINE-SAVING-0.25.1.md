# VEXO Music 0.25.1 offline saving fix

Listening to online songs did not create offline saves because the production cache was constructed with a hard-coded false download flag. Tests used a separate true flag, so they did not exercise that production behavior. The flag and alternate constructor have been removed. Both production and tests now use the same auto-save path, controlled by the existing auto_save preference.

Settings now includes Auto-save offline music with a correct ON/OFF state independent of Data saver. Changing it notifies the playback service immediately, allowing the currently playing song to start saving when enabled. Turning it off cancels pending saving. Completed songs remain available in Library → Offline.

The existing 500 × 1024 × 1024 byte limit is retained. When there is no room, saving stops and existing songs remain. Remove individual offline songs or clear offline storage to make room. Failed or interrupted saves are excluded from the offline library. Complete cached audio can be replayed without resolving an online stream and survives a process restart. Saving uses internet data and may continue separately from streaming the current song; a song appears under Offline only after its full save completes.

Regression coverage uses the default production constructor for completed saves, persistence and network-free replay; verifies rejected over-budget downloads, preservation of existing songs, clear and save again, partial failure cleanup/retry, disabled saving, and the actual Settings switch.

The test build is 0.25.1-dev/code36, which can update 0.25.0-dev/code35 or public 0.24.0/code34 with the original signing identity. Public stable must remain at 0.24.0 pending acceptance.

Phone acceptance: install over the existing app, enable Settings → Auto-save offline music, listen online until a song appears under Library → Offline, then turn on airplane mode and replay it. Close and reopen VEXO while still offline and replay it again. Verify that the switch stops new saves and that clearing offline storage leaves phone originals, favourites and playlists intact.

Validation: Android workflow 40467834814 passed 218 tests with eleven skipped and no failures, including five offline regressions and the Settings UI check. Both lint and APK build variants, dependency audit and source guard passed. All eight live discovery checks passed. Original certificate, non-debuggable metadata, versionCode 36 and exact unsigned-candidate payload match verified. A signed 0.25.1-dev APK was provided for phone acceptance; no public stable or tester-host update was published.
