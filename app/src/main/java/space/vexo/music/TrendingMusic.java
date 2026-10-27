package space.vexo.music;

import java.util.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.playlist.*;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;

/** Public, curator-selected music. This is never labelled an official TikTok ranking. */
final class TrendingMusic {
  static List<Track> load(String query, String country) throws Exception {
    OnlineMusic.initialize();
    List<Track> found = new ArrayList<>();
    try {
      SearchExtractor search = ServiceList.YouTube.getSearchExtractor(ServiceList.YouTube.getSearchQHFactory()
          .fromQuery(query, Collections.singletonList("music_playlists"), ""));
      search.forceContentCountry(new ContentCountry(country)); search.fetchPage();
      int attempted = 0;
      for (InfoItem item : search.getInitialPage().getItems()) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!(item instanceof PlaylistInfoItem) || !eligible(item.getName())) continue;
        if (++attempted > 2) break;
        try {
          PlaylistExtractor playlist = ServiceList.YouTube.getPlaylistExtractor(item.getUrl());
          playlist.forceContentCountry(new ContentCountry(country)); playlist.fetchPage();
          found.addAll(OnlineMusic.convert(playlist.getInitialPage().getItems()));
          if (MixCatalogue.songs(found).size() >= 20) break;
        } catch (Exception unavailable) {
          if (unavailable instanceof InterruptedException || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        }
      }
    } catch (Exception unavailable) {
      if (unavailable instanceof InterruptedException || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
    if (MixCatalogue.songs(found).size() < 5) found.addAll(OnlineMusic.preview("TikTok viral songs", country));
    return candidates(found);
  }
  static List<Track> candidates(List<Track> input) {
    Map<String, Track> songs = new LinkedHashMap<>();
    for (Track track : input) {
      if (RadioPolicy.music(track)) songs.putIfAbsent(RadioPolicy.songKey(track), track);
      if (songs.size() >= 100) break;
    }
    return new ArrayList<>(songs.values());
  }

  static boolean eligible(String title) {
    return RadioPolicy.normalize(title).matches(".*\\b(tiktok|tik tok|viral|trending)\\b.*");
  }
}
