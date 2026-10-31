package space.vexo.music;

import com.google.firebase.messaging.*;
import java.util.Map;

/** Release messages respect notification settings and never install or start playback. */
public final class VexoMessagingService extends FirebaseMessagingService {
  @Override
  public void onNewToken(String ignored) {
    if (PushNotifications.optedIn(this)) PushNotifications.restore(this);
  }

  @Override
  public void onMessageReceived(RemoteMessage message) {
    if (!PushNotifications.optedIn(this) || !AppNotifications.enabled(this)) return;
    android.content.SharedPreferences settings = getSharedPreferences("settings", 0);
    String id = message.getMessageId();
    if (id != null && id.equals(settings.getString("last_push_id", ""))) return;
    Map<String, String> data = message.getData();
    if (!data.containsKey("versionCode")) return;
    try {
      int code = Integer.parseInt(data.get("versionCode"));
      String channel = data.get("channel");
      String expected = BuildConfig.VERSION_NAME.contains("dev") ? "testing" : "stable";
      if (!expected.equals(channel)
          || code <= BuildConfig.VERSION_CODE
          || code <= settings.getInt("announced_push_version", 0)) return;
      boolean testing = "testing".equals(channel);
      boolean posted =
          AppNotifications.post(
              this,
              44,
              "VEXO Music update",
              testing
                  ? "A new test build was announced. Tap to review the tester page."
                  : "A new release was announced. Open Settings to review it.",
              testing);
      if (posted) {
        android.content.SharedPreferences.Editor edit =
            settings.edit().putInt("announced_push_version", code);
        if (id != null) edit.putString("last_push_id", id);
        edit.apply();
      }
    } catch (RuntimeException invalid) {
    }
  }
}
