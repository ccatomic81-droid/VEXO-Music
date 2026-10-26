package space.vexo.music;

import androidx.media3.common.*;
import java.util.*;
import java.util.function.Consumer;

/** Explicit queue choices precede automatic picks without reshuffling or seeking playback. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ManualQueue {
  private static List<Integer> order(Player p) {
    Timeline timeline = p.getCurrentTimeline();
    boolean shuffle = p.getShuffleModeEnabled();
    return QueuePolicy.order(p.getMediaItemCount(), timeline.getFirstWindowIndex(shuffle),
        at -> timeline.getNextWindowIndex(at, Player.REPEAT_MODE_OFF, shuffle));
  }

  static boolean add(Player p, Track track, boolean next, Consumer<int[]> shuffleOrder) {
    int count = p.getMediaItemCount(), current = p.getCurrentMediaItemIndex();
    if (count >= QueuePolicy.MAX_ITEMS) return false;
    if (current < 0 || count == 0) {
      p.addMediaItem(track.mediaItem());
      return true;
    }
    List<Integer> order = order(p);
    int position = order.indexOf(current) + 1;
    if (!next) {
      while (position < order.size()
          && !Track.from(p.getMediaItemAt(order.get(position))).recommended) position++;
    }
    if (p.getShuffleModeEnabled()) {
      p.addMediaItem(track.mediaItem());
      order.add(position, count);
      shuffleOrder.accept(order.stream().mapToInt(Integer::intValue).toArray());
    } else {
      p.addMediaItem(position < order.size() ? order.get(position) : count, track.mediaItem());
    }
    return true;
  }

  static void promote(Player p, int from, Consumer<int[]> shuffleOrder) {
    int current = p.getCurrentMediaItemIndex();
    if (from == current || from < 0 || from >= p.getMediaItemCount()) return;
    List<Integer> order = order(p);
    // A deliberate Play next converts an automatic pick into the user's choice.
    p.replaceMediaItem(from, Track.from(p.getMediaItemAt(from)).mediaItem());
    if (p.getShuffleModeEnabled()) {
      order.remove(Integer.valueOf(from));
      order.add(order.indexOf(current) + 1, from);
      shuffleOrder.accept(order.stream().mapToInt(Integer::intValue).toArray());
    } else {
      p.moveMediaItem(from, QueuePolicy.playNextTarget(from, current));
    }
  }
}
