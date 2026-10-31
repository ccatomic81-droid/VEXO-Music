# VEXO Music 0.28.1

Developer: **Kavindu Kavishka**.

Public version **0.28.1** defaults update alerts and release alerts to ON, connects release alerts automatically after Android notification permission is granted, and initializes crossfade at 4 seconds. Saved Off choices and custom durations are respected.

[Download VEXO Music 0.28.1](https://vexo-music-updates.kavindukavishka785.workers.dev/releases/VEXO-Music-0.28.1.apk), use Settings → Check for updates, or open the [GitHub release](https://github.com/ccatomic81-droid/VEXO-Music/releases/tag/vexo-v0.28.1). Install over the existing app without uninstalling. Version code 46 uses the original signing identity. See [release notes](release/notes-0.28.1.md) and [update behavior](UPDATES.md).

The 0.28.0 Home, Settings, Library and queue improvements remain, along with synced lyrics, artist circles, personalized mixes, radio, MindRadio, PureSong, Smart, offline music, crossfade and playlists. Connecting a YouTube account is optional.

Search now shows real song names, artwork and artists while typing, with direct play controls. Home has quick collection tiles, horizontally scrolling music cards, regional picks and VEXO mood mixes. Full playlist pages support Play all, a one-shot shuffled queue, adding songs, renaming and deleting a playlist, and removing individual tracks.

The canonical source is [ccatomic81-droid/VEXO-Music](https://github.com/ccatomic81-droid/VEXO-Music), branch `main`, at the repository root. Source history, build automation and releases have moved to this standalone repository. The app package, signing identity and built-in update address are unchanged. Install over the previous version to keep favourites, downloads and playlists.

## Refresh, repair and a calmer Home (0.9.0)

The Home header now has a subtle gradient greeting, visible Refresh control, balanced collection links and two responsive cover cards across a typical phone screen. The mini-player has a rounded cover, compact spacing, a progress line and content-based height for large text. Its Play/Pause control acts directly; tap the row to open Now Playing.

Tap Home’s Refresh or Settings → Refresh & repair to discard replaceable discovery/stream/artwork caches, cancel stale requests and reload music. The app preserves your name/photo, playlists, favourites, history, recent searches, queue and completed offline audio. It does not reset Android permissions or delete app data. Discovery lists need a connection to refill.

Automatic recovery is on by default and can be disabled in Settings. After a recorded app crash, the next process launch refreshes the same temporary caches once. Playback errors invalidate cached stream addresses for the next retry. Recovery cannot repair missing permissions, unavailable source songs, an internet outage or a provider change requiring a new app version.

Settings → Report a problem previews a local diagnostic report and opens an email addressed to **ccatomic81@gmail.com**. The user reviews and sends it using their email app; Copy report works when no email app is available. Reports include app/Android/device versions, time zone, connectivity, storage/heap and up to 12 recent issue records. Error messages, track names/URLs, recent searches, profile names and photos are excluded. A user’s optional description is included as typed. There is no background report upload, embedded mail credential or added broad permission.

Home shows up to eight personal playlist cards; the Your playlists shortcut opens the full library. Artwork loading is bounded to two workers and 48 queued requests, uses weak references to screen views, discards stale results after repair, and releases replaceable cache entries on low-memory callbacks. Clock-related UI work pauses when the activity is not visible. See [ISSUES-FIXED.md](ISSUES-FIXED.md).

## Personal Home and built-in updates (0.8.0)

On first launch, enter a name (1–40 characters) and optionally choose a photo using Android’s system picker. Tap the Home avatar or Settings → Your profile to edit it. VEXO copies a cropped photo into private app storage; no account, gallery-wide permission or photo upload is required. Existing playlists, favourites and downloads remain.

Home uses the phone’s local time: morning 04:00–11:59, afternoon 12:00–16:59, evening 17:00–20:59 and night 21:00–03:59. The greeting refreshes each local minute, immediately on time/time-zone changes while open, and on resume. It uses the phone’s time zone; the selected music region does not override the clock.

Update checks now use the public release service built into this APK. Users do not enter an address. Settings → Check for updates checks immediately; automatic checks are optional and run at most once a day when opening VEXO. Get update opens the signed APK in the browser; Android handles installation. See [UPDATES.md](UPDATES.md).

Artwork now selects the largest thumbnail in source metadata and requests higher resolution from supported YouTube/Google image hosts, with original-URL fallback. Decoding retains full resolution for smaller images and bounds larger images to 1024 pixels. Source quality still sets the limit; unavailable artwork keeps its placeholder.

## Discovery and playlists (0.7.0)

Type two or more characters in Search for debounced online song matches; cached matching songs appear immediately. Results show title, artist/channel, cover art, and a Play button without submitting the whole search. Artist/channel matches and search phrases remain available. Old asynchronous requests cannot replace a newer query. Search contracts its header when the keyboard opens.

Home mixes are original VEXO collections of live YouTube music-search results. The first three mixes warm in the background, and other mixes load when opened. Cached mixes are scoped by country and refreshed after six hours. They are not Spotify-owned playlists or ranked charts. No fake songs are bundled. If no cached songs exist and a provider request fails, the mix shows Retry and disables playback until songs are available. Cover art comes from real source metadata, with a gradient fallback when artwork is absent.

Open a mix, then choose **Save to your playlists** to retain its current songs on this device. A saved copy does not change when the discovery mix refreshes. Open a personal playlist → **Add songs** to search and use each song’s + button; choose **Done** to return. Song menus also support adding songs to any playlist. **Edit playlist** handles rename, individual removal and deletion. Song URLs are deduplicated. Existing personal collections are never overwritten by saving a mix.

Play all starts the displayed collection in its stored order. Shuffle starts a randomized copy, leaving saved order unchanged. Both use the existing player, radio, crossfade and queue limits. Saving playlist metadata does not automatically download audio; existing auto-save rules still apply when listening.

Crossfade, song radio, offline saves, per-install What's new, recent-search controls, and automatic-update settings remain. Public update delivery is built in. See [UPDATES.md](UPDATES.md). GitHub Actions produces a clean-install test APK unless the existing signing key is configured securely; use the delivered signed APK to upgrade an installed version.

## Crossfade (0.5.0)

Open **Settings → Crossfade**. Choose **Off** or **1–12 seconds** and tap **Save**. The default is **4 seconds**. Cancel leaves the setting unchanged.

At a natural song ending, a prepared next song fades in while the current song fades out with an equal-power curve. This works with finite, seekable local/offline songs and online audio when the next song has buffered. Queue order and Shuffle are preserved. Very short songs use a shorter fade; Repeat One and an absent/unsupported/unready next track use normal playback. Manual skips and seeking remain immediate. A setting change applies to the next transition, without cutting an overlap already playing.

Crossfade prepares audio early and may use extra mobile data. Turning it off avoids the additional crossfade preload. Pause, clearing the queue, sleep timer, unplugging headphones and audio-focus loss stop any hidden outgoing audio. See [CROSSFADE.md](CROSSFADE.md) for implementation details and verification limits. Host-side checks do not establish physical-phone sound quality.

The app name remains **VEXO Music**, developer **Kavindu Kavishka**. Install over v0.4.x Radio / VEXO Music to keep that app's library; the package and backed-up signer are unchanged.

## Settings privacy and branding (0.4.2)

The installed app is named **VEXO Music**. Settings → About and the Settings footer credit **Developer: Kavindu Kavishka**.

Settings shows offline storage usage and saved-song count without downloading/saved song titles. The songs themselves remain visible in Library → Offline; auto-save and existing downloads are preserved. The summary uses “1 saved song” for a single song.

Install this APK over v0.4.0/v0.4.1 Radio. Its internal package and signing certificate are unchanged, so existing app data is retained.

## Player alignment and icon update (0.4.1)

Now Playing uses a square, rounded artwork frame sized to the available window, a consistent content width and a title/favourite row with separate space. The timer, speed, queue and more controls occupy equal slots. Radio buttons have a visible gap, and Autoplay has a clear switch. On short screens or with large text, the content scrolls while the player header stays fixed.

Icons use a consistent 24 dp glyph size inside their touch targets, with a larger central play/pause glyph. Queue, repeat, timer and favourite shapes have been redrawn, and screen-reader action descriptions are clearer. The playback-speed label shows `1×` instead of `1.0×`.

This APK uses the same app ID and signing certificate as v0.4.0 Radio. Install it over v0.4.0 Radio to retain that app's library. The older v0.3.0 app remains a separate installation.

## This update: Song Radio

- Start song radio from any supported online song's menu or from Now Playing. The original song anchors the station while related songs continue automatically.
- Choose Balanced, Familiar or Discover in Settings. Up next contains autoplay and shows the station status.
- Local favourites and listening history influence ranking. Artist variety prevents same-artist runs when alternatives exist.
- Duplicate YouTube URLs, alternate lyric/official uploads, recent songs and common non-music/long-compilation results are filtered.
- “Don't recommend in radio” hides a song and matching title/artist uploads from future radio refills. Reset radio feedback in Settings.
- Up next shows station/loading status. Retry appears when recommendations fail or run out; starting radio preserves current playback and manual queue entries.
- The original song anchors candidate gathering; continuation can expand through songs in that station. Sparse results fall back to verified same-artist songs. Cancellation prevents an older station replacing the newly selected song.

See [RADIO-RESEARCH.md](RADIO-RESEARCH.md) for the Spotify/YouTube research, exact ranking rules and limitations. This is an original metadata-based implementation; it does not claim to reproduce those services' private recommendation models.

## Retained features

Regional Home discovery, manual region selection, YouTube search, local phone audio, favourites, playlists, listening history, mini-player, full player, seek, shuffle/repeat, sleep timer and automatic offline saving remain available.

Home detects the music region through network/SIM country or phone locale, with a manual override and no GPS permission. It shows a real regional YouTube chart where available; unsupported regions such as LK use explicitly labelled regional search picks. Lists are cached locally.

Auto-save is enabled by default and attempts to save the full audio of a played online song. Only complete saves appear in Library → Offline. Storage is limited to 500 MiB, with controls to turn auto-save off, remove a song or clear saved audio.

## Install or update VEXO Music

Install `VEXO-Music-0.24.0.apk`. It appears as **VEXO Music**, package `space.vexo.music.radio`, version code 34.

The earlier delivery workspace and its signing key were reset. The v0.4.x line uses its backed-up development signing certificate and a separate app ID so it can coexist with v0.3.0. **Keep the old app installed to preserve its favourites, playlists and offline audio. That private app data does not automatically transfer to this build.** This APK is not an in-place update to v0.3.0.

For production, choose a permanent app ID and a stable private signing key, keep an external backup of that key, and configure it for CI. No private signing key or credentials are included in this source ZIP. The separately delivered `VEXO-Radio-Development-Signing-Backup.zip` backs up this Radio build’s development signer; keep it private and outside the repository.

If Android requests it, allow APK installation for the browser or Files app you use to open the APK. Use **Library → Scan phone** to grant audio access, or **Import audio** to choose individual audio files.

Auto-saved audio is stored privately inside the app, for offline listening in VEXO Music. It is not exported as MP3 files to the public Downloads folder. Auto-save uses the phone’s current internet connection. Uninstalling clears saved audio and app library data, while your original phone audio files stay intact.

Auto-save runs with the playback service. Switching songs cancels an unfinished save and starts the new song; already completed saves remain. Android force-stop or service shutdown ends unfinished work. Retry by playing that song again. When storage fills, existing offline songs remain and new saves stop until space is cleared.

## Other features

YouTube song/artist search, direct YouTube links, phone music, favourites, named playlists, listening history, queue controls, seek, shuffle, repeat, sleep timer, mini-player, audio-focus management and unplug-to-pause. No account, API key, analytics backend or cloud database is required.

## Build with GitHub, without Android Studio

Upload this folder’s contents to a GitHub repository, including `.github`, `gradle`, `gradlew` and `gradlew.bat`. Run **Actions → Build VEXO Music APK → Run workflow**, then download **VEXO-Music-APK** when the run succeeds. The workflow tests, lints, builds and verifies the APK.

The supplied APK uses the existing backed-up development signing key. A new GitHub runner’s default debug key will be different. Configure a stable private release key in Actions secrets before using CI for future updates to an existing install. No keystore or credentials are in this source archive.

## Build locally

Use JDK 17, Android SDK platform 35 and build tools 35.0.0. Set `ANDROID_HOME` to the SDK path. Dependencies come from Google Maven, Maven Central and JitPack.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat` instead. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Source structure

| File | Purpose |
| --- | --- |
| `MainActivity.java` | Screens, navigation, local music, regional feed UI and settings |
| `TransportControls.java` | Measured full-player controls that fit narrow windows |
| `PlaybackService.java` | Media3 background playback, queue, autoplay, auto-save and sleep timer |
| `RadioPolicy.java`, `RadioCatalogue.java`, `RadioStore.java`, `RadioTuner.java` | Station candidates, local taste, feedback and radio tuning |
| `AutoplayManager.java`, `QueuePolicy.java` | Stale-result-safe radio loading, duplicate filtering and shuffle-aware queue rules |
| `QueueStore.java`, `QueuePanel.java` | Paused queue restoration and Up Next UI |
| `OfflineLibrary.java` | Persistent complete audio downloads, stable cache keys, offline reads and storage budget |
| `RegionPolicy.java` | Automatic region selection and country validation |
| `RegionalFeed.java` | Per-region YouTube chart, labelled search fallback and local feed cache |
| `OnlineMusic.java` | NewPipe search, pagination and short-lived stream resolution |
| `LibraryStore.java` | Favourites, playlists, imports and history |
| `Track.java`, `Visuals.java` | Metadata, original icons and cover placeholders |

## Verification and limits

See [VERIFICATION.md](VERIFICATION.md) and `verification/` for this build's actual results. Host-side tests cover ranking, duplicate filtering, familiarity/discovery choices, station persistence, request fallback/cancellation, queue state/races and narrow/large-text layouts. UI renders use fixture metadata and do not represent a bundled music catalogue. Offline tests verify exact stored bytes persist and replay with network fetching disabled.

This app depends on YouTube/NewPipe availability. Source changes, unavailable/restricted songs or network failures can prevent playback and radio continuation. Related metadata and artist terms are sent to YouTube; the favourites/history dataset itself stays local. There is no cloud recommender, mood/tempo embedding model or guaranteed language matching.

Earlier live audio probing received an HTML “Site Unavailable” response from the audio CDN; HTML/JSON responses are rejected. Audible YouTube playback, physical-device background/lock-screen behavior and saving real online audio still need phone testing. Successful metadata requests do not establish that audio is playable.
