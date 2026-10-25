package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import java.io.IOException;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AppReleaseTest {
  String json(int code, String app, String url) throws Exception {
    return new JSONObject()
        .put("applicationId", app)
        .put("versionCode", code)
        .put("versionName", "0.7.0")
        .put("summary", "Better music search")
        .put("downloadUrl", url)
        .toString();
  }

  @Test
  public void comparesMonotonicCodesAndChecksAppIdentityAndTransport() throws Exception {
    AppRelease release =
        AppRelease.parse(json(9, BuildConfig.APPLICATION_ID, "https://example.com/update.apk"));
    assertTrue(release.newerThan(8));
    assertFalse(release.newerThan(9));
    assertFalse(release.newerThan(10));
    for (String invalid :
        new String[] {
          "http://example.com/app.apk",
          "javascript:alert(1)",
          "https://user:pass@example.com/app.apk"
        })
      assertThrows(
          IOException.class, () -> AppRelease.parse(json(9, BuildConfig.APPLICATION_ID, invalid)));
    assertThrows(
        IOException.class, () -> AppRelease.parse(json(9, "another.app", "https://example.com")));
    assertThrows(IOException.class, () -> AppRelease.parse("{}"));
    assertThrows(
        IOException.class,
        () -> AppRelease.parse(json(0, BuildConfig.APPLICATION_ID, "https://example.com")));
  }

  @Test
  public void noticesPersistPerInstalledVersionAndManualChecksCanIgnoreSnooze() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    UpdateChecker updates = new UpdateChecker(c);
    assertTrue(updates.shouldAnnounceInstalled(8));
    updates.seen(8);
    assertFalse(new UpdateChecker(c).shouldAnnounceInstalled(8));
    assertTrue(new UpdateChecker(c).shouldAnnounceInstalled(9));
    AppRelease release =
        AppRelease.parse(json(9, BuildConfig.APPLICATION_ID, "https://example.com/update.apk"));
    updates.snooze(release);
    assertTrue(new UpdateChecker(c).snoozed(release));
    assertFalse(updates.snoozed(new AppRelease(10, "next", "summary", "https://example.com")));
    updates.source("https://example.com/stable.json");
    assertFalse(updates.snoozed(release));
    assertTrue(updates.shouldCheck(System.currentTimeMillis()));
    c.getSharedPreferences("app_updates", 0)
        .edit()
        .putLong("checked_at", System.currentTimeMillis())
        .commit();
    assertFalse(updates.shouldCheck(System.currentTimeMillis()));
    updates.source("");
    assertFalse(updates.shouldCheck(System.currentTimeMillis()));
  }
}
