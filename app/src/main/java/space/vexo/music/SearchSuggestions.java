package space.vexo.music;

import android.os.Handler;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

/** Only the most recent edit can update the UI; queued obsolete work is cancelled. */
final class SearchSuggestions implements AutoCloseable {
  interface Source {
    List<String> fetch(String query, String country) throws Exception;
  }

  interface Listener {
    void ready(String query, List<String> suggestions, boolean unavailable);
  }

  static final Source ONLINE =
      (query, country) -> {
        OnlineMusic.initialize();
        SuggestionExtractor extractor = ServiceList.YouTube.getSuggestionExtractor();
        extractor.forceContentCountry(new ContentCountry(country));
        extractor.forceLocalization(new Localization(Locale.getDefault().getLanguage(), country));
        return extractor.suggestionList(query);
      };
  private final Handler main;
  private final ExecutorService executor;
  private final Source source;
  private final Map<String, List<String>> cache = new LinkedHashMap<>();
  private Runnable pending;
  private Future<?> running;
  private int generation;
  private boolean closed;

  SearchSuggestions(Handler main, ExecutorService executor, Source source) {
    this.main = main;
    this.executor = executor;
    this.source = source;
  }

  void request(String query, String country, Listener listener) {
    cancel();
    if (closed) return;
    final int token = generation;
    String term = SearchHistory.clean(query);
    if (term.length() < 2 || term.contains("://")) {
      listener.ready(term, Collections.emptyList(), false);
      return;
    }
    String key = country + ":" + term.toLowerCase(Locale.ROOT);
    List<String> hit = cache.get(key);
    if (hit != null) {
      listener.ready(term, new ArrayList<>(hit), false);
      return;
    }
    pending =
        () -> {
          pending = null;
          running =
              executor.submit(
                  () -> {
                    List<String> values;
                    boolean failed = false;
                    try {
                      values =
                          SearchHistory.merge(
                              "", Collections.emptyList(), source.fetch(term, country));
                    } catch (Exception e) {
                      values = Collections.emptyList();
                      failed = true;
                    }
                    final List<String> result = values;
                    final boolean unavailable = failed;
                    main.post(
                        () -> {
                          if (closed || token != generation) return;
                          if (!unavailable) {
                            if (cache.size() >= 30) cache.remove(cache.keySet().iterator().next());
                            cache.put(key, result);
                          }
                          listener.ready(term, new ArrayList<>(result), unavailable);
                        });
                  });
        };
    main.postDelayed(pending, 300);
  }

  void clearCache() {
    cancel();
    cache.clear();
  }

  void cancel() {
    generation++;
    if (pending != null) main.removeCallbacks(pending);
    pending = null;
    if (running != null) running.cancel(true);
    running = null;
  }

  @Override
  public void close() {
    cancel();
    closed = true;
    executor.shutdownNow();
    cache.clear();
  }
}
