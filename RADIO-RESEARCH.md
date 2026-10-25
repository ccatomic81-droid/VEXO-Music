# Song Radio: research and VEXO implementation

Research checked 24 October 2026. Sources below are Spotify and YouTube's own published descriptions, not third-party guesses about their algorithms.

| Feature | Publicly documented behavior | Implemented in VEXO 0.4.0 |
| --- | --- | --- |
| Song Radio | Spotify creates a changing collection from a song, artist or album. | Start song radio from an online song's menu or Now Playing. The song becomes the station anchor. |
| Autoplay | Spotify continues with similar songs after the selected queue finishes. | Prefetch at two remaining songs, append after the user's existing queue and continue when it ends. |
| Coherence and variety | Spotify describes balancing the previous music, variety and the listener's taste. | Combine original-seed and current-track related candidates, rank with local artist affinity and interleave artists. |
| Radio tuning | YouTube Music documents familiarity, discovery and variety controls. | Balanced, Familiar and Discover modes; changes apply to the next refill. |
| Feedback | YouTube describes Supermix queue updates using likes, impressions and skips. | VEXO uses favourites, local listening history and explicit “Don't recommend in radio” feedback. It does not infer dislikes from every skip. |

Sources:

1. [Spotify Radio](https://support.spotify.com/us/article/spotify-radio/)
2. [Spotify Autoplay](https://support.spotify.com/us/article/autoplay/)
3. [Spotify: The Art and Science Behind Your Summer Listening Picks](https://newsroom.spotify.com/2024-06-27/the-art-and-science-behind-your-summer-listening-picks/), especially the explanation of what plays next.
4. [YouTube Music getting started guide](https://blog.youtube/news-and-events/get-started-youtube-music/), custom radio station and radio steering.
5. [YouTube Music November 2023 feature highlights](https://support.google.com/youtubemusic/thread/246044029/youtube-music-new-features-highlights-nov-2023?hl=en), responsive Supermix and tuning.

## What the app actually does

A station retains its original seed across refills and service recreation. Each refill collects up to 40 candidates per related request from the original seed and current song (one request if they are the same), plus one music-only search for the anchor artist. A failed source can fall back to the others. Results are cached by the existing extractor adapter. This needs no Spotify account, Google login or recommendation API credentials.

The selector removes canonical-URL duplicates, normalized title/artist duplicates, hidden tracks, recent songs and existing queue items. It accepts known durations of 30 seconds to 15 minutes and filters common podcast, interview and compilation title markers. These are metadata heuristics, so unusual titles or long legitimate music may be excluded. Manual playback still accepts those tracks.

- Balanced preserves source relevance while modestly favouring favourite and seed artists.
- Familiar gives more weight to favourite artists, previously played artists and the seed artist.
- Discover prefers artists absent from favourites/history and excludes the full retained listening history (up to 100 songs).
- Balanced and Familiar exclude the most recent 20 songs, allowing older songs to return.
- Artist repetition is penalized and consecutive same-artist picks are avoided when different artists remain. Sparse pools can still continue.

Up to ten songs are appended per refill; the working queue remains capped at 100. Existing pause, repeat, shuffle order, clear-queue and stale-request guards remain active. Starting a new station deliberately resets repeat/shuffle and replaces the queue with its seed. Merely changing tuning or hiding a recommendation preserves the current queue; the feedback applies to future refills. “Retry radio” retries a failed/empty lookup without repeatedly retrying in the background.

Preferences, the station seed, hidden IDs, favourites and history stay on the device. Related requests and music searches still contact YouTube with song URLs or artist terms. VEXO does not send a listening-profile dataset to an analytics backend. Reset radio feedback removes hidden-song exclusions without deleting favourites or history.

## Limits

This is a transparent metadata ranking implementation. It does not reproduce Spotify's or YouTube Music's proprietary models, collaborative listening graph, audio embeddings, tempo/key analysis, or guaranteed mood/language matching. It cannot promise nonstop music if no fresh candidates are returned, the network fails or the source blocks audio. Physical-phone playback still needs testing.
