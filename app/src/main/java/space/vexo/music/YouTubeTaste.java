package space.vexo.music;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional Google data stays separate from the library and cloud sync. No OAuth tokens here. */
final class YouTubeTaste {
  static final long MAX_AGE = 28L * 24 * 60 * 60 * 1000;
  private final PrivateStore prefs;

  YouTubeTaste(Context context) {
    prefs = new PrivateStore(context, "youtube_taste");
  }

  static final class Snapshot {
    final boolean connected;
    final long at;
    final List<Track> likes, picks, playlistSongs;
    final boolean playlistEnabled;

    Snapshot(boolean connected, long at, List<Track> likes, List<Track> picks) {
      this(connected, at, likes, picks, Collections.emptyList(), false);
    }

    Snapshot(
        boolean connected,
        long at,
        List<Track> likes,
        List<Track> picks,
        List<Track> playlistSongs,
        boolean playlistEnabled) {
      this.connected = connected;
      this.at = at;
      this.likes = Collections.unmodifiableList(new ArrayList<>(likes));
      this.picks = Collections.unmodifiableList(new ArrayList<>(picks));
      this.playlistSongs = Collections.unmodifiableList(new ArrayList<>(playlistSongs));
      this.playlistEnabled = playlistEnabled;
    }

    List<Track> sources() {
      java.util.LinkedHashMap<String, Track> all = new java.util.LinkedHashMap<>();
      for (Track t : likes) all.put(t.url, t);
      if (playlistEnabled) for (Track t : playlistSongs) all.putIfAbsent(t.url, t);
      return new ArrayList<>(all.values());
    }

    static Snapshot empty() {
      return new Snapshot(false, 0, Collections.emptyList(), Collections.emptyList());
    }
  }

  Snapshot read() {
    return read(System.currentTimeMillis());
  }

  Snapshot read(long now) {
    try {
      String raw = prefs.getString("snapshot", "");
      if (raw.isEmpty()) return Snapshot.empty();
      JSONObject data = new JSONObject(raw);
      long at = data.optLong("at");
      if (at <= 0 || at > now || now - at >= MAX_AGE) {
        clear();
        return Snapshot.empty();
      }
      return new Snapshot(
          true,
          at,
          tracks(data.optJSONArray("likes"), 100),
          tracks(data.optJSONArray("picks"), 10),
          tracks(data.optJSONArray("playlistSongs"), 100),
          data.optBoolean("playlistEnabled", false));
    } catch (IllegalStateException unavailable) {
      throw unavailable;
    } catch (Exception invalid) {
      clear();
      return Snapshot.empty();
    }
  }

  private static List<Track> tracks(JSONArray values, int limit) {
    List<Track> out = new ArrayList<>();
    if (values != null)
      for (int i = 0; i < Math.min(values.length(), limit); i++) {
        JSONObject item = values.optJSONObject(i);
        if (item == null) continue;
        Track track = Track.from(item);
        if (YouTubeMusic.valid(track)) out.add(track);
      }
    return out;
  }

  void save(List<Track> likes, List<Track> picks, long now) {
    save(likes, picks, Collections.emptyList(), false, now);
  }

  void save(
      List<Track> likes, List<Track> picks, List<Track> playlists, boolean enabled, long now) {
    try {
      JSONObject data =
          new JSONObject()
              .put("at", now)
              .put("playlistSongs", json(enabled ? playlists : Collections.emptyList(), 100))
              .put("playlistEnabled", enabled)
              .put("likes", json(likes, 100))
              .put("picks", json(picks, 10));
      prefs.edit().putString("snapshot", data.toString()).apply();
    } catch (org.json.JSONException invalid) {
      throw new IllegalStateException("Couldn’t save your YouTube music.", invalid);
    }
  }

  private static JSONArray json(List<Track> values, int limit) {
    JSONArray out = new JSONArray();
    for (Track track : values) {
      if (out.length() == limit) break;
      if (YouTubeMusic.valid(track)) out.put(track.json());
    }
    return out;
  }

  void clear() {
    prefs.edit().clear().apply();
  }
}
