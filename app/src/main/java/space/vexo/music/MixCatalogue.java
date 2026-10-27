package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Original VEXO mixes populated with real music search results, cached per country. */
final class MixCatalogue {
  static final class Mix {
    final String id, name, detail, query, intent;
    final int color;

    Mix(String id, String name, String detail, String query, int color) {
      this.id = id;
      this.name = name;
      this.detail = detail;
      this.query = query;
      this.intent = id.equals("focus") ? "lofi focus instrumental" : id.equals("acoustic") ? "acoustic chill"
          : id.equals("energy") ? "upbeat workout" : id.equals("calm") ? "calm relaxing"
          : id.equals("feelgood") ? "happy uplifting" : id.equals("tamil") ? "Tamil melodic"
          : id.equals("regional") ? query.replace(" popular songs", "") : id.equals("pop") ? "pop" : "";
      this.color = color;
    }
  }

  private final PrivateStore prefs;
  private final Context context;

  MixCatalogue(Context context) {
    this.context = context.getApplicationContext();
    prefs = new PrivateStore(context, "music_mixes");
  }

  static List<Mix> mixes(String country) {
    String regional =
        country.equals("LK") ? "Sinhala favourites" : RegionPolicy.name(country) + " favourites";
    return Arrays.asList(
        new Mix("calm", "Calm & connected", "A softer side of your taste", "calm relaxing songs", 0xff416c72),
        new Mix("feelgood", "Feel-good favourites", "Your kind of good energy", "happy uplifting songs", 0xffaa743d),
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
    for (Mix m : all(country)) if (m.id.equals(id)) return m;
    return null;
  }

  static List<Mix> discovery() {
    return Arrays.asList(new Mix("daily", "Daily Mix", "Familiar favourites. Fresh company.", "", 0xff54784a),
        new Mix("discover", "New to you", "Songs you haven’t played in VEXO", "", 0xff73529a));
  }

  static Mix trending() { return new Mix("trending", "Trending Music", "TikTok-inspired song picks", "", 0xffa33d64); }

  static List<Mix> all(String country) {
    List<Mix> out = new ArrayList<>(discovery()); out.add(trending()); out.addAll(mixes(country)); return out;
  }

  String scope(Mix mix, String country, long now) {
    String day = MixDiscovery.day(now);
    // Daily Mix keeps its daily identity; explicit tuning always invalidates it.
    long revision = mix.id.equals("daily") ? 0 : DiscoveryRevision.read(context);
    return country + ":" + day + ":" + revision + ":" + context.getSharedPreferences("discovery_revision", 0).getLong("tune:" + mix.id, 0);
  }

  String description(Mix mix, String country) {
    String detail = mix.id.equals("trending") ? "TikTok-inspired full-song picks · Public YouTube Music playlists and search, not a TikTok chart"
        : mix.id.equals("discover") ? "Unplayed in your VEXO history, shaped by your interests · not necessarily newly released"
        : mix.id.equals("daily") ? "Your daily selection, shaped by VEXO listening and favourites"
        : "Your interests, matched with " + mix.intent + " music";
    try {
      long at = new JSONObject(prefs.getString(country + ":" + mix.id, "{}")).optLong("at");
      if (at > 0) detail += " · Updated " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date(at));
    } catch (JSONException ignored) { }
    return detail;
  }

  void tuned(Mix mix) {
    android.content.SharedPreferences p = context.getSharedPreferences("discovery_revision", 0);
    p.edit().putLong("tune:" + mix.id, p.getLong("tune:" + mix.id, 0) + 1).apply();
  }

  static void clearDerived(Context context) { new PrivateStore(context, "music_mixes").edit().clear().apply(); }

  List<Track> cached(Mix mix, String country) {
    List<Track> songs = new ArrayList<>();
    try {
      JSONObject cache = new JSONObject(prefs.getString(country + ":" + mix.id, "{}"));
      if ((mix.id.equals("daily") || mix.id.equals("discover"))
          && !MixDiscovery.day(System.currentTimeMillis()).equals(cache.optString("day"))) return songs;
      JSONArray a = cache.getJSONArray("songs");
      for (int i = 0; i < a.length(); i++) songs.add(Track.from(a.getJSONObject(i)));
    } catch (Exception ignored) {
    }
    return songs;
  }

  boolean fresh(Mix mix, String country, long now) {
    try {
      long at = new JSONObject(prefs.getString(country + ":" + mix.id, "{}")).getLong("at");
      JSONObject value = new JSONObject(prefs.getString(country + ":" + mix.id, "{}"));
      long ttl = mix.id.equals("daily") ? 24 * 60 * 60_000L : 6 * 60 * 60_000L;
      return now >= at && now - at < ttl && scope(mix, country, now).equals(value.optString("scope")) && !cached(mix, country).isEmpty();
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

  void save(Mix mix, String country, List<Track> songs) { save(mix, country, songs, scope(mix, country, System.currentTimeMillis())); }

  void save(Mix mix, String country, List<Track> songs, String scope) {
    try {
      JSONArray a = new JSONArray();
      for (Track t : songs) a.put(t.json());
      JSONObject o = new JSONObject().put("at", System.currentTimeMillis()).put("songs", a)
          .put("day", MixDiscovery.day(System.currentTimeMillis())).put("scope", scope);
      prefs.edit().putString(country + ":" + mix.id, o.toString()).apply();
    } catch (JSONException ignored) {
    }
  }
}
