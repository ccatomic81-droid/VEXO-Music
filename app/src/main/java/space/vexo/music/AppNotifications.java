package space.vexo.music;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class AppNotifications {
  static final String CHANNEL = "vexo_updates";

  AppNotifications() {}

  static boolean enabled(Context context) {
    return NotificationManagerCompat.from(context).areNotificationsEnabled();
  }

  static Intent settings(Context context) {
    return new Intent("android.settings.APP_NOTIFICATION_SETTINGS")
        .putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
  }

  static void channel(Context context) {
    NotificationChannel notificationChannel =
        new NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT);
    notificationChannel.setDescription("Verified VEXO updates and notification tests");
    ((NotificationManager) context.getSystemService(NotificationManager.class))
        .createNotificationChannel(notificationChannel);
  }

  static boolean post(Context context, int i, String str, String str2) {
    return post(context, i, str, str2, false);
  }

  static boolean post(Context context, int i, String str, String str2, boolean z) {
    Intent intentAddFlags;
    if (!enabled(context)) {
      return false;
    }
    channel(context);
    NotificationChannel notificationChannel =
        ((NotificationManager) context.getSystemService(NotificationManager.class))
            .getNotificationChannel(CHANNEL);
    if (notificationChannel != null && notificationChannel.getImportance() != 0) {
      if (z) {
        intentAddFlags =
            new Intent(
                "android.intent.action.VIEW",
                Uri.parse("https://vexo-music-testing.kavindukavishka785.workers.dev/"));
      } else {
        intentAddFlags =
            new Intent(context, (Class<?>) MainActivity.class)
                .setAction("space.vexo.music.OPEN_UPDATES")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      }
      try {
        NotificationManagerCompat.from(context)
            .notify(
                i,
                new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(str)
                    .setContentText(str2)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(str2))
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            z ? 44 : 41,
                            intentAddFlags,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build());
        return true;
      } catch (SecurityException unused) {
      }
    }
    return false;
  }

  static void update(Context context, AppRelease appRelease) {
    SharedPreferences sharedPreferences = context.getSharedPreferences("settings", 0);
    if (appRelease.authenticated
        && sharedPreferences.getBoolean("update_alerts", false)
        && sharedPreferences.getInt("notified_update", 0) < appRelease.code) {
      if (post(
          context,
          42,
          "VEXO Music " + appRelease.version + " is ready",
          "Tap to review the update in Settings.")) {
        sharedPreferences.edit().putInt("notified_update", appRelease.code).apply();
      }
    }
  }
}
