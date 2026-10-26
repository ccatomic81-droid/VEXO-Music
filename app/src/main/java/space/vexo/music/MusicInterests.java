package space.vexo.music;

import android.content.Context;
import java.util.*;
import org.json.*;

/**
 * Ephemeral view of existing private data. Imported Google data is never copied into local history.
 */
final class MusicInterests {
  final List<Track> seeds, favourites, history, imported, cached;
  final Set<String> hidden;
  private final Set<String> seedHidden;
  final JSONObject preferences, learned;
  final boolean youtube;
  final String signature;
  private final Map<String, Integer> artists = new HashMap<>();
  private final JSONObject feedback;

  private MusicInterests(
      List<Track> liked,
      List<Track> history,
      List<Track> playlists,
      YouTubeTaste.Snapshot yt,
      JSONObject preferences,
      JSONObject learned,
      JSONObject feedback,
      Set<String> hidden,
      Set<String> recent) {
    this.preferences = preferences;
    this.feedback = feedback;
    this.learned = preferences.optBoolean("learn_listening", true) ? learned : new JSONObject();
    this.history = Collections.unmodifiableList(new ArrayList<>(history));
    this.seedHidden = new HashSet<>(hidden);
    this.hidden = new HashSet<>(hidden);
    this.hidden.addAll(recent);
    youtube = yt.connected;
    imported = yt.connected ? yt.sources() : Collections.emptyList();
    LinkedHashMap<String, Track> all = new LinkedHashMap<>();
    add(all, liked, 28);
    add(all, imported, 24);
    add(all, playlists, 14);
    if (!preferences.optBoolean("pause_history") && preferences.optBoolean("learn_listening", true))
      add(all, history.subList(0, Math.min(20, history.size())), 8);
    favourites = new ArrayList<>(liked);
    favourites.addAll(imported);
    cached = yt.connected ? yt.picks : Collections.emptyList();
    List<Track> ranked = new ArrayList<>(all.values());
    ranked.removeIf(t -> seedBlocked(t) || bonus(t) < 0);
    ranked.sort((a, b) -> Integer.compare(bonus(b), bonus(a)));
    LinkedHashSet<String> used = new LinkedHashSet<>();
    List<Track> diverse = new ArrayList<>();
    for (Track t : ranked)
      if (used.add(RadioPolicy.artist(t))) {
        diverse.add(t);
        if (diverse.size() == 6) break;
      }
    seeds = Collections.unmodifiableList(diverse);
    signature =
        yt.at
            + ":"
            + all.keySet()
            + ":"
            + preferences
            + ":"
            + this.learned
            + ":"
            + hidden
            + ":"
            + feedback;
  }

  static MusicInterests read(Context context) {
    LibraryStore library = new LibraryStore(context);
    SmartStore smart = new SmartStore(context);
    List<Track> playlists = new ArrayList<>();
    List<String> names = library.playlists();
    for (String name : names.subList(0, Math.min(8, names.size()))) {
      List<Track> songs = library.tracks("playlist:" + name);
      playlists.addAll(songs.subList(0, Math.min(30, songs.size())));
    }
    Set<String> blocked = new HashSet<>(new RadioStore(context).hidden());

    List<Track> explicit = new ArrayList<>(library.tracks("favourites"));
    JSONObject choices = smart.feedback();
    Iterator<String> keys = choices.keys();
    int extra = 0;
    while (keys.hasNext() && extra < 30) {
      JSONObject choice = choices.optJSONObject(keys.next());
      if (choice != null
          && "more".equals(choice.optString("value"))
          && choice.optJSONObject("track") != null) {
        explicit.add(Track.from(choice.optJSONObject("track")));
        extra++;
      }
    }
    MusicInterests result =
        new MusicInterests(
            explicit,
            library.tracks("history"),
            playlists,
            new YouTubeTaste(context).read(),
            smart.preferences(),
            smart.taste(),
            choices,
            blocked,
            SmartPolicy.blocked(smart, System.currentTimeMillis()));
    return result;
  }

  static MusicInterests of(
      List<Track> favourites,
      List<Track> history,
      List<Track> playlists,
      YouTubeTaste.Snapshot youtube,
      JSONObject prefs,
      JSONObject learned,
      Set<String> blocked) {
    return new MusicInterests(
        favourites,
        history,
        playlists,
        youtube,
        prefs,
        learned,
        new JSONObject(),
        blocked,
        Collections.emptySet());
  }

  private void add(Map<String, Track> all, List<Track> songs, int weight) {
    for (Track t : songs) {
      if (!RadioPolicy.music(t) || seedBlocked(t)) continue;
      all.putIfAbsent(QueuePolicy.identity(t.url), t);
      String artist = RadioPolicy.artist(t);
      if (!artist.isEmpty()) artists.put(artist, Math.max(weight, artists.getOrDefault(artist, 0)));
    }
  }

  private boolean seedBlocked(Track t) {
    JSONObject choice = feedback.optJSONObject(RadioPolicy.songKey(t));
    String value = choice == null ? "" : choice.optString("value");
    return seedHidden.contains(QueuePolicy.identity(t.url))
        || seedHidden.contains("song:" + RadioPolicy.songKey(t))
        || value.equals("never")
        || value.equals("break")
            && System.currentTimeMillis() - choice.optLong("at") < 7 * 86400000L;
  }

  boolean blocked(Track t) {
    return seedBlocked(t)
        || hidden.contains(QueuePolicy.identity(t.url))
        || hidden.contains("song:" + RadioPolicy.songKey(t));
  }

  int bonus(Track t) {
    int result = artists.getOrDefault(RadioPolicy.artist(t), 0);
    String text = RadioPolicy.normalize(t.title + " " + t.artist);
    for (String token : RadioPolicy.normalize(preferences.optString("artists")).split(" +"))
      if (token.length() > 2 && text.contains(token)) result += 10;
    JSONObject choice = feedback.optJSONObject(RadioPolicy.songKey(t));
    String value = choice == null ? "" : choice.optString("value");
    return result
        + (value.equals("less") ? -80 : value.equals("more") ? 40 : 0)
        + TastePolicy.bonus(t, learned, System.currentTimeMillis());
  }

  List<Track> rank(List<Track> input, boolean discover) {
    List<Track> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Map<String, Integer> order = new HashMap<>();
    for (Track t : input) {
      if (!RadioPolicy.music(t) || blocked(t) || !seen.add(RadioPolicy.songKey(t))) continue;
      order.put(t.url, out.size());
      out.add(t);
    }
    out.sort(
        (a, b) ->
            Double.compare(
                score(b, order.get(b.url), discover), score(a, order.get(a.url), discover)));
    return out;
  }

  private double score(Track t, int position, boolean discover) {
    int affinity = bonus(t);
    // Exploration keeps source relevance and learned dislikes; familiar artists get a smaller
    // boost.
    return 50 - position * .8 + (discover ? Math.min(5, affinity) : affinity);
  }

  List<Track> variety(List<Track> input, int limit) {
    List<Track> pool = rank(input, false), out = new ArrayList<>();
    Map<String, Integer> counts = new HashMap<>();
    String previous = "";
    while (!pool.isEmpty() && out.size() < limit) {
      int next = -1;
      for (int i = 0; i < pool.size(); i++) {
        String artist = RadioPolicy.artist(pool.get(i));
        if (!artist.equals(previous) && counts.getOrDefault(artist, 0) < 2) {
          next = i;
          break;
        }
      }
      if (next < 0)
        for (int i = 0; i < pool.size(); i++)
          if (counts.getOrDefault(RadioPolicy.artist(pool.get(i)), 0) < 2) {
            next = i;
            break;
          }
      if (next < 0) break;
      Track t = pool.remove(next);
      previous = RadioPolicy.artist(t);
      counts.put(previous, counts.getOrDefault(previous, 0) + 1);
      out.add(t);
    }
    return out;
  }

  String query() {
    String artists = preferences.optString("artists").trim();
    if (!artists.isEmpty()) return artists.substring(0, Math.min(120, artists.length())) + " music";
    String text =
        (preferences.optString("language")
                + " "
                + preferences.optString("genres")
                + " "
                + preferences.optString("mood"))
            .trim();
    return text.isEmpty() ? "" : text.substring(0, Math.min(160, text.length())) + " songs";
  }

  String description() {
    return youtube && !imported.isEmpty()
        ? "Based on your connected YouTube music and VEXO interests"
        : youtube
            ? "YouTube connected · building your taste from music you enjoy in VEXO"
            : "Based on your favourites, playlists and listening in VEXO";
  }
}
