package space.vexo.music;

import android.content.Context;
import java.util.*;
import org.json.*;

/** Queue metadata and position only; reopening the app never starts playback by itself. */
public final class QueueStore {
  private final android.content.SharedPreferences prefs;

  public QueueStore(Context c) {
    prefs = c.getSharedPreferences("playback_queue", 0);
  }

  public static final class Snapshot {
    public final List<Track> tracks;
    public final int index;
    public final long position;

    Snapshot(List<Track> tracks, int index, long position) {
      this.tracks = tracks;
      this.index = index;
      this.position = position;
    }
  }

  public void save(List<Track> tracks, int index, long position) {
    JSONArray a = new JSONArray();
    for (Track t : tracks.subList(0, Math.min(QueuePolicy.MAX_ITEMS, tracks.size())))
      a.put(t.json());
    prefs
        .edit()
        .putString("songs", a.toString())
        .putInt("index", Math.max(0, index))
        .putLong("position", Math.max(0, position))
        .apply();
  }

  public Snapshot read() {
    List<Track> tracks = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("songs", "[]"));
      for (int i = 0; i < a.length() && i < QueuePolicy.MAX_ITEMS; i++) {
        Track t = Track.from(a.getJSONObject(i));
        if (t.url.startsWith("https://") || t.url.startsWith("content://")) tracks.add(t);
      }
    } catch (Exception e) {
      tracks.clear();
    }
    return new Snapshot(
        tracks,
        Math.max(0, Math.min(prefs.getInt("index", 0), tracks.size() - 1)),
        Math.max(0, prefs.getLong("position", 0)));
  }
}
