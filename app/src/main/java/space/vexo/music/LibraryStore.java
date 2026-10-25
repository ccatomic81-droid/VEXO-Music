package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public final class LibraryStore {
  private final SharedPreferences prefs;

  public LibraryStore(Context c) {
    prefs = c.getSharedPreferences("library", Context.MODE_PRIVATE);
  }

  public synchronized List<Track> tracks(String key) {
    List<Track> out = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString(key, "[]"));
      for (int i = 0; i < a.length(); i++) out.add(Track.from(a.getJSONObject(i)));
    } catch (Exception ignored) {
    }
    return out;
  }

  public synchronized void save(String key, List<Track> tracks) {
    JSONArray a = new JSONArray();
    for (Track t : tracks) a.put(t.json());
    prefs.edit().putString(key, a.toString()).apply();
  }

  public synchronized boolean contains(String key, Track t) {
    for (Track x : tracks(key)) if (x.url.equals(t.url)) return true;
    return false;
  }

  public synchronized void toggle(String key, Track t) {
    List<Track> a = tracks(key);
    boolean found = a.removeIf(x -> x.url.equals(t.url));
    if (!found) a.add(0, t);
    save(key, a);
  }

  public synchronized void recent(Track t) {
    List<Track> a = tracks("history");
    a.removeIf(x -> x.url.equals(t.url));
    a.add(0, t);
    if (a.size() > 100) a = a.subList(0, 100);
    save("history", a);
  }

  public List<String> playlists() {
    List<String> names = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("playlists", "[]"));
      for (int i = 0; i < a.length(); i++) names.add(a.getString(i));
    } catch (Exception ignored) {
    }
    return names;
  }

  public void createPlaylist(String name) {
    List<String> a = playlists();
    if (!a.contains(name)) a.add(name);
    prefs.edit().putString("playlists", new JSONArray(a).toString()).apply();
  }

  public void deletePlaylist(String name) {
    List<String> a = playlists();
    a.remove(name);
    prefs
        .edit()
        .putString("playlists", new JSONArray(a).toString())
        .remove("playlist:" + name)
        .apply();
  }

  public void addToPlaylist(String name, Track t) {
    List<Track> a = tracks("playlist:" + name);
    if (a.stream().noneMatch(x -> x.url.equals(t.url))) a.add(t);
    save("playlist:" + name, a);
  }
}
