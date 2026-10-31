package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;

final class PlaylistPanel extends MusicSurface {
  interface Listener {
    void back();

    void play(int index);

    void shuffle();

    void options(Track track);

    void add();

    void edit();

    void save();

    void retry();

    default void tune() {}
  }

  PlaylistPanel(
      Context c,
      String name,
      String detail,
      List<Track> songs,
      boolean mix,
      boolean editable,
      boolean loading,
      String error,
      int color,
      Listener listener) {
    super(c);
    LinearLayout top = row();
    Icon back = new Icon(c, "back", TEXT);
    back.setContentDescription("Back from playlist");
    back.setOnClickListener(v -> listener.back());
    top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView category = text(mix ? "VEXO MIX" : "YOUR COLLECTION", 11, LIME, true);
    category.setLetterSpacing(.13f);
    top.addView(category);
    body.addView(top);
    gap(body, 14);
    FrameLayout art = cover(name, songs, 168, color);
    LinearLayout.LayoutParams artp = new LinearLayout.LayoutParams(dp(168), dp(168));
    artp.gravity = Gravity.CENTER_HORIZONTAL;
    body.addView(art, artp);
    gap(body, 20);
    body.addView(text(name, 28, TEXT, true));
    gap(body, 8);
    body.addView(text(detail, 13, MUTED, false));
    gap(body, 6);
    body.addView(
        text(
            songs.size()
                + (songs.size() == 1 ? " song" : " songs")
                + (mix ? " · Music from YouTube" : " · Your collection"),
            12,
            MUTED,
            false));
    gap(body, 18);
    LinearLayout controls = row();
    TextView play = action("Play all", true, () -> listener.play(0));
    play.setEnabled(!songs.isEmpty());
    play.setAlpha(songs.isEmpty() ? .4f : 1);
    controls.addView(play, new LinearLayout.LayoutParams(0, -2, 1));
    TextView shuffle = action("Shuffle", false, listener::shuffle);
    shuffle.setEnabled(!songs.isEmpty());
    shuffle.setAlpha(songs.isEmpty() ? .4f : 1);
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
    sp.setMargins(dp(10), 0, 0, 0);
    controls.addView(shuffle, sp);
    body.addView(controls);
    gap(body, 12);
    if (mix) {
      LinearLayout tuning = row();
      tuning.addView(action("Tune My Mix", false, listener::tune), new LinearLayout.LayoutParams(0, -2, 1));
      TextView refresh = action("Refresh mix", false, listener::retry);
      refresh.setEnabled(!loading); refresh.setAlpha(loading ? .4f : 1);
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1);
      rp.leftMargin = dp(10); tuning.addView(refresh, rp); body.addView(tuning);
      gap(body, 10);
      TextView save = action("Save to your playlists", false, listener::save);
      save.setEnabled(!songs.isEmpty());
      save.setAlpha(songs.isEmpty() ? .4f : 1);
      body.addView(save);
      gap(body, 10);
    }
    if (editable) {
      LinearLayout editing = row();
      editing.addView(
          action("Add songs", false, listener::add), new LinearLayout.LayoutParams(0, -2, 1));
      TextView edit = action("Edit playlist", false, listener::edit);
      LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, -2, 1);
      ep.setMargins(dp(10), 0, 0, 0);
      editing.addView(edit, ep);
      body.addView(editing);
      gap(body, 10);
    }
    if (loading) {
      body.addView(
          text(
              songs.isEmpty() ? "Finding songs for this mix…" : "Refreshing this mix…",
              13,
              MUTED,
              false));
      gap(body, 10);
    }
    if (!error.isEmpty()) {
      body.addView(text(error, 13, MUTED, false));
      gap(body, 8);
      if (!mix) body.addView(action("Retry", false, listener::retry));
      gap(body, 10);
    }
    if (songs.isEmpty() && !loading && error.isEmpty())
      body.addView(
          text(
              editable
                  ? "Add a song to start your playlist."
                  : "Find songs to start this collection.",
              14,
              MUTED,
              false));
    if (songs.isEmpty() && !mix && !editable) {
      gap(body, 12);
      body.addView(action("Find music", true, listener::add));
    }
    for (int i = 0; i < songs.size(); i++) {
      Track song = songs.get(i);
      int index = i;
      LinearLayout row = row();
      row.setPadding(0, dp(8), 0, dp(8));
      row.setMinimumHeight(dp(72));
      row.addView(cover(song.title, Collections.singletonList(song), 48, color));
      LinearLayout labels = column();
      labels.setPadding(dp(12), 0, dp(4), 0);
      TextView title = text(song.title, 15, TEXT, true);
      title.setMaxLines(2);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      labels.addView(title);
      gap(labels, 5);
      TextView by = text(song.artist, 12, MUTED, false);
      by.setMaxLines(2);
      labels.addView(by);
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      Icon playSong = new Icon(c, "play", LIME);
      playSong.setContentDescription("Play " + song.title);
      playSong.setOnClickListener(v -> listener.play(index));
      row.addView(playSong, new LinearLayout.LayoutParams(dp(48), dp(48)));
      Icon more = new Icon(c, "more", MUTED);
      more.setContentDescription("Options for " + song.title);
      more.setOnClickListener(v -> listener.options(song));
      row.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
      row.setContentDescription("Play " + song.title + " by " + song.artist);
      row.setOnClickListener(v -> listener.play(index));
      body.addView(row);
    }
  }
}
