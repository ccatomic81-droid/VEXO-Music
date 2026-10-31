# VEXO Music 0.28.0-dev — design and runtime audit

This tester updates 0.27.0 with focused navigation, layout and queue corrections.

| Finding in 0.27.0 | Change |
| --- | --- |
| Settings rows show the player's next-track icon | A quiet navigation chevron and four clear sections |
| Collection Back uses a previous-track glyph | A standard back chevron |
| Empty phone library repeats Import device music | One primary import action with one file-picker alternative |
| Empty playlist list repeats Create playlist | One create action in the empty state |
| Home's empty artist prompt pushes music down | Compact heading action; artist circles keep their position above Made for you |
| Up next includes previously passed tracks | Counts tracks after the current index, with correct singular text |
| Empty queues offer ineffective clearing actions | Unavailable clear actions are disabled and dimmed |
| User playlists containing online music say On this device | Neutral Your collection label |
| Library keeps requesting another layout after playback is cleared | Dock updates change RecyclerView clipping only when its value differs; repeated layout callbacks do no work |

The existing recommendation engine, radio, smart queue, artist circles, synced lyrics, PureSong, MindRadio, Smart, offline library, crossfade and account features are retained. Connecting a YouTube account remains optional.

## Runtime finding

The first complete emulator interaction reached local playback and pause, then failed to become idle. Its log showed repeated RecyclerView layout requests on every frame. `updateDockSpace` called `RecyclerView.setClipToPadding(false)` from a page-layout callback even when clipping was already false. RecyclerView schedules another layout from that setter after its first layout. The dock now checks the existing value before setting it. A regression check verifies that repeated dock updates with identical geometry request no additional RecyclerView layout.

The device fixture also marks the optional notification invitation as already seen so modal permission onboarding does not obscure navigation captures.

## Validation

Final app source: `1edccc6701705f5c78400dd551a074c9463921ea`. [CI run 41109575382](https://github.com/ccatomic81-droid/VEXO-Music/actions/runs/41109575382) passed both build and device-smoke jobs.

- 268 regression checks: 255 passed, 13 optional live checks skipped, zero failures or errors.
- Separate live discovery/radio probe: 10 passed, zero skipped or failed.
- Android 15 emulator: one complete interaction test at each font size, both passed (17.455 seconds at normal text and 12.796 seconds at 160% text).
- Debug/release lint and APK builds passed; 21 service tests and the source credential guard passed.
- The delivered release APK is not debuggable and its signing certificate matches the existing VEXO app.

 The new Android device smoke test launches the real activity and Media3 service, navigates Home, Search, Library and Settings in both themes, and checks local WAV playback, position advancement and pause. It runs at 320dp with standard and 160% font size. Native Robolectric layout checks cover the corresponding narrow-screen designs, alongside the existing regression suite.

The WAV is generated inside the test app's private directory and removed after the check. No user data is used. Instrumentation results and logs are CI artifacts; native host-rendered layout images are in the checks artifact. Diagnostic screenshot collection is best-effort because successful device-test teardown removes the app files.

Physical-phone upgrade over an existing library, audible quality, Bluetooth, lock-screen controls and long-running background playback still require device acceptance testing. Online provider availability is checked separately and is not established by a local WAV test.

The tester uses version code 44 and the existing VEXO signing identity. The built-in updater continues to check the stable feed. Android requires a higher version code for the next in-place update.
