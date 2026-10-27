package space.vexo.music;

import java.util.*;

/** A bounded candidate pool anchored to the station, with independent request fallbacks. */
public final class RadioCatalogue {
  public interface Source {
    List<Track> related(Track seed) throws Exception;

    List<Track> search(String query) throws Exception;
  }

  private final Source source;

  public RadioCatalogue(Source source) {
    this.source = source;
  }

  public List<Track> load(Track anchor, Track current) throws Exception {
    return load(anchor, current, Collections.emptyList());
  }

  public List<Track> load(Track anchor, Track current, List<Track> interests) throws Exception {
    LinkedHashMap<String, Track> pool = new LinkedHashMap<>();
    Exception failure = null;
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    try {
      add(pool, source.related(anchor));
    } catch (Exception e) {
      failure = e;
    }
    // Expand only from a song the original station actually recommends. A manual queue
    // addition or an unrelated listening interest must not redirect this station.
    String currentId = QueuePolicy.identity(current.url);
    if (!QueuePolicy.identity(anchor.url).equals(currentId) && pool.containsKey(currentId)) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      try {
        add(pool, source.related(current));
      } catch (Exception e) {
        failure = e;
      }
    }
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    // Search is a sparse-provider fallback, not a second queue of search results.
    String artist = RadioPolicy.artist(anchor);
    if (pool.size() < 10 && !artist.isEmpty()) {
      try {
        List<Track> fallback = new ArrayList<>(source.search(anchor.artist.trim() + " songs"));
        fallback.removeIf(song -> !RadioPolicy.artist(song).equals(artist));
        add(pool, fallback);
      } catch (Exception e) {
        failure = e;
      }
    }
    if (pool.isEmpty() && failure != null) throw failure;
    return new ArrayList<>(pool.values());
  }

  private static void add(Map<String, Track> pool, List<Track> songs) {
    int accepted = 0;
    for (Track t : songs) {
      if (RadioPolicy.music(t)) pool.putIfAbsent(QueuePolicy.identity(t.url), t);
      if (++accepted >= 40) break;
    }
  }
}
