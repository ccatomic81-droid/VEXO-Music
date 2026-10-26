package space.vexo.music;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.os.Looper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NotificationPermissionPromptTest {
  @Before public void isolate() {
    RuntimeEnvironment.getApplication().getSharedPreferences("settings", 0).edit().clear().commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
  }

  @Test public void enableWaitsForExplicitTapAndInvitationDoesNotRepeatAfterRecreation() {
    AtomicInteger requests = new AtomicInteger();
    try (var controller = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true)) {
      Activity a = controller.get();
      NotificationPermissionPrompt prompt = new NotificationPermissionPrompt(a, () -> true, requests::incrementAndGet);
      prompt.resume();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
      assertNotNull(dialog); assertTrue(dialog.isShowing()); assertEquals(0, requests.get());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals(1, requests.get());
      assertTrue(a.getSharedPreferences("settings", 0).getBoolean("notifications_asked", false));
      prompt.close();
      controller.recreate().visible().windowFocusChanged(true);
      assertFalse(new NotificationPermissionPrompt(controller.get(), () -> true, requests::incrementAndGet).eligible());
    }
  }

  @Test public void notNowAndBackDismissalAreRememberedWithoutRequestingPermission() {
    AtomicInteger requests = new AtomicInteger();
    try (var controller = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true)) {
      Activity a = controller.get();
      NotificationPermissionPrompt prompt = new NotificationPermissionPrompt(a, () -> true, requests::incrementAndGet);
      prompt.resume(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      assertEquals(0, requests.get()); assertFalse(prompt.eligible()); prompt.close();
      a.getSharedPreferences("settings", 0).edit().clear().commit();
      NotificationPermissionPrompt again = new NotificationPermissionPrompt(a, () -> true, requests::incrementAndGet);
      again.resume(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      ShadowDialog.getLatestDialog().cancel();
      assertEquals(0, requests.get()); assertFalse(again.eligible()); again.close();
    }
  }

  @Test public void alreadyEnabledAndPreviouslyDeniedUsersAreSkipped() {
    try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
      Activity a = controller.get();
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
      Shadows.shadowOf(a.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
      NotificationPermissionPrompt prompt = new NotificationPermissionPrompt(a, () -> true, () -> fail("Must not ask"));
      assertFalse(prompt.eligible());
      Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
      a.getSharedPreferences("settings", 0).edit().putBoolean("notifications_asked", true).commit();
      assertFalse(prompt.eligible()); prompt.close();
    }
  }

  @Test public void onboardingAndBackgroundDoNotConsumeInvitation() {
    try (var controller = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true)) {
      Activity a = controller.get();
      NotificationPermissionPrompt prompt = new NotificationPermissionPrompt(a, () -> false, () -> fail("Must not ask"));
      prompt.resume(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      assertTrue(prompt.eligible());
      prompt.pause(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
      assertTrue(prompt.eligible()); prompt.close();
    }
  }

  @Test @Config(sdk = 26)
  public void olderAndroidOpensSettingsWithoutRequestingUnsupportedRuntimePermission() {
    try (var controller = Robolectric.buildActivity(Activity.class).setup().visible().windowFocusChanged(true)) {
      Activity a = controller.get();
      Shadows.shadowOf(a.getSystemService(NotificationManager.class)).setNotificationsEnabled(false);
      NotificationPermissionPrompt prompt = new NotificationPermissionPrompt(a, () -> true, () -> fail("No runtime permission before Android 13"));
      prompt.resume(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      android.content.Intent intent = Shadows.shadowOf(a).getNextStartedActivity();
      assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.getAction());
      assertEquals(a.getPackageName(), intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE));
      prompt.close();
    }
  }
}
