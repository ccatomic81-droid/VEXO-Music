package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

final class ArtistPanel extends MusicSurface {
  interface Listener {
    void back();
    void follow();
    void play(int index);
    void shuffle();
    void options(Track song);
    void more();
    void retry();
  }

  ArtistPanel(Context c, SearchArtists.Artist artist, List<Track> songs, boolean following,
      boolean loading, boolean more, String error, Listener listener) {
    super(c);
    setBackgroundColor(BG);
    LinearLayout top = row();
    Icon back = new Icon(c, "back", TEXT);
    back.setContentDescription("Back from artist");
    back.setOnClickListener(v -> listener.back());
    top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView category = text("ARTIST", 11, LIME, true);
    category.setLetterSpacing(.13f);
    top.addView(category);
    body.addView(top);
    gap(body, 12);
    LinearLayout hero = column();
    hero.setPadding(dp(18), dp(24), dp(18), dp(24));
    hero.setBackground(round(SELECTED, dp(24)));
    LinearLayout.LayoutParams photo = new LinearLayout.LayoutParams(dp(152), dp(152));
    photo.gravity = Gravity.CENTER_HORIZONTAL;
    hero.addView(ArtistsPanel.portrait(c, artist), photo);
    gap(hero, 22);
    TextView name = text(artist.name, 30, TEXT, true);
    name.setGravity(Gravity.CENTER);
    hero.addView(name);
    gap(hero, 8);
    TextView subtitle = text("Songs & collaborations", 13, MUTED, false);
    subtitle.setGravity(Gravity.CENTER);
    hero.addView(subtitle);
    body.addView(hero);
    gap(body, 18);
    LinearLayout controls = row();
    TextView follow = action(following ? "Following" : "Follow", false, listener::follow);
    follow.setEnabled(!artist.id.isEmpty());
    follow.setAlpha(artist.id.isEmpty() ? .4f : 1);
    controls.addView(follow, new LinearLayout.LayoutParams(0, -2, 1));
    TextView play = action("Play all", true, () -> listener.play(0));
    play.setEnabled(!songs.isEmpty());
    play.setAlpha(songs.isEmpty() ? .4f : 1);
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -2, 1);
    pp.leftMargin = dp(10);
    controls.addView(play, pp);
    Icon shuffle = new Icon(c, "shuffle", TEXT);
    shuffle.setContentDescription("Shuffle artist songs");
    shuffle.setEnabled(!songs.isEmpty());
    shuffle.setAlpha(songs.isEmpty() ? .4f : 1);
    shuffle.setOnClickListener(v -> listener.shuffle());
    controls.addView(shuffle, new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(controls);
    gap(body, 24);
    section("Songs", songs.isEmpty() ? null : songs.size() + " found", () -> {});
    if (loading) {
      body.addView(text(songs.isEmpty() ? "Loading artist songs…" : "Loading more songs…",
          14, MUTED, false));
      gap(body, 12);
    }
    if (!error.isEmpty()) {
      body.addView(text(error, 14, MUTED, false));
      gap(body, 12);
      body.addView(action("Try again", false, listener::retry));
    } else if (!loading && songs.isEmpty()) {
      body.addView(text("No credited songs found yet. Try loading more or come back later.",
          14, MUTED, false));
    }
    for (int i = 0; i < songs.size(); i++) {
      Track song = songs.get(i);
      int index = i;
      LinearLayout item = row();
      item.setPadding(0, dp(10), 0, dp(10));
      item.addView(cover(song.title, Collections.singletonList(song), 48, SELECTED));
      LinearLayout labels = column();
      labels.setPadding(dp(12), 0, dp(4), 0);
      TextView title = text(song.title, 15, TEXT, true);
      title.setMaxLines(2);
      title.setEllipsize(TextUtils.TruncateAt.END);
      labels.addView(title);
      gap(labels, 5);
      TextView by = text(song.artist, 12, MUTED, false);
      by.setMaxLines(2);
      by.setEllipsize(TextUtils.TruncateAt.END);
      labels.addView(by);
      item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      Icon options = new Icon(c, "more", MUTED);
      options.setContentDescription("Options for " + song.title);
      options.setOnClickListener(v -> listener.options(song));
      item.addView(options, new LinearLayout.LayoutParams(dp(48), dp(48)));
      item.setContentDescription("Play " + song.title + " by " + song.artist);
      item.setFocusable(true);
      item.setOnClickListener(v -> listener.play(index));
      body.addView(item);
    }
    if (more && !loading && error.isEmpty()) {
      gap(body, 14);
      body.addView(action("Load more songs", false, listener::more));
    }
  }
}
