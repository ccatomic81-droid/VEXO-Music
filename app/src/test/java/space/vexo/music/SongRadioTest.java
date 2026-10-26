package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class SongRadioTest {
  @Test
  public void restartingRadioReplacesOnlyUpcomingAutomaticPicks() {
    Context context = RuntimeEnvironment.getApplication();
    CrossfadePlayer player = new CrossfadePlayer(context, new DefaultDataSource.Factory(context));
    try {
      Track past = AutoplayManagerTest.song("past"), current = AutoplayManagerTest.song("current"),
          manual = AutoplayManagerTest.song("manual"), stale = AutoplayManagerTest.song("stale-radio");
      player.setMediaItems(Arrays.asList(past.mediaItem(true), current.mediaItem(true),
          manual.mediaItem(), stale.mediaItem(true)), 1, 54321);
      player.pause();
      SongRadio.activate(player, current);
      assertEquals(3, player.getMediaItemCount());
      assertEquals(current.url, player.getCurrentMediaItem().mediaId);
      assertEquals(54321, player.getCurrentPosition());
      assertFalse(player.getPlayWhenReady());
      assertEquals(manual.url, player.getMediaItemAt(2).mediaId);
      assertEquals(past.url, player.getMediaItemAt(0).mediaId);
    } finally {
      player.release();
    }
  }

  @Test
  public void selectedSearchSongReplacesOldResultsAndStartsAtTheBeginning() {
    Context context = RuntimeEnvironment.getApplication();
    CrossfadePlayer player = new CrossfadePlayer(context, new DefaultDataSource.Factory(context));
    try {
      player.setMediaItems(Arrays.asList(AutoplayManagerTest.song("old-result").mediaItem(),
          AutoplayManagerTest.song("unrelated-result").mediaItem()), 0, 75234);
      player.setRepeatMode(Player.REPEAT_MODE_ALL);
      Track selected = AutoplayManagerTest.song("selected-search-song");
      SongRadio.playSelected(player, selected);
      assertEquals(selected.url, player.getCurrentMediaItem().mediaId);
      assertEquals(1, player.getMediaItemCount());
      assertEquals(0, player.getCurrentPosition());
      assertTrue(player.getPlayWhenReady());
      assertEquals(Player.REPEAT_MODE_OFF, player.getRepeatMode());
    } finally {
      player.release();
    }
  }

  @Test
  public void startingRadioKeepsPlayingSongPositionAndManualQueueEvenForAnotherSeed() {
    Context context = RuntimeEnvironment.getApplication();
    CrossfadePlayer player = new CrossfadePlayer(context, new DefaultDataSource.Factory(context));
    try {
      Track current = AutoplayManagerTest.song("current"),
          queued = AutoplayManagerTest.song("manual");
      player.setMediaItems(Arrays.asList(current.mediaItem(), queued.mediaItem()), 0, 75234);
      player.setPlaybackSpeed(1.25f);
      player.setVolume(.5f);
      player.setShuffleModeEnabled(true);
      player.play();
      int[] jumps = {0};
      player.addListener(
          new Player.Listener() {
            @Override
            public void onPositionDiscontinuity(
                Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
              jumps[0]++;
            }
          });
      SongRadio.activate(player, AutoplayManagerTest.song("another-radio-seed"));
      assertEquals(current.url, player.getCurrentMediaItem().mediaId);
      assertEquals(75234, player.getCurrentPosition());
      assertTrue(player.getPlayWhenReady());
      assertEquals(2, player.getMediaItemCount());
      assertEquals(queued.url, player.getMediaItemAt(1).mediaId);
      assertTrue(player.getShuffleModeEnabled());
      assertEquals(1.25f, player.getPlaybackParameters().speed, .001f);
      assertEquals(.5f, player.getVolume(), .001f);
      assertEquals(0, jumps[0]);
      SongRadio.activate(player, current);
      assertEquals(75234, player.getCurrentPosition());
      assertEquals(0, jumps[0]);
    } finally {
      player.release();
    }
  }

  @Test
  public void pausedPlaybackStaysPausedWhenRadioIsEnabled() {
    Context context = RuntimeEnvironment.getApplication();
    CrossfadePlayer player = new CrossfadePlayer(context, new DefaultDataSource.Factory(context));
    try {
      player.setMediaItem(AutoplayManagerTest.song("paused").mediaItem(), 42000);
      player.pause();
      SongRadio.activate(player, AutoplayManagerTest.song("paused"));
      assertFalse(player.getPlayWhenReady());
      assertEquals(42000, player.getCurrentPosition());
    } finally {
      player.release();
    }
  }

  @Test
  public void anEmptyPlayerStartsTheChosenRadioSeed() {
    Context context = RuntimeEnvironment.getApplication();
    CrossfadePlayer player = new CrossfadePlayer(context, new DefaultDataSource.Factory(context));
    try {
      SongRadio.activate(player, AutoplayManagerTest.song("first"));
      assertEquals("first", Track.from(player.getCurrentMediaItem()).title);
      assertTrue(player.getPlayWhenReady());
    } finally {
      player.release();
    }
  }
}
