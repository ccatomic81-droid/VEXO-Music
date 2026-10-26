package space.vexo.music;

import android.os.*;
import androidx.media3.common.*;

/**
 * Monotonic audible time, never seek distance. Failure/pause/automatic transitions are not
 * dislikes.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ListeningTracker implements Player.Listener {
  interface Recorder {
    void record(Track track, long milliseconds, boolean manualSkip, boolean failed);
  }

  private final Player player;
  private final Recorder recorder;
  private final Handler main = new Handler(Looper.getMainLooper());
  private Track current;
  private long at = SystemClock.elapsedRealtime(), listened;
  private boolean playing, failed, manual, repeat, closed, discard;
  private final Runnable pulse =
      new Runnable() {
        public void run() {
          if (closed) return;
          sample();
          main.postDelayed(this, 1000);
        }
      };

  ListeningTracker(Player player, Recorder recorder) {
    this.player = player;
    this.recorder = recorder;
    player.addListener(this);
    sample();
    main.postDelayed(pulse, 1000);
  }

  @Override
  public void onPlayerError(PlaybackException error) {
    failed = true;
  }

  @Override
  public void onMediaItemTransition(MediaItem item, int reason) {
    repeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT;
  }

  @Override
  public void onPositionDiscontinuity(
      Player.PositionInfo old, Player.PositionInfo next, int reason) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK
        && old.mediaItem != null
        && next.mediaItem != null
        && !old.mediaItem.mediaId.equals(next.mediaItem.mediaId)) manual = true;
  }

  @Override
  public void onEvents(Player p, Player.Events events) {
    sample();
  }

  private void sample() {
    long now = SystemClock.elapsedRealtime();
    if (playing && current != null) listened += Math.max(0, Math.min(5000, now - at));
    at = now;
    MediaItem item = player.getCurrentMediaItem();
    String next = item == null ? "" : item.mediaId;
    if (current != null && (!current.url.equals(next) || repeat)) {
      if (!discard) recorder.record(current, listened, manual, failed);
      current = null;
      listened = 0;
      failed = false;
      discard = false;
      manual = false;
    }
    if (current == null && item != null) current = Track.from(item);
    playing = player.isPlaying();
    repeat = false;
  }

  void settingsChanged() {
    discard = current != null;
    listened = 0;
    at = SystemClock.elapsedRealtime();
  }

  void close() {
    if (closed) return;
    sample();
    if (current != null && !discard) recorder.record(current, listened, false, failed);
    closed = true;
    main.removeCallbacks(pulse);
    player.removeListener(this);
  }
}
