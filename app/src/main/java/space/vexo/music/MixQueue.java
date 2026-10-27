package space.vexo.music;

import java.util.*;

/** A mix refill appends only. Manual entries and the current song are never returned or moved. */
final class MixQueue {
  static List<Track> select(List<Track> candidates, List<Track> queue, List<Track> recent, int limit) {
    Set<String> keys = new HashSet<>();
    for (Track song : queue) keys.add(RadioPolicy.songKey(song));
    List<Track> pool = new ArrayList<>();
    for (Track song : QueuePolicy.fresh(candidates, queue, Collections.emptyList(), limit + 20))
      if (RadioPolicy.music(song) && keys.add(RadioPolicy.songKey(song))) pool.add(song);
    List<Track> out = new ArrayList<>();
    String last = queue.isEmpty() ? "" : RadioPolicy.artist(queue.get(queue.size() - 1));
    int room = Math.max(0, Math.min(limit, QueuePolicy.MAX_ITEMS - queue.size()));
    while (!pool.isEmpty() && out.size() < room) {
      int at = 0;
      for (int i = 0; i < pool.size(); i++) if (!RadioPolicy.artist(pool.get(i)).equals(last)) { at = i; break; }
      Track selected = pool.remove(at); last = RadioPolicy.artist(selected); out.add(selected);
    }
    return out;
  }
}
