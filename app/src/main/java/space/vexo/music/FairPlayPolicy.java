package space.vexo.music;

import java.util.*;
import org.json.*;

/** One request per person per round, preserving each person's request order. Device-local. */
final class FairPlayPolicy {
  static final class Request {
    final String id, person;
    final Track track;

    Request(String id, String person, Track track) {
      this.id = id;
      this.person = person;
      this.track = track;
    }
  }

  static List<Request> requests(SmartStore s) {
    List<Request> out = new ArrayList<>();
    JSONArray a = s.preferences().optJSONArray("group_requests");
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.optJSONObject(i);
        if (o != null)
          out.add(
              new Request(
                  o.optString("id"), o.optString("person"), Track.from(o.optJSONObject("track"))));
      }
    return out;
  }

  static void add(SmartStore s, String person, Track t) {
    List<Request> a = requests(s);
    if (a.size() >= 100)
      throw new IllegalStateException("This session has 100 requests. Remove a request first.");
    a.add(new Request(UUID.randomUUID().toString(), person, t));
    save(s, a);
  }

  static void remove(SmartStore s, String id) {
    List<Request> a = requests(s);
    a.removeIf(r -> r.id.equals(id));
    save(s, a);
  }

  private static void save(SmartStore s, List<Request> a) {
    JSONArray out = new JSONArray();
    for (Request r : a)
      try {
        out.put(
            new JSONObject().put("id", r.id).put("person", r.person).put("track", r.track.json()));
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    s.preference("group_requests", out);
  }

  static List<Track> rotate(List<Request> requests) {
    Map<String, ArrayDeque<Track>> people = new LinkedHashMap<>();
    for (Request r : requests)
      people
          .computeIfAbsent(r.person.trim().toLowerCase(Locale.ROOT), k -> new ArrayDeque<>())
          .add(r.track);
    List<Track> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean more = true;
    while (more) {
      more = false;
      for (ArrayDeque<Track> songs : people.values()) {
        while (!songs.isEmpty()) {
          more = true;
          Track t = songs.remove();
          if (seen.add(QueuePolicy.identity(t.url))) {
            out.add(t);
            break;
          }
        }
      }
    }
    return out;
  }
}
