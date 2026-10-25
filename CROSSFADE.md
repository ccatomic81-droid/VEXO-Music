# Crossfade implementation

VEXO Music 0.5.0 implements fixed-duration crossfade, rather than beat matching, tempo changes, silence detection or an AutoMix model.

Two ExoPlayer decks use the same read-only offline/network playback factory. Only the primary player is exposed through one ForwardingSimpleBasePlayer and MediaSession. Near the end of a finite, seekable song, a standby deck copies the entire logical playlist, playback parameters, repeat mode and exact shuffle permutation, and prepares the actual successor at volume zero. It cannot download saved-library entries or record history merely by preloading.

The outgoing player remains at normal gain until the standby is ready and playing. At overlap start the session delegate switches to the incoming deck. Its metadata/position become current in the UI and saved queue; the outgoing deck is temporarily paused at its item boundary so it cannot play the successor twice. Cosine/sine volume ramps follow incoming playback position, with 25 ms updates during a blend. After the fade, the outgoing player is released. The system audio mixer mixes both AudioTracks.

The selected duration is wall-clock seconds and accounts for playback speed. An overlap is limited to a third of either track for short audio. Repeat One, missing/same-song successors, unknown durations, live/dynamic/advertising content and unsupported/unready sources fall back to normal playback. Preload errors are bounded to one attempt for the current plan; queue changes or user actions permit a fresh plan. If buffering or an error occurs during an active blend, the outgoing deck is released and the primary retains normal playback/error handling. Network failures can still cause pauses.

A single AudioFocusRequest manages the logical player; each native deck has automatic audio focus disabled. Transient losses pause playback and cancel the overlap, with resume only if the user has not paused in the meantime. Permanent loss requires an explicit play action. Ducking applies to the logical mix (or is handled by Android's system ducking). A noisy-output broadcast pauses everything. Pause, stop, seeking, changing repeat/shuffle/speed and replacing the queue cancel hidden audio; future-only queue edits preserve an overlap already playing. Changing crossfade length applies to the next blend.

This can preload an extra stream and requires two audio decoders briefly. Turning the feature off disables crossfade preloading. It does not normalize different tracks' mastering loudness and cannot guarantee an inaudible transition on every device, Bluetooth route or network.

Relevant primary documentation:

- [Apple: transition songs](https://support.apple.com/guide/iphone/transition-songs-iphadf2fe1f4/ios) describes simultaneous fade out/fade in and distinguishes fixed crossfade from AutoMix.
- [Media3 1.8.0 release notes](https://developer.android.com/jetpack/androidx/releases/media3#1.8.0) document replacing the delegate in ForwardingSimpleBasePlayer.
- [ForwardingSimpleBasePlayer](https://developer.android.com/reference/androidx/media3/common/ForwardingSimpleBasePlayer) and the pinned 1.8.0 source from Google Maven define the wrapper and state behavior used here.
- [Android audio focus](https://developer.android.com/media/optimize/audio-focus) documents focus loss, ducking and Android 15 foreground restrictions.

See VERIFICATION.md for actual results. The host-side engine tests simulate both decks and evaluate the overlap math and cancellation races. Media3 integration tests exercise real ExoPlayer playlist/volume/focus behavior and a prepared-deck handoff with a local unavailable fixture; they do not decode two audible tracks on a real phone. Physical listening, Bluetooth and phone-call testing remain necessary before calling the transitions device-verified.
