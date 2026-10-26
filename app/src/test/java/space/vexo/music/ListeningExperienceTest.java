package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.media3.common.*;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class ListeningExperienceTest {
  Track song(String id, String artist) {
    return new Track("https://www.youtube.com/watch?v=" + id, id, artist, "", 180);
  }

  @Test
  public void learningWeighsRealListensIgnoresErrorsAndDecays() throws Exception {
    JSONObject state = new JSONObject();
    Track a = song("one", "Artist A"), b = song("two", "Artist B");
    long now = 100000000;
    TastePolicy.record(state, a, 120000, false, false, now);
    assertTrue(TastePolicy.bonus(a, state, now) > 0);
    int before = TastePolicy.bonus(a, state, now);
    TastePolicy.record(state, a, 120000, false, false, now + 1000);
    assertEquals(before, TastePolicy.bonus(a, state, now));
    TastePolicy.record(state, b, 5000, true, true, now);
    assertFalse(state.has(RadioPolicy.songKey(b)));
    TastePolicy.record(state, b, 5000, false, false, now);
    assertFalse(state.has(RadioPolicy.songKey(b)));
    TastePolicy.record(state, b, 5000, true, false, now);
    assertTrue(TastePolicy.bonus(b, state, now) < 0);
    assertTrue(TastePolicy.bonus(a, state, now + 60L * 86400000) < before);
    assertTrue(TastePolicy.bonus(song("new", "Artist A"), state, now) > 0);
  }

  @Test
  public void privacyPauseOptOutResetAndExplicitFeedbackWin() {
    Context context = RuntimeEnvironment.getApplication();
    SmartStore smart = new SmartStore(context);
    Track a = song("one", "Artist A"), b = song("two", "Artist B");
    long now = System.currentTimeMillis();
    smart.preference("pause_history", true);
    smart.listened(a, 120000, false, false, now);
    assertEquals(0, smart.taste().length());
    smart.preference("pause_history", false);
    smart.preference("learn_listening", false);
    smart.listened(a, 120000, false, false, now);
    assertEquals(0, smart.taste().length());
    smart.preference("learn_listening", true);
    smart.preference("repeat_hours", 0);
    smart.listened(a, 120000, false, false, now);
    assertEquals(
        a.url,
        SmartPolicy.rank(Arrays.asList(b, a), smart, Collections.emptyList(), false).get(0).url);
    smart.feedback(a, "less");
    assertEquals(
        b.url,
        SmartPolicy.rank(Arrays.asList(a, b), smart, Collections.emptyList(), false).get(0).url);
    assertFalse(
        context
            .getSharedPreferences("smart_private_v1", 0)
            .getAll()
            .toString()
            .contains("Artist A"));
    smart.clearHistory();
    assertEquals(0, smart.taste().length());
    smart.listened(a, 120000, false, false, System.currentTimeMillis());
    assertEquals(
        "A track started before clearing cannot restore the erased taste",
        0,
        smart.taste().length());
  }

  static class Fake {
    MediaItem item;
    long position = 0, duration = 180000;
    boolean playing = true, ready = true;
    int seeks;
    Player player =
        (Player)
            Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, m, args) -> {
                  switch (m.getName()) {
                    case "getCurrentMediaItem":
                      return item;
                    case "getCurrentPosition":
                      return position;
                    case "getDuration":
                      return duration;
                    case "getPlaybackState":
                      return ready ? Player.STATE_READY : Player.STATE_BUFFERING;
                    case "isPlaying":
                    case "getPlayWhenReady":
                      return playing;
                    case "seekTo":
                      position = ((Number) args[args.length - 1]).longValue();
                      seeks++;
                      return null;
                    case "hashCode":
                      return System.identityHashCode(proxy);
                    case "equals":
                      return proxy == args[0];
                    case "toString":
                      return "Fake playback";
                    default:
                      if (m.getReturnType() == boolean.class) return false;
                      if (m.getReturnType() == int.class) return 0;
                      if (m.getReturnType() == long.class) return 0L;
                      if (m.getReturnType() == float.class) return 0f;
                      return null;
                  }
                });
  }

  @Test
  public void trackerCountsAudibleTimeNotSeekDistanceOrPausedTime() {
    Fake f = new Fake();
    f.item = song("one", "A").mediaItem();
    List<Long> times = new ArrayList<>();
    List<Boolean> failed = new ArrayList<>();
    ListeningTracker tracker =
        new ListeningTracker(
            f.player,
            (t, ms, skip, error) -> {
              times.add(ms);
              failed.add(error);
            });
    for (int i = 0; i < 5; i++) {
      ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
      tracker.onEvents(f.player, null);
    }
    f.position = 160000;
    tracker.onEvents(f.player, null);
    f.playing = false;
    tracker.onEvents(f.player, null);
    ShadowSystemClock.advanceBy(Duration.ofMinutes(5));
    tracker.onEvents(f.player, null);
    f.item = song("two", "B").mediaItem();
    tracker.onEvents(f.player, null);
    assertEquals(Collections.singletonList(5000L), times);
    tracker.onPlayerError(
        new PlaybackException(
            "Network interruption",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
    tracker.close();
    assertTrue(failed.get(1));
  }

  private Player.PositionInfo position(MediaItem item, int index, long ms) {
    return new Player.PositionInfo(
        null, index, item, null, 0, ms, ms, C.INDEX_UNSET, C.INDEX_UNSET);
  }

  @Test
  public void onlyManualCrossTrackSeeksAreEarlySkipSignals() {
    Fake f = new Fake();
    MediaItem a = song("one", "A").mediaItem(),
        b = song("two", "B").mediaItem(),
        c = song("three", "C").mediaItem();
    f.item = a;
    List<Boolean> skips = new ArrayList<>();
    ListeningTracker tracker =
        new ListeningTracker(f.player, (t, ms, skip, error) -> skips.add(skip));
    ShadowSystemClock.advanceBy(Duration.ofSeconds(4));
    tracker.onEvents(f.player, null);
    tracker.onPositionDiscontinuity(
        position(a, 0, 4000), position(a, 0, 18000), Player.DISCONTINUITY_REASON_SEEK);
    tracker.onEvents(f.player, null);
    f.item = b;
    tracker.onMediaItemTransition(b, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    tracker.onEvents(f.player, null);
    assertEquals(Collections.singletonList(false), skips);
    ShadowSystemClock.advanceBy(Duration.ofSeconds(4));
    tracker.onEvents(f.player, null);
    tracker.onPositionDiscontinuity(
        position(b, 1, 4000), position(c, 2, 0), Player.DISCONTINUITY_REASON_SEEK);
    f.item = c;
    tracker.onMediaItemTransition(c, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
    tracker.onEvents(f.player, null);
    assertEquals(Arrays.asList(false, true), skips);
    tracker.close();
  }

  @Test
  public void privacyChangeDropsTheInFlightSampleUntilTheNextTrack() {
    Fake f = new Fake();
    f.item = song("old", "A").mediaItem();
    List<String> songs = new ArrayList<>();
    ListeningTracker tracker =
        new ListeningTracker(f.player, (t, ms, skip, error) -> songs.add(t.title));
    ShadowSystemClock.advanceBy(Duration.ofSeconds(4));
    tracker.onEvents(f.player, null);
    tracker.settingsChanged();
    ShadowSystemClock.advanceBy(Duration.ofSeconds(4));
    tracker.onEvents(f.player, null);
    f.item = song("new", "B").mediaItem();
    tracker.onEvents(f.player, null);
    assertTrue(songs.isEmpty());
    ShadowSystemClock.advanceBy(Duration.ofSeconds(4));
    tracker.onEvents(f.player, null);
    tracker.close();
    assertEquals(Collections.singletonList("new"), songs);
  }

  @Test
  public void confirmedMarkersNeverGuessOnlineCutsAndFullVersionWins() {
    SmartStore smart = new SmartStore(RuntimeEnvironment.getApplication());
    PureSong pure = new PureSong(smart);
    Track local =
        new Track("content://media/external/audio/media/1", "Imported song", "Artist", "", 180);
    assertFalse(pure.enabled());
    pure.mark(local, 18000, 180000);
    assertEquals(18000, pure.start(local, 180000));
    assertEquals(0, pure.start(local, 200000));
    try {
      pure.mark(song("online", "A"), 10000, 180000);
      fail();
    } catch (IllegalArgumentException expected) {
    }
    Fake f = new Fake();
    f.item = local.mediaItem();
    boolean[] room = {false};
    IntroSkipper skip = new IntroSkipper(f.player, pure, () -> room[0]);
    skip.onMediaItemTransition(f.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    skip.onEvents(f.player, null);
    assertEquals(0, f.seeks);
    pure.enabled(true);
    assertEquals(18000, pure.crossfadeStart(local, 180000, false));
    assertEquals(0, pure.crossfadeStart(local, 180000, true));
    assertEquals(0, pure.crossfadeStart(local, 200000, false));
    skip.onMediaItemTransition(f.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    skip.onEvents(f.player, null);
    assertEquals(18000, f.position);
    skip.onEvents(f.player, null);
    assertEquals(1, f.seeks);
    skip.fullVersion();
    skip.onEvents(f.player, null);
    assertEquals(0, f.position);
    room[0] = true;
    skip.onMediaItemTransition(f.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    skip.onEvents(f.player, null);
    assertEquals(2, f.seeks);
    assertFalse(skip.skipNow());
    room[0] = false;
    skip.onMediaItemTransition(f.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    pure.enabled(false);
    skip.settingsChanged();
    skip.onEvents(f.player, null);
    assertEquals(2, f.seeks);
    pure.mark(local, 1000, 180000);
    pure.enabled(true);
    f.position = 1000;
    int previous = f.seeks;
    skip.onMediaItemTransition(f.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    skip.onEvents(f.player, null);
    assertEquals(
        "A pre-positioned crossfade deck must not be interrupted by a duplicate seek",
        previous,
        f.seeks);
    pure.reset(local);
    assertEquals(0, pure.start(local, 180000));
    skip.close();
  }

  @Test
  public void categoriesKeepTypedCluesAndLanguageWithinCollection() {
    MusicCategory chill = MusicCategory.find("chill");
    assertEquals("Amaradeva Sinhala chill relaxing music", chill.query(" Amaradeva ", "Sinhala"));
    assertEquals("தமிழ் chill relaxing music", chill.query("தமிழ்", "Any"));
    assertEquals(14, MusicCategory.ALL.size());
    assertNull(MusicCategory.find("untrusted"));
  }
}
