package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;

/** Startup defaults for new installs and settings absent on older versions. */
final class AppDefaults {
  static final int CROSSFADE_SECONDS = 4;

  static void initialize(Context c) {
    SharedPreferences prefs = c.getSharedPreferences("settings", 0);
    SharedPreferences.Editor edit = prefs.edit();
    boolean changed = false;
    if (!prefs.contains("update_alerts")) {
      edit.putBoolean("update_alerts", true);
      changed = true;
    }
    if (!prefs.contains("push_release_alerts")) {
      edit.putBoolean("push_release_alerts", true);
      changed = true;
    }
    if (!prefs.contains("crossfade")) {
      edit.putInt("crossfade", CROSSFADE_SECONDS);
      changed = true;
    }
    if (changed) edit.apply();
  }

  static boolean updateAlerts(Context c) {
    return c.getSharedPreferences("settings", 0).getBoolean("update_alerts", true);
  }

  static int crossfadeSeconds(Context c) {
    return CrossfadeEngine.clampSeconds(
        c.getSharedPreferences("settings", 0).getInt("crossfade", CROSSFADE_SECONDS));
  }
}
