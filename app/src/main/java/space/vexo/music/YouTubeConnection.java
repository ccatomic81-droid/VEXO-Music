package space.vexo.music;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/** A separate, user-initiated read-only Google permission. Never piggybacks on VEXO sign-in. */
final class YouTubeConnection implements AutoCloseable {
  interface Player {
    void play(List<Track> tracks, int index);
  }

  private final ComponentActivity activity;
  private final YouTubeTaste taste;
  private final Runnable changed;
  private final Player player;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ActivityResultLauncher<IntentSenderRequest> permission;
  private Dialog dialog;
  private boolean busy, closed, awaitingConsent;
  private int generation;
  private Future<?> task;
  private YouTubeMusic api;
  private String status = "";
  private Boolean includePlaylists;

  YouTubeConnection(ComponentActivity activity, Bundle state, Runnable changed, Player player) {
    this.activity = activity;
    this.taste = new YouTubeTaste(activity);
    this.changed = changed;
    this.player = player;
    awaitingConsent = state != null && state.getBoolean("youtube_consent", false);
    busy = awaitingConsent;
    permission =
        activity.registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
              if (closed || !awaitingConsent) return;
              awaitingConsent = false;
              if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                finish("YouTube connection cancelled. Your existing music is unchanged.");
                return;
              }
              try {
                accept(
                    Identity.getAuthorizationClient(activity)
                        .getAuthorizationResultFromIntent(result.getData()),
                    generation);
              } catch (Exception unavailable) {
                finish("Google permission wasn’t completed. You can try again whenever you want.");
              }
            });
  }

  void saveState(Bundle state) {
    state.putBoolean("youtube_consent", awaitingConsent);
  }

  void show() {
    if (closed) return;
    if (dialog == null || !dialog.isShowing()) {
      dialog = new Dialog(activity);
      draw();
      dialog.show();
    } else draw();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      dialog
          .getWindow()
          .setLayout(-1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .82f));
    }
  }

  private void draw() {
    if (dialog == null) return;
    MusicSurface panel = new MusicSurface(activity);
    panel.setBackground(Glass.dialogBackdrop(activity));
    panel.setClipToOutline(true);
    panel.body.addView(panel.text("Your YouTube taste", 25, Visuals.TEXT, true));
    panel.gap(panel.body, 12);
    panel.body.addView(
        panel.text(
            "Use connected YouTube music to shape Home, discovery, related songs and radio. "
                + "This is optional and separate from your VEXO account.",
            14,
            Visuals.MUTED,
            false));
    panel.gap(panel.body, 12);
    panel.body.addView(
        panel.text(
            "Google shares read-only access to your YouTube account. VEXO reads up to 200 liked"
                + " videos and keeps up to 100 liked music tracks. If you enable playlists below,"
                + " it also checks up to 50 videos from each of 3 playlists you own and keeps up to"
                + " 100 music tracks. Imported metadata stays encrypted on this phone for 28 days."
                + " It does not read your watch history or change your YouTube account. YouTube’s"
                + " exact Home feed is not available here.",
            13,
            Visuals.MUTED,
            false));
    panel.gap(panel.body, 12);
    panel.body.addView(
        panel.text(
            "Google may show an unverified-app warning until VEXO’s YouTube permission is verified."
                + " You can cancel and keep using VEXO. This warning cannot be removed inside the"
                + " app.",
            12,
            Visuals.MUTED,
            false));
    panel.gap(panel.body, 18);
    YouTubeTaste.Snapshot snapshot;
    try {
      snapshot = taste.read();
    } catch (IllegalStateException unavailable) {
      snapshot = YouTubeTaste.Snapshot.empty();
      status = "Private storage is unavailable. Your existing library has been kept.";
    }
    if (!status.isEmpty()) {
      TextView message = panel.text(status, 14, Visuals.TEXT, false);
      message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
      panel.body.addView(message);
      panel.gap(panel.body, 14);
    }
    if (includePlaylists == null) includePlaylists = snapshot.playlistEnabled;
    android.widget.Switch playlists = new android.widget.Switch(activity);
    playlists.setText("Include music from my YouTube playlists");
    playlists.setTextColor(Visuals.TEXT);
    playlists.setTextSize(14);
    playlists.setMinHeight(panel.dp(56));
    playlists.setChecked(includePlaylists);
    playlists.setEnabled(!busy);
    playlists.setOnCheckedChangeListener((v, enabled) -> includePlaylists = enabled);
    panel.body.addView(playlists);
    panel.gap(panel.body, 12);
    TextView connect =
        panel.action(
            busy ? "Connecting…" : snapshot.connected ? "Refresh from YouTube" : "Connect YouTube",
            true,
            this::connect);
    connect.setEnabled(!busy);
    connect.setAlpha(busy ? .6f : 1);
    panel.body.addView(connect);
    if (busy || snapshot.connected) {
      panel.gap(panel.body, 10);
      if (busy) panel.body.addView(panel.action("Cancel connection", false, this::cancel));
      if (snapshot.connected) {
        panel.gap(panel.body, 10);
        panel.body.addView(
            panel.action("Disconnect & remove imported data", false, this::disconnect));
      }
    }
    if (snapshot.connected) {
      panel.gap(panel.body, 14);
      panel.body.addView(
          panel.text(
              snapshot.likes.size()
                  + " liked tracks · "
                  + snapshot.playlistSongs.size()
                  + " playlist tracks · updated "
                  + android.text.format.DateFormat.getDateFormat(activity)
                      .format(new java.util.Date(snapshot.at)),
              12,
              Visuals.MUTED,
              false));
      if (snapshot.sources().isEmpty()) {
        panel.gap(panel.body, 10);
        panel.body.addView(
            panel.text(
                "No matching music was found in this limited sample. Check that this Google "
                    + "account/channel has liked music, or enable playlist music and refresh. "
                    + "Home and radio can still learn from your VEXO favourites and interests.",
                14,
                Visuals.MUTED,
                false));
      } else {
        panel.gap(panel.body, 18);
        panel.section("VEXO picks for you", null, null);
        panel.body.addView(
            panel.text(
                "Suggested by VEXO using your connected YouTube music.", 12, Visuals.MUTED, false));
        panel.gap(panel.body, 10);
        if (snapshot.picks.isEmpty())
          panel.body.addView(
              panel.text(
                  "New recommendations couldn’t load. Your liked music is ready below; refresh to"
                      + " try again.",
                  13,
                  Visuals.MUTED,
                  false));
        songs(panel, snapshot.picks, 10);
        panel.gap(panel.body, 18);
        panel.section("Liked on YouTube", null, null);
        songs(panel, snapshot.likes, 100);
        if (!snapshot.playlistSongs.isEmpty()) {
          panel.gap(panel.body, 18);
          panel.section("Music from your YouTube playlists", null, null);
          songs(panel, snapshot.playlistSongs, 100);
        }
      }
    }
    panel.gap(panel.body, 18);
    panel.body.addView(
        panel.text(
            "Disconnect deletes this feature’s imported data from this phone. "
                + "To revoke Google’s permission, use Manage Google access.",
            12,
            Visuals.MUTED,
            false));
    panel.gap(panel.body, 10);
    panel.body.addView(
        panel.action(
            "Manage Google access", false, () -> open("https://myaccount.google.com/connections")));
    panel.gap(panel.body, 10);
    panel.body.addView(
        panel.action(
            "Google privacy policy", false, () -> open("https://policies.google.com/privacy")));
    panel.gap(panel.body, 10);
    panel.body.addView(panel.action("Close", false, () -> dialog.dismiss()));
    dialog.setContentView(panel);
  }

  private void songs(MusicSurface panel, List<Track> tracks, int limit) {
    for (int i = 0; i < Math.min(limit, tracks.size()); i++) {
      final int position = i;
      Track song = tracks.get(i);
      TextView row =
          panel.action(
              song.title + "\n" + song.artist,
              false,
              () -> {
                dialog.dismiss();
                player.play(tracks, position);
              });
      row.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
      row.setContentDescription("Play " + song.title + " by " + song.artist);
      panel.body.addView(row, new LinearLayout.LayoutParams(-1, -2));
      panel.gap(panel.body, 8);
    }
  }

  private void open(String url) {
    try {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (android.content.ActivityNotFoundException unavailable) {
      finish("No browser is available to open this link.");
    }
  }

  private void connect() {
    if (closed || busy) return;
    busy = true;
    status = "Choose your Google account and allow read-only YouTube access.";
    final int request = ++generation;
    draw();
    try {
      AuthorizationRequest scopes =
          AuthorizationRequest.builder()
              .setRequestedScopes(Collections.singletonList(new Scope(YouTubeMusic.SCOPE)))
              .build();
      Identity.getAuthorizationClient(activity)
          .authorize(scopes)
          .addOnSuccessListener(
              result -> {
                if (!current(request)) return;
                try {
                  if (result.hasResolution()) {
                    if (result.getPendingIntent() == null) throw new IllegalStateException();
                    awaitingConsent = true;
                    permission.launch(
                        new IntentSenderRequest.Builder(result.getPendingIntent()).build());
                  } else accept(result, request);
                } catch (Exception unavailable) {
                  finish(
                      "Google permission couldn’t open. Check Google Play services and try again.");
                }
              })
          .addOnFailureListener(
              error -> {
                if (current(request))
                  finish(
                      "YouTube connection couldn’t start. Check Google Play services and your"
                          + " connection. The app’s Google permission setup may need attention.");
              });
    } catch (Exception unavailable) {
      finish("Google Play services are unavailable. You can keep using VEXO without this feature.");
    }
  }

  private boolean current(int request) {
    return !closed && busy && generation == request && !activity.isDestroyed();
  }

  private void accept(AuthorizationResult result, int request) {
    if (!current(request)) return;
    String token = result.getAccessToken();
    if (token == null
        || token.isEmpty()
        || !result.getGrantedScopes().contains(YouTubeMusic.SCOPE)) {
      finish("Read-only YouTube permission wasn’t granted. Your music is unchanged.");
      return;
    }
    status = "Reading liked music and finding VEXO picks…";
    draw();
    api = new YouTubeMusic();
    YouTubeMusic client = api;
    final boolean playlistsAllowed = Boolean.TRUE.equals(includePlaylists);
    task =
        io.submit(
            () -> {
              try {
                List<Track> likes = client.likes(token);
                List<Track> playlistSongs = Collections.emptyList();
                boolean playlistFailed = false;
                if (playlistsAllowed) {
                  try {
                    playlistSongs = client.playlistMusic(token);
                  } catch (java.io.IOException unavailable) {
                    playlistFailed = true;
                  }
                }
                List<Track> sources = new java.util.ArrayList<>(likes);
                sources.addAll(playlistSongs);
                List<Track> picks = YouTubeMusic.picks(sources, OnlineMusic::relatedPreview);
                final List<Track> importedPlaylists = playlistSongs;
                final boolean partial = playlistFailed;
                activity.runOnUiThread(
                    () -> {
                      if (!current(request)) return;
                      try {
                        taste.save(
                            likes,
                            picks,
                            importedPlaylists,
                            playlistsAllowed,
                            System.currentTimeMillis());
                        changed.run();
                        finish(
                            partial
                                ? "Connected. Liked music imported; playlist music couldn’t load."
                                      + " Refresh to retry."
                                : sources.isEmpty()
                                    ? "Connected. No matching music was found in this sample."
                                    : "Connected · your music now helps shape Home and radio.");
                      } catch (IllegalStateException unavailable) {
                        finish(
                            "Couldn’t save private music data. Your previous data has been kept.");
                      }
                    });
              } catch (Exception error) {
                if (error instanceof java.io.IOException
                    && error.getMessage() != null
                    && error.getMessage().startsWith("Your Google permission expired"))
                  clearToken(token);
                String message =
                    error instanceof java.io.IOException
                            && error.getMessage() != null
                            && !error.getMessage().contains("\n")
                            && error.getMessage().length() < 220
                        ? error.getMessage()
                        : "Couldn’t load liked music. Check your connection and try again.";
                // Only our fixed messages are displayed; network exception details can contain
                // addresses.
                if (!(message.startsWith("YouTube ")
                    || message.startsWith("Google ")
                    || message.startsWith("Your Google ")
                    || message.startsWith("Couldn’t ")))
                  message = "Couldn’t load liked music. Check your connection and try again.";
                final String safe = message;
                activity.runOnUiThread(
                    () -> {
                      if (current(request)) finish(safe);
                    });
              }
            });
  }

  private void clearToken(String token) {
    try {
      Identity.getAuthorizationClient(activity)
          .clearToken(ClearTokenRequest.builder().setToken(token).build());
    } catch (Exception ignored) {
    }
  }

  private void finish(String message) {
    busy = false;
    awaitingConsent = false;
    status = message;
    if (dialog != null && dialog.isShowing()) draw();
  }

  void disconnect() {
    cancelWork();
    try {
      taste.clear();
      status = "Disconnected. Imported YouTube data and VEXO picks were removed from this phone.";
      changed.run();
    } catch (IllegalStateException unavailable) {
      status = "Couldn’t remove private data. Please try again.";
    }
    if (dialog != null && dialog.isShowing()) draw();
  }

  void cancel() {
    cancelWork();
    finish("Connection cancelled. Your previous music import is unchanged.");
  }

  private void cancelWork() {
    generation++;
    awaitingConsent = false;
    busy = false;
    if (api != null) api.cancel();
    if (task != null) task.cancel(true);
  }

  @Override
  public void close() {
    closed = true;
    generation++;
    if (api != null) api.cancel();
    if (task != null) task.cancel(true);
    io.shutdownNow();
    if (dialog != null) dialog.dismiss();
  }
}
