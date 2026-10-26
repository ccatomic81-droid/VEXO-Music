package space.vexo.music;

import java.util.*;
import org.json.*;

/** Honest metadata-based controls; does not invent audio features, popularity or lyric matches. */
final class SmartPolicy {
  static String normalize(String text) {
    return RadioPolicy.normalize(text);
  }

  static final Map<String, String> ALIASES = new LinkedHashMap<>();

  static {
    ALIASES.put("sinhala", "සිංහල");
    ALIASES.put("sindu", "සිංදු");
    ALIASES.put("tamil", "தமிழ்");
    ALIASES.put("paadal", "பாடல்");
    ALIASES.put("bathi gee", "බැති ගී");
    ALIASES.put("adaraya", "ආදරය");
  }

  static List<String> variants(String query) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    out.add(query.trim());
    String q = normalize(query);
    for (Map.Entry<String, String> a : ALIASES.entrySet())
      if (q.contains(a.getKey())) out.add(q.replace(a.getKey(), a.getValue()));
    return new ArrayList<>(out);
  }

  static List<Track> find(JSONArray events, String clue, long from, long to) {
    String query = normalize(clue);
    LinkedHashMap<String, Track> matches = new LinkedHashMap<>();
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (e == null || e.optLong("at") < from || e.optLong("at") > to) continue;
      JSONObject track = e.optJSONObject("track");
      if (track == null) continue;
      Track t = Track.from(track);
      String hay = normalize(t.title + " " + t.artist);
      boolean match = query.isEmpty();
      for (String variant : variants(query)) {
        String[] tokens = normalize(variant).split(" +");
        boolean all = true;
        for (String token : tokens)
          if (!hay.contains(token) && !nearToken(hay, token)) {
            all = false;
            break;
          }
        if (all) match = true;
      }
      if (match) matches.putIfAbsent(QueuePolicy.identity(t.url), t);
    }
    return new ArrayList<>(matches.values());
  }

  private static boolean nearToken(String hay, String token) {
    if (token.length() < 5) return false;
    for (String word : hay.split(" +")) if (distance(word, token) <= 1) return true;
    return false;
  }

  private static int distance(String a, String b) {
    if (Math.abs(a.length() - b.length()) > 1) return 2;
    int[] prev = new int[b.length() + 1];
    for (int j = 0; j < prev.length; j++) prev[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      int[] next = new int[b.length() + 1];
      next[0] = i;
      for (int j = 1; j <= b.length(); j++)
        next[j] =
            Math.min(
                Math.min(next[j - 1] + 1, prev[j] + 1),
                prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
      prev = next;
    }
    return prev[b.length()];
  }

  static String query(JSONObject p, String command) {
    String q = command == null ? "" : command.trim();
    if (q.length() > 200) q = q.substring(0, 200);
    // Preserve an exact artist/title clue. Settings are search hints, not inferred audio labels.
    return (q
            + " "
            + p.optString("genres")
            + " "
            + p.optString("language")
            + " "
            + p.optString("mood")
            + " "
            + p.optString("energy")
            + " "
            + p.optString("artists")
            + " music")
        .trim()
        .replaceAll(" +", " ");
  }

  static Set<String> blocked(SmartStore store, long now) {
    Set<String> out = new HashSet<>();
    JSONObject p = store.preferences();
    long hours = Math.max(0, Math.min(720, p.optInt("repeat_hours", 24)));
    JSONArray events = store.events();
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (e != null && hours > 0 && e.optLong("at") >= now - hours * 3600000L) {
        Track t = Track.from(e.optJSONObject("track"));
        out.add(QueuePolicy.identity(t.url));
        out.add("song:" + RadioPolicy.songKey(t));
      }
    }
    JSONObject f = store.feedback();
    Iterator<String> keys = f.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      JSONObject v = f.optJSONObject(k);
      if (v == null) continue;
      String value = v.optString("value");
      if (value.equals("never") || (value.equals("break") && now - v.optLong("at") < 7 * 86400000L))
        out.add("song:" + k);
    }
    return out;
  }

  static List<Track> rank(
      List<Track> input, SmartStore store, List<Track> history, boolean discovery) {
    JSONObject feedback = store.feedback(), prefs = store.preferences();
    Set<String> known = new HashSet<>();
    for (Track t : history) known.add(RadioPolicy.artist(t));
    Set<String> blocked = blocked(store, System.currentTimeMillis());
    List<Track> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Track t : input) {
      String key = RadioPolicy.songKey(t);
      if (!RadioPolicy.music(t)
          || blocked.contains(QueuePolicy.identity(t.url))
          || blocked.contains("song:" + key)
          || !seen.add(key)) continue;
      result.add(t);
    }
    JSONObject taste = prefs.optBoolean("learn_listening", true) ? store.taste() : new JSONObject();
    long now = System.currentTimeMillis();
    Map<String, Integer> scores = new HashMap<>();
    for (Track t : result)
      scores.put(
          t.url,
          score(t, feedback, known, prefs, discovery)
              + TastePolicy.bonus(t, taste, now)
              + PureSong.audioBonus(t, prefs));
    result.sort((a, b) -> Integer.compare(scores.get(b.url), scores.get(a.url)));
    return result;
  }

  private static int score(
      Track t, JSONObject feedback, Set<String> known, JSONObject p, boolean discovery) {
    JSONObject f = feedback.optJSONObject(RadioPolicy.songKey(t));
    String v = f == null ? "" : f.optString("value");
    int score = v.equals("more") ? 30 : v.equals("less") ? -30 : 0;
    Iterator<String> feedbackKeys = feedback.keys();
    while (feedbackKeys.hasNext()) {
      JSONObject other = feedback.optJSONObject(feedbackKeys.next());
      if (other != null
          && other.optString("value").equals("more")
          && other.optJSONObject("track") != null
          && RadioPolicy.artist(Track.from(other.optJSONObject("track")))
              .equals(RadioPolicy.artist(t))) score += 10;
    }
    boolean fresh = !known.contains(RadioPolicy.artist(t));
    if (fresh) score += discovery ? 100 : p.optInt("discovery", 50) / 2;
    String text = normalize(t.title + " " + t.artist);
    for (String token : normalize(p.optString("artists")).split(" +"))
      if (!token.isEmpty() && text.contains(token)) score += 10;
    return score;
  }

  static List<Track> shuffle(List<Track> songs, SmartStore store) {
    Set<String> excluded = blocked(store, System.currentTimeMillis()), seen = new HashSet<>();
    List<Track> pool = new ArrayList<>(), out = new ArrayList<>();
    for (Track t : songs)
      if (!excluded.contains(QueuePolicy.identity(t.url))
          && !excluded.contains("song:" + RadioPolicy.songKey(t))
          && seen.add(RadioPolicy.songKey(t))) pool.add(t);
    Collections.shuffle(pool);
    String previous = "";
    while (!pool.isEmpty()) {
      int choose = 0;
      for (int i = 0; i < pool.size(); i++)
        if (!RadioPolicy.artist(pool.get(i)).equals(previous)) {
          choose = i;
          break;
        }
      Track next = pool.remove(choose);
      out.add(next);
      previous = RadioPolicy.artist(next);
    }
    return out;
  }

  static List<Track> forgotten(List<Track> favourites, JSONArray events, long now) {
    Set<String> recent = new HashSet<>();
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (e != null && e.optLong("at") > now - 30 * 86400000L)
        recent.add(QueuePolicy.identity(Track.from(e.optJSONObject("track")).url));
    }
    List<Track> out = new ArrayList<>();
    for (Track t : favourites) if (!recent.contains(QueuePolicy.identity(t.url))) out.add(t);
    return out;
  }
}
