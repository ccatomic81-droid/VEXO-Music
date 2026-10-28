package space.vexo.music;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** User-initiated access to the MediaStore music library, with denial recovery. */
final class DeviceMusicAccess {
  static String permission() {
    return Build.VERSION.SDK_INT >= 33
        ? Manifest.permission.READ_MEDIA_AUDIO
        : Manifest.permission.READ_EXTERNAL_STORAGE;
  }

  static boolean granted(Activity activity) {
    return activity.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
  }

  static void request(Activity activity, Runnable launch, Runnable scan, Runnable chooseFiles) {
    if (granted(activity)) {
      scan.run();
      return;
    }
    var prefs = activity.getSharedPreferences("settings", 0);
    boolean asked = prefs.getBoolean("device_music_asked", false);
    boolean rationale = activity.shouldShowRequestPermissionRationale(permission());
    Runnable ask =
        () -> {
          prefs.edit().putBoolean("device_music_asked", true).apply();
          launch.run();
        };
    if (!asked && !rationale) {
      ask.run();
      return;
    }
    boolean settings = asked && !rationale;
    new VexoDialogs.Builder(activity)
        .setTitle("Allow device music")
        .setMessage(
            settings
                ? "Enable music and audio access in Android app permissions to show the songs on"
                      + " this phone. Your music stays on your device."
                : "VEXO needs music and audio access to show the songs on this phone. Your music"
                      + " stays on your device.")
        .setNegativeButton("Not now", null)
        .setNeutralButton("Choose files", (d, w) -> chooseFiles.run())
        .setPositiveButton(
            settings ? "Open settings" : "Allow access",
            (d, w) -> {
              if (settings)
                activity.startActivity(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
              else ask.run();
            })
        .show();
  }
}
