package space.vexo.music;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public final class LibraryStore {
  private final PrivateStore prefs;
  private final Context context;
  private final SmartStore smart;

  LibraryStore(Context c, SmartStore smart) {
    context = c.getApplicationContext();
    this.smart = smart;
    prefs = new PrivateStore(c, "library");
  }

  private void checkpoint(String name, String reason) {
    smart.snapshot(name, tracks("playlist:" + name), reason);
  }

  public LibraryStore(Context c) {
    context = c.getApplicationContext();
    smart = new SmartStore(c);
    prefs = new PrivateStore(c, "library");
  }

  public synchronized List<Track> tracks(String key) {
    List<Track> out = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString(key, "[]"));
      for (int i = 0; i < a.length(); i++) out.add(Track.from(a.getJSONObject(i)));
    } catch (IllegalStateException unavailable) {
      throw unavailable;
    } catch (Exception ignored) {
    }
    return out;
  }

  public synchronized void save(String key, List<Track> tracks) {
    if (key.startsWith("playlist:") && prefs.contains(key))
      checkpoint(key.substring(9), "Before edit / undo");
    JSONArray a = new JSONArray();
    for (Track t : tracks) a.put(t.json());
    prefs.edit().putString(key, a.toString()).apply();
    DiscoveryRevision.changed(context);
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
    } catch (IllegalStateException unavailable) {
      throw unavailable;
    } catch (Exception ignored) {
    }
    return names;
  }

  public void createPlaylist(String name) {
    List<String> a = playlists();
    if (!a.contains(name)) a.add(name);
    prefs.edit().putString("playlists", new JSONArray(a).toString()).apply();
  }

  public synchronized void deletePlaylist(String name) {
    if (playlists().contains(name)) checkpoint(name, "Before deletion");
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

  public synchronized boolean renamePlaylist(String oldName, String newName) {
    String name = newName == null ? "" : newName.trim();
    List<String> names = playlists();
    if (!names.contains(oldName) || name.isEmpty() || name.length() > 60) return false;
    for (String existing : names)
      if (!existing.equals(oldName) && existing.equalsIgnoreCase(name)) return false;
    if (oldName.equals(name)) return true;
    checkpoint(oldName, "Before rename to " + name);
    JSONArray songs = new JSONArray();
    for (Track t : tracks("playlist:" + oldName)) songs.put(t.json());
    names.set(names.indexOf(oldName), name);
    prefs
        .edit()
        .putString("playlists", new JSONArray(names).toString())
        .remove("playlist:" + oldName)
        .putString("playlist:" + name, songs.toString())
        .apply();
    return true;
  }

  synchronized void mergeCollections(List<Track> list, java.util.Map<String, List<Track>> map)
      throws java.io.IOException {
    PrivateStore.Editor editorEdit = this.prefs.edit();
    List<String> listPlaylists = playlists();
    for (java.util.Map.Entry<String, List<Track>> entry : map.entrySet()) {
      if (listPlaylists.contains(entry.getKey())) {
        checkpoint(entry.getKey(), "Before cloud merge");
      } else {
        listPlaylists.add(entry.getKey());
      }
      JSONArray jSONArray = new JSONArray();
      java.util.Iterator<Track> it = entry.getValue().iterator();
      while (it.hasNext()) {
        jSONArray.put(it.next().json());
      }
      editorEdit.putString("playlist:" + entry.getKey(), jSONArray.toString());
    }
    JSONArray jSONArray2 = new JSONArray();
    java.util.Iterator<Track> it2 = list.iterator();
    while (it2.hasNext()) {
      jSONArray2.put(it2.next().json());
    }
    if (!editorEdit
        .putString("playlists", new JSONArray((java.util.Collection) listPlaylists).toString())
        .putString("favourites", jSONArray2.toString())
        .commit()) {
      throw new java.io.IOException(
          "Couldn’t safely save synced collections. Your existing library was kept.");
    }
  }

  /** Saving the same mix twice reuses its copy; never overwrite a user's edited playlist. */
  public synchronized String savePlaylistCopy(String base, List<Track> songs) {
    String name = base.trim();
    if (name.length() > 50) name = name.substring(0, 50);
    if (name.isEmpty()) name = "My mix";
    String candidate = name;
    for (int count = 2; playlists().contains(candidate); count++) {
      List<Track> existing = tracks("playlist:" + candidate);
      if (existing.size() == songs.size()) {
        boolean same = true;
        for (int i = 0; i < songs.size(); i++)
          if (!existing.get(i).url.equals(songs.get(i).url)) {
            same = false;
            break;
          }
        if (same) return candidate;
      }
      candidate = name + " (" + count + ")";
    }
    createPlaylist(candidate);
    java.util.LinkedHashMap<String, Track> unique = new java.util.LinkedHashMap<>();
    for (Track t : songs) unique.putIfAbsent(t.url, t);
    save("playlist:" + candidate, new ArrayList<>(unique.values()));
    return candidate;
  }
}
