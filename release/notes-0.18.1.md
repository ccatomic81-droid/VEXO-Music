# VEXO Music 0.18.1

- Reuse visited tabs and preserve their scroll positions. Navigation controls are updated in place.
- Short directional slide transitions respect the system's disabled-animation setting; rapid taps cancel earlier motion immediately.
- Move interest-profile construction, validation and radio ranking off the UI thread. Compute ranking scores once per candidate instead of repeatedly during sorting.
- Reuse validated private-data envelopes in bounded process memory only, always matched against the current ciphertext. Empty collection reads no longer write/migrate an empty vault.
- Invalidate hidden pages after data/settings changes and when background search or regional results arrive. Keep the Smart page attached while switching tabs.
- Preserve music playback and all existing YouTube, library and radio functionality.

Device frame-rate and audible playback need testing on a physical phone. Automated UI tests check page reuse, preserved scroll, rapid navigation, bounded page count, motion settling and vault cache invalidation; they do not prove a particular FPS.
