package space.vexo.music;

import android.content.Context;
import android.os.*;
import androidx.media3.common.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

/** Persistent service-owned connection. Audio is streamed independently on each device. */
@androidx.media3.common.util.UnstableApi
final class RemoteListening implements AutoCloseable {
  private final Context context;
  private final Player player;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final SmartStore store;
  private final Runnable suspend;
  private WebSocket socket;
  private JSONObject session;
  private boolean closed, following = true, applying;
  private int generation, retries;
  private long offset;
  private boolean advancing;
  private String lastAdvance = "", finishedTrack = "";

  RemoteListening(Context c, Player p, Runnable suspend) {
    context = c.getApplicationContext();
    player = p;
    store = new SmartStore(c);
    this.suspend = suspend;
  }

  boolean applying() {
    return applying;
  }

  boolean active() {
    return session != null && !closed;
  }

  boolean host() {
    return active() && session.optBoolean("host");
  }

  private void status(String value) {
    context
        .getSharedPreferences("playback_state", 0)
        .edit()
        .putString("room_status", value)
        .putBoolean("room_active", active())
        .putBoolean("room_host", host())
        .apply();
  }

  void connect() {
    try {
      JSONObject saved = store.preferences().optJSONObject("room_session");
      if (saved == null || saved.optLong("expires") < System.currentTimeMillis()) {
        status("No active room");
        return;
      }
      session = saved;
      closed = false;
      following = store.preferences().optBoolean("room_follow", true);
      suspend.run();
      open();
    } catch (Exception e) {
      status("Private room data unavailable");
    }
  }

  private void open() {
    if (!active()) return;
    int token = ++generation;
    status("Connecting to listening room…");
    Request request =
        new Request.Builder()
            .url(
                CloudApi.BASE.replace("https:", "wss:")
                    + "/v1/rooms/"
                    + session.optString("room")
                    + "/socket")
            .header("Authorization", "Bearer " + session.optString("token"))
            .build();
    socket =
        OnlineMusic.HTTP.newWebSocket(
            request,
            new WebSocketListener() {
              @Override
              public void onOpen(WebSocket ws, Response r) {
                main.post(
                    () -> {
                      if (token != generation) return;
                      retries = 0;
                      status(host() ? "Hosting · live room" : "Connected · following host");
                      main.removeCallbacks(heartbeat);
                      main.postDelayed(heartbeat, 1000);
                    });
              }

              @Override
              public void onMessage(WebSocket ws, String text) {
                if (text.length() > 32768) return;
                main.post(
                    () -> {
                      if (token != generation || !active()) return;
                      try {
                        JSONObject value = new JSONObject(text);
                        if (value.optString("type").equals("error")) {
                          status(value.optString("error"));
                          advancing = false;
                          return;
                        }
                        if (!value.optString("type").equals("state")) return;
                        String advance = value.optString("advance");
                        boolean moved = !advance.equals(lastAdvance);
                        if (moved) {
                          lastAdvance = advance;
                          advancing = false;
                        }
                        offset = value.optLong("serverTime") - System.currentTimeMillis();
                        store.preference("room_public", value);
                        if (value.optBoolean("closed")) {
                          leave(false);
                          status("Room ended");
                          return;
                        }
                        if (!host() && following && !value.optBoolean("hostOnline", true)) {
                          applying = true;
                          player.pause();
                          applying = false;
                          status("Host disconnected · waiting for reconnection");
                          return;
                        }
                        apply(value.optJSONObject("playback"), moved);
                        status(
                            host()
                                ? "Hosting · "
                                    + value.optJSONArray("members").length()
                                    + " listeners"
                                : following
                                    ? "Live · following the host"
                                    : "Connected · personal playback");
                      } catch (Exception e) {
                        status("Couldn’t read room update");
                      }
                    });
              }

              @Override
              public void onClosed(WebSocket ws, int code, String reason) {
                main.post(() -> lost(token));
              }

              @Override
              public void onFailure(WebSocket ws, Throwable error, Response r) {
                main.post(
                    () -> {
                      if (r != null && (r.code() == 401 || r.code() == 403 || r.code() == 410)) {
                        if (token == generation) {
                          leave(false);
                          status("Room expired. Ask the host for a new invite.");
                        }
                      } else lost(token);
                    });
              }
            });
  }

  private void lost(int token) {
    if (token != generation || !active()) return;
    status("Room connection lost · reconnecting");
    main.removeCallbacks(heartbeat);
    if (++retries <= 6)
      main.postDelayed(
          () -> {
            if (token == generation && active()) open();
          },
          Math.min(30000, 1000L << retries));
    else status("Room disconnected · reopen live listening to reconnect");
  }

  private final Runnable heartbeat =
      new Runnable() {
        public void run() {
          if (!active()) return;
          if (host() && !advancing && !applying && player.getCurrentMediaItem() != null) {
            Track t = Track.from(player.getCurrentMediaItem());
            if (t.online())
              try {
                send(
                    new JSONObject()
                        .put("type", "playback")
                        .put("track", t.json())
                        .put("position", Math.max(0, player.getCurrentPosition()))
                        .put(
                            "playing",
                            player.getPlayWhenReady()
                                && player.getPlaybackState() != Player.STATE_ENDED));
              } catch (JSONException ignored) {
              }
          }
          main.postDelayed(this, 5000);
        }
      };

  void apply(JSONObject playback, boolean force) {
    if (playback == null || (!following && !host())) return;
    JSONObject o = playback.optJSONObject("track");
    if (o == null) return;
    Track t = Track.from(o);
    long target = playback.optLong("position");
    if (playback.optBoolean("playing"))
      target += Math.max(0, System.currentTimeMillis() + offset - playback.optLong("at"));
    target = Math.min(t.duration * 1000, Math.max(0, target));
    applying = true;
    try {
      boolean same =
          player.getCurrentMediaItem() != null
              && player.getCurrentMediaItem().mediaId.equals(t.url);
      if (!same || force) {
        finishedTrack = "";
        player.setMediaItem(t.mediaItem(), target);
        player.prepare();
      } else if (!host() && Math.abs(player.getCurrentPosition() - target) > 1500)
        player.seekTo(target);
      if (!host() || !same || force) player.setPlayWhenReady(playback.optBoolean("playing"));
    } finally {
      applying = false;
    }
  }

  void follow(boolean value) {
    following = value;
    try {
      store.preference("room_follow", value);
    } catch (IllegalStateException unavailable) {
      status("Couldn’t save room preferences");
    }
    if (value)
      try {
        JSONObject s = store.preferences().optJSONObject("room_public");
        if (s != null) apply(s.optJSONObject("playback"), false);
      } catch (Exception ignored) {
      }
    status(value ? "Following host playback" : "Personal playback · still in the room");
  }

  void send(JSONObject value) {
    if (active() && socket != null && !socket.send(value.toString()))
      status("Room connection is reconnecting");
  }

  void next() {
    if (host() && !advancing) {
      advancing = true;
      try {
        send(new JSONObject().put("type", "next"));
      } catch (JSONException ignored) {
      }
    }
  }

  void ended() {
    if (host() && !advancing && !applying && player.getCurrentMediaItem() != null) {
      String key = player.getCurrentMediaItem().mediaId + lastAdvance;
      if (!key.equals(finishedTrack)) {
        finishedTrack = key;
        next();
      }
    }
  }

  void leave(boolean notify) {
    if (!notify && following && !host()) {
      applying = true;
      player.pause();
      applying = false;
    }
    if (notify && active())
      try {
        send(new JSONObject().put("type", host() ? "end" : "leave"));
      } catch (JSONException ignored) {
      }
    generation++;
    main.removeCallbacks(heartbeat);
    if (socket != null) socket.close(1000, "Left room");
    socket = null;
    session = null;
    try {
      store.preference("room_session", JSONObject.NULL);
      store.preference("room_public", JSONObject.NULL);
    } catch (Exception ignored) {
    }
    status("Left listening room");
  }

  @Override
  public void close() {
    closed = true;
    generation++;
    main.removeCallbacksAndMessages(null);
    if (socket != null) socket.close(1000, "Service stopped");
    socket = null;
    session = null;
  }
}
