package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Original VEXO mixes populated with real music search results, cached per country. */
final class MixCatalogue {
  static final class Mix {
    final String id, name, detail, query;
    final int color;

    Mix(String id, String name, String detail, String query, int color) {
      this.id = id;
      this.name = name;
      this.detail = detail;
      this.query = query;
      this.color = color;
    }
  }

  private final SharedPreferences prefs;

  MixCatalogue(Context context) {
    prefs = context.getSharedPreferences("music_mixes", 0);
  }

  static List<Mix> mixes(String country) {
    String regional =
        country.equals("LK") ? "Sinhala favourites" : RegionPolicy.name(country) + " favourites";
    return Arrays.asList(
        new Mix(
            "regional",
            regional,
            "A little closer to home",
            country.equals("LK")
                ? "Sinhala popular songs"
                : RegionPolicy.name(country) + " popular songs",
            0xff6456b8),
        new Mix("tamil", "Tamil hits", "Melodies worth repeating", "Tamil hit songs", 0xffa94f3c),
        new Mix(
            "pop",
            "Pop rotation",
            "Big hooks. Good energy.",
            "English pop popular songs",
            0xffb34879),
        new Mix(
            "focus",
            "Lo-fi focus",
            "Ease into your flow",
            "lofi chill instrumental songs",
            0xff3f7c7e),
        new Mix(
            "acoustic",
            "Acoustic evenings",
            "Slow down and settle in",
            "acoustic chill songs",
            0xff8a6b44),
        new Mix("energy", "Energy boost", "Turn your day up", "workout upbeat songs", 0xff417c50));
  }

  static Mix find(String id, String country) {
    for (Mix m : mixes(country)) if (m.id.equals(id)) return m;
    return null;
  }

  List<Track> cached(Mix mix, String country) {
    List<Track> songs = new ArrayList<>();
    try {
      JSONArray a =
          new JSONObject(prefs.getString(country + ":" + mix.id, "{}")).getJSONArray("songs");
      for (int i = 0; i < a.length(); i++) songs.add(Track.from(a.getJSONObject(i)));
    } catch (Exception ignored) {
    }
    return songs;
  }

  boolean fresh(Mix mix, String country, long now) {
    try {
      long at = new JSONObject(prefs.getString(country + ":" + mix.id, "{}")).getLong("at");
      return now >= at && now - at < 6 * 60 * 60_000L && !cached(mix, country).isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  static List<Track> songs(List<Track> results) {
    LinkedHashMap<String, Track> unique = new LinkedHashMap<>();
    for (Track t : results) if (RadioPolicy.music(t)) unique.putIfAbsent(t.url, t);
    List<Track> out = new ArrayList<>(unique.values());
    return new ArrayList<>(out.subList(0, Math.min(30, out.size())));
  }

  void save(Mix mix, String country, List<Track> songs) {
    try {
      JSONArray a = new JSONArray();
      for (Track t : songs) a.put(t.json());
      JSONObject o = new JSONObject().put("at", System.currentTimeMillis()).put("songs", a);
      prefs.edit().putString(country + ":" + mix.id, o.toString()).apply();
    } catch (JSONException ignored) {
    }
  }
}
