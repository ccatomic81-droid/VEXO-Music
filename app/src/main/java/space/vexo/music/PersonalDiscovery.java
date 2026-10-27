package space.vexo.music;

import java.util.*;

/** Bounded metadata discovery. UI owns cancellation, generation checks and cache lifetime. */
final class PersonalDiscovery {
  interface Source {
    List<Track> related(Track track) throws Exception;

    List<Track> search(String query) throws Exception;

    default List<Track> trending(String query) throws Exception { return search(query); }
  }

  static List<Track> load(MusicInterests interests, Source source) throws InterruptedException {
    List<Track> candidates = new ArrayList<>(interests.cached);
    Set<String> seeds = new HashSet<>();
    for (Track t : interests.seeds) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      if (seeds.size() == 2) break;
      if (!seeds.add(RadioPolicy.artist(t))) continue;
      try {
        candidates.addAll(source.related(t));
      } catch (Exception unavailable) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      }
    }
    String query = interests.query();
    if (!query.isEmpty() && candidates.size() < 12) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      try {
        candidates.addAll(source.search(query));
      } catch (Exception unavailable) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      }
    }
    Set<String> recentlyPlayed = new HashSet<>();
    for (Track t : interests.history.subList(0, Math.min(5, interests.history.size())))
      recentlyPlayed.add(RadioPolicy.songKey(t));
    candidates.removeIf(t -> recentlyPlayed.contains(RadioPolicy.songKey(t)));
    List<Track> result = interests.variety(candidates, 12);
    // Saved music stays useful when discovery is offline; never invent remote results.
    if (result.isEmpty()) result = interests.variety(interests.favourites, 12);
    return result;
  }
}
