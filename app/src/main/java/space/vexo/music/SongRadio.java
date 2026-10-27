package space.vexo.music;

import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;

/** Starting recommendations is not a request to restart, seek or replace existing playback. */
@UnstableApi
final class SongRadio {
  static void playSelected(Player player, Track seed) {
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    player.setMediaItem(seed.mediaItem());
    player.prepare();
    player.play();
  }

  static void activate(Player player, Track seed) {
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    if (player.getCurrentMediaItem() != null) {
      pruneRecommendations(player);
      return;
    }
    player.setMediaItem(seed.mediaItem());
    player.prepare();
    player.play();
  }

  static void pruneRecommendations(Player player) {
    if (player.getCurrentMediaItem() != null) {
      java.util.List<Integer> upcoming = QueuePolicy.order(player.getMediaItemCount(),
          player.getCurrentMediaItemIndex(), at -> player.getCurrentTimeline().getNextWindowIndex(
              at, Player.REPEAT_MODE_OFF, player.getShuffleModeEnabled()));
      java.util.Set<Integer> future = new java.util.HashSet<>(upcoming);
      future.remove(player.getCurrentMediaItemIndex());
      for (int i = player.getMediaItemCount() - 1; i >= 0; i--)
        if (future.contains(i) && Track.from(player.getMediaItemAt(i)).recommended)
          player.removeMediaItem(i);
    }
  }
}
