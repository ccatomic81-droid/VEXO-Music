package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;

/** Repairs only replaceable discovery/stream/artwork caches, never the user's library or audio. */
final class AppRecovery {
  static void refresh(Context context) {
    context.getSharedPreferences("regional_feed", 0).edit().clear().apply();
    context.getSharedPreferences("music_mixes", 0).edit().clear().apply();
    OnlineMusic.resetTransientCaches();
    Visuals.clearArtwork();
  }

  static boolean recoverIfNeeded(Context context) {
    SharedPreferences prefs = context.getSharedPreferences("diagnostics", 0);
    if (!prefs.getBoolean("pending_recovery", false)) return false;
    // Consume once: a service/provider failure must not create an endless repair/restart loop.
    prefs.edit().putBoolean("pending_recovery", false).apply();
    if (!context.getSharedPreferences("settings", 0).getBoolean("auto_repair", true)) return false;
    refresh(context);
    prefs.edit().putLong("recovered_at", System.currentTimeMillis()).apply();
    return true;
  }
}
