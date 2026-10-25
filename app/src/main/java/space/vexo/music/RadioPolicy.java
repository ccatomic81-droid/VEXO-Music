package space.vexo.music;

import java.text.Normalizer;
import java.util.*;

/** Transparent metadata ranking: related candidates, local taste and artist variety. */
public final class RadioPolicy {
  public enum Mode {
    BALANCED("Balanced", "Related songs with a mix of familiar and different artists"),
    FAMILIAR("Familiar", "More of the artists you like and listen to"),
    DISCOVER("Discover", "Prefer artists and songs you have not played here");
    public final String label, detail;

    Mode(String label, String detail) {
      this.label = label;
      this.detail = detail;
    }

    public static Mode read(String value) {
      try {
        return valueOf(value);
      } catch (Exception ignored) {
        return BALANCED;
      }
    }
  }

  static String normalize(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ")
        .trim()
        .replaceAll(" +", " ");
  }

  public static String artist(Track track) {
    return normalize(track.artist)
        .replaceAll("(?: topic| official| vevo| official channel)$", "")
        .trim();
  }

  /** Only upload decorations are removed; live/remix/version words remain meaningful. */
  static String songKey(Track track) {
    String title =
        normalize(track.title)
            .replaceAll(
                "\\b(official music video|official video|official audio|lyric video|lyrics"
                    + " video|lyrics|official|hd|4k)\\b",
                " ")
            .trim()
            .replaceAll(" +", " ");
    String by = artist(track);
    if (!by.isEmpty() && title.startsWith(by + " ")) title = title.substring(by.length()).trim();
    return by + "|" + title;
  }

  public static boolean music(Track t) {
    if (t == null || !t.online() || t.title.trim().isEmpty() || t.duration < 30 || t.duration > 900)
      return false;
    String title = normalize(t.title);
    return !title.matches(
        ".*\\b(full album|full movie|compilation|podcast|interview|reaction|nonstop|non stop|1"
            + " hour|10 hours)\\b.*");
  }

  public static List<Track> select(
      Track anchor,
      Track current,
      List<Track> candidates,
      List<Track> queue,
      List<Track> history,
      List<Track> favourites,
      Set<String> hidden,
      Mode mode,
      int limit) {
    return select(anchor, current, candidates, queue, history, favourites, hidden, mode, limit, -1);
  }

  static List<Track> select(
      Track anchor,
      Track current,
      List<Track> candidates,
      List<Track> queue,
      List<Track> history,
      List<Track> favourites,
      Set<String> hidden,
      Mode mode,
      int limit,
      int recencyOverride) {
    Set<String> ids = new HashSet<>(hidden),
        keys = new HashSet<>(),
        known = new HashSet<>(),
        liked = new HashSet<>();
    for (String hiddenKey : hidden)
      if (hiddenKey.startsWith("song:")) keys.add(hiddenKey.substring(5));
    for (Track t : queue) {
      ids.add(QueuePolicy.identity(t.url));
      keys.add(songKey(t));
    }
    int recency =
        recencyOverride >= 0
            ? recencyOverride
            : mode == Mode.DISCOVER ? history.size() : Math.min(20, history.size());
    for (int i = 0; i < history.size(); i++) {
      Track t = history.get(i);
      known.add(artist(t));
      if (i < recency) {
        ids.add(QueuePolicy.identity(t.url));
        keys.add(songKey(t));
      }
    }
    for (Track t : favourites) liked.add(artist(t));
    known.remove("");
    liked.remove("");
    List<Track> pool = new ArrayList<>();
    for (Track t : candidates) {
      if (!music(t)) continue;
      String id = QueuePolicy.identity(t.url), key = songKey(t);
      if (ids.contains(id) || keys.contains(key)) continue;
      ids.add(id);
      keys.add(key);
      pool.add(t);
    }
    List<Track> result = new ArrayList<>();
    Map<String, Integer> used = new HashMap<>();
    String lastArtist = artist(current), anchorArtist = artist(anchor);
    int room = Math.max(0, Math.min(Math.min(limit, 10), QueuePolicy.MAX_ITEMS - queue.size()));
    while (!pool.isEmpty() && result.size() < room) {
      int best = -1;
      double highest = -Double.MAX_VALUE;
      boolean alternative = false;
      for (Track t : pool)
        if (!artist(t).isEmpty()
            && !artist(t).equals(lastArtist)
            && used.getOrDefault(artist(t), 0) < 2) alternative = true;
      for (int i = 0; i < pool.size(); i++) {
        Track t = pool.get(i);
        String by = artist(t);
        int count = used.getOrDefault(by, 0);
        // A hard two-per-artist cap when alternatives remain; sparse pools can continue.
        if (alternative && (count >= 2 || !by.isEmpty() && by.equals(lastArtist))) continue;
        double score = 40.0 - i * .35 - count * 15;
        if (mode == Mode.FAMILIAR) {
          if (by.equals(anchorArtist)) score += 26;
          if (liked.contains(by)) score += 34;
          if (known.contains(by)) score += 18;
        } else if (mode == Mode.DISCOVER) {
          if (!known.contains(by)
              && !liked.contains(by)
              && !by.equals(anchorArtist)
              && !by.isEmpty()) score += 35;
          if (by.equals(anchorArtist)) score -= 15;
        } else {
          if (liked.contains(by)) score += 12;
          if (by.equals(anchorArtist)) score += 8;
        }
        if (score > highest) {
          highest = score;
          best = i;
        }
      }
      if (best < 0) break;
      Track chosen = pool.remove(best);
      result.add(chosen);
      lastArtist = artist(chosen);
      used.put(lastArtist, used.getOrDefault(lastArtist, 0) + 1);
    }
    return result;
  }
}
