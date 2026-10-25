package space.vexo.music;

import androidx.media3.common.*;
import java.util.function.BooleanSupplier;

/** One permitted seek at a fresh track start. Personal markers never alter shared-room playback. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class IntroSkipper implements Player.Listener {
  private final Player player;
  private final PureSong pure;
  private final BooleanSupplier room;
  private boolean armed;

  IntroSkipper(Player player, PureSong pure, BooleanSupplier room) {
    this.player = player;
    this.pure = pure;
    this.room = room;
    player.addListener(this);
  }

  @Override
  public void onMediaItemTransition(MediaItem item, int reason) {
    try {
      armed = pure.enabled() && !room.getAsBoolean();
    } catch (IllegalStateException e) {
      armed = false;
    }
  }

  @Override
  public void onEvents(Player p, Player.Events events) {
    if (!armed || player.getPlaybackState() != Player.STATE_READY) return;
    armed = false;
    try {
      if (!pure.enabled()
          || room.getAsBoolean()
          || !player.getPlayWhenReady()
          || player.getCurrentPosition() > 1500
          || player.getCurrentMediaItem() == null) return;
      long start = pure.start(Track.from(player.getCurrentMediaItem()), player.getDuration());
      if (start > player.getCurrentPosition()) player.seekTo(start);
    } catch (IllegalStateException unavailable) {
      /* Existing music continues without a guessed seek. */
    }
  }

  void settingsChanged() {
    if (!pure.enabled()) armed = false;
  }

  boolean skipNow() {
    armed = false;
    if (room.getAsBoolean() || player.getCurrentMediaItem() == null) return false;
    long start = pure.start(Track.from(player.getCurrentMediaItem()), player.getDuration());
    if (start <= player.getCurrentPosition()) return false;
    player.seekTo(start);
    return true;
  }

  void fullVersion() {
    armed = false;
    if (!room.getAsBoolean()) player.seekTo(0);
  }

  void close() {
    player.removeListener(this);
  }
}
