package space.vexo.music;

import java.util.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;

/** Public YouTube Music artist results, never a song-cover or approximate-name match. */
final class ArtistCatalogue {
  static List<SearchArtists.Artist> artists(List<? extends InfoItem> results) {
    LinkedHashMap<String, SearchArtists.Artist> out = new LinkedHashMap<>();
    for (InfoItem item : results.subList(0, Math.min(40, results.size()))) {
      if (!(item instanceof ChannelInfoItem)) continue;
      String id = channelId(item.getUrl()), name = SearchArtists.name(item.getName());
      if (!id.isEmpty() && !name.isEmpty())
        out.putIfAbsent(id, new SearchArtists.Artist(id, name,
            ArtworkQuality.best(item.getThumbnails()), Collections.emptyList(),
            System.currentTimeMillis(), false));
    }
    return new ArrayList<>(out.values());
  }

  static List<SearchArtists.Artist> resolve(SearchArtists.Artist artist, String country)
      throws Exception {
    List<SearchArtists.Artist> exact = search(artist, country);
    if (!exact.isEmpty()) return exact;
    // Only legacy combined labels need textual recovery. Structured provider credits stay intact.
    if (!artist.legacy) return Collections.singletonList(artist.checked("", ""));
    List<String> parts = parts(artist.name);
    if (parts.size() < 2) return Collections.singletonList(artist.checked("", ""));
    List<SearchArtists.Artist> out = new ArrayList<>();
    for (String part : parts) {
      SearchArtists.Artist individual = artist.individual(part);
      try {
        List<SearchArtists.Artist> found = search(individual, country);
        out.add(found.isEmpty() ? individual.checked("", "") : found.get(0));
      } catch (Exception error) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        out.add(individual.checked("", ""));
      }
    }
    return out;
  }

  static List<String> parts(String name) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String part : name.split("(?i)\\s*[,;]\\s*|\\s+(?:&|feat\\.?|ft\\.?|featuring)\\s+")) {
      String clean = SearchArtists.name(part);
      if (!clean.isEmpty()) out.add(clean);
      if (out.size() == 4) break;
    }
    return new ArrayList<>(out);
  }

  private static List<SearchArtists.Artist> search(SearchArtists.Artist wanted, String country)
      throws Exception {
    return OnlineMusic.artistPortraits(wanted, country);
  }

  static List<SearchArtists.Artist> matches(
      SearchArtists.Artist wanted, List<? extends InfoItem> results) {
    List<SearchArtists.Artist> out = new ArrayList<>();
    for (InfoItem item : results.subList(0, Math.min(10, results.size()))) {
      if (!(item instanceof ChannelInfoItem)) continue;
      String id = channelId(item.getUrl());
      if (id.isEmpty()) continue;
      String name = SearchArtists.name(item.getName());
      boolean match =
          wanted.id.isEmpty()
              ? RadioPolicy.normalize(name).equals(RadioPolicy.normalize(wanted.name))
              : wanted.id.equals(id);
      if (!match) continue;
      out.add(wanted.checked(id, ArtworkQuality.best(item.getThumbnails())));
      break;
    }
    return out;
  }

  static String channelId(String value) {
    okhttp3.HttpUrl url = okhttp3.HttpUrl.parse(value == null ? "" : value);
    if (url == null
        || !url.isHttps()
        || !url.username().isEmpty()
        || !url.password().isEmpty()
        || !(url.host().equals("www.youtube.com")
            || url.host().equals("youtube.com")
            || url.host().equals("music.youtube.com"))) return "";
    List<String> path = url.pathSegments();
    return path.size() == 2
            && path.get(0).equals("channel")
            && path.get(1).matches("UC[A-Za-z0-9_-]{22}")
        ? path.get(1)
        : "";
  }
}
