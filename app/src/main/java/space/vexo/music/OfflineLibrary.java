package space.vexo.music;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.exoplayer.offline.ProgressiveDownloader;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Persistent, bounded offline audio. Only completely saved streams enter the Offline library. */
@UnstableApi
public final class OfflineLibrary {
  private static OfflineLibrary instance;

  public static synchronized OfflineLibrary get(Context c) {
    if (instance == null)
      instance =
          new OfflineLibrary(
              c.getApplicationContext(),
              network(c.getApplicationContext()),
              new File(c.getFilesDir(), "offline-audio"),
              500L * 1024 * 1024, false);
    return instance;
  }

  static DataSource.Factory network(Context c) {
    DefaultHttpDataSource.Factory http =
        new DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setContentTypePredicate(
                type ->
                    type == null
                        || (!type.toLowerCase(Locale.ROOT).startsWith("text/")
                            && !type.toLowerCase(Locale.ROOT).contains("json")));
    DefaultDataSource.Factory base = new DefaultDataSource.Factory(c, http);
    return () ->
        new ResolvingDataSource(
            base.createDataSource(),
            new ResolvingDataSource.Resolver() {
              String original = "";
              boolean saver;

              @Override
              public DataSpec resolveDataSpec(DataSpec spec) throws IOException {
                String host = spec.uri.getHost();
                if (host != null
                    && (host.equals("www.youtube.com")
                        || host.equals("youtube.com")
                        || host.equals("music.youtube.com")
                        || host.equals("youtu.be"))) {
                  if (!original.equals(spec.uri.toString())) {
                    original = spec.uri.toString();
                    saver = c.getSharedPreferences("settings", 0).getBoolean("data_saver", false);
                  }
                  return spec.withUri(Uri.parse(OnlineMusic.resolve(original, saver)));
                }
                original = "";
                return spec;
              }

              @Override
              public Uri resolveReportedUri(Uri resolved) {
                return original.isEmpty() ? resolved : Uri.parse(original);
              }
            });
  }

  private final Context context;
  private final LibraryStore store;
  private final SimpleCache cache;
  private final StandaloneDatabaseProvider database;
  private final DataSource.Factory upstream;
  private final long budget;
  private final boolean downloadAuthorized;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Set<Runnable> listeners = new CopyOnWriteArraySet<>();
  private final Handler main = new Handler(Looper.getMainLooper());
  private Future<?> task;
  private ProgressiveDownloader downloader;
  private volatile long generation;
  private String active = "";
  private volatile String state = "Songs save automatically while playing";

  OfflineLibrary(Context c, DataSource.Factory upstream, File directory, long budget) {
    this(c, upstream, directory, budget, false);
  }

  OfflineLibrary(Context c, DataSource.Factory upstream, File directory, long budget, boolean downloadAuthorized) {
    context = c;
    store = new LibraryStore(c);
    this.upstream = upstream;
    this.budget = budget;
    this.downloadAuthorized = downloadAuthorized;
    database = new StandaloneDatabaseProvider(c);
    cache = new SimpleCache(directory, new NoOpCacheEvictor(), database);
  }

  private CacheDataSource.Factory factory(boolean write, boolean offlineOnly) {
    CacheDataSource.Factory f =
        new CacheDataSource.Factory()
            .setCache(cache)
            .setFlags(write ? CacheDataSource.FLAG_BLOCK_ON_CACHE : 0);
    if (!offlineOnly) f.setUpstreamDataSourceFactory(upstream);
    if (!write) f.setCacheWriteDataSinkFactory(null);
    return f;
  }

  public DataSource.Factory playbackFactory() {
    return factory(false, false);
  }

  DataSource.Factory offlineOnlyFactory() {
    return factory(false, true);
  }

  public boolean enabled() {
    return context.getSharedPreferences("settings", 0).getBoolean("auto_save", true);
  }

  public long bytes() {
    return cache.getCacheSpace();
  }

  public String status() {
    return state;
  }

  public void addListener(Runnable r) {
    listeners.add(r);
  }

  public void removeListener(Runnable r) {
    listeners.remove(r);
  }

  private void changed() {
    for (Runnable r : listeners) main.post(r);
  }

  public boolean complete(String key) {
    long length = ContentMetadata.getContentLength(cache.getContentMetadata(key));
    return length > 0 && cache.isCached(key, 0, length);
  }

  public synchronized List<Track> saved() {
    List<Track> result = new ArrayList<>();
    for (Track t : store.tracks("offline")) if (complete(t.url)) result.add(t);
    return result;
  }

  public synchronized void request(Track track) {
    if (!enabled() || !track.online() || active.equals(track.url)) return;
    // Default production source has no provider-authorized download entitlement.
    if (!downloadAuthorized) { state = "Offline saves require a provider-authorized source. Imported audio stays available."; changed(); return; }
    if (complete(track.url)) {
      if (!store.contains("offline", track)) {
        List<Track> songs = store.tracks("offline");
        songs.add(0, track);
        store.save("offline", songs);
        changed();
      }
      return;
    }
    cancel();
    long token = ++generation;
    active = track.url;
    state = "Saving “" + track.title + "” for offline listening";
    changed();
    task =
        worker.submit(
            () -> {
              boolean success = false;
              try {
                if (bytes() >= budget
                    || context.getFilesDir().getUsableSpace() < 100L * 1024 * 1024)
                  throw new StorageLimitException();
                ProgressiveDownloader next =
                    new ProgressiveDownloader(
                        track.mediaItem(), factory(true, false), Runnable::run);
                synchronized (this) {
                  if (token != generation) return;
                  downloader = next;
                }
                next.download(
                    (length, downloaded, percent) -> {
                      if (Thread.currentThread().isInterrupted()
                          || token != generation
                          || !enabled()) throw new CancellationException();
                      if (bytes() > budget
                          || (length > 0 && bytes() + Math.max(0, length - downloaded) > budget))
                        throw new StorageLimitException();
                    });
                if (bytes() > budget) throw new StorageLimitException();
                if (!complete(track.url)) throw new IOException("Audio was not completely saved");
                synchronized (this) {
                  if (token != generation) return;
                  List<Track> songs = store.tracks("offline");
                  songs.removeIf(t -> t.url.equals(track.url));
                  songs.add(0, track);
                  store.save("offline", songs);
                  state = "Saved “" + track.title + "” · available offline";
                  success = true;
                }
              } catch (Exception error) {
                if (!Thread.currentThread().isInterrupted()
                    && !(error instanceof CancellationException))
                  AppDiagnostics.record(context, "offline_save", error);
                synchronized (this) {
                  if (token == generation)
                    state =
                        error instanceof StorageLimitException
                            ? "Storage limit reached · remove offline songs to make room"
                            : Thread.currentThread().isInterrupted()
                                    || error instanceof CancellationException
                                ? "Auto-save paused"
                                : "Couldn’t save audio · listen again to retry";
                }
              } finally {
                if (!success && !complete(track.url)) cache.removeResource(track.url);
                synchronized (this) {
                  if (token == generation) {
                    downloader = null;
                    active = "";
                    task = null;
                  }
                }
                changed();
              }
            });
  }

  public synchronized void cancel() {
    generation++;
    if (downloader != null) downloader.cancel();
    if (task != null) task.cancel(true);
    downloader = null;
    task = null;
    active = "";
    state = enabled() ? "Auto-save paused" : "Auto-save is off";
    changed();
  }

  public synchronized void remove(String key) {
    if (key.equals(active)) cancel();
    cache.removeResource(key);
    List<Track> songs = store.tracks("offline");
    songs.removeIf(t -> t.url.equals(key));
    store.save("offline", songs);
    changed();
  }

  public synchronized void clear() {
    cancel();
    worker.submit(
        () -> {
          synchronized (this) {
            for (String key : new HashSet<>(cache.getKeys())) cache.removeResource(key);
            store.save("offline", Collections.emptyList());
            state = "Offline storage cleared";
          }
          changed();
        });
  }

  // Test-only lifetime control. Production has one cache instance for the app process.
  void close() throws InterruptedException {
    cancel();
    worker.shutdownNow();
    worker.awaitTermination(5, TimeUnit.SECONDS);
    cache.release();
    database.close();
  }

  boolean awaitIdle() throws Exception {
    Future<?> f;
    synchronized (this) {
      f = task;
    }
    if (f != null) f.get(15, TimeUnit.SECONDS);
    return true;
  }

  private static final class StorageLimitException extends RuntimeException {}
}
