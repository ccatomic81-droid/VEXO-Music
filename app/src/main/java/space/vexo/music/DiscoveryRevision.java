package space.vexo.music;

import android.content.Context;

/** Non-personal cache generation; listening details remain in encrypted storage. */
final class DiscoveryRevision {
  static synchronized void changed(Context c) {
    android.content.SharedPreferences p = c.getSharedPreferences("discovery_revision", 0);
    p.edit().putLong("value", p.getLong("value", 0) + 1).apply();
  }
  static long read(Context c) { return c.getSharedPreferences("discovery_revision", 0).getLong("value", 0); }
}
