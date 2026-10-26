package space.vexo.music;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Fetches ahead of the end and rejects stale results after a song/queue change. */
public final class AutoplayManager {
  public interface Queue {
    List<Track> tracks();

    int index();

    boolean repeatOff();

    boolean wantsPlay();

    boolean ended();

    void append(List<Track> songs);

    void resumeAt(int index);

    default int remaining() {
      return Math.max(0, tracks().size() - index() - 1);
    }
  }

  public interface Loader {
    List<Track> load(Track seed) throws Exception;
  }

  public interface Selector {
    List<Track> select(Track seed, List<Track> candidates, List<Track> queue, List<Track> recent);
  }

  private final Selector selector;
  private final Queue queue;
  private final Loader loader;
  private final Executor worker, ui;
  private final BooleanSupplier enabled;
  private final Supplier<List<Track>> recent;
  private final Consumer<String> status;
  private boolean loading, closed, suspended;
  private long generation;
  private String attempted = "";
  private java.util.concurrent.Future<?> pending;

  private void invalidate() {
    generation++;
    loading = false;
    if (pending != null) pending.cancel(true);
    pending = null;
  }

  public AutoplayManager(
      Queue queue,
      Loader loader,
      Executor worker,
      Executor ui,
      BooleanSupplier enabled,
      Supplier<List<Track>> recent,
      Consumer<String> status) {
    this(
        queue,
        loader,
        worker,
        ui,
        enabled,
        recent,
        status,
        (seed, candidates, now, history) -> QueuePolicy.fresh(candidates, now, history, 10));
  }

  public AutoplayManager(
      Queue queue,
      Loader loader,
      Executor worker,
      Executor ui,
      BooleanSupplier enabled,
      Supplier<List<Track>> recent,
      Consumer<String> status,
      Selector selector) {
    this.selector = selector;
    this.queue = queue;
    this.loader = loader;
    this.worker = worker;
    this.ui = ui;
    this.enabled = enabled;
    this.recent = recent;
    this.status = status;
  }

  public void newQueue() {
    invalidate();
    suspended = false;
    attempted = "";
    status.accept(
        enabled.getAsBoolean() ? "Autoplay on · related songs follow your queue" : "Autoplay off");
  }

  public void suspend() {
    invalidate();
    suspended = true;
    status.accept("Autoplay paused for this queue");
  }

  public void settingsChanged() {
    newQueue();
    check();
  }

  public void close() {
    closed = true;
    invalidate();
  }

  public void check() {
    if (closed
        || suspended
        || !enabled.getAsBoolean()
        || loading
        || !queue.repeatOff()
        || !queue.wantsPlay()) return;
    List<Track> tracks = queue.tracks();
    int index = queue.index();
    if (index < 0
        || index >= tracks.size()
        || queue.remaining() > 2
        || tracks.size() >= QueuePolicy.MAX_ITEMS) return;
    Track seed = tracks.get(index);
    String seedId = QueuePolicy.identity(seed.url);
    if (!seed.online() || attempted.equals(seedId)) return;
    attempted = seedId;
    loading = true;
    long request = ++generation;
    status.accept("Finding related songs…");
    Runnable fetch =
        () -> {
          List<Track> candidates;
          try {
            candidates = loader.load(seed);
          } catch (Exception e) {
            ui.execute(
                () -> {
                  if (!closed && request == generation) {
                    loading = false;
                    List<Track> now = queue.tracks();
                    int at = queue.index();
                    if (at >= 0
                        && at < now.size()
                        && !seedId.equals(QueuePolicy.identity(now.get(at).url))) {
                      attempted = "";
                      check();
                      return;
                    }
                    status.accept("Couldn’t load related songs · retry from Up next");
                  }
                });
            return;
          }
          ui.execute(
              () -> {
                if (closed || request != generation) return;
                loading = false;
                if (suspended || !enabled.getAsBoolean() || !queue.repeatOff()) return;
                List<Track> now = queue.tracks();
                int current = queue.index();
                if (current < 0 || current >= now.size()) return;
                if (!seedId.equals(QueuePolicy.identity(now.get(current).url))) {
                  attempted = "";
                  check();
                  return;
                }
                if (queue.remaining() > 2) return;
                List<Track> songs = selector.select(seed, candidates, now, recent.get());
                if (songs.isEmpty()) {
                  status.accept("No new related songs available");
                  return;
                }
                boolean restart = queue.ended() && queue.wantsPlay();
                int next = now.size();
                queue.append(songs);
                status.accept("Autoplay added " + songs.size() + " related songs");
                if (restart) queue.resumeAt(next);
              });
        };
    if (worker instanceof java.util.concurrent.ExecutorService)
      pending = ((java.util.concurrent.ExecutorService) worker).submit(fetch);
    else worker.execute(fetch);
  }
}
