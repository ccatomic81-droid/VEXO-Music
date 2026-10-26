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
    for (Track seed :
        QueuePolicy.identity(anchor.url).equals(QueuePolicy.identity(current.url))
            ? Arrays.asList(anchor)
            : Arrays.asList(anchor, current)) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      try {
        add(pool, source.related(seed));
      } catch (Exception e) {
        failure = e;
      }
    }
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    // Always include music-search candidates: related videos can be sparse or mostly non-music.
    String query = anchor.artist.trim();
    if (query.isEmpty()) query = anchor.title;
    try {
      add(pool, source.search(query + " songs"));
    } catch (Exception e) {
      failure = e;
    }
    // A single additional taste seed broadens sparse stations without unbounded requests.
    for (Track taste : interests) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      if (!RadioPolicy.music(taste)
          || RadioPolicy.artist(taste).equals(RadioPolicy.artist(anchor))
          || RadioPolicy.artist(taste).equals(RadioPolicy.artist(current))) continue;
      try {
        add(pool, source.related(taste));
      } catch (Exception e) {
        failure = e;
      }
      break;
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
