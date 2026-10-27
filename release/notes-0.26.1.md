# VEXO Music 0.26.1

- Fix repeated artist circles caused by treating collaboration labels as separate artists.
- Preserve individual artist names and provider channel identities through song searches, saved metadata and playback. Keep structured band names intact, including punctuation.
- Use exact YouTube Music artist results for artist portraits. Song covers are no longer used as artist photos; readable initials appear when a portrait is unavailable.
- Repair existing artist shortcuts automatically without deleting recent searches. Resolve legacy combined labels by exact full-name match first, then recover individual collaboration names when needed.
- Keep one shortcut per artist identity and retain all associated search references. Removing one search keeps artists still connected to another search; clearing searches invalidates in-flight portrait results.
- Load portrait metadata on a separate cancellable worker, cache successful lookups for 7 days and unavailable lookups for 6 hours, and respect Data saver. Home Refresh & repair retries portrait metadata.
- Keep the artist row above Made for you, existing light/dark/device themes, the round player progress dot, Global discovery, animated updates and 500 MB offline saving.

Install over the existing app without uninstalling. Version code 41 updates public 0.26.0/code40 with the original signing identity, preserving saved music and settings.

Validation includes collaboration identity deduplication, legacy data repair, exact portrait matches, punctuated band names, multiple search references, clearing during lookup, worker caching and Data saver. Live checks verify Kevin Smokio and Billie Eilish identities and decode real artist photo bytes. Physical-phone audio and Bluetooth/background playback are not claimed as manually tested.
