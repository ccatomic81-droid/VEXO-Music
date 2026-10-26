# Music radio and uninterrupted playback

Starting radio previously called `setMediaItem(seed)`, `prepare()` and `play()` even when that song was already playing. This replaced the timeline and reset playback to zero.

The radio action now changes the station anchor and enables upcoming recommendations. When a current item exists, it preserves that item, position, play/pause intent, shuffle, volume, speed, and existing manual queue. Repeat is turned off so radio can continue. With an empty player it starts the selected seed. The existing autoplay loader appends upcoming songs, rejects stale requests and respects later user pauses.

VEXO's existing song-search filter already queries YouTube Music through NewPipe's `WEB_REMIX` music search. Radio now also prefers public YouTube Music `next` queues, using a song's `RDAMVM` radio endpoint and a plain-song fallback. Client version and HTTP headers come from the installed NewPipeExtractor implementation. Existing related-video and music-search fallbacks remain for unavailable/empty music queues. Requests contain only public song identifiers and do not use Google account tokens or private session cookies.

The response reader accepts bounded playlist-panel song rows, validates IDs/durations/artwork, separates artist labels from album labels and removes duplicate entries. Existing VEXO ranking then applies hidden-song preferences, recent-history handling and artist variety. Metadata lookup never replaces the active player's media item.

Research reference: [Metrolist](https://github.com/MetrolistGroup/Metrolist), especially `YouTubeQueue.radio`, `YouTube.next`, and `NextPage.fromPlaylistPanelVideoRenderer`, and NewPipeExtractor's music search transport. The Java adapter and playback fix are implemented in VEXO; the Metrolist app, account-login implementation and playback engine are not embedded. Region/provider availability still determines which metadata endpoints answer.

Verification includes real Media3 wrapper checks at a nonzero playback position, repeated radio activation, another seed, preserved manual queue, preserved pause intent, and empty-player activation; bounded response-parser fixtures; existing playback/queue/radio tests; and an optional live public-metadata probe. These checks do not replace a physical-device listening test.
