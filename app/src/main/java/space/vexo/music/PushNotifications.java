package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.concurrent.atomic.AtomicInteger;

final class PushNotifications {
  static final String TOPIC =
      BuildConfig.VERSION_NAME.contains("dev") ? "vexo_testing_updates" : "vexo_release_updates";
  private static final AtomicInteger generation = new AtomicInteger();

  PushNotifications() {}

  static boolean optedIn(Context context) {
    return context.getSharedPreferences("settings", 0).getBoolean("push_release_alerts", false);
  }

  static String status(Context context) {
    String str;
    SharedPreferences sharedPreferences = context.getSharedPreferences("settings", 0);
    if (optedIn(context)) {
      str = "Connecting to release alerts…";
    } else {
      str = "Off · choose whether to receive release announcements";
    }
    return sharedPreferences.getString("push_status", str);
  }

  static void enabled(Context context, boolean z) {
    final Context applicationContext = context.getApplicationContext();
    final int iIncrementAndGet = generation.incrementAndGet();
    boolean z2 = false;
    final SharedPreferences sharedPreferences =
        applicationContext.getSharedPreferences("settings", 0);
    if (z && AppNotifications.enabled(applicationContext)) {
      z2 = true;
    }
    sharedPreferences
        .edit()
        .putBoolean("push_release_alerts", z2)
        .putString("push_status", z2 ? "Connecting to release alerts…" : "Off")
        .apply();
    try {
      final FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance();
      firebaseMessaging.setAutoInitEnabled(z2);
      firebaseMessaging.unsubscribeFromTopic(
          TOPIC.equals("vexo_testing_updates") ? "vexo_release_updates" : "vexo_testing_updates");
      if (z2) {
        firebaseMessaging
            .subscribeToTopic(TOPIC)
            .addOnCompleteListener(
                new OnCompleteListener() {
                  public final void onComplete(Task task) {
                    PushNotifications.lambda$enabled$0(
                        iIncrementAndGet, applicationContext, sharedPreferences, task);
                  }
                });
      } else {
        firebaseMessaging
            .unsubscribeFromTopic(TOPIC)
            .addOnCompleteListener(
                new OnCompleteListener() {
                  public final void onComplete(Task task) {
                    PushNotifications.lambda$enabled$1(
                        iIncrementAndGet, applicationContext, firebaseMessaging, task);
                  }
                });
      }
    } catch (RuntimeException unused) {
      sharedPreferences
          .edit()
          .putString("push_status", "Unavailable · check Google Play services and your connection")
          .apply();
    }
  }

  static /* synthetic */ void lambda$enabled$0(
      int i, Context context, SharedPreferences sharedPreferences, Task task) {
    String str;
    if (i == generation.get() && optedIn(context)) {
      SharedPreferences.Editor editorEdit = sharedPreferences.edit();
      if (task.isSuccessful()) {
        str = "On · release announcements connected";
      } else {
        str = "Connection failed · turn off and on to retry";
      }
      editorEdit.putString("push_status", str).apply();
    }
  }

  static /* synthetic */ void lambda$enabled$1(
      int i, Context context, FirebaseMessaging firebaseMessaging, Task task) {
    if (i != generation.get() || optedIn(context)) {
      return;
    }
    firebaseMessaging.deleteToken();
  }

  static void restore(Context context) {
    if (optedIn(context)) {
      enabled(context, AppNotifications.enabled(context));
    }
  }
}
