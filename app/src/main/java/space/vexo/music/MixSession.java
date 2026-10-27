package space.vexo.music;

import android.content.Context;

/** Queue context, independent from whichever mix is currently being browsed. */
final class MixSession {
  static MixCatalogue.Mix active(Context c) {
    return MixCatalogue.find(c.getSharedPreferences("playback_state", 0).getString("discovery_mix", ""), country(c));
  }
  static String country(Context c) {
    String country = c.getSharedPreferences("playback_state", 0).getString("discovery_country", "");
    return RegionPolicy.valid(country) ? country : RegionPolicy.current(c);
  }
  static void set(Context c, String id, String country) {
    if (!RegionPolicy.valid(country) || MixCatalogue.find(id, country) == null) { id = ""; country = ""; }
    c.getSharedPreferences("playback_state", 0).edit().putString("discovery_mix", id).putString("discovery_country", country).apply();
  }
  static void clear(Context c) { set(c, "", ""); }
}
