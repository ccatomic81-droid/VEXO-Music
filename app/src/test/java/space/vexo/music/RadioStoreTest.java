package space.vexo.music;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RadioStoreTest {
  @Test
  public void survivesRecreationAndResetKeepsTheStationAndTuning() {
    RadioStore store = new RadioStore(RuntimeEnvironment.getApplication());
    store.anchor(RadioPolicyTest.SEED);
    store.mode(RadioPolicy.Mode.DISCOVER);
    store.hide(RadioPolicyTest.SEED);
    RadioStore reopened = new RadioStore(RuntimeEnvironment.getApplication());
    assertEquals(RadioPolicyTest.SEED.url, reopened.anchor().url);
    assertEquals(RadioPolicy.Mode.DISCOVER, reopened.mode());
    assertTrue(reopened.hidden().contains("youtube:seed"));
    reopened.clearFeedback();
    assertTrue(reopened.hidden().isEmpty());
    assertNotNull(reopened.anchor());
  }
}
