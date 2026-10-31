package space.vexo.music;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import java.util.*;
import java.util.function.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DefaultAlertsTest {
  private Context c;
  private FakeRegistration client;
  private int factories;

  @Before public void setup() {
    c = RuntimeEnvironment.getApplication();
    c.getSharedPreferences("settings", 0).edit().clear().commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    Shadows.shadowOf(c.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
    client = new FakeRegistration();
    factories = 0;
  }

  private PushNotifications.Registration registration() { factories++; return client; }
  private void grant() {
    Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
  }

  @Test public void newAndUpgradedSettingsDefaultToAlertsAndFourSecondCrossfade() {
    AppDefaults.initialize(c);
    assertTrue(AppDefaults.updateAlerts(c));
    assertTrue(PushNotifications.optedIn(c));
    assertEquals(4, AppDefaults.crossfadeSeconds(c));
    assertFalse("Android permission is still required", PushNotifications.deliveryAllowed(c));
    assertTrue(c.getSharedPreferences("settings", 0).contains("crossfade"));
    c.getSharedPreferences("settings", 0).edit().putString("appearance", "light").commit();
    AppDefaults.initialize(c);
    assertEquals("light", c.getSharedPreferences("settings", 0).getString("appearance", ""));
  }

  @Test public void explicitOffAndCustomCrossfadeSurviveRepeatedStartup() {
    c.getSharedPreferences("settings", 0).edit().putBoolean("update_alerts", false)
        .putBoolean("push_release_alerts", false).putInt("crossfade", 0).commit();
    AppDefaults.initialize(c); AppDefaults.initialize(c);
    assertFalse(AppDefaults.updateAlerts(c)); assertFalse(PushNotifications.optedIn(c));
    assertEquals(0, AppDefaults.crossfadeSeconds(c));
    c.getSharedPreferences("settings", 0).edit().putInt("crossfade", 8).commit();
    AppDefaults.initialize(c); assertEquals(8, AppDefaults.crossfadeSeconds(c));
  }

  @Test public void permissionDenialDoesNotEraseAlertPreferenceOrCreateFirebaseRegistration() {
    AppDefaults.initialize(c);
    PushNotifications.enabled(c, true, this::registration);
    assertTrue(PushNotifications.optedIn(c)); assertEquals(0, factories);
    assertTrue(PushNotifications.status(c).contains("enable Android notifications"));
    PushNotifications.permissionsChanged(c, this::registration);
    assertEquals(0, factories);
    PushNotifications.enabled(c, false, this::registration);
    assertFalse(PushNotifications.optedIn(c)); assertEquals(0, factories);
  }

  @Test public void grantingPermissionConnectsAutomaticallyAndRevokingItPausesDelivery() {
    AppDefaults.initialize(c);
    PushNotifications.enabled(c, true, this::registration);
    grant(); PushNotifications.permissionsChanged(c, this::registration);
    assertEquals(1, factories);
    assertTrue(client.autoInit); assertTrue(client.subscriptions.contains(PushNotifications.TOPIC));
    client.subscribeComplete.accept(true);
    assertEquals("On · release announcements connected", PushNotifications.status(c));
    PushNotifications.permissionsChanged(c, this::registration);
    assertEquals("An unchanged permission must not reconnect repeatedly", 1, factories);
    Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    PushNotifications.permissionsChanged(c, this::registration);
    assertFalse(client.autoInit); assertTrue(PushNotifications.optedIn(c));
    client.unsubscribeComplete.accept(true);
    assertEquals(1, client.deleted);
    assertFalse(PushNotifications.deliveryAllowed(c));
  }

  @Test public void staleUnsubscribeCannotDeleteTokenAfterAlertsAreReenabled() {
    grant(); PushNotifications.enabled(c, true, this::registration);
    Consumer<Boolean> oldSubscription = client.subscribeComplete;
    PushNotifications.enabled(c, false, this::registration);
    Consumer<Boolean> oldUnsubscribe = client.unsubscribeComplete;
    oldSubscription.accept(true);
    assertFalse(PushNotifications.optedIn(c));
    PushNotifications.enabled(c, true, this::registration);
    oldUnsubscribe.accept(true);
    assertEquals(0, client.deleted);
    client.subscribeComplete.accept(true);
    assertTrue(PushNotifications.deliveryAllowed(c));
    assertEquals("On · release announcements connected", PushNotifications.status(c));
  }

  private static final class FakeRegistration implements PushNotifications.Registration {
    boolean autoInit; int deleted;
    final List<String> subscriptions = new ArrayList<>();
    Consumer<Boolean> subscribeComplete, unsubscribeComplete;
    public void autoInit(boolean value) { autoInit = value; }
    public void subscribe(String topic, Consumer<Boolean> complete) {
      subscriptions.add(topic); subscribeComplete = complete;
    }
    public void unsubscribe(String topic, Consumer<Boolean> complete) { unsubscribeComplete = complete; }
    public void deleteToken() { deleted++; }
  }
}
