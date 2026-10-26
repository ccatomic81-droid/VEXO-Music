package space.vexo.music;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class SessionQueueTest {
  Track song(String id, String artist) {
    return new Track("https://www.youtube.com/watch?v=" + id, id, artist, "", 180);
  }

  @Test public void sessionLearnsWithoutYouTubeAndExpiresAndRespectsPrivacy() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SmartStore smart = new SmartStore(c);
    long now = System.currentTimeMillis();
    Track first = song("first", "Session artist"), other = song("other", "Other");
    smart.preference("repeat_hours", 0);
    smart.listened(first, 60000, false, false, now);
    assertFalse(new YouTubeTaste(c).read().connected);
    MusicInterests interests = MusicInterests.read(c);
    assertEquals(first.url, interests.seeds.get(0).url);
    assertEquals(first.url, interests.rank(Arrays.asList(other, first), false).get(0).url);
    assertTrue(SessionTaste.bonus(first, smart.session(), now) > 0);
    assertEquals(0, SessionTaste.bonus(first, smart.session(), now + SessionTaste.WINDOW));
    assertTrue(SessionTaste.seeds(smart.session(), now - 1).isEmpty());
    smart.preference("pause_history", true);
    assertTrue(MusicInterests.read(c).seeds.isEmpty());
    smart.preference("pause_history", false);
    smart.resetTaste();
    assertEquals(0, smart.session().length());
    smart.listened(first, 60000, false, false, now + 1);
    assertEquals(0, smart.session().length());
  }

  @Test public void skipsDifferFromFailuresAndOptionalImportCannotDisableLocalDiscovery() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SmartStore smart = new SmartStore(c);
    Track first = song("first", "Artist");
    long now = System.currentTimeMillis();
    smart.listened(first, 5000, true, true, now);
    assertEquals(0, smart.session().length());
    smart.listened(first, 5000, false, false, now);
    assertEquals(0, smart.session().length());
    smart.listened(first, 5000, true, false, now);
    assertTrue(SessionTaste.bonus(first, smart.session(), now) < 0);
    assertTrue(SessionTaste.seeds(smart.session(), now).isEmpty());
    new LibraryStore(c).save("favourites", Arrays.asList(first));
    c.getSharedPreferences("private_collections_v1", 0).edit()
        .putString("youtube_taste", "broken-optional-vault").commit();
    assertEquals(first.url, MusicInterests.read(c).favourites.get(0).url);
  }

  List<String> upcoming(Player p) {
    Timeline t = p.getCurrentTimeline();
    List<String> result = new ArrayList<>();
    for (int i : QueuePolicy.order(p.getMediaItemCount(), p.getCurrentMediaItemIndex(),
        at -> t.getNextWindowIndex(at, Player.REPEAT_MODE_OFF, p.getShuffleModeEnabled())))
      result.add(p.getMediaItemAt(i).mediaId);
    return result;
  }

  @Test public void manualAddAndPlayNextPreserveShuffledOrderPositionAndPause() {
    Context c = RuntimeEnvironment.getApplication();
    CrossfadePlayer p = new CrossfadePlayer(c, new DefaultDataSource.Factory(c));
    try {
      Track current = song("current", "A"), manual = song("manual", "B"),
          auto = song("auto", "C"), extra = song("extra", "D"), next = song("next", "E");
      p.setMediaItems(Arrays.asList(current.mediaItem(), auto.mediaItem(true), manual.mediaItem()), 0, 42000);
      p.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(new int[] {0, 2, 1}, 1));
      p.setShuffleModeEnabled(true);
      p.pause();
      java.util.function.Consumer<int[]> order = values ->
          p.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(values, 1));
      assertTrue(ManualQueue.add(p, extra, false, order));
      assertEquals(Arrays.asList(current.url, manual.url, extra.url, auto.url), upcoming(p));
      assertTrue(ManualQueue.add(p, next, true, order));
      assertEquals(Arrays.asList(current.url, next.url, manual.url, extra.url, auto.url), upcoming(p));
      ManualQueue.promote(p, 1, order);
      assertEquals(Arrays.asList(current.url, auto.url, next.url, manual.url, extra.url), upcoming(p));
      assertFalse(Track.from(p.getMediaItemAt(1)).recommended);
      assertTrue(p.getShuffleModeEnabled());
      assertFalse(p.getPlayWhenReady());
      assertEquals(42000, p.getCurrentPosition());
      assertEquals(current.url, p.getCurrentMediaItem().mediaId);
    } finally { p.release(); }
  }

  @Test public void normalQueuePreservesManualPriorityAndSourcesAcrossRestart() {
    Context c = RuntimeEnvironment.getApplication();
    CrossfadePlayer p = new CrossfadePlayer(c, new DefaultDataSource.Factory(c));
    try {
      Track current = song("current", "A"), manual = song("manual", "B"),
          auto = song("auto", "C"), extra = song("extra", "D");
      p.setMediaItems(Arrays.asList(current.mediaItem(), manual.mediaItem(), auto.mediaItem(true)), 0, 12000);
      ManualQueue.add(p, extra, false, ignored -> fail());
      assertEquals(Arrays.asList(current.url, manual.url, extra.url, auto.url), upcoming(p));
      List<Track> songs = new ArrayList<>();
      for (int i = 0; i < p.getMediaItemCount(); i++) songs.add(Track.from(p.getMediaItemAt(i)));
      new QueueStore(c).save(songs, 0, 12000);
      QueueStore.Snapshot saved = new QueueStore(c).read();
      assertTrue(saved.tracks.get(3).recommended);
      assertFalse(saved.tracks.get(1).recommended);
      assertFalse(Track.from(saved.tracks.get(3).mediaItem()).recommended);
      assertEquals(12000, p.getCurrentPosition());
    } finally { p.release(); }
  }
}
