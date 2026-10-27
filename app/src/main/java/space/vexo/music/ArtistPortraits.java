package space.vexo.music;

import android.content.Context;
import android.os.Handler;
import java.util.*;
import java.util.concurrent.*;

/** One cancellable metadata worker, separate from song search and playback. */
final class ArtistPortraits implements AutoCloseable {
  interface Source {
    List<SearchArtists.Artist> resolve(SearchArtists.Artist artist, String country)
        throws Exception;
  }

  private final Context context;
  private final SearchArtists store;
  private final Handler main;
  private final ExecutorService io;
  private final Source source;
  private Future<?> task;
  private boolean running, closed;
  private int generation;

  ArtistPortraits(Context c, Handler main, ExecutorService io, Source source) {
    context = c.getApplicationContext();
    store = new SearchArtists(context);
    this.main = main;
    this.io = io;
    this.source = source;
  }

  void refresh(String country, Runnable changed) {
    if (closed
        || running
        || context.getSharedPreferences("settings", 0).getBoolean("data_saver", false)) return;
    List<SearchArtists.Artist> pending = store.entries();
    pending.removeIf(a -> !a.due(System.currentTimeMillis()));
    if (pending.isEmpty()) return;
    running = true;
    int token = ++generation;
    long revision = store.revision();
    task =
        io.submit(
            () -> {
              for (SearchArtists.Artist artist : pending) {
                if (Thread.currentThread().isInterrupted()
                    || store.revision() != revision
                    || context.getSharedPreferences("settings", 0).getBoolean("data_saver", false))
                  break;
                List<SearchArtists.Artist> result;
                try {
                  result = source.resolve(artist, country);
                } catch (Exception error) {
                  if (Thread.currentThread().isInterrupted()) break;
                  result =
                      Collections.singletonList(
                          new SearchArtists.Artist(
                              artist.id,
                              artist.name,
                              artist.artwork,
                              artist.queries,
                              System.currentTimeMillis(),
                              artist.legacy));
                }
                List<SearchArtists.Artist> delivered = result;
                main.post(
                    () -> {
                      if (!closed
                          && token == generation
                          && store.resolved(artist, delivered, revision)) changed.run();
                    });
              }
              main.post(
                  () -> {
                    if (!closed && token == generation) running = false;
                  });
            });
  }

  void retry() {
    generation++;
    if (task != null) task.cancel(true);
    running = false;
    store.retryPortraits();
  }

  @Override
  public void close() {
    closed = true;
    generation++;
    if (task != null) task.cancel(true);
    io.shutdownNow();
  }
}
