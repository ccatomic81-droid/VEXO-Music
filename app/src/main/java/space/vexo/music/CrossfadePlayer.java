package space.vexo.music;

import android.content.*;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;

/** Stable MediaSession-facing player with two ExoPlayer decks and one shared focus owner. */
@UnstableApi
public final class CrossfadePlayer extends ForwardingSimpleBasePlayer {
  private final Context context;
  private final DataSource.Factory factory;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private NativeDeck active;
  private final CrossfadeEngine engine;
  private final PlaybackFocus focus;
  private long revision;
  private float userVolume = 1, focusGain = 1;
  private boolean released;
  private final BroadcastReceiver noisy =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
          pause();
        }
      };

  private static ExoPlayer create(Context c, DataSource.Factory factory) {
    ExoPlayer player =
        new ExoPlayer.Builder(c)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(factory))
            .build();
    player.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build(),
        false);
    player.setWakeMode(C.WAKE_MODE_LOCAL);
    return player;
  }

  public CrossfadePlayer(Context c, DataSource.Factory source) {
    super(create(c, source));
    context = c.getApplicationContext();
    factory = source;
    active = new NativeDeck((ExoPlayer) getPlayer());
    engine =
        new CrossfadeEngine(
            new CrossfadeEngine.Host() {
              public CrossfadeEngine.Deck current() {
                return active;
              }

              public CrossfadeEngine.Deck prepareNext() {
                return prepareDeck();
              }

              public void promote(CrossfadeEngine.Deck deck) {
                active = (NativeDeck) deck;
                setPlayer(active.player);
              }
            },
            () -> context.getSharedPreferences("settings", 0).getInt("crossfade", 4));
    focus =
        PlaybackFocus.android(
            context,
            handler,
            new PlaybackFocus.Target() {
              public boolean wantsPlay() {
                return active.player.getPlayWhenReady();
              }

              public void pauseForFocus() {
                engine.interrupt();
                active.player.pause();
              }

              public void resumeAfterFocus() {
                active.player.play();
              }

              public void gain(float value) {
                focusGain = value;
                engine.master(userVolume * focusGain);
              }
            });
    androidx.core.content.ContextCompat.registerReceiver(
        context,
        noisy,
        new IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    handler.post(pulse);
  }

  private final Runnable pulse =
      new Runnable() {
        public void run() {
          if (released) return;
          engine.tick();
          handler.postDelayed(this, engine.blending() ? 25 : engine.preparing() ? 50 : 250);
        }
      };

  private NativeDeck prepareDeck() {
    ExoPlayer previous = active.player;
    int next = previous.getNextMediaItemIndex();
    if (next == C.INDEX_UNSET || next == previous.getCurrentMediaItemIndex()) return null;
    ExoPlayer candidate = create(context, factory);
    List<MediaItem> items = new ArrayList<>();
    for (int i = 0; i < previous.getMediaItemCount(); i++) items.add(previous.getMediaItemAt(i));
    candidate.setPlaybackParameters(previous.getPlaybackParameters());
    candidate.setRepeatMode(previous.getRepeatMode());
    candidate.setShuffleModeEnabled(previous.getShuffleModeEnabled());
    candidate.setVolume(0);
    candidate.setMediaItems(items, next, 0);
    Timeline timeline = previous.getCurrentTimeline();
    if (previous.getShuffleModeEnabled()) {
      List<Integer> order =
          QueuePolicy.order(
              items.size(),
              timeline.getFirstWindowIndex(true),
              at -> timeline.getNextWindowIndex(at, Player.REPEAT_MODE_OFF, true));
      candidate.setShuffleOrder(
          new ShuffleOrder.DefaultShuffleOrder(
              QueuePolicy.appendOrder(order, items.size(), 0), System.nanoTime()));
    }
    NativeDeck deck = new NativeDeck(candidate);
    deck.introPending = true;
    candidate.prepare();
    return deck;
  }

  private final class NativeDeck implements CrossfadeEngine.Deck {
    final ExoPlayer player;
    boolean disposed, introPending;

    NativeDeck(ExoPlayer player) {
      this.player = player;
      player.addListener(
          new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
              if (!released && active == NativeDeck.this && focus != null) {
                engine.interrupt();
                player.pause();
                focus.userPause();
              }
            }
          });
    }

    public String stamp() {
      return revision + ":" + player.getCurrentMediaItemIndex() + ":" + item();
    }

    public String item() {
      MediaItem item = player.getCurrentMediaItem();
      return item == null ? "" : item.mediaId;
    }

    public long position() {
      return player.getCurrentPosition();
    }

    public long duration() {
      return player.getDuration();
    }

    public float speed() {
      return player.getPlaybackParameters().speed;
    }

    public boolean playing() {
      return player.isPlaying();
    }

    public boolean ready() {
      if (introPending && player.getPlaybackState() == Player.STATE_READY) {
        introPending = false;
        try {
          MediaItem item = player.getCurrentMediaItem();
          boolean room =
              context.getSharedPreferences("playback_state", 0).getBoolean("room_active", false);
          long start =
              item == null
                  ? 0
                  : new PureSong(new SmartStore(context))
                      .crossfadeStart(Track.from(item), player.getDuration(), room);
          if (start > 0) {
            player.seekTo(start);
            return false;
          }
        } catch (IllegalStateException unavailable) {
          /* Keep the full version if private markers are unavailable. */
        }
      }
      return player.getPlaybackState() == Player.STATE_READY;
    }

    public boolean failed() {
      return player.getPlayerError() != null;
    }

    public boolean eligible() {
      return player.getDuration() > 0
          && player.isCurrentMediaItemSeekable()
          && !player.isCurrentMediaItemLive()
          && !player.isCurrentMediaItemDynamic()
          && !player.isPlayingAd();
    }

    public boolean hasNext() {
      int at = player.getNextMediaItemIndex();
      return player.getRepeatMode() != Player.REPEAT_MODE_ONE
          && at != C.INDEX_UNSET
          && at != player.getCurrentMediaItemIndex()
          && !player.getMediaItemAt(at).mediaId.equals(item());
    }

    public void play() {
      player.play();
    }

    public void volume(float value) {
      player.setVolume(value);
    }

    public void pauseAtEnd(boolean value) {
      player.setPauseAtEndOfMediaItems(value);
    }

    public void release() {
      if (!disposed) {
        disposed = true;
        player.release();
      }
    }
  }

  private void interrupt() {
    engine.interrupt();
    revision++;
  }

  private void changed() {
    engine.queueChanged();
    revision++;
  }

  public void crossfadeChanged() {
    if (!engine.blending()) engine.queueChanged();
  }

  public void setShuffleOrder(ShuffleOrder order) {
    changed();
    active.player.setShuffleOrder(order);
  }

  @Override
  protected State getState() {
    return super.getState().buildUpon().setVolume(userVolume).build();
  }

  @Override
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean play) {
    if (!play) {
      engine.interrupt();
      focus.userPause();
      return super.handleSetPlayWhenReady(false);
    }
    if (!focus.request()) return super.handleSetPlayWhenReady(false);
    return super.handleSetPlayWhenReady(true);
  }

  @Override
  protected ListenableFuture<?> handlePrepare() {
    if (active.player.getPlayWhenReady() && !focus.request()) active.player.pause();
    return super.handlePrepare();
  }

  @Override
  protected ListenableFuture<?> handleStop() {
    interrupt();
    focus.userPause();
    return super.handleStop();
  }

  @Override
  protected ListenableFuture<?> handleSeek(int index, long position, int command) {
    interrupt();
    return super.handleSeek(index, position, command);
  }

  @Override
  protected ListenableFuture<?> handleSetMediaItems(
      List<MediaItem> items, int index, long position) {
    interrupt();
    return super.handleSetMediaItems(items, index, position);
  }

  @Override
  protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> items) {
    changed();
    return super.handleAddMediaItems(index, items);
  }

  @Override
  protected ListenableFuture<?> handleMoveMediaItems(int from, int to, int index) {
    changed();
    return super.handleMoveMediaItems(from, to, index);
  }

  @Override
  protected ListenableFuture<?> handleRemoveMediaItems(int from, int to) {
    if (from <= getCurrentMediaItemIndex() && to > getCurrentMediaItemIndex()) interrupt();
    else changed();
    return super.handleRemoveMediaItems(from, to);
  }

  @Override
  protected ListenableFuture<?> handleReplaceMediaItems(int from, int to, List<MediaItem> items) {
    interrupt();
    return super.handleReplaceMediaItems(from, to, items);
  }

  @Override
  protected ListenableFuture<?> handleSetRepeatMode(int mode) {
    interrupt();
    return super.handleSetRepeatMode(mode);
  }

  @Override
  protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean enabled) {
    interrupt();
    return super.handleSetShuffleModeEnabled(enabled);
  }

  @Override
  protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters parameters) {
    interrupt();
    return super.handleSetPlaybackParameters(parameters);
  }

  @Override
  protected ListenableFuture<?> handleSetVolume(float value) {
    userVolume = value;
    engine.master(userVolume * focusGain);
    invalidateState();
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetAudioAttributes(
      AudioAttributes attributes, boolean handleFocus) {
    interrupt();
    return super.handleSetAudioAttributes(attributes, false);
  }

  @Override
  protected ListenableFuture<?> handleRelease() {
    if (!released) {
      released = true;
      handler.removeCallbacksAndMessages(null);
      engine.close();
      focus.close();
      context.unregisterReceiver(noisy);
      active.release();
    }
    return Futures.immediateVoidFuture();
  }
}
