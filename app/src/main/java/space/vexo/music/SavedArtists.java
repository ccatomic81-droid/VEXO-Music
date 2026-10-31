package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Followed artists are independent of disposable song-search history. */
final class SavedArtists {
  private final SharedPreferences prefs;

  SavedArtists(Context c) { prefs = c.getSharedPreferences("followed_artists", 0); }

  List<SearchArtists.Artist> entries() {
    List<SearchArtists.Artist> out = new ArrayList<>();
    try {
      JSONArray array = new JSONArray(prefs.getString("artists", "[]"));
      for (int i = 0; i < Math.min(100, array.length()); i++) {
        SearchArtists.Artist a = decode(array.optJSONObject(i));
        if (a != null && !a.id.isEmpty() && out.stream().noneMatch(old -> old.id.equals(a.id)))
          out.add(a);
      }
    } catch (JSONException ignored) {}
    return out;
  }

  boolean contains(SearchArtists.Artist artist) {
    return !artist.id.isEmpty() && entries().stream().anyMatch(a -> a.id.equals(artist.id));
  }

  boolean follow(SearchArtists.Artist artist) {
    if (artist.id.isEmpty()) return false;
    List<SearchArtists.Artist> all = entries();
    all.removeIf(a -> a.id.equals(artist.id));
    if (all.size() >= 100) return false;
    all.add(0, artist);
    write(all);
    return true;
  }

  void unfollow(SearchArtists.Artist artist) {
    List<SearchArtists.Artist> all = entries();
    all.removeIf(a -> a.id.equals(artist.id));
    write(all);
  }

  List<SearchArtists.Artist> home(Context c) {
    List<SearchArtists.Artist> out = new ArrayList<>(entries());
    for (SearchArtists.Artist recent : new SearchArtists(c).visible()) {
      if (out.stream().noneMatch(a -> a.key().equals(recent.key()))) out.add(recent);
    }
    return new ArrayList<>(out.subList(0, Math.min(12, out.size())));
  }

  private void write(List<SearchArtists.Artist> all) {
    JSONArray array = new JSONArray();
    for (SearchArtists.Artist a : all) array.put(encode(a));
    prefs.edit().putString("artists", array.toString()).apply();
  }

  static JSONObject encode(SearchArtists.Artist a) {
    JSONObject o = new JSONObject();
    try { o.put("id", a.id).put("name", a.name).put("artwork", a.artwork); }
    catch (JSONException ignored) {}
    return o;
  }

  static SearchArtists.Artist decode(JSONObject o) {
    if (o == null || SearchArtists.name(o.optString("name")).isEmpty()) return null;
    return new SearchArtists.Artist(o.optString("id"), o.optString("name"),
        o.optString("artwork"), Collections.emptyList(), System.currentTimeMillis(), false);
  }
}
