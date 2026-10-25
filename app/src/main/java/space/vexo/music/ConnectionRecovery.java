package space.vexo.music;

import android.content.Context;
import android.net.*;
import android.os.*;
import androidx.media3.common.*;

/** Bounded network retries preserve the item/position and never override a user pause. */
@androidx.media3.common.util.UnstableApi
final class ConnectionRecovery implements AutoCloseable {
  private final Player player;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ConnectivityManager connectivity;
  private final Context context;
  private boolean pending, closed, registered;
  private int attempts;
  private String item = "";
  private long position;
  private final Runnable retry = this::retry;
  private final ConnectivityManager.NetworkCallback network =
      new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
          if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            main.post(
                () -> {
                  if (pending) {
                    main.removeCallbacks(retry);
                    main.postDelayed(retry, 500);
                  }
                });
        }
      };

  ConnectionRecovery(Context c, Player player) {
    this.context = c.getApplicationContext();
    this.player = player;
    connectivity = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    try {
      connectivity.registerDefaultNetworkCallback(network);
      registered = true;
    } catch (RuntimeException ignored) {
    }
  }

  void failed(PlaybackException e) {
    if (closed || !player.getPlayWhenReady() || player.getCurrentMediaItem() == null) return;
    if (e.errorCode != PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        && e.errorCode != PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return;
    if (attempts >= 4) {
      status("Connection recovery paused · tap Play to retry");
      return;
    }
    item = player.getCurrentMediaItem().mediaId;
    position = player.getCurrentPosition();
    pending = true;
    status("Connection interrupted · waiting to resume");
    main.removeCallbacks(retry);
    main.postDelayed(retry, Math.min(16000, 1000L << attempts));
  }

  private void status(String value) {
    context
        .getSharedPreferences("playback_state", 0)
        .edit()
        .putString("connection_status", value)
        .apply();
  }

  void recovered() {
    if (attempts > 0 || pending) {
      reset();
    }
  }

  private void retry() {
    if (closed
        || !pending
        || !player.getPlayWhenReady()
        || player.getCurrentMediaItem() == null
        || !player.getCurrentMediaItem().mediaId.equals(item)) {
      cancel();
      return;
    }
    if (attempts >= 4) {
      pending = false;
      status("Connection recovery paused · tap Play to retry");
      return;
    }
    NetworkCapabilities caps = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
    if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
      main.postDelayed(retry, 5000);
      return;
    }
    pending = false;
    attempts++;
    OnlineMusic.clearCache();
    player.seekTo(position);
    player.prepare();
    status("Resuming music…");
  }

  void cancel() {
    pending = false;
    main.removeCallbacks(retry);
  }

  void reset() {
    cancel();
    attempts = 0;
    status("");
  }

  @Override
  public void close() {
    closed = true;
    cancel();
    if (registered)
      try {
        connectivity.unregisterNetworkCallback(network);
      } catch (RuntimeException ignored) {
      }
  }
}
