package space.vexo.music;

import static org.junit.Assert.*;

import android.content.*;
import android.media.AudioManager;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioManager;

@RunWith(RobolectricTestRunner.class)
// API 35's simulated loudness codec bootstraps an unavailable Bluetooth nested class
// in Robolectric 4.17. Test the Media3 session/queue wrapper on API 34; UI also covers 26/35.
@Config(sdk = 34)
public class CrossfadePlayerTest {
  @Test
  public void logicalQueueVolumeAndShuffleSurviveTheSessionWrapper() {
    Context c = RuntimeEnvironment.getApplication();
    CrossfadePlayer p = new CrossfadePlayer(c, new DefaultDataSource.Factory(c));
    androidx.media3.session.MediaSession session = null;
    try {
      session =
          new androidx.media3.session.MediaSession.Builder(c, p)
              .setId("crossfade-wrapper-test")
              .build();
      assertSame(p, session.getPlayer());
      p.setMediaItems(
          Arrays.asList(
              AutoplayManagerTest.song("a").mediaItem(),
              AutoplayManagerTest.song("b").mediaItem(),
              AutoplayManagerTest.song("c").mediaItem()),
          0,
          0);
      p.setRepeatMode(Player.REPEAT_MODE_ALL);
      p.setShuffleModeEnabled(true);
      p.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(new int[] {0, 2, 1}, 3));
      p.setPlaybackSpeed(1.25f);
      p.setVolume(.45f);
      assertEquals(3, p.getMediaItemCount());
      assertEquals("a", Track.from(p.getCurrentMediaItem()).title);
      assertEquals(Player.REPEAT_MODE_ALL, p.getRepeatMode());
      assertTrue(p.getShuffleModeEnabled());
      assertEquals(.45f, p.getVolume(), 0);
      assertEquals(1.25f, p.getPlaybackParameters().speed, 0);
      p.moveMediaItem(2, 1);
      assertEquals("c", Track.from(p.getMediaItemAt(1)).title);
      p.removeMediaItem(2);
      assertEquals(2, p.getMediaItemCount());
    } finally {
      if (session != null) session.release();
      p.release();
    }
  }

  @Test
  public void focusLossUserPauseAndNoisyBroadcastStopPlaybackWithoutAutoRestart() {
    Context c = RuntimeEnvironment.getApplication();
    AudioManager manager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    ShadowAudioManager audio = Shadows.shadowOf(manager);
    audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    CrossfadePlayer p = new CrossfadePlayer(c, new DefaultDataSource.Factory(c));
    try {
      p.play();
      assertTrue(p.getPlayWhenReady());
      assertNotNull(audio.getLastAudioFocusRequest());
      audio
          .getLastAudioFocusRequest()
          .listener
          .onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse(p.getPlayWhenReady());
      p.pause();
      audio.getLastAudioFocusRequest().listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse(p.getPlayWhenReady());
      p.play();
      c.sendBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse(p.getPlayWhenReady());
      audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
      p.play();
      assertFalse(p.getPlayWhenReady());
    } finally {
      p.release();
    }
  }

  @Test
  public void preparedDeckKeepsTheWholeQueueAndActualShuffleSuccessorOnPromotion()
      throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    CrossfadePlayer p = new CrossfadePlayer(c, new DefaultDataSource.Factory(c));
    androidx.media3.exoplayer.ExoPlayer outgoing = null;
    try {
      List<MediaItem> queue = new ArrayList<>();
      for (String id : new String[] {"A", "B", "C"})
        queue.add(
            new MediaItem.Builder()
                .setMediaId(id)
                .setUri("file:///not-a-test-network-request/" + id + ".wav")
                .build());
      p.setMediaItems(queue, 0, 0);
      p.setShuffleModeEnabled(true);
      p.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(new int[] {0, 2, 1}, 1));
      p.setRepeatMode(Player.REPEAT_MODE_ALL);
      p.setPlaybackSpeed(1.25f);
      java.lang.reflect.Field active = CrossfadePlayer.class.getDeclaredField("active");
      active.setAccessible(true);
      Object old = active.get(p);
      java.lang.reflect.Field nativePlayer = old.getClass().getDeclaredField("player");
      nativePlayer.setAccessible(true);
      outgoing = (androidx.media3.exoplayer.ExoPlayer) nativePlayer.get(old);
      java.lang.reflect.Method prepare = CrossfadePlayer.class.getDeclaredMethod("prepareDeck");
      prepare.setAccessible(true);
      CrossfadeEngine.Deck candidate = (CrossfadeEngine.Deck) prepare.invoke(p);
      androidx.media3.exoplayer.ExoPlayer incoming =
          (androidx.media3.exoplayer.ExoPlayer) nativePlayer.get(candidate);
      assertEquals(3, incoming.getMediaItemCount());
      assertEquals("C", incoming.getCurrentMediaItem().mediaId);
      assertEquals(1, incoming.getNextMediaItemIndex());
      assertEquals(0, incoming.getVolume(), 0);
      assertEquals(1.25f, incoming.getPlaybackParameters().speed, 0);
      java.lang.reflect.Field engineField = CrossfadePlayer.class.getDeclaredField("engine");
      engineField.setAccessible(true);
      Object engine = engineField.get(p);
      java.lang.reflect.Field host = CrossfadeEngine.class.getDeclaredField("host");
      host.setAccessible(true);
      ((CrossfadeEngine.Host) host.get(engine)).promote(candidate);
      assertEquals("C", p.getCurrentMediaItem().mediaId);
      assertEquals(3, p.getMediaItemCount());
      assertEquals(1, p.getNextMediaItemIndex());
      assertTrue(p.getShuffleModeEnabled());
      assertEquals(Player.REPEAT_MODE_ALL, p.getRepeatMode());
    } finally {
      if (outgoing != null) outgoing.release();
      p.release();
    }
  }
}
