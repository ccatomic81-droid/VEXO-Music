package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VaultReadCacheTest {
  @Test
  public void emptyPrivateCollectionReadDoesNotCreateOrWriteAVault() {
    Context c = ApplicationProvider.getApplicationContext();
    String bucket = "empty_navigation_collection";
    android.content.SharedPreferences vault = c.getSharedPreferences("private_collections_v1", 0);
    assertFalse(vault.contains(bucket));
    for (int i = 0; i < 5; i++)
      assertEquals("", new PrivateStore(c, bucket).getString("missing", ""));
    assertFalse(vault.contains(bucket));
  }

  @Test
  public void cachedSmartReadIsDefensiveAndChangedOrCorruptCiphertextNeverUsesOldData()
      throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    SmartStore smart = new SmartStore(c);
    smart.preference("mood", "calm");
    assertEquals("calm", new SmartStore(c).preferences().optString("mood"));
    new SmartStore(c).preferences().put("mood", "mutated-only-in-memory");
    assertEquals("calm", new SmartStore(c).preferences().optString("mood"));
    smart.preference("mood", "energetic");
    assertEquals("energetic", new SmartStore(c).preferences().optString("mood"));
    c.getSharedPreferences("smart_private_v1", 0)
        .edit()
        .putString("vault:preferences", "broken-seal")
        .commit();
    assertThrows(IllegalStateException.class, () -> new SmartStore(c).preferences());
  }

  @Test
  public void privateCollectionCacheTracksCurrentCiphertextAcrossInstancesAndClear() {
    Context c = ApplicationProvider.getApplicationContext();
    PrivateStore a = new PrivateStore(c, "navigation_test");
    a.edit().putString("value", "first").apply();
    assertEquals("first", new PrivateStore(c, "navigation_test").getString("value", ""));
    a.edit().putString("value", "second").apply();
    assertEquals("second", new PrivateStore(c, "navigation_test").getString("value", ""));
    a.edit().clear().apply();
    assertEquals("", new PrivateStore(c, "navigation_test").getString("value", ""));
    c.getSharedPreferences("private_collections_v1", 0)
        .edit()
        .putString("navigation_test", "bad")
        .commit();
    assertThrows(
        IllegalStateException.class,
        () -> new PrivateStore(c, "navigation_test").getString("value", ""));
  }
}
