package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** A bounded, on-device shelf from successful song searches; no extra network requests. */
final class SearchArtists {
  static final int LIMIT = 12;
  private final SharedPreferences prefs;

  static final class Artist {
    final String name, artwork, query;

    Artist(String name, String artwork, String query) {
      this.name = name;
      this.artwork = artwork;
      this.query = query;
    }

    String key() {
      return RadioPolicy.normalize(name);
    }
  }

  SearchArtists(Context c) {
    prefs = c.getSharedPreferences("search_history", 0);
  }

  List<Artist> entries() {
    List<Artist> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    try {
      JSONArray array = new JSONArray(prefs.getString("artists", "[]"));
      for (int i = 0; i < Math.min(LIMIT, array.length()); i++) {
        JSONObject o = array.optJSONObject(i);
        if (o == null) continue;
        Artist a =
            new Artist(
                name(o.optString("name")),
                o.optString("artwork"),
                SearchHistory.clean(o.optString("query")));
        if (!a.name.isEmpty() && seen.add(a.key())) out.add(a);
      }
    } catch (JSONException ignored) {
    }
    return out;
  }

  void record(String query, List<Track> songs) {
    query = SearchHistory.clean(query);
    // Honour the search-history privacy control, including a clear while a request is in flight.
    final String term = query;
    if (query.isEmpty() || query.contains("://") || missing(term)) return;
    List<Artist> next = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Track song : songs.subList(0, Math.min(6, songs.size()))) {
      if (!RadioPolicy.music(song)) continue;
      Artist a = new Artist(name(song.artist), song.artwork, query);
      if (!a.name.isEmpty() && seen.add(a.key())) next.add(a);
      if (next.size() == 3) break;
    }
    if (next.isEmpty()) return;
    for (Artist old : entries()) if (seen.add(old.key())) next.add(old);
    write(prefs, next);
  }

  static String name(String value) {
    String name =
        SearchHistory.clean(value)
            .replaceFirst("(?i)(?:\\s*[-–]\\s*topic|\\s+official(?:\\s+channel)?|\\s*vevo)$", "")
            .trim();
    String key = RadioPolicy.normalize(name);
    return key.isEmpty()
            || key.equals("unknown")
            || key.equals("unknown artist")
            || key.equals("various artists")
            || key.equals("youtube")
        ? ""
        : name;
  }

  static void remove(SharedPreferences prefs, String term) {
    try {
      JSONArray old = new JSONArray(prefs.getString("artists", "[]")), next = new JSONArray();
      for (int i = 0; i < Math.min(LIMIT, old.length()); i++) {
        JSONObject o = old.optJSONObject(i);
        if (o != null && !o.optString("query").equalsIgnoreCase(term)) next.put(o);
      }
      prefs.edit().putString("artists", next.toString()).apply();
    } catch (JSONException ignored) {
    }
  }

  private static void write(SharedPreferences prefs, List<Artist> artists) {
    JSONArray next = new JSONArray();
    for (Artist a : artists.subList(0, Math.min(LIMIT, artists.size())))
      try {
        next.put(
            new JSONObject().put("name", a.name).put("artwork", a.artwork).put("query", a.query));
      } catch (JSONException ignored) {
      }
    prefs.edit().putString("artists", next.toString()).apply();
  }

  private boolean missing(String term) {
    try {
      JSONArray terms = new JSONArray(prefs.getString("terms", "[]"));
      for (int i = 0; i < Math.min(20, terms.length()); i++)
        if (term.equalsIgnoreCase(terms.optString(i))) return false;
    } catch (JSONException ignored) {
    }
    return true;
  }
}
