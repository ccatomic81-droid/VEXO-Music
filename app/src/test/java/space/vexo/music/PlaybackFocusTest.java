package space.vexo.music;

import static org.junit.Assert.*;

import android.media.AudioManager;
import org.junit.Test;

public class PlaybackFocusTest {
  static class Fake implements PlaybackFocus.Backend, PlaybackFocus.Target {
    boolean playing = true, allowed = true;
    int requests, abandons, pauses, resumes;
    float gain = 1;

    public boolean request() {
      requests++;
      return allowed;
    }

    public void abandon() {
      abandons++;
    }

    public boolean wantsPlay() {
      return playing;
    }

    public void pauseForFocus() {
      playing = false;
      pauses++;
    }

    public void resumeAfterFocus() {
      playing = true;
      resumes++;
    }

    public void gain(float value) {
      gain = value;
    }
  }

  @Test
  public void permanentLossNeverResumesWithoutAUserAction() {
    Fake f = new Fake();
    PlaybackFocus focus = new PlaybackFocus(f, f);
    assertTrue(focus.request());
    focus.change(AudioManager.AUDIOFOCUS_LOSS);
    focus.change(AudioManager.AUDIOFOCUS_GAIN);
    assertFalse(f.playing);
    assertEquals(0, f.resumes);
    assertEquals(1, f.abandons);
  }

  @Test
  public void repeatedTransientLossResumesOnceButUserPauseCancelsIt() {
    Fake f = new Fake();
    PlaybackFocus focus = new PlaybackFocus(f, f);
    focus.request();
    focus.change(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    focus.change(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    focus.change(AudioManager.AUDIOFOCUS_GAIN);
    assertEquals(1, f.resumes);
    focus.change(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    focus.userPause();
    focus.change(AudioManager.AUDIOFOCUS_GAIN);
    assertEquals(1, f.resumes);
    assertFalse(f.playing);
  }

  @Test
  public void duckAffectsTheLogicalMixAndRegainsItsOriginalLevel() {
    Fake f = new Fake();
    PlaybackFocus focus = new PlaybackFocus(f, f);
    focus.request();
    focus.change(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
    assertEquals(.2f, f.gain, 0);
    assertTrue(f.playing);
    focus.change(AudioManager.AUDIOFOCUS_GAIN);
    assertEquals(1, f.gain, 0);
    assertEquals(0, f.resumes);
  }

  @Test
  public void deniedFocusAndClosedCallbacksDoNotRestartAudio() {
    Fake f = new Fake();
    f.allowed = false;
    PlaybackFocus focus = new PlaybackFocus(f, f);
    assertFalse(focus.request());
    focus.close();
    focus.change(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    focus.change(AudioManager.AUDIOFOCUS_GAIN);
    assertEquals(0, f.resumes);
    assertEquals(0, f.pauses);
    assertFalse(focus.request());
  }
}
