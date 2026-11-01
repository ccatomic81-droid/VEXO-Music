package space.vexo.music;

import android.content.Context;
import java.util.*;

/** Session-only controls for future automatic picks. Popular/deep cuts use search intent, not invented play counts. */
final class QueueMixFilter {
  static final String[] LABELS = {"All", "Familiar", "Popular", "Discover", "Deep cuts"};
  static String valid(String value) {
    for (String label : LABELS) if (label.equals(value)) return label;
    return "All";
  }
  static String read(Context context) {
    return valid(context.getSharedPreferences("playback_state", 0).getString("queue_filter", "All"));
  }
  static String query(String filter, Track seed) {
    String artist = seed.artist.substring(0, Math.min(80, seed.artist.length()));
    if (filter.equals("Popular")) return artist + " popular hit songs";
    if (filter.equals("Deep cuts")) return artist + " album tracks b sides lesser known songs";
    return "";
  }
  static List<Track> select(String filter, List<Track> input, List<Track> history, List<Track> liked) {
    Set<String> known = new HashSet<>(), artists = new HashSet<>();
    for (List<Track> source : Arrays.asList(history, liked))
      for (Track song : source) {
        known.add(RadioPolicy.songKey(song));
        if (!RadioPolicy.artist(song).isEmpty()) artists.add(RadioPolicy.artist(song));
      }
    LinkedHashMap<String, Track> result = new LinkedHashMap<>();
    for (Track song : input) {
      if (!RadioPolicy.music(song)) continue;
      boolean familiar = known.contains(RadioPolicy.songKey(song));
      if (filter.equals("Familiar") && !familiar && !artists.contains(RadioPolicy.artist(song))) continue;
      if (filter.equals("Discover") && familiar) continue;
      result.putIfAbsent(RadioPolicy.songKey(song), song);
    }
    return new ArrayList<>(result.values());
  }
}
