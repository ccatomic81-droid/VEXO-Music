package space.vexo.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.function.BooleanSupplier;

/** One invitation for this upgrade cohort. A refusal is never reset by a later app version. */
final class NotificationPermissionPrompt {
  static final String SEEN = "notification_permission_intro_v1_seen";
  private final Activity activity;
  private final BooleanSupplier ready;
  private final Runnable requestPermission;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final SharedPreferences settings;
  private final Runnable show = this::showWhenReady;
  private AlertDialog dialog;
  private boolean resumed, closed;

  NotificationPermissionPrompt(Activity activity, BooleanSupplier ready, Runnable requestPermission) {
    this.activity = activity;
    this.ready = ready;
    this.requestPermission = requestPermission;
    settings = activity.getSharedPreferences("settings", 0);
  }

  static boolean enabled(Activity activity) {
    return (Build.VERSION.SDK_INT < 33
            || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
        && activity.getSystemService(NotificationManager.class).areNotificationsEnabled();
  }

  boolean eligible() {
    return !settings.getBoolean(SEEN, false)
        && !settings.getBoolean("notifications_asked", false)
        && !enabled(activity);
  }

  void resume() {
    resumed = true;
    if (enabled(activity)) settings.edit().putBoolean(SEEN, true).apply();
    schedule();
  }

  void pause() {
    resumed = false;
    main.removeCallbacks(show);
  }

  void schedule() {
    main.removeCallbacks(show);
    if (!closed && resumed && eligible()) main.postDelayed(show, 900);
  }

  void showWhenReady() {
    if (closed || !resumed || activity.isFinishing() || activity.isDestroyed()
        || !activity.hasWindowFocus() || !ready.getAsBoolean() || !eligible()
        || (dialog != null && dialog.isShowing())) return;
    AlertDialog candidate = new VexoDialogs.Builder(activity)
        .setTitle("Enable notifications?")
        .setMessage("Update alerts are on by default. Allow Android notifications to receive them. You can change this anytime in Settings.")
        .setPositiveButton("Enable", (unused, which) -> enable())
        .setNegativeButton("Not now", null)
        .create();
    try {
      candidate.show();
      dialog = candidate;
      // Persist only after a real invitation is visible. Back/outside dismissal also counts.
      if (!settings.edit().putBoolean(SEEN, true).commit()) candidate.dismiss();
    } catch (android.view.WindowManager.BadTokenException unavailable) {
      // Activity teardown must not consume an invitation that was never shown.
    }
  }

  private void enable() {
    if (Build.VERSION.SDK_INT >= 33
        && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      if (settings.edit().putBoolean("notifications_asked", true).commit()) requestPermission.run();
    } else {
      try {
        activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
      } catch (ActivityNotFoundException unavailable) {
        // The invitation remains optional even on devices without this Settings screen.
      }
    }
  }

  void close() {
    closed = true;
    pause();
    if (dialog != null) dialog.dismiss();
    dialog = null;
  }
}
