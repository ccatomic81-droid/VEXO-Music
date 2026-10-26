package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RecoveryAndClockTest {
  final SearchUiTest finder = new SearchUiTest();

  @Before
  public void seed() {
    new DiscoveryUiTest().seed();
  }

  private TimeZone zoneFor(int hour, int minute) {
    Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    int offset =
        (hour * 60 + minute - utc.get(Calendar.HOUR_OF_DAY) * 60 - utc.get(Calendar.MINUTE))
            * 60_000;
    return new SimpleTimeZone(offset, "Test/local-time");
  }

  @Test
  public void clockAndTimeZoneBroadcastsRefreshTheExistingHome() throws Exception {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(zoneFor(4, 59));
      try (ActivityController<MainActivity> a =
          Robolectric.buildActivity(MainActivity.class).setup()) {
        MainActivity activity = a.get();
        View root = activity.findViewById(android.R.id.content);
        assertNotNull(finder.text(root, "Good morning, Listener")); // 04:59 Sri Lanka
        View originalHome = finder.description(root, "Refresh Home");
        TimeZone.setDefault(zoneFor(12, 0));
        activity.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(finder.text(root, "Good afternoon, Listener"));
        assertSame(originalHome, finder.description(root, "Refresh Home"));
        TimeZone.setDefault(zoneFor(2, 30));
        activity.sendBroadcast(new Intent(Intent.ACTION_TIMEZONE_CHANGED));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(finder.text(root, "Good night, Listener")); // 02:30
        a.pause();
        TimeZone.setDefault(zoneFor(12, 0));
        a.resume();
        assertNotNull(finder.text(root, "Good afternoon, Listener"));
        new DiscoveryUiTest().capture(root, 360, 800, "home-v09-morning-layout");
      }
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  public void repairConsumesCrashOnceAndPreservesEveryPrivateCollectionAndAudio() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    new UserProfile(c).saveName("Private name");
    new SearchHistory(c).add("private search");
    File audio = new File(c.getFilesDir(), "offline-audio/kept.bin");
    audio.getParentFile().mkdirs();
    try (FileOutputStream out = new FileOutputStream(audio)) {
      out.write(new byte[] {1, 2, 3});
    }
    Track song =
        new Track("https://www.youtube.com/watch?v=kept", "Private song", "Artist", "", 120);
    new QueueStore(c).save(Collections.singletonList(song), 0, 12000);
    Map<String, ?> library = new HashMap<>(c.getSharedPreferences("library", 0).getAll());
    AppDiagnostics diagnostics = new AppDiagnostics(c);
    diagnostics.record("app_crash", new IllegalStateException("SECRET name URL token"), true);
    assertTrue(AppRecovery.recoverIfNeeded(c));
    assertFalse(AppRecovery.recoverIfNeeded(c));
    assertTrue(c.getSharedPreferences("music_mixes", 0).getAll().isEmpty());
    assertTrue(c.getSharedPreferences("regional_feed", 0).getAll().isEmpty());
    assertEquals(library, c.getSharedPreferences("library", 0).getAll());
    assertEquals("Private name", new UserProfile(c).name());
    assertEquals(1, new QueueStore(c).read().tracks.size());
    assertEquals(12000, new QueueStore(c).read().position);
    assertEquals(3, audio.length());
    assertTrue(new SearchHistory(c).entries().contains("private search"));
    assertFalse(diagnostics.report().contains("SECRET"));
    assertTrue(diagnostics.report().contains("IllegalStateException"));
    c.getSharedPreferences("settings", 0).edit().putBoolean("auto_repair", false).commit();
    diagnostics.record("app_crash", new IOException(), true);
    assertFalse(AppRecovery.recoverIfNeeded(c));
  }

  @Test
  public void diagnosticsAreBoundedPrivateAndAddressTheRequestedMailbox() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    AppDiagnostics diagnostics = new AppDiagnostics(c);
    for (int i = 0; i < 20; i++)
      diagnostics.record(
          "playback", new IOException("https://secret.invalid PRIVATE SEARCH"), false);
    String report = diagnostics.report();
    assertEquals(
        12,
        new org.json.JSONArray(c.getSharedPreferences("diagnostics", 0).getString("events", "[]"))
            .length());
    assertFalse(report.contains("PRIVATE SEARCH"));
    assertFalse(report.contains("secret.invalid"));
    Intent email = AppDiagnostics.email(report, "Audio stopped");
    assertEquals(Intent.ACTION_SENDTO, email.getAction());
    assertEquals("mailto", email.getData().getScheme());
    assertTrue(email.getDataString().startsWith("mailto:ccatomic81@gmail.com?"));
    assertArrayEquals(
        new String[] {"ccatomic81@gmail.com"}, email.getStringArrayExtra(Intent.EXTRA_EMAIL));
    assertTrue(email.getStringExtra(Intent.EXTRA_TEXT).contains("Audio stopped"));
  }

  @Test
  public void supportScreenPreviewsDiagnosticsAndCopiesWithoutEmailApp() throws Exception {
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = a.get().findViewById(android.R.id.content);
      finder.description(root, "Settings tab").performClick();
      assertNotNull(finder.text(root, "Refresh & repair"));
      ((View) finder.text(root, "Report a problem").getParent().getParent()).performClick();
      AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      assertNotNull(finder.description(dialog.getWindow().getDecorView(), "Describe the problem"));
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      ClipboardManager clipboard =
          (ClipboardManager) a.get().getSystemService(Context.CLIPBOARD_SERVICE);
      assertTrue(
          clipboard
              .getPrimaryClip()
              .getItemAt(0)
              .getText()
              .toString()
              .contains("VEXO Music diagnostic report"));
    }
  }

  @Test
  public void miniPlayerAndHomeFitNarrowScreensWithLargeText() throws Exception {
    Context app = RuntimeEnvironment.getApplication();
    android.content.res.Configuration config =
        new android.content.res.Configuration(app.getResources().getConfiguration());
    config.fontScale = 1.6f;
    app.getResources().updateConfiguration(config, null);
    new UserProfile(app).saveName("Kavindu Kavishka");
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = a.get();
      View root = activity.findViewById(android.R.id.content);
      java.lang.reflect.Field miniField = MainActivity.class.getDeclaredField("mini");
      miniField.setAccessible(true);
      View mini = (View) miniField.get(activity);
      mini.setVisibility(View.VISIBLE);
      java.lang.reflect.Field title = MainActivity.class.getDeclaredField("miniTitle");
      title.setAccessible(true);
      ((TextView) title.get(activity)).setText("A long song title with enough room for controls");
      java.lang.reflect.Field artist = MainActivity.class.getDeclaredField("miniArtist");
      artist.setAccessible(true);
      TextView artistView = (TextView) artist.get(activity);
      artistView.setText("Jaymes Young");
      new DiscoveryUiTest().capture(root, 320, 640, "home-mini-v09-large");
      assertTrue(mini.getHeight() >= 66);
      int[] bottom = new int[2], line = new int[2];
      mini.getLocationOnScreen(bottom);
      artistView.getLocationOnScreen(line);
      assertTrue(line[1] + artistView.getHeight() <= bottom[1] + mini.getHeight());
      assertTrue(finder.description(root, "Settings tab").getHeight() >= 48);
    }
  }
}
