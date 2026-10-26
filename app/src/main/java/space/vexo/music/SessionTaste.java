package space.vexo.music;

import java.util.*;
import org.json.*;

/** Short-lived, on-device session evidence from audible listening, never inferred audio moods. */
final class SessionTaste {
  static final long WINDOW = 2 * 60 * 60 * 1000L;

  static JSONArray record(JSONArray old, Track track, long ms, boolean skip, long now)
      throws JSONException {
    JSONArray out = new JSONArray();
    boolean accepted = RadioPolicy.music(track)
        && (ms >= 30000 || skip && ms >= 2000 && ms < 20000);
    if (accepted)
      out.put(new JSONObject().put("track", track.json()).put("at", now)
          .put("value", skip && ms < 20000 ? -1 : 1));
    for (int i = 0; i < old.length() && out.length() < 12; i++) {
      JSONObject e = old.optJSONObject(i);
      if (valid(e, now) && e.optJSONObject("track") != null
          && (!accepted || !RadioPolicy.songKey(track).equals(
              RadioPolicy.songKey(Track.from(e.getJSONObject("track"))))))
        out.put(e);
    }
    return out;
  }

  private static boolean valid(JSONObject e, long now) {
    return e != null && e.optLong("at") <= now && now - e.optLong("at") < WINDOW;
  }

  static List<Track> seeds(JSONArray events, long now) {
    List<Track> out = new ArrayList<>();
    Set<String> artists = new HashSet<>();
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (!valid(e, now) || e.optInt("value") <= 0 || e.optJSONObject("track") == null) continue;
      Track t = Track.from(e.optJSONObject("track"));
      if (RadioPolicy.music(t) && artists.add(RadioPolicy.artist(t))) out.add(t);
      if (out.size() == 2) break;
    }
    return out;
  }

  static int bonus(Track track, JSONArray events, long now) {
    double score = 0;
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.optJSONObject(i);
      if (!valid(e, now) || e.optJSONObject("track") == null) continue;
      Track recent = Track.from(e.optJSONObject("track"));
      boolean same = RadioPolicy.songKey(track).equals(RadioPolicy.songKey(recent));
      if (same || !RadioPolicy.artist(track).isEmpty()
          && RadioPolicy.artist(track).equals(RadioPolicy.artist(recent)))
        score += (same && e.optInt("value") < 0 ? 60 : 16) * e.optInt("value")
            * Math.pow(.5, (now - e.optLong("at")) / (30.0 * 60000));
    }
    return (int) Math.round(Math.max(-60, Math.min(32, score)));
  }
}
