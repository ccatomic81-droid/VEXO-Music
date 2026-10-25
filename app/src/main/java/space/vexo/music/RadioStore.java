package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Device-only tuning and explicit negative feedback. Never sends a taste profile to a server. */
public final class RadioStore {
  private final SharedPreferences prefs;

  public RadioStore(Context c) {
    prefs = c.getSharedPreferences("radio", 0);
  }

  public RadioPolicy.Mode mode() {
    return RadioPolicy.Mode.read(prefs.getString("mode", "BALANCED"));
  }

  public void mode(RadioPolicy.Mode value) {
    prefs.edit().putString("mode", value.name()).apply();
  }

  public Track anchor() {
    try {
      return Track.from(new JSONObject(prefs.getString("anchor", "")));
    } catch (Exception ignored) {
      return null;
    }
  }

  public void anchor(Track t) {
    if (t == null) prefs.edit().remove("anchor").apply();
    else prefs.edit().putString("anchor", t.json().toString()).apply();
  }

  public Set<String> hidden() {
    Set<String> values = new LinkedHashSet<>(prefs.getStringSet("hidden", Collections.emptySet()));
    for (String key : prefs.getStringSet("hidden_keys", Collections.emptySet()))
      values.add("song:" + key);
    return values;
  }

  public int hiddenCount() {
    return prefs.getStringSet("hidden", Collections.emptySet()).size();
  }

  public void hide(Track t) {
    Set<String> ids = new LinkedHashSet<>(prefs.getStringSet("hidden", Collections.emptySet()));
    Set<String> keys =
        new LinkedHashSet<>(prefs.getStringSet("hidden_keys", Collections.emptySet()));
    ids.add(QueuePolicy.identity(t.url));
    keys.add(RadioPolicy.songKey(t));
    while (ids.size() > 500) ids.remove(ids.iterator().next());
    while (keys.size() > 500) keys.remove(keys.iterator().next());
    prefs.edit().putStringSet("hidden", ids).putStringSet("hidden_keys", keys).apply();
  }

  public void clearFeedback() {
    prefs.edit().remove("hidden").remove("hidden_keys").apply();
  }

  public String describe() {
    Track seed = anchor();
    return (seed == null ? "Autoplay" : "Radio · " + seed.title) + " · " + mode().label;
  }
}
