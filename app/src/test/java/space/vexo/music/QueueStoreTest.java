package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class QueueStoreTest {
  Context context;

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
    context.getSharedPreferences("playback_queue", 0).edit().clear().commit();
  }

  @Test
  public void savesQueueOrderIndexAndPositionAcrossInstances() {
    QueueStore store = new QueueStore(context);
    store.save(
        Arrays.asList(
            AutoplayManagerTest.song("first"),
            new Track("content://media/2", "සිංහල", "A", "", 100)),
        1,
        12345);
    QueueStore.Snapshot restored = new QueueStore(context).read();
    assertEquals(2, restored.tracks.size());
    assertEquals("සිංහල", restored.tracks.get(1).title);
    assertEquals(1, restored.index);
    assertEquals(12345, restored.position);
  }

  @Test
  public void invalidPositionAndCorruptSnapshotAreSafe() {
    QueueStore store = new QueueStore(context);
    store.save(Arrays.asList(AutoplayManagerTest.song("first")), 500, -100);
    assertEquals(0, store.read().index);
    assertEquals(0, store.read().position);
    context.getSharedPreferences("playback_queue", 0).edit().putString("songs", "invalid").commit();
    assertTrue(store.read().tracks.isEmpty());
  }
}
