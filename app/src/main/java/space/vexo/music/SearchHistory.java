package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONArray;

/** Search terms stay on this device. Case-insensitive, newest first, bounded. */
final class SearchHistory {
  private final SharedPreferences prefs;

  SearchHistory(Context context) {
    prefs = context.getSharedPreferences("search_history", 0);
  }

  List<String> entries() {
    List<String> out = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("terms", "[]"));
      for (int i = 0; i < Math.min(20, a.length()); i++) {
        String term = clean(a.optString(i));
        if (!term.isEmpty()) out.add(term);
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  void add(String term) {
    term = clean(term);
    if (term.isEmpty() || term.contains("://")) return;
    List<String> terms = entries();
    final String value = term;
    terms.removeIf(s -> s.equalsIgnoreCase(value));
    terms.add(0, value);
    write(terms.subList(0, Math.min(20, terms.size())));
  }

  void remove(String term) {
    List<String> terms = entries();
    terms.removeIf(s -> s.equalsIgnoreCase(term));
    write(terms);
    SearchArtists.remove(prefs, term);
  }

  void clear() {
    prefs
        .edit()
        .remove("terms")
        .remove("artists")
        .putLong("artist_revision", prefs.getLong("artist_revision", 0) + 1)
        .apply();
  }

  private void write(List<String> terms) {
    prefs.edit().putString("terms", new JSONArray(terms).toString()).apply();
  }

  static String clean(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    return trimmed.substring(0, Math.min(200, trimmed.length()));
  }

  static List<String> merge(String query, List<String> recent, List<String> remote) {
    String needle = clean(query).toLowerCase(Locale.ROOT);
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (String s : recent) {
      String term = clean(s);
      if (term.toLowerCase(Locale.ROOT).contains(needle))
        out.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
    }
    for (String s : remote) {
      String term = clean(s);
      if (!term.isEmpty()) out.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
    }
    return new ArrayList<>(out.values()).subList(0, Math.min(8, out.size()));
  }
}
