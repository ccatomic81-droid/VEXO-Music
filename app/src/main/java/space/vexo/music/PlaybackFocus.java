package space.vexo.music;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;

/** One audio-focus owner for the entire logical player, including both overlapping decks. */
public final class PlaybackFocus {
  public interface Backend {
    boolean request();

    void abandon();
  }

  public interface Target {
    boolean wantsPlay();

    void pauseForFocus();

    void resumeAfterFocus();

    void gain(float value);
  }

  private final Backend backend;
  private final Target target;
  private boolean resume, closed;

  public PlaybackFocus(Backend backend, Target target) {
    this.backend = backend;
    this.target = target;
  }

  public static PlaybackFocus android(Context context, Handler handler, Target target) {
    AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    PlaybackFocus[] owner = new PlaybackFocus[1];
    AudioFocusRequest request =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(
                value -> {
                  if (owner[0] != null) owner[0].change(value);
                },
                handler)
            .build();
    owner[0] =
        new PlaybackFocus(
            new Backend() {
              public boolean request() {
                return manager.requestAudioFocus(request)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
              }

              public void abandon() {
                manager.abandonAudioFocusRequest(request);
              }
            },
            target);
    return owner[0];
  }

  public boolean request() {
    if (closed) return false;
    resume = false;
    boolean granted = backend.request();
    if (granted) target.gain(1);
    return granted;
  }

  public void userPause() {
    resume = false;
    backend.abandon();
    target.gain(1);
  }

  public void change(int change) {
    if (closed) return;
    switch (change) {
      case AudioManager.AUDIOFOCUS_GAIN:
        target.gain(1);
        boolean shouldResume = resume;
        resume = false;
        if (shouldResume) target.resumeAfterFocus();
        break;
      case AudioManager.AUDIOFOCUS_LOSS:
        resume = false;
        target.pauseForFocus();
        backend.abandon();
        target.gain(1);
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
        resume = resume || target.wantsPlay();
        target.pauseForFocus();
        break;
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
        target.gain(.2f);
        break;
    }
  }

  public void close() {
    userPause();
    closed = true;
  }
}
