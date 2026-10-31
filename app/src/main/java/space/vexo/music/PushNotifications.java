package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A saved alert preference is separate from Android's permission to deliver alerts. */
final class PushNotifications {
  static final String TOPIC =
      BuildConfig.VERSION_NAME.contains("dev") ? "vexo_testing_updates" : "vexo_release_updates";
  static final String ANDROID_ENABLED = "push_android_enabled";
  static final String CLIENT_STARTED = "push_client_started";
  private static final AtomicInteger generation = new AtomicInteger();

  interface Registration {
    void autoInit(boolean value);
    void subscribe(String topic, Consumer<Boolean> complete);
    void unsubscribe(String topic, Consumer<Boolean> complete);
    void deleteToken();
  }

  private static final Supplier<Registration> FCM =
      () -> {
        FirebaseMessaging client = FirebaseMessaging.getInstance();
        return new Registration() {
          public void autoInit(boolean value) { client.setAutoInitEnabled(value); }
          public void subscribe(String topic, Consumer<Boolean> complete) {
            client.subscribeToTopic(topic).addOnCompleteListener(t -> complete.accept(t.isSuccessful()));
          }
          public void unsubscribe(String topic, Consumer<Boolean> complete) {
            client.unsubscribeFromTopic(topic).addOnCompleteListener(t -> complete.accept(t.isSuccessful()));
          }
          public void deleteToken() { client.deleteToken(); }
        };
      };

  static boolean optedIn(Context c) {
    return c.getSharedPreferences("settings", 0).getBoolean("push_release_alerts", true);
  }

  static boolean deliveryAllowed(Context c) {
    return optedIn(c) && AppNotifications.enabled(c);
  }

  static String status(Context c) {
    if (!optedIn(c)) return "Off · release announcements are disabled";
    if (!AppNotifications.enabled(c))
      return "On · enable Android notifications to receive release alerts";
    return c.getSharedPreferences("settings", 0)
        .getString("push_status", "Connecting to release alerts…");
  }

  static void enabled(Context c, boolean value) {
    enabled(c, value, FCM);
  }

  static void enabled(Context c, boolean requested, Supplier<Registration> factory) {
    Context app = c.getApplicationContext();
    SharedPreferences prefs = app.getSharedPreferences("settings", 0);
    int id = generation.incrementAndGet();
    boolean androidEnabled = AppNotifications.enabled(app);
    boolean allowed = requested && androidEnabled;
    boolean started = prefs.getBoolean(CLIENT_STARTED, false);
    prefs.edit()
        .putBoolean("push_release_alerts", requested)
        .putBoolean(ANDROID_ENABLED, androidEnabled)
        .putString("push_status", !requested ? "Off · release announcements are disabled"
            : allowed ? "Connecting to release alerts…"
            : "On · enable Android notifications to receive release alerts")
        .apply();
    // Do not create an FCM registration before Android permits notifications.
    if (!allowed && !started) return;
    try {
      Registration client = factory.get();
      client.autoInit(allowed);
      if (allowed) prefs.edit().putBoolean(CLIENT_STARTED, true).apply();
      client.unsubscribe(TOPIC.equals("vexo_testing_updates")
          ? "vexo_release_updates" : "vexo_testing_updates", ignored -> {});
      if (allowed) {
        client.subscribe(TOPIC, success -> {
          if (id != generation.get() || !deliveryAllowed(app)) return;
          prefs.edit().putString("push_status", success
              ? "On · release announcements connected"
              : "Connection failed · turn off and on to retry").apply();
        });
      } else {
        client.unsubscribe(TOPIC, success -> {
          if (id != generation.get() || deliveryAllowed(app)) return;
          client.deleteToken();
          prefs.edit().putBoolean(CLIENT_STARTED, false).apply();
        });
      }
    } catch (RuntimeException unavailable) {
      prefs.edit().putString("push_status",
          "Unavailable · check Google Play services and your connection").apply();
    }
  }

  static void permissionsChanged(Context c) {
    permissionsChanged(c, FCM);
  }

  static void permissionsChanged(Context c, Supplier<Registration> factory) {
    SharedPreferences prefs = c.getSharedPreferences("settings", 0);
    if (prefs.getBoolean(ANDROID_ENABLED, false) != AppNotifications.enabled(c))
      enabled(c, optedIn(c), factory);
  }

  static void restore(Context c) {
    if (optedIn(c) || c.getSharedPreferences("settings", 0).getBoolean(CLIENT_STARTED, false))
      enabled(c, optedIn(c));
  }
}
