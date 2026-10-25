package space.vexo.music;

import java.util.*;
import org.json.*;

/**
 * Small, decaying on-device preferences; explicit feedback and manual queues stay authoritative.
 */
final class TastePolicy {
  private static final long DAY = 86400000L;

  static void record(
      JSONObject state, Track t, long listened, boolean manualSkip, boolean failed, long now) {
    if (t == null || failed || listened < 2000 || RadioPolicy.songKey(t).equals("|")) return;
    String key = RadioPolicy.songKey(t);
    JSONObject old = state.optJSONObject(key);
    if (old != null && now - old.optLong("at") < 30 * 60000L) return;
    double delta =
        manualSkip && listened < 20000
            ? -1.5
            : listened >= Math.max(30000, Math.min(120000, t.duration * 800L))
                ? 4
                : listened >= 30000 ? 1 : 0;
    if (delta == 0) return;
    try {
      double previous = old == null ? 0 : decayed(old, now);
      state.put(
          key,
          new JSONObject()
              .put("artist", RadioPolicy.artist(t))
              .put("score", Math.max(-20, Math.min(20, previous + delta)))
              .put("at", now));
      while (state.length() > 300) {
        String oldest = null;
        long time = Long.MAX_VALUE;
        Iterator<String> it = state.keys();
        while (it.hasNext()) {
          String k = it.next();
          long at = state.optJSONObject(k).optLong("at");
          if (at < time) {
            time = at;
            oldest = k;
          }
        }
        if (oldest == null) break;
        state.remove(oldest);
      }
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static double decayed(JSONObject entry, long now) {
    return entry.optDouble("score")
        * Math.pow(.5, Math.max(0, now - entry.optLong("at")) / (30.0 * DAY));
  }

  static int bonus(Track t, JSONObject state, long now) {
    JSONObject own = state.optJSONObject(RadioPolicy.songKey(t));
    double score = own == null ? 0 : decayed(own, now), artist = 0;
    int count = 0;
    String name = RadioPolicy.artist(t);
    Iterator<String> it = state.keys();
    if (!name.isEmpty())
      while (it.hasNext()) {
        JSONObject e = state.optJSONObject(it.next());
        if (e != null && name.equals(e.optString("artist"))) {
          artist += decayed(e, now);
          count++;
        }
      }
    return (int)
        Math.round(score + (count == 0 ? 0 : Math.max(-8, Math.min(8, artist / count * .5))));
  }

  static String reason(Track t, JSONObject state, long now) {
    return bonus(t, state, now) > 0
        ? "Matches music you listened to here"
        : "Related music with artist variety";
  }
}
