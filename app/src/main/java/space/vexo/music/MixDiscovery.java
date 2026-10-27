package space.vexo.music;

import java.time.*;
import java.util.*;
import org.json.*;

/** Bounded, account-optional music discovery. Search relevance supplies mood, not audio inference. */
final class MixDiscovery {
  static final class Tuning {
    final int familiarity;
    final String language, mood;
    final boolean avoidRecent;
    Tuning(JSONObject value) {
      familiarity = Math.max(0, Math.min(2, value.optInt("familiarity", 1)));
      language = value.optString("language", "").trim();
      mood = value.optString("mood", "").trim();
      avoidRecent = value.optBoolean("avoid_recent", true);
    }
    static Tuning read(MusicInterests interests, String id) {
      JSONObject value = interests.preferences.optJSONObject("mix_tune:" + id);
      return new Tuning(value == null ? new JSONObject() : value);
    }
  }

  static String day(long now) { return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(); }

  static String query(MixCatalogue.Mix mix, MusicInterests interests, Tuning tuning, String country, long now) {
    String language = tuning.language.isEmpty() ? interests.preferences.optString("language") : tuning.language;
    if (language.isEmpty()) language = country.equals("LK") ? "Sinhala Tamil" : RegionPolicy.name(country);
    language = language.substring(0, Math.min(60, language.length()));
    String month = day(now).substring(0, 7).replace('-', ' ');
    if (mix.id.equals("trending")) return language + " TikTok viral songs " + month;
    String mood = tuning.mood.isEmpty() ? mix.intent : tuning.mood;
    String artist = interests.seeds.isEmpty() ? "" : interests.seeds.get(0).artist;
    artist = artist.substring(0, Math.min(80, artist.length()));
    if (mix.id.equals("discover")) return language + " " + interests.preferences.optString("genres", "") + " songs discover new artists";
    if (mix.id.equals("daily")) return language + " " + mood + " " + interests.preferences.optString("genres", "") + " songs";
    // One themed artist query plus the broad mood pool below keeps mood and interests together.
    return language + " " + artist + " " + mood + " songs";
  }

  static List<Track> load(MixCatalogue.Mix mix, MusicInterests interests, PersonalDiscovery.Source source,
      String country, long now, Set<String> exclude) throws Exception {
    Tuning tuning = Tuning.read(interests, mix.id);
    List<Track> pool = new ArrayList<>();
    boolean station = mix.id.equals("daily") || mix.id.equals("discover");
    if (station) {
      // Rotate seed artists by local day, not on every redraw or play tap.
      List<Track> seeds = new ArrayList<>(interests.seeds);
      if (!seeds.isEmpty()) Collections.rotate(seeds, -Math.floorMod(day(now).hashCode(), seeds.size()));
      for (Track seed : seeds.subList(0, Math.min(2, seeds.size()))) {
        interrupted();
        try { pool.addAll(source.related(seed)); } catch (Exception e) { if (e instanceof InterruptedException) throw e; interrupted(); }
      }
    }
    interrupted();
    try { pool.addAll(mix.id.equals("trending") ? source.trending(query(mix, interests, tuning, country, now)) : source.search(query(mix, interests, tuning, country, now))); }
    catch (Exception e) { if (e instanceof InterruptedException) throw e; interrupted(); if (pool.isEmpty()) throw e; }
    if (!station && !mix.id.equals("trending") && pool.size() < 12) {
      String language = tuning.language.isEmpty() ? interests.preferences.optString("language") : tuning.language;
      interrupted();
      try { pool.addAll(source.search(language + " " + (tuning.mood.isEmpty() ? mix.intent : tuning.mood) + " songs")); }
      catch (Exception e) { if (e instanceof InterruptedException) throw e; interrupted(); }
    }
    if (mix.id.equals("daily")) pool.addAll(interests.favourites);
    List<Track> result = select(mix, interests, pool, tuning, now, exclude, 30);
    // Offline fallback only for familiar/daily mixes. Discovery never claims a known song is new.
    if (result.isEmpty() && !mix.id.equals("discover") && !mix.id.equals("trending"))
      result = select(mix, interests, interests.favourites, tuning, now, exclude, 30);
    return result;
  }

  private static void interrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
  }

  static List<Track> select(MixCatalogue.Mix mix, MusicInterests interests, List<Track> input,
      Tuning tuning, long now, Set<String> excluded, int limit) {
    Set<String> known = new HashSet<>(), recent = new HashSet<>(), ids = new HashSet<>(), keys = new HashSet<>();
    for (List<Track> source : Arrays.asList(interests.history, interests.favourites))
      for (Track t : source) known.add(RadioPolicy.songKey(t));
    Iterator<String> learned = interests.learned.keys();
    while (learned.hasNext()) {
      JSONObject entry = interests.learned.optJSONObject(learned.next());
      if (entry != null && entry.optJSONObject("track") != null)
        known.add(RadioPolicy.songKey(Track.from(entry.optJSONObject("track"))));
    }
    for (Track t : interests.history.subList(0, Math.min(12, interests.history.size()))) recent.add(RadioPolicy.songKey(t));
    List<Track> pool = new ArrayList<>(); Map<String, Integer> order = new HashMap<>();
    for (Track t : input) {
      String key = RadioPolicy.songKey(t);
      if (!RadioPolicy.music(t) || interests.blocked(t) || interests.bonus(t) < -20
          || excluded.contains(QueuePolicy.identity(t.url)) || excluded.contains(key)
          || mix.id.equals("discover") && known.contains(key)
          || tuning.avoidRecent && recent.contains(key)
          || !ids.add(QueuePolicy.identity(t.url)) || !keys.add(key)) continue;
      order.put(t.url, pool.size()); pool.add(t);
    }
    pool.sort((a,b) -> Double.compare(score(b, interests, tuning, mix, order.get(b.url), now),
        score(a, interests, tuning, mix, order.get(a.url), now)));
    List<Track> out = new ArrayList<>(); Map<String, Integer> count = new HashMap<>(); String last = "";
    int cap = tuning.familiarity == 2 ? 4 : 2;
    while (!pool.isEmpty() && out.size() < limit) {
      int next = -1;
      for (int i = 0; i < pool.size(); i++) {
        String artist = RadioPolicy.artist(pool.get(i));
        if (!artist.equals(last) && count.getOrDefault(artist, 0) < cap) { next = i; break; }
      }
      if (next < 0) for (int i = 0; i < pool.size(); i++)
        if (count.getOrDefault(RadioPolicy.artist(pool.get(i)), 0) < cap) { next = i; break; }
      if (next < 0) break;
      Track song = pool.remove(next); last = RadioPolicy.artist(song);
      count.put(last, count.getOrDefault(last, 0) + 1); out.add(song);
    }
    return out;
  }

  private static double score(Track t, MusicInterests interests, Tuning tuning, MixCatalogue.Mix mix, int at, long now) {
    int affinity = interests.bonus(t);
    double novelty = Math.floorMod((day(now) + RadioPolicy.songKey(t)).hashCode(), 101) / 100.0;
    // Exploration retains dislikes while reducing familiarity; trending keeps search order prominent.
    double weight = tuning.familiarity == 0 || mix.id.equals("discover") ? .12 : tuning.familiarity == 2 ? 1.5 : .7;
    if (mix.id.equals("trending")) weight *= .3;
    return 50 - at * .65 + (affinity < 0 ? affinity : affinity * weight) + novelty * (mix.id.equals("daily") ? 5 : 1);
  }
}
