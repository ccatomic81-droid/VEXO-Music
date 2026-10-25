package space.vexo.music;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

@UnstableApi
public final class PlaybackService extends MediaSessionService {
  public static final String SLEEP = "space.vexo.music.SLEEP";
  public static final String CROSSFADE_CHANGED = "space.vexo.music.CROSSFADE_CHANGED";
  public static final String OFFLINE_SETTINGS = "space.vexo.music.OFFLINE_SETTINGS";
  public static final String NEW_QUEUE = "space.vexo.music.NEW_QUEUE";
  public static final String AUTOPLAY_CHANGED = "space.vexo.music.AUTOPLAY_CHANGED";
  public static final String CLEAR_UPCOMING = "space.vexo.music.CLEAR_UPCOMING";
  public static final String CLEAR_QUEUE = "space.vexo.music.CLEAR_QUEUE";
  private final java.util.concurrent.ExecutorService radioWorker =
      java.util.concurrent.Executors.newFixedThreadPool(2);
  public static final String START_RADIO = "space.vexo.music.START_RADIO";
  public static final String HIDE_RADIO = "space.vexo.music.HIDE_RADIO";
  private AutoplayManager autoplay;
  private RadioStore radioStore;

  private void startRadio(Track seed) {
    if (!RadioPolicy.music(seed)) return;
    autoplay.suspend();
    radioStore.anchor(seed);
    getSharedPreferences("settings", 0).edit().putBoolean("autoplay", true).apply();
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    player.setShuffleModeEnabled(false);
    player.setMediaItem(seed.mediaItem());
    autoplay.newQueue();
    player.prepare();
    player.play();
  }

  private void hideFromRadio(Track track) {
    radioStore.hide(track);
    // Existing manually queued songs and the current playback are respected.
    // Future refills re-rank with this feedback immediately.
    autoplay.settingsChanged();
  }

  private java.util.List<Track> smartSelect(
      Track anchor,
      Track seed,
      java.util.List<Track> candidates,
      java.util.List<Track> queue,
      java.util.List<Track> history,
      java.util.List<Track> favourites,
      java.util.Set<String> hidden,
      RadioPolicy.Mode mode,
      int limit) {
    try {
      SmartStore smart = new SmartStore(this);
      java.util.Set<String> excluded = new java.util.HashSet<>(hidden);
      excluded.addAll(SmartPolicy.blocked(smart, System.currentTimeMillis()));
      java.util.List<Track> ranked =
          SmartPolicy.rank(candidates, smart, history, mode == RadioPolicy.Mode.DISCOVER);
      return RadioPolicy.select(
          anchor, seed, ranked, queue, history, favourites, excluded, mode, limit, 0);
    } catch (IllegalStateException unavailable) {
      return RadioPolicy.select(
          anchor, seed, candidates, queue, history, favourites, hidden, mode, limit);
    }
  }

  private String lastSaveRequest = "";

  private java.util.List<Track> queueTracks() {
    java.util.List<Track> songs = new java.util.ArrayList<>();
    if (player != null)
      for (int i = 0; i < player.getMediaItemCount(); i++)
        songs.add(Track.from(player.getMediaItemAt(i)));
    return songs;
  }

  private void saveQueue() {
    if (player != null)
      new QueueStore(this)
          .save(queueTracks(), player.getCurrentMediaItemIndex(), player.getCurrentPosition());
  }

  private final Runnable saveQueueTask = this::saveQueue;
  private final Runnable positionCheckpoint =
      new Runnable() {
        @Override
        public void run() {
          if (player != null && player.isPlaying()) saveQueue();
          handler.postDelayed(this, 5000);
        }
      };

  private void clearUpcoming() {
    autoplay.suspend();
    int index = player.getCurrentMediaItemIndex();
    if (index >= 0 && index < player.getMediaItemCount()) {
      player.removeMediaItems(index + 1, player.getMediaItemCount());
      if (index > 0) player.removeMediaItems(0, index);
    }
  }

  private void maybeSave() {
    if (!OfflineLibrary.get(this).enabled()) {
      OfflineLibrary.get(this).cancel();
      lastSaveRequest = "";
      return;
    }
    MediaItem item = player.getCurrentMediaItem();
    if (player.isPlaying() && item != null && !item.mediaId.equals(lastSaveRequest)) {
      lastSaveRequest = item.mediaId;
      OfflineLibrary.get(this).request(Track.from(item));
    }
  }

  private MediaSession session;
  private CrossfadePlayer player;
  private ConnectionRecovery recovery;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable sleep =
      () -> {
        if (player != null) player.pause();
      };

  @Override
  public void onCreate() {
    super.onCreate();
    radioStore = new RadioStore(this);
    OfflineLibrary offline = OfflineLibrary.get(this);
    player = new CrossfadePlayer(this, offline.playbackFactory());
    recovery = new ConnectionRecovery(this, player);
    player.setRepeatMode(
        getSharedPreferences("settings", 0).getInt("repeat", Player.REPEAT_MODE_OFF));
    player.setShuffleModeEnabled(getSharedPreferences("settings", 0).getBoolean("shuffle", false));
    player.setPlaybackSpeed(
        Math.max(.5f, Math.min(2f, getSharedPreferences("settings", 0).getFloat("speed", 1f))));
    QueueStore.Snapshot restored = new QueueStore(this).read();
    if (!restored.tracks.isEmpty()) {
      java.util.List<MediaItem> items = new java.util.ArrayList<>();
      for (Track t : restored.tracks) items.add(t.mediaItem());
      player.setMediaItems(items, restored.index, restored.position);
      player.setPlayWhenReady(false);
    }
    autoplay =
        new AutoplayManager(
            new AutoplayManager.Queue() {
              public java.util.List<Track> tracks() {
                return queueTracks();
              }

              public int index() {
                return player.getCurrentMediaItemIndex();
              }

              public int remaining() {
                return Math.max(
                    0,
                    QueuePolicy.order(
                                player.getMediaItemCount(),
                                player.getCurrentMediaItemIndex(),
                                at ->
                                    player
                                        .getCurrentTimeline()
                                        .getNextWindowIndex(
                                            at,
                                            Player.REPEAT_MODE_OFF,
                                            player.getShuffleModeEnabled()))
                            .size()
                        - 1);
              }

              public boolean repeatOff() {
                return player.getRepeatMode() == Player.REPEAT_MODE_OFF;
              }

              public boolean wantsPlay() {
                return player.getPlayWhenReady();
              }

              public boolean ended() {
                return player.getPlaybackState() == Player.STATE_ENDED;
              }

              public void append(java.util.List<Track> songs) {
                int before = player.getMediaItemCount();
                boolean shuffle = player.getShuffleModeEnabled();
                androidx.media3.common.Timeline timeline = player.getCurrentTimeline();
                java.util.List<Integer> order =
                    QueuePolicy.order(
                        before,
                        timeline.getFirstWindowIndex(shuffle),
                        at -> timeline.getNextWindowIndex(at, Player.REPEAT_MODE_OFF, shuffle));
                java.util.List<MediaItem> items = new java.util.ArrayList<>();
                for (Track t : songs) items.add(t.mediaItem());
                player.addMediaItems(items);
                if (shuffle)
                  player.setShuffleOrder(
                      new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                          QueuePolicy.appendOrder(order, before, songs.size()), System.nanoTime()));
              }

              public void resumeAt(int index) {
                player.seekTo(index, 0);
                player.prepare();
                player.play();
              }
            },
            seed -> {
              Track anchor = radioStore.anchor();
              if (anchor == null) anchor = seed;
              return new RadioCatalogue(
                      new RadioCatalogue.Source() {
                        public java.util.List<Track> related(Track t) throws Exception {
                          return OnlineMusic.related(t);
                        }

                        public java.util.List<Track> search(String query) throws Exception {
                          return OnlineMusic.search(query).tracks;
                        }
                      })
                  .load(anchor, seed);
            },
            radioWorker,
            handler::post,
            () -> getSharedPreferences("settings", 0).getBoolean("autoplay", true),
            () -> new LibraryStore(this).tracks("history"),
            value ->
                getSharedPreferences("playback_state", 0)
                    .edit()
                    .putString("autoplay_status", radioStore.describe() + "\n" + value)
                    .apply(),
            (seed, candidates, now, history) ->
                smartSelect(
                    radioStore.anchor() == null ? seed : radioStore.anchor(),
                    seed,
                    candidates,
                    now,
                    history,
                    new LibraryStore(this).tracks("favourites"),
                    radioStore.hidden(),
                    radioStore.mode(),
                    10));
    autoplay.newQueue();
    handler.postDelayed(positionCheckpoint, 5000);
    player.addListener(
        new Player.Listener() {
          private String recorded = "";

          @Override
          public void onMediaItemTransition(MediaItem item, int reason) {
            recorded = "";
            recovery.reset();
          }

          @Override
          public void onPlayWhenReadyChanged(boolean ready, int reason) {
            if (!ready) recovery.cancel();
          }

          @Override
          public void onEvents(Player p, Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                || events.contains(Player.EVENT_IS_PLAYING_CHANGED) && p.isPlaying())
              lastSaveRequest = "";
            if (p.isPlaying()) recovery.recovered();
            maybeSave();
            if (player.getMediaItemCount() >= 90) {
              androidx.media3.common.Timeline timeline = player.getCurrentTimeline();
              java.util.List<Integer> order =
                  QueuePolicy.order(
                      player.getMediaItemCount(),
                      timeline.getFirstWindowIndex(player.getShuffleModeEnabled()),
                      at ->
                          timeline.getNextWindowIndex(
                              at, Player.REPEAT_MODE_OFF, player.getShuffleModeEnabled()));
              if (order.indexOf(player.getCurrentMediaItemIndex()) > 40)
                for (int at : QueuePolicy.oldPlayed(order, player.getCurrentMediaItemIndex(), 10))
                  player.removeMediaItem(at);
            }
            if (radioStore.anchor() == null && player.getCurrentMediaItem() != null) {
              Track seed = Track.from(player.getCurrentMediaItem());
              if (seed.online()) radioStore.anchor(seed);
            }
            autoplay.check();
            handler.removeCallbacks(saveQueueTask);
            handler.postDelayed(saveQueueTask, 500);
            MediaItem item = p.getCurrentMediaItem();
            if (p.isPlaying() && item != null && !item.mediaId.equals(recorded)) {
              try {
                SmartStore smart = new SmartStore(PlaybackService.this);
                if (!smart.preferences().optBoolean("pause_history")) {
                  new LibraryStore(PlaybackService.this).recent(Track.from(item));
                  Track played = Track.from(item);
                  long at = System.currentTimeMillis();
                  radioWorker.execute(
                      () -> {
                        try {
                          new SmartStore(PlaybackService.this).played(played, at);
                        } catch (IllegalStateException unavailable) {
                          /* Keep playback working; do not store plaintext. */
                        }
                      });
                }
              } catch (IllegalStateException unavailable) {
                /* Fail closed for private history. */
              }
              recorded = item.mediaId;
            }
          }

          @Override
          public void onPlayerError(androidx.media3.common.PlaybackException error) {
            AppDiagnostics.record(PlaybackService.this, "playback", error);
            OnlineMusic.clearCache();
            recovery.failed(error);
          }

          @Override
          public void onPlaybackParametersChanged(
              androidx.media3.common.PlaybackParameters parameters) {
            getSharedPreferences("settings", 0).edit().putFloat("speed", parameters.speed).apply();
          }

          @Override
          public void onRepeatModeChanged(int mode) {
            getSharedPreferences("settings", 0).edit().putInt("repeat", mode).apply();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean enabled) {
            getSharedPreferences("settings", 0).edit().putBoolean("shuffle", enabled).apply();
          }
        });
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    session =
        new MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession s, MediaSession.ControllerInfo c) {
                    if (c.getUid() != android.os.Process.myUid() && !c.isTrusted())
                      return MediaSession.ConnectionResult.reject();
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(s)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                .buildUpon()
                                .add(new SessionCommand(SLEEP, Bundle.EMPTY))
                                .add(new SessionCommand(OFFLINE_SETTINGS, Bundle.EMPTY))
                                .add(new SessionCommand(CROSSFADE_CHANGED, Bundle.EMPTY))
                                .add(new SessionCommand(NEW_QUEUE, Bundle.EMPTY))
                                .add(new SessionCommand(AUTOPLAY_CHANGED, Bundle.EMPTY))
                                .add(new SessionCommand(CLEAR_UPCOMING, Bundle.EMPTY))
                                .add(new SessionCommand(CLEAR_QUEUE, Bundle.EMPTY))
                                .add(new SessionCommand(START_RADIO, Bundle.EMPTY))
                                .add(new SessionCommand(HIDE_RADIO, Bundle.EMPTY))
                                .build())
                        .build();
                  }

                  @Override
                  public ListenableFuture<SessionResult> onCustomCommand(
                      MediaSession s,
                      MediaSession.ControllerInfo c,
                      SessionCommand command,
                      Bundle args) {
                    if (command.customAction.equals(START_RADIO)
                        || command.customAction.equals(HIDE_RADIO)) {
                      try {
                        Track seed =
                            Track.from(new org.json.JSONObject(args.getString("track", "")));
                        if (!RadioPolicy.music(seed))
                          return Futures.immediateFuture(
                              new SessionResult(
                                  androidx.media3.session.SessionError.ERROR_BAD_VALUE));
                        if (command.customAction.equals(START_RADIO)) startRadio(seed);
                        else hideFromRadio(seed);
                        return Futures.immediateFuture(
                            new SessionResult(SessionResult.RESULT_SUCCESS));
                      } catch (Exception e) {
                        return Futures.immediateFuture(
                            new SessionResult(
                                androidx.media3.session.SessionError.ERROR_BAD_VALUE));
                      }
                    }
                    if (command.customAction.equals(NEW_QUEUE)) {
                      Track anchor = null;
                      try {
                        Track selected =
                            Track.from(new org.json.JSONObject(args.getString("track", "")));
                        if (selected.online()) anchor = selected;
                      } catch (Exception ignored) {
                      }
                      radioStore.anchor(anchor);
                      autoplay.newQueue();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    if (command.customAction.equals(AUTOPLAY_CHANGED)) {
                      autoplay.settingsChanged();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    if (command.customAction.equals(CLEAR_UPCOMING)) {
                      clearUpcoming();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    if (command.customAction.equals(CLEAR_QUEUE)) {
                      autoplay.suspend();
                      player.stop();
                      player.clearMediaItems();
                      radioStore.anchor(null);
                      OfflineLibrary.get(PlaybackService.this).cancel();
                      saveQueue();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    if (command.customAction.equals(CROSSFADE_CHANGED)) {
                      player.crossfadeChanged();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    if (command.customAction.equals(OFFLINE_SETTINGS)) {
                      lastSaveRequest = "";
                      maybeSave();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    if (command.customAction.equals(SLEEP)) {
                      handler.removeCallbacks(sleep);
                      int minutes = args.getInt("minutes", 0);
                      if (minutes > 0) handler.postDelayed(sleep, Math.min(minutes, 180) * 60_000L);
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    return Futures.immediateFuture(
                        new SessionResult(
                            androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED));
                  }
                })
            .build();
  }

  @Override
  public MediaSession onGetSession(MediaSession.ControllerInfo controller) {
    return session;
  }

  @Override
  public void onDestroy() {
    if (recovery != null) recovery.close();
    if (autoplay != null) autoplay.close();
    radioWorker.shutdownNow();
    saveQueue();
    handler.removeCallbacksAndMessages(null);
    OfflineLibrary.get(this).cancel();
    if (session != null) {
      session.release();
      player.release();
    }
    super.onDestroy();
  }
}
