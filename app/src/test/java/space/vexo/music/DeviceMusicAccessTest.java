package space.vexo.music;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.Looper;
import android.provider.Settings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DeviceMusicAccessTest {
  @Before
  public void reset() {
    RuntimeEnvironment.getApplication().getSharedPreferences("settings", 0).edit().clear().commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(
            Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE);
  }

  @Test
  public void deviceMusicFirstRequestsOnlyTheAndroid13AudioPermission() {
    assertEquals(Manifest.permission.READ_MEDIA_AUDIO, DeviceMusicAccess.permission());
    try (var c = Robolectric.buildActivity(Activity.class).setup()) {
      int[] requests = {0}, scans = {0};
      DeviceMusicAccess.request(
          c.get(),
          () -> requests[0]++,
          () -> scans[0]++,
          () -> fail("No file picker without a tap"));
      assertEquals(1, requests[0]);
      assertEquals(0, scans[0]);
      assertTrue(
          c.get().getSharedPreferences("settings", 0).getBoolean("device_music_asked", false));
    }
  }

  @Test
  @Config(sdk = 32)
  public void olderAndroidRequestsLegacyAudioLibraryAccess() {
    assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, DeviceMusicAccess.permission());
    try (var c = Robolectric.buildActivity(Activity.class).create().start().resume()) {
      int[] requests = {0};
      DeviceMusicAccess.request(
          c.get(), () -> requests[0]++, () -> fail("Must ask first"), () -> {});
      assertEquals(1, requests[0]);
    }
  }

  @Test
  public void alreadyGrantedAccessScansWithoutAnotherPermissionPrompt() {
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(Manifest.permission.READ_MEDIA_AUDIO);
    try (var c = Robolectric.buildActivity(Activity.class).setup()) {
      int[] scans = {0};
      DeviceMusicAccess.request(
          c.get(), () -> fail("Permission is already granted"), () -> scans[0]++, () -> {});
      assertEquals(1, scans[0]);
    }
  }

  @Test
  public void blockedAccessOffersSettingsAndDoesNotOpenThemUntilTapped() {
    try (var c = Robolectric.buildActivity(Activity.class).setup()) {
      c.get()
          .getSharedPreferences("settings", 0)
          .edit()
          .putBoolean("device_music_asked", true)
          .commit();
      DeviceMusicAccess.request(
          c.get(),
          () -> fail("Do not silently repeat a blocked request"),
          () -> fail("No access"),
          () -> {});
      AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
      assertEquals("Open settings", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      Intent intent = Shadows.shadowOf(c.get()).getNextStartedActivity();
      assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
      assertEquals("package:" + c.get().getPackageName(), intent.getDataString());
    }
  }

  @Test
  public void decliningAccessCanStillChooseIndividualFiles() {
    try (var c = Robolectric.buildActivity(Activity.class).setup()) {
      c.get()
          .getSharedPreferences("settings", 0)
          .edit()
          .putBoolean("device_music_asked", true)
          .commit();
      int[] picks = {0};
      DeviceMusicAccess.request(
          c.get(), () -> fail("No access"), () -> fail("No scan"), () -> picks[0]++);
      AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, picks[0]);
    }
  }

  @Test
  public void phoneLibraryImportActionRequestsTheActualAudioPermission() {
    DiscoveryUiTest helper = new DiscoveryUiTest();
    helper.seed();
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      android.view.View root = c.get().findViewById(android.R.id.content);
      helper.find(root, "Library tab").performClick();
      assertNotNull(helper.text(root, "Choose files"));
      helper.text(root, "Import device music").performClick();
      var request = Shadows.shadowOf(c.get()).getLastRequestedPermission();
      assertNotNull(request);
      assertArrayEquals(
          new String[] {Manifest.permission.READ_MEDIA_AUDIO}, request.requestedPermissions);
    }
  }
}
