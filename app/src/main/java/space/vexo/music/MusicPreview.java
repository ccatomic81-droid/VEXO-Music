package space.vexo.music;

import android.os.Handler;
import java.util.*;
import java.util.concurrent.*;

/** Debounced playable matches; the field itself is never rebuilt by asynchronous results. */
final class MusicPreview implements AutoCloseable {
  interface Source {
    List<Track> fetch(String query, String country) throws Exception;
  }

  interface Listener {
    void ready(String query, List<Track> songs, boolean unavailable);
  }

  private final Handler main;
  private final ExecutorService executor;
  private final Source source;
  private final Map<String, List<Track>> cache = new LinkedHashMap<>();
  private int generation;
  private Runnable pending;
  private Future<?> running;
  private boolean closed;

  MusicPreview(Handler main, ExecutorService executor, Source source) {
    this.main = main;
    this.executor = executor;
    this.source = source;
  }

  void request(String value, String region, Listener listener) {
    cancel();
    if (closed) return;
    String query = SearchHistory.clean(value);
    int token = generation;
    if (RadioPolicy.normalize(query).length() < 2 || query.contains("://")) {
      listener.ready(query, Collections.emptyList(), false);
      return;
    }
    String key = region + ":" + query.toLowerCase(Locale.ROOT);
    List<Track> hit = cache.get(key);
    if (hit != null) {
      listener.ready(query, new ArrayList<>(hit), false);
      return;
    }
    pending =
        () -> {
          pending = null;
          running =
              executor.submit(
                  () -> {
                    List<Track> songs;
                    boolean failed = false;
                    try {
                      songs = combine("", Collections.emptyList(), source.fetch(query, region), 6);
                    } catch (Exception error) {
                      songs = Collections.emptyList();
                      failed = true;
                    }
                    List<Track> found = songs;
                    boolean unavailable = failed;
                    main.post(
                        () -> {
                          if (closed || token != generation) return;
                          if (!unavailable && !found.isEmpty()) {
                            if (cache.size() >= 24) cache.remove(cache.keySet().iterator().next());
                            cache.put(key, found);
                          }
                          listener.ready(query, new ArrayList<>(found), unavailable);
                        });
                  });
        };
    main.postDelayed(pending, 400);
  }

  /** Cached/device matches appear immediately; remote order follows without duplicate songs. */
  static List<Track> combine(String query, List<Track> device, List<Track> remote, int max) {
    String needle = RadioPolicy.normalize(query);
    LinkedHashMap<String, Track> unique = new LinkedHashMap<>();
    if (!needle.isEmpty()) {
      List<Track> matches = new ArrayList<>();
      for (Track t : device)
        if (RadioPolicy.normalize(t.title + " " + t.artist).contains(needle)) matches.add(t);
      matches.sort(
          Comparator.comparingInt(t -> RadioPolicy.normalize(t.title).startsWith(needle) ? 0 : 1));
      for (Track t :
          matches.subList(0, Math.min(remote.isEmpty() ? max : Math.min(3, max), matches.size())))
        unique.putIfAbsent(t.url, t);
    }
    for (Track t : remote)
      if (t != null && !t.url.isEmpty() && !t.title.trim().isEmpty()) unique.putIfAbsent(t.url, t);
    List<Track> out = new ArrayList<>(unique.values());
    return new ArrayList<>(out.subList(0, Math.min(max, out.size())));
  }

  void cancel() {
    generation++;
    if (pending != null) main.removeCallbacks(pending);
    if (running != null) running.cancel(true);
    pending = null;
    running = null;
  }

  public void close() {
    cancel();
    closed = true;
    cache.clear();
    executor.shutdownNow();
  }
}
