package space.vexo.music;

import java.util.*;
import org.json.*;

/** Provider-separated artist credits; a band's punctuation is part of its name. */
final class ArtistCredit {
  final String id, name;

  ArtistCredit(String id, String name) {
    this.id = id != null && id.matches("UC[A-Za-z0-9_-]{22}") ? id : "";
    this.name = SearchArtists.name(name);
  }

  JSONObject json() {
    JSONObject o = new JSONObject();
    try {
      o.put("id", id).put("name", name);
    } catch (JSONException ignored) {
    }
    return o;
  }

  static List<ArtistCredit> read(JSONArray a) {
    List<ArtistCredit> out = new ArrayList<>();
    if (a != null)
      for (int i = 0; i < Math.min(8, a.length()); i++) {
        JSONObject o = a.optJSONObject(i);
        if (o != null) {
          ArtistCredit credit = new ArtistCredit(o.optString("id"), o.optString("name"));
          if (!credit.name.isEmpty()) out.add(credit);
        }
      }
    return out;
  }

  static JSONArray json(List<ArtistCredit> credits) {
    JSONArray out = new JSONArray();
    for (ArtistCredit credit : credits) out.put(credit.json());
    return out;
  }
}
