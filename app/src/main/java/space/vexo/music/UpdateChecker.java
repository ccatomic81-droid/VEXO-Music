package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

final class UpdateChecker {
  private final SharedPreferences prefs;
  private final OkHttpClient client;
  private static final OkHttpClient HTTP =
      new OkHttpClient.Builder()
          .connectTimeout(6, TimeUnit.SECONDS)
          .readTimeout(8, TimeUnit.SECONDS)
          .callTimeout(12, TimeUnit.SECONDS)
          .followRedirects(false)
          .build();

  UpdateChecker(Context c) {
    this(c, HTTP);
  }

  UpdateChecker(Context c, OkHttpClient client) {
    prefs = c.getSharedPreferences("app_updates", 0);
    this.client = client;
  }

  String source() {
    return prefs.getString("source", BuildConfig.UPDATE_FEED_URL);
  }

  void source(String value) throws IOException {
    String url = value.trim().isEmpty() ? "" : AppRelease.https(value.trim());
    prefs.edit().putString("source", url).remove("checked_at").remove("snoozed").apply();
  }

  boolean shouldCheck(long now) {
    long at = prefs.getLong("checked_at", 0);
    return !source().isEmpty() && (at == 0 || now < at || now - at >= TimeUnit.HOURS.toMillis(24));
  }

  boolean shouldAnnounceInstalled(int installedCode) {
    return installedCode > prefs.getInt("seen_code", 0);
  }

  void seen(int code) {
    prefs.edit().putInt("seen_code", code).apply();
  }

  void snooze(AppRelease release) {
    prefs.edit().putInt("snoozed", release.code).apply();
  }

  boolean snoozed(AppRelease release) {
    return release.code <= prefs.getInt("snoozed", 0);
  }

  boolean automatic() {
    return prefs.getBoolean("automatic", true);
  }

  void automatic(boolean enabled) {
    prefs.edit().putBoolean("automatic", enabled).apply();
  }

  AppRelease fetch() throws IOException {
    String feed = AppRelease.https(source());
    prefs.edit().putLong("checked_at", System.currentTimeMillis()).apply();
    // Every redirect must remain HTTPS. A response-size cap also applies when length is unknown.
    for (int redirects = 0; redirects <= 3; redirects++) {
      Request request =
          new Request.Builder().url(feed).header("Accept", "application/json").build();
      try (Response response = client.newCall(request).execute()) {
        if (response.isRedirect()) {
          HttpUrl next = response.request().url().resolve(response.header("Location", ""));
          if (next == null) throw new IOException("The update address redirected incorrectly.");
          feed = AppRelease.https(next.toString());
          continue;
        }
        if (!response.isSuccessful() || response.body() == null)
          throw new IOException("Couldn’t check for updates. Try again later.");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = response.body().byteStream()) {
          byte[] buffer = new byte[4096];
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (bytes.size() + count > 64 * 1024)
              throw new IOException("The update response was too large.");
            bytes.write(buffer, 0, count);
          }
        }
        AppRelease release =
            AppRelease.parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        return release;
      }
    }
    throw new IOException("The update address redirected too many times.");
  }
}
