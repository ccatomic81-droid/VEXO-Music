# VEXO 0.25 discovery tester

Implements the seven planned features using existing VEXO UI, encrypted local interests and playback.

- Trending Music finds public TikTok/viral music playlists and music search fallbacks, with full-song playback. It is clearly labelled TikTok-inspired, with source, refresh time and region; no TikTok chart ranks, metrics, private API or account login are claimed. TikTok Research Tools are not available to commercial users: https://developers.tiktok.com/docs/en/research-api-faq . NewPipe supports music playlist search and playlist extraction: https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/services/youtube/linkHandler/YoutubeSearchQueryHandlerFactory.html .
- Mood mixes combine themed music search with the listener’s artists, explicit favourites, preferences and decaying audible-listening evidence. Mood describes search intent, not measured audio characteristics.
- Daily Mix has a stable local-day cache; explicit refresh/tuning can rebuild it. New to you excludes songs in local history, favourites and retained learning, including alternate uploads; it does not claim release-date freshness.
- Tune My Mix saves familiarity, language, mood and recent-song avoidance per mix. Global hidden/negative feedback stays authoritative. Save updates future automatic picks in an active mix; it keeps playback position, pause/repeat settings and manually queued songs.
- Active mix playback context survives queue restore and remains independent of the mix being browsed. New song radio/explicit queues replace that context. Mix refills append unique music, keep artist variety, support provider continuation and retain the existing queue cap/history pruning. Autoplay, repeat and live-room preferences remain authoritative.
- Learning records audible time, qualified manual skips and decaying positive signals. Failed playback and seek distance are excluded. Pause/reset controls remain in Smart. Cached personalized mixes are encrypted; reset learning clears derived caches.

0.25.0-dev/code35 is an opt-in tester updating public 0.24.0/code34 with the original certificate. Public stable remains 0.24.0. Android/unit/native-layout, live provider and signed-download verification are required before test publication. Physical-device audio/Bluetooth/background acceptance remains for testers.
