package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;

final class MusicHomePanel extends MusicSurface {
  private TextView greeting;
  private FrameLayout profileAvatar;
  private long avatarVersion = Long.MIN_VALUE;

  void personalize() {
    UserProfile profile = new UserProfile(getContext());
    if (greeting != null)
      greeting.setText(
          profile.complete() ? profile.greeting() + ", " + profile.name() : "Made for your day.");
    if (profileAvatar != null && profile.photoVersion() != avatarVersion) {
      avatarVersion = profile.photoVersion();
      profileAvatar.removeAllViews();
      android.graphics.Bitmap photo = profile.avatar();
      if (photo != null) {
        ImageView image = new ImageView(getContext());
        image.setImageBitmap(photo);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profileAvatar.addView(image, new FrameLayout.LayoutParams(-1, -1));
      } else {
        String name = profile.name();
        TextView initial =
            text(
                name.isEmpty()
                    ? "V"
                    : new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.ROOT),
                20,
                BG,
                true);
        initial.setGravity(Gravity.CENTER);
        profileAvatar.addView(initial, new FrameLayout.LayoutParams(-1, -1));
      }
    }
  }

  private final Map<String, LinearLayout> mixCards = new HashMap<>();

  void updateMix(MixCatalogue.Mix mix, List<Track> songs) {
    LinearLayout tile = mixCards.get(mix.id);
    if (tile == null || songs.isEmpty()) return;
    tile.removeViewAt(0);
    tile.addView(cover(mix.name, songs, shelfCardSize(), mix.color), 0);
  }

  interface Listener {
    default void smart() {}

    void region();

    void profile();

    void refresh();

    void search();

    void library(String shelf);

    void collection(String name, String key);

    void mix(MixCatalogue.Mix mix);

    void create();

    void play(List<Track> songs, int index);

    void filter(String filter);
  }

  MusicHomePanel(
      Context c,
      LibraryStore store,
      MixCatalogue catalogue,
      RegionalFeed feed,
      String country,
      boolean loading,
      String error,
      String filter,
      Listener listener) {
    super(c);
    LinearLayout brand = row();
    profileAvatar = new FrameLayout(c);
    android.graphics.drawable.GradientDrawable avatarBackground =
        new android.graphics.drawable.GradientDrawable();
    avatarBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    avatarBackground.setColor(LIME);
    profileAvatar.setBackground(avatarBackground);
    profileAvatar.setClipToOutline(true);
    profileAvatar.setContentDescription("Edit your profile");
    profileAvatar.setOnClickListener(v -> listener.profile());
    brand.addView(profileAvatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView word = text("VEXO MUSIC", 12, TEXT, true);
    word.setLetterSpacing(.12f);
    word.setPadding(dp(12), 0, 0, 0);
    brand.addView(word, new LinearLayout.LayoutParams(0, -2, 1));
    TextView region = action(RegionPolicy.name(country) + " ▾", false, listener::region);
    region.setTextSize(11);
    region.setPadding(dp(10), dp(12), dp(10), dp(12));
    region.setMaxWidth(dp(108));
    region.setMaxLines(2);
    region.setContentDescription("Change music region. Current: " + RegionPolicy.name(country));
    brand.addView(region);
    Icon refresh = new Icon(c, "refresh", MUTED);
    refresh.setGlyphSize(20);
    refresh.setContentDescription("Refresh Home");
    refresh.setTooltipText("Refresh music");
    refresh.setOnClickListener(v -> listener.refresh());
    brand.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(brand);
    gap(body, 22);
    LinearLayout welcome = column();
    android.graphics.drawable.GradientDrawable glow =
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[] {0xff29362b, 0xff141e21});
    glow.setCornerRadius(dp(20));
    glow.setStroke(dp(1), 0xff344638);
    welcome.setBackground(glow);
    welcome.setPadding(dp(18), dp(20), dp(18), dp(20));
    TextView eyebrow = text("YOUR DAILY SOUNDTRACK", 10, LIME, true);
    eyebrow.setLetterSpacing(.12f);
    welcome.addView(eyebrow);
    gap(welcome, 10);
    greeting = text("", 26, TEXT, true);
    greeting.setMaxLines(3);
    greeting.setEllipsize(android.text.TextUtils.TruncateAt.END);
    welcome.addView(greeting);
    gap(welcome, 10);
    TextView subtitle = text("A little music. A better day.", 13, 0xffb8c6ba, false);
    welcome.addView(subtitle);
    body.addView(welcome, new LinearLayout.LayoutParams(-1, -2));
    personalize();
    gap(body, 18);
    HorizontalScrollView filters = new HorizontalScrollView(c);
    filters.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = row();
    for (String choice : new String[] {"All", "Music", "Playlists"}) {
      TextView chip = action(choice, choice.equals(filter), () -> listener.filter(choice));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
      p.setMargins(0, 0, dp(8), 0);
      chips.addView(chip, p);
    }
    filters.addView(chips);
    body.addView(filters);
    gap(body, 16);
    body.addView(action("Your smart experience", true, listener::smart));
    gap(body, 16);
    Map<String, List<View>> modules = new LinkedHashMap<>();
    int moduleStart = body.getChildCount();
    List<Track> likes = store.tracks("favourites"), history = store.tracks("history");
    LinearLayout first = row();
    quick(
        first,
        "Liked songs",
        likes,
        "liked",
        0xff6255aa,
        () -> listener.collection("Liked songs", "favourites"));
    quick(
        first,
        "Recent listens",
        history,
        "history",
        0xff367477,
        () -> listener.collection("Recently played", "history"));
    body.addView(first);
    gap(body, 8);
    LinearLayout second = row();
    quick(
        second,
        "Your playlists",
        Collections.emptyList(),
        "library",
        0xffa96172,
        () -> listener.library("Playlists"));
    quick(
        second,
        "Offline music",
        Collections.emptyList(),
        "download",
        0xff4e6b4d,
        () -> listener.library("Offline"));
    body.addView(second);
    gap(body, 26);
    capture(modules, "Shortcuts", moduleStart);
    moduleStart = body.getChildCount();
    section("Mixes for your mood", null, null);
    LinearLayout mixes = shelf();
    for (MixCatalogue.Mix mix : MixCatalogue.mixes(country)) {
      List<Track> songs = catalogue.cached(mix, country);
      if (songs.isEmpty()
          && mix.id.equals("regional")
          && feed != null
          && country.equals(feed.country)) songs = feed.tracks;
      card(mixes, mix.name, mix.detail, songs, mix.color, () -> listener.mix(mix));
      mixCards.put(mix.id, (LinearLayout) mixes.getChildAt(mixes.getChildCount() - 1));
    }
    gap(body, 26);
    capture(modules, "Mood mixes", moduleStart);
    moduleStart = body.getChildCount();
    if (!filter.equals("Music")) {
      section("Your playlists", "Create", listener::create);
      List<String> names = store.playlists();
      if (names.isEmpty()) {
        TextView start = action("+ Create your first playlist", false, listener::create);
        body.addView(start);
        gap(body, 8);
        body.addView(text("Keep your favourite songs together.", 13, MUTED, false));
      } else {
        LinearLayout saved = shelf();
        for (String name : names.subList(0, Math.min(8, names.size()))) {
          List<Track> songs = store.tracks("playlist:" + name);
          card(
              saved,
              name,
              songs.size() + " songs",
              songs,
              0xff665e91,
              () -> listener.collection(name, "playlist:" + name));
        }
      }
      gap(body, 26);
    }
    capture(modules, "Playlists", moduleStart);
    moduleStart = body.getChildCount();
    if (!filter.equals("Playlists")) {
      if (!history.isEmpty()) {
        section("Recently played", "View all", () -> listener.library("History"));
        LinearLayout recent = shelf();
        for (int i = 0; i < Math.min(8, history.size()); i++) {
          int index = i;
          Track song = history.get(i);
          songCard(recent, song, () -> listener.play(history, index));
        }
        gap(body, 26);
      }
      capture(modules, "Recently played", moduleStart);
      moduleStart = body.getChildCount();
      boolean available = feed != null && feed.country.equals(country) && !feed.tracks.isEmpty();
      section(
          available && feed.ranked ? "Trending near you" : "Regional viral picks",
          loading ? "Updating…" : "Refresh",
          listener::refresh);
      if (available) {
        body.addView(text(feed.source + " · " + RegionPolicy.name(country), 12, MUTED, false));
        gap(body, 12);
        LinearLayout regional = shelf();
        for (int i = 0; i < Math.min(10, feed.tracks.size()); i++) {
          int index = i;
          Track song = feed.tracks.get(i);
          songCard(regional, song, () -> listener.play(feed.tracks, index));
        }
      } else {
        body.addView(
            text(
                loading ? "Finding music near you…" : "Find fresh music from your region",
                15,
                TEXT,
                true));
        gap(body, 8);
        body.addView(
            action(loading ? "Loading songs…" : "Load regional songs", false, listener::refresh));
      }
      if (!error.isEmpty()) {
        gap(body, 10);
        body.addView(text(error, 12, MUTED, false));
      }
    }
    capture(modules, "Regional music", moduleStart);
    org.json.JSONObject preferences;
    try {
      preferences = new SmartStore(c).preferences();
    } catch (IllegalStateException unavailable) {
      preferences = new org.json.JSONObject();
    }
    for (List<View> module : modules.values()) for (View child : module) body.removeView(child);
    org.json.JSONArray pins = preferences.optJSONArray("pins");
    if (pins != null && pins.length() > 0) {
      section("Pinned playlists", null, null);
      LinearLayout pinnedShelf = shelf();
      for (int i = 0; i < pins.length(); i++) {
        String pinned = pins.optString(i);
        if (store.playlists().contains(pinned))
          card(
              pinnedShelf,
              pinned,
              "Your favourite place to start",
              store.tracks("playlist:" + pinned),
              0xff665e91,
              () -> listener.collection(pinned, "playlist:" + pinned));
      }
      gap(body, 18);
    }
    for (String name : SmartExperiencePanel.order(preferences)) {
      if (preferences.optBoolean("hide:" + name)
          || preferences.optBoolean("focus") && !name.equals("Shortcuts")) continue;
      List<View> views = modules.get(name);
      if (views != null) for (View view : views) body.addView(view);
    }
    gap(body, 24);
  }

  private void capture(Map<String, List<View>> modules, String name, int from) {
    List<View> views = new ArrayList<>();
    for (int i = from; i < body.getChildCount(); i++) views.add(body.getChildAt(i));
    modules.put(name, views);
  }

  private void quick(
      LinearLayout parent,
      String name,
      List<Track> songs,
      String glyph,
      int color,
      Runnable click) {
    LinearLayout tile = row();
    tile.setBackground(round(PANEL, dp(12)));
    tile.setPadding(dp(8), dp(10), dp(8), dp(10));
    tile.setMinimumHeight(dp(64));
    FrameLayout art = cover(name, songs, 36, color);
    if (songs.isEmpty()) {
      art.removeAllViews();
      Icon icon = new Icon(getContext(), glyph.equals("history") ? "timer" : glyph, TEXT);
      icon.setGlyphSize(19);
      art.addView(icon, new FrameLayout.LayoutParams(-1, -1));
    }
    tile.addView(art);
    TextView label = text(name, 12, TEXT, true);
    label.setMaxLines(2);
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    label.setPadding(dp(8), 0, 0, 0);
    tile.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    tile.setContentDescription("Open " + name);
    tile.setOnClickListener(v -> click.run());
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(0, 0, parent.getChildCount() == 0 ? dp(8) : 0, 0);
    parent.addView(tile, p);
  }

  private void songCard(LinearLayout shelf, Track song, Runnable play) {
    LinearLayout card = column();
    FrameLayout image =
        cover(song.title, Collections.singletonList(song), shelfCardSize(), 0xff465d77);
    Icon start = new Icon(getContext(), "play", BG);
    start.setBackground(round(LIME, dp(24)));
    FrameLayout.LayoutParams button =
        new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.END);
    button.setMargins(0, 0, dp(8), dp(8));
    image.addView(start, button);
    start.setContentDescription("Play " + song.title);
    start.setOnClickListener(v -> play.run());
    card.addView(image);
    gap(card, 9);
    TextView title = text(song.title, 14, TEXT, true);
    title.setMaxLines(2);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(title);
    gap(card, 5);
    TextView artist = text(song.artist, 12, MUTED, false);
    artist.setMaxLines(2);
    artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
    card.addView(artist);
    card.setOnClickListener(v -> play.run());
    card.setContentDescription("Play " + song.title + " by " + song.artist);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(shelfCardSize()), -2);
    p.setMargins(0, 0, dp(14), 0);
    shelf.addView(card, p);
  }
}
