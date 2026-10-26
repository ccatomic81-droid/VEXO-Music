package space.vexo.music;

import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

/** Starting recommendations is not a request to restart, seek or replace existing playback. */
@UnstableApi
final class SongRadio {
  static void activate(Player player, Track seed) {
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    if (player.getCurrentMediaItem() != null) return;
    player.setMediaItem(seed.mediaItem());
    player.prepare();
    player.play();
  }
}
