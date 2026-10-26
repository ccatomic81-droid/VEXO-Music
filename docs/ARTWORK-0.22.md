# Artwork recovery — 0.22.0

The old player compared artwork URLs with a view tag. Clearing artwork cancelled the request but left that URL marker intact, so the same song could remain blank. A failed request also left a marker with no retry path. Home collection covers skipped tracks whose artwork field was empty even when the song had an identifiable public thumbnail.

The shared loader now tracks each view's actual request state, invalidates it during repair, restarts attached views and resumes repaired cached views when reattached. It retries final temporary failures twice with delays, without starting a new request on every playback tick. Rebinding a view makes older results/retries obsolete. Placeholder bounds no longer control the clipping background.

Artwork sources are normalized, supported provider HTTP thumbnail addresses are upgraded to HTTPS, and YouTube song IDs provide bounded hqdefault/mqdefault fallbacks when metadata is absent or unavailable. Local content artwork continues to work. No unrelated image is invented. Existing artwork caches remain useful.

Regression tests cover same-cover reload after repair, expired and missing metadata, normalized addresses, transient failure retry, recycled targets and shared fetches. A live discovery probe downloads and decodes real image bytes for music search results. Physical-phone verification remains necessary for device-specific rendering and network behavior.
