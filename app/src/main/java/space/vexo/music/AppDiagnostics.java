package space.vexo.music;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/**
 * Bounded, local diagnostics. Exception messages and user music/profile data are never recorded.
 */
final class AppDiagnostics {
  static final String EMAIL = "ccatomic81@gmail.com";
  private static final Object LOCK = new Object();
  private final Context context;
  private final SharedPreferences prefs;

  AppDiagnostics(Context context) {
    this.context = context.getApplicationContext();
    prefs = context.getSharedPreferences("diagnostics", 0);
  }

  static void record(Context context, String area, Throwable error) {
    new AppDiagnostics(context).record(area, error, false);
  }

  void record(String area, Throwable error, boolean fatal) {
    synchronized (LOCK) {
      try {
        // Only fixed internal category labels are accepted, never a title, URL or error message.
        if (!area.matches("[a-z_]{1,32}")) area = "app";
        JSONArray previous;
        try {
          previous = new JSONArray(prefs.getString("events", "[]"));
        } catch (Exception corrupt) {
          previous = new JSONArray();
        }
        JSONArray events = new JSONArray();
        for (int i = Math.max(0, previous.length() - 11); i < previous.length(); i++)
          events.put(previous.getJSONObject(i));
        StringBuilder frames = new StringBuilder();
        Throwable cause = error;
        int count = 0;
        for (int depth = 0; cause != null && depth < 3; depth++, cause = cause.getCause()) {
          if (depth > 0) frames.append("caused by ");
          frames.append(cause.getClass().getSimpleName()).append('\n');
          if (cause instanceof androidx.media3.common.PlaybackException)
            frames
                .append("Playback code: ")
                .append(((androidx.media3.common.PlaybackException) cause).errorCode)
                .append('\n');
          for (StackTraceElement frame : cause.getStackTrace())
            if (frame.getClassName().startsWith("space.vexo.music.") && count++ < 8)
              frames
                  .append(frame.getClassName())
                  .append('.')
                  .append(frame.getMethodName())
                  .append(':')
                  .append(frame.getLineNumber())
                  .append('\n');
        }
        events.put(
            new JSONObject()
                .put("at", System.currentTimeMillis())
                .put("area", area)
                .put("version", BuildConfig.VERSION_NAME)
                .put("fatal", fatal)
                .put("trace", frames.toString()));
        SharedPreferences.Editor editor = prefs.edit().putString("events", events.toString());
        if (fatal) editor.putBoolean("pending_recovery", true);
        // Persist before forwarding a fatal error to Android's normal crash handler.
        if (fatal) editor.commit();
        else editor.apply();
      } catch (Throwable ignored) {
        // Reporting must never crash the app or hide the original failure.
      }
    }
  }

  String report() {
    StringBuilder text = new StringBuilder("VEXO Music diagnostic report\n");
    text.append("Version: ")
        .append(BuildConfig.VERSION_NAME)
        .append(" (")
        .append(BuildConfig.VERSION_CODE)
        .append(")\n")
        .append("Android: ")
        .append(Build.VERSION.RELEASE)
        .append(" / API ")
        .append(Build.VERSION.SDK_INT)
        .append('\n')
        .append("Device: ")
        .append(Build.MANUFACTURER)
        .append(' ')
        .append(Build.MODEL)
        .append('\n')
        .append("Time zone: ")
        .append(TimeZone.getDefault().getID())
        .append('\n')
        .append("Local hour: ")
        .append(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        .append('\n')
        .append("Connection: ")
        .append(connection())
        .append('\n')
        .append("Free app storage: ")
        .append(context.getFilesDir().getUsableSpace() / 1048576)
        .append(" MB\n")
        .append("Heap limit: ")
        .append(Runtime.getRuntime().maxMemory() / 1048576)
        .append(" MB\n")
        .append(
            "Profile, photos, song names, searches, URLs and account data are excluded.\n\n"
                + "Recent issues:\n");
    try {
      JSONArray events = new JSONArray(prefs.getString("events", "[]"));
      if (events.length() == 0) text.append("No recorded app errors.\n");
      SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.ROOT);
      for (int i = events.length() - 1; i >= 0; i--) {
        JSONObject e = events.getJSONObject(i);
        text.append(date.format(new Date(e.getLong("at"))))
            .append(" · ")
            .append(e.optString("version"))
            .append(" · ")
            .append(e.getString("area"))
            .append(e.optBoolean("fatal") ? " · crash" : "")
            .append('\n')
            .append(e.optString("trace"))
            .append('\n');
      }
    } catch (Exception ignored) {
      text.append("Previous diagnostic data could not be read.\n");
    }
    return text.toString();
  }

  private String connection() {
    try {
      ConnectivityManager manager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
      if (caps == null) return "Offline";
      return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
          ? "Internet available"
          : "Network without verified internet";
    } catch (Exception ignored) {
      return "Unknown";
    }
  }

  static Intent email(String report, String description) {
    String subject = "VEXO Music " + BuildConfig.VERSION_NAME + " — problem report";
    String body = "What happened:\n" + description + "\n\n" + report;
    // Query fields work with mail apps that ignore the SENDTO extras; extras help other clients.
    Intent intent =
        new Intent(
            Intent.ACTION_SENDTO,
            Uri.parse(
                "mailto:"
                    + EMAIL
                    + "?subject="
                    + Uri.encode(subject)
                    + "&body="
                    + Uri.encode(body)));
    intent.putExtra(Intent.EXTRA_EMAIL, new String[] {EMAIL});
    intent.putExtra(Intent.EXTRA_SUBJECT, subject);
    intent.putExtra(Intent.EXTRA_TEXT, body);
    return intent;
  }
}
