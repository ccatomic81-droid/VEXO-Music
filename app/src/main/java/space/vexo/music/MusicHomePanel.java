package space.vexo.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

final class MusicHomePanel extends MusicSurface {
  private long avatarVersion;
  private LinearLayout personalSlot, trendingShelf, artistSlot;
  private TextView greeting;
  private final Map<String, LinearLayout> mixCards;
  private FrameLayout profileAvatar;
  private final MixCatalogue catalogue;
  private final String country;
  private final Listener listener;

  interface Listener {
    void collection(String str, String str2);

    void create();

    void filter(String str);

    void library(String str);

    void mix(MixCatalogue.Mix mix);

    void play(List<Track> list, int i);

    default void mixPlay(MixCatalogue.Mix mix, List<Track> songs, int index) {
      play(songs, index);
    }

    void profile();

    default void pure() {}

    default void pureSettings() {}

    void refresh();

    void region();

    void search();

    default void smart() {}

    default void artist(String name) {}
  }

  void artists() {
    if (artistSlot == null) return;
    int offset =
        artistSlot.getChildCount() > 0 && artistSlot.getChildAt(0) instanceof ArtistStoriesPanel
            ? ((ArtistStoriesPanel) artistSlot.getChildAt(0)).scrollPosition()
            : 0;
    artistSlot.removeAllViews();
    ArtistStoriesPanel panel =
        new ArtistStoriesPanel(
            getContext(),
            new SearchArtists(getContext()).visible(),
            listener::artist,
            listener::search);
    artistSlot.addView(panel);
    panel.restoreScroll(offset);
  }

  void personalize() {
    String upperCase;
    String str;
    UserProfile userProfile = new UserProfile(getContext());
    TextView textView = this.greeting;
    if (textView != null) {
      if (userProfile.complete()) {
        str = userProfile.greeting() + ", " + userProfile.name().split(" ", 2)[0];
      } else {
        str = "Made for your day.";
      }
      textView.setText(str);
    }
    if (this.profileAvatar == null || userProfile.photoVersion() == this.avatarVersion) {
      return;
    }
    this.avatarVersion = userProfile.photoVersion();
    this.profileAvatar.removeAllViews();
    Bitmap bitmapAvatar = userProfile.avatar();
    if (bitmapAvatar != null) {
      ImageView imageView = new ImageView(getContext());
      imageView.setImageBitmap(bitmapAvatar);
      imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
      this.profileAvatar.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
      return;
    }
    String strName = userProfile.name();
    if (strName.isEmpty()) {
      upperCase = "V";
    } else {
      upperCase = new String(Character.toChars(strName.codePointAt(0))).toUpperCase(Locale.ROOT);
    }
    TextView textViewText = text(upperCase, 20, Visuals.BG, true);
    textViewText.setGravity(17);
    this.profileAvatar.addView(textViewText, new FrameLayout.LayoutParams(-1, -1));
  }

  void updateMix(MixCatalogue.Mix mix, List<Track> list) {
    if (mix.id.equals("trending") && trendingShelf != null) {
      trendingShelf.removeAllViews();
      for (int i = 0; i < Math.min(6, list.size()); i++) {
        final int at = i;
        songCard(trendingShelf, list.get(i), () -> listener.mixPlay(mix, list, at));
      }
      return;
    }
    LinearLayout linearLayout = this.mixCards.get(mix.id);
    if (linearLayout == null || list.isEmpty()) {
      return;
    }
    linearLayout.removeViewAt(0);
    linearLayout.addView(cover(mix.name, list, shelfCardSize(), mix.color), 0);
  }

  MusicHomePanel(
      Context context,
      LibraryStore libraryStore,
      MixCatalogue mixCatalogue,
      final RegionalFeed regionalFeed,
      String str,
      boolean z,
      String str2,
      String str3,
      final Listener listener) {
    super(context);
    this.catalogue = mixCatalogue;
    this.country = str;
    this.listener = listener;
    boolean zEnabled;
    boolean z2;
    JSONObject jSONObject;
    Object obj;
    boolean z3;
    MixCatalogue mixCatalogue2 = mixCatalogue;
    this.avatarVersion = Long.MIN_VALUE;
    this.mixCards = new HashMap();
    LinearLayout linearLayoutRow = row();
    this.profileAvatar = new FrameLayout(context);
    GradientDrawable gradientDrawable = new GradientDrawable();
    gradientDrawable.setShape(GradientDrawable.OVAL);
    gradientDrawable.setColor(Visuals.LIME);
    this.profileAvatar.setBackground(gradientDrawable);
    this.profileAvatar.setClipToOutline(true);
    this.profileAvatar.setContentDescription("Edit your profile");
    this.profileAvatar.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            listener.profile();
          }
        });
    LinearLayout heading = column();
    this.greeting = text("", 22, Visuals.TEXT, true);
    this.greeting.setMaxLines(2);
    this.greeting.setEllipsize(TextUtils.TruncateAt.END);
    heading.addView(this.greeting);
    LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(40), dp(40));
    avatarParams.rightMargin = dp(12);
    linearLayoutRow.addView(this.profileAvatar, avatarParams);
    linearLayoutRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    String str4 = RegionPolicy.name(str) + " ▾";
    Objects.requireNonNull(listener);
    TextView textViewAction =
        action(
            str4,
            false,
            new Runnable() {
              @Override // java.lang.Runnable
              public final void run() {
                listener.region();
              }
            });
    textViewAction.setTextSize(11.0f);
    textViewAction.setPadding(0, dp(8), dp(8), dp(8));
    textViewAction.setBackground(null);
    textViewAction.setTextColor(Visuals.MUTED);
    textViewAction.setMinHeight(dp(48));
    textViewAction.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
    textViewAction.setMaxWidth(dp(108.0f));
    textViewAction.setMaxLines(2);
    textViewAction.setContentDescription("Change music region. Current: " + RegionPolicy.name(str));
    Visuals.Icon icon = new Visuals.Icon(context, "refresh", Visuals.MUTED);
    icon.setGlyphSize(20.0f);
    icon.setContentDescription("Refresh Home");
    icon.setTooltipText("Refresh music");
    icon.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            listener.refresh();
          }
        });
    this.body.addView(linearLayoutRow);
    gap(this.body, 8);
    try {
      zEnabled = new PureSong(new SmartStore(context)).enabled();
    } catch (IllegalStateException unused) {
      zEnabled = false;
    }
    LinearLayout linearLayoutRow2 = row();
    linearLayoutRow2.setGravity(16);
    LinearLayout linearLayoutRow3 = row();
    linearLayoutRow3.setGravity(17);
    linearLayoutRow3.setMinimumHeight(dp(48.0f));
    linearLayoutRow3.setPadding(dp(12.0f), 0, dp(14.0f), 0);
    linearLayoutRow3.setBackground(null);
    linearLayoutRow3.setSelected(zEnabled);
    linearLayoutRow3.addView(
        new Visuals.Icon(context, "intro", zEnabled ? Visuals.LIME : Visuals.MUTED),
        new LinearLayout.LayoutParams(dp(20), dp(20)));
    TextView textViewText2 =
        text(
            zEnabled ? "Skip intros" : "Intros off",
            12,
            zEnabled ? Visuals.LIME : Visuals.MUTED,
            true);
    textViewText2.setPadding(dp(8.0f), 0, 0, 0);
    linearLayoutRow3.addView(textViewText2);
    linearLayoutRow3.setContentDescription(zEnabled ? "Skip intros, on" : "Skip intros, off");
    linearLayoutRow3.setTooltipText("Skip confirmed intros · hold for PureSong settings");
    linearLayoutRow3.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            listener.pure();
          }
        });
    linearLayoutRow3.setOnLongClickListener(
        new View.OnLongClickListener() {
          @Override // android.view.View.OnLongClickListener
          public final boolean onLongClick(View view) {
            return MusicHomePanel.lambda$new$4(listener, view);
          }
        });
    LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
    layoutParams2.rightMargin = dp(10.0f);
    linearLayoutRow2.addView(textViewAction, layoutParams2);
    linearLayoutRow2.addView(linearLayoutRow3);
    linearLayoutRow2.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
    personalize();
    HorizontalScrollView horizontalScrollView = new HorizontalScrollView(context);
    horizontalScrollView.setHorizontalScrollBarEnabled(false);
    LinearLayout linearLayoutRow4 = row();
    String str5 = "Music";
    String[] strArr = {"All", "Music", "Playlists"};
    int i = 0;
    for (int i2 = 3; i < i2; i2 = 3) {
      final String str6 = strArr[i];
      String[] strArr2 = strArr;
      TextView textViewAction2 =
          action(
              str6,
              str6.equals(str3),
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.filter(str6);
                }
              });
      textViewAction2.setTextSize(13);
      textViewAction2.setMinHeight(dp(48));
      textViewAction2.setBackground(
          new android.graphics.drawable.InsetDrawable(
              Visuals.round(str6.equals(str3) ? Visuals.LIME : Visuals.CONTROL, dp(18)),
              0,
              dp(6),
              0,
              dp(6)));
      // InsetDrawable supplies its own padding when installed; set label padding afterwards.
      textViewAction2.setPadding(dp(16), dp(6), dp(16), dp(6));
      LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
      layoutParams3.setMargins(0, 0, dp(8.0f), 0);
      linearLayoutRow4.addView(textViewAction2, layoutParams3);
      i++;
      str5 = str5;
      strArr = strArr2;
    }
    String str7 = str5;
    horizontalScrollView.addView(linearLayoutRow4);
    this.body.addView(horizontalScrollView);
    gap(this.body, 2);
    this.body.addView(linearLayoutRow2);
    gap(this.body, 8);
    if (!str3.equals("Playlists")) {
      artistSlot = column();
      body.addView(artistSlot);
      artists();
    }
    personalSlot = column();
    if (!str3.equals("Playlists")) body.addView(personalSlot);
    Map<String, List<View>> linkedHashMap = new LinkedHashMap<>();
    int childCount = this.body.getChildCount();
    List<Track> listTracks = libraryStore.tracks("favourites");
    final List<Track> listTracks2 = libraryStore.tracks("history");
    LinearLayout linearLayoutRow5 = row();
    quick(
        linearLayoutRow5,
        "Liked songs",
        listTracks,
        "liked",
        -10332758,
        new Runnable() {
          @Override // java.lang.Runnable
          public final void run() {
            listener.collection("Liked songs", "favourites");
          }
        });
    quick(
        linearLayoutRow5,
        "Recent listens",
        listTracks2,
        "history",
        -13208457,
        new Runnable() {
          @Override // java.lang.Runnable
          public final void run() {
            listener.collection("Recently played", "history");
          }
        });
    this.body.addView(linearLayoutRow5);
    gap(this.body, 8);
    LinearLayout linearLayoutRow6 = row();
    quick(
        linearLayoutRow6,
        "Your playlists",
        Collections.EMPTY_LIST,
        "library",
        -5676686,
        new Runnable() {
          @Override // java.lang.Runnable
          public final void run() {
            listener.library("Playlists");
          }
        });
    quick(
        linearLayoutRow6,
        "Offline music",
        Collections.EMPTY_LIST,
        "download",
        -11637939,
        new Runnable() {
          @Override // java.lang.Runnable
          public final void run() {
            listener.library("Offline");
          }
        });
    this.body.addView(linearLayoutRow6);
    gap(this.body, 26);
    capture(linkedHashMap, "Shortcuts", childCount);
    int childCount2 = this.body.getChildCount();
    section("Mixes for your mood", null, null);
    LinearLayout linearLayoutShelf2 = shelf();
    for (final MixCatalogue.Mix mix2 : MixCatalogue.mixes(str)) {
      List<Track> listCached2 = mixCatalogue2.cached(mix2, str);
      if (listCached2.isEmpty()
          && mix2.id.equals("regional")
          && regionalFeed != null
          && str.equals(regionalFeed.country)) {
        listCached2 = regionalFeed.tracks;
      }
      card(
          linearLayoutShelf2,
          mix2.name,
          mix2.detail,
          listCached2,
          mix2.color,
          new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
              listener.mix(mix2);
            }
          });
      this.mixCards.put(
          mix2.id,
          (LinearLayout) linearLayoutShelf2.getChildAt(linearLayoutShelf2.getChildCount() - 1));
      mixCatalogue2 = mixCatalogue;
    }
    gap(this.body, 26);
    capture(linkedHashMap, "Mood mixes", childCount2);
    int childCount3 = this.body.getChildCount();
    if (!str3.equals(str7)) {
      Objects.requireNonNull(listener);
      section(
          "Your playlists",
          "Create",
          new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
              listener.create();
            }
          });
      List<String> listPlaylists = libraryStore.playlists();
      if (listPlaylists.isEmpty()) {
        Objects.requireNonNull(listener);
        this.body.addView(
            action(
                "+ Create your first playlist",
                false,
                new Runnable() {
                  @Override // java.lang.Runnable
                  public final void run() {
                    listener.create();
                  }
                }));
        gap(this.body, 8);
        this.body.addView(text("Keep your favourite songs together.", 13, Visuals.MUTED, false));
      } else {
        LinearLayout linearLayoutColumn2 = column();
        this.body.addView(linearLayoutColumn2);
        for (final String str8 : listPlaylists.subList(0, Math.min(8, listPlaylists.size()))) {
          final List<Track> listTracks3 = libraryStore.tracks("playlist:" + str8);
          LinearLayout linearLayout = linearLayoutColumn2;
          playlistRow(
              linearLayout,
              str8,
              listTracks3,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  MusicHomePanel.Listener listener2 = listener;
                  String str9 = str8;
                  listener2.collection(str9, "playlist:" + str9);
                }
              },
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.play(listTracks3, 0);
                }
              });
          linearLayoutColumn2 = linearLayout;
        }
      }
      gap(this.body, 26);
    }
    capture(linkedHashMap, "Playlists", childCount3);
    int childCount4 = this.body.getChildCount();
    if (str3.equals("Playlists")) {
      z2 = false;
    } else {
      if (!listTracks2.isEmpty()) {
        section(
            "Recently played",
            "View all",
            new Runnable() {
              @Override // java.lang.Runnable
              public final void run() {
                listener.library("History");
              }
            });
        LinearLayout linearLayoutShelf3 = shelf();
        for (int position = 0; position < Math.min(8, listTracks2.size()); position++) {
          final int i5 = position;
          songCard(
              linearLayoutShelf3,
              listTracks2.get(i5),
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.play(listTracks2, i5);
                }
              });
        }
        gap(this.body, 26);
      }
      capture(linkedHashMap, "Recently played", childCount4);
      childCount4 = this.body.getChildCount();
      boolean z4 =
          (regionalFeed == null
                  || !regionalFeed.country.equals(str)
                  || regionalFeed.tracks.isEmpty())
              ? false
              : true;
      MixCatalogue.Mix trending = MixCatalogue.trending();
      section("Trending Music", "Explore", () -> listener.mix(trending));
      body.addView(text("TikTok-inspired · full songs", 12, Visuals.MUTED, false));
      gap(body, 12);
      LinearLayout trendingCards = shelf();
      trendingShelf = trendingCards;
      List<Track> trendSongs = mixCatalogue.cached(trending, str);
      if (trendSongs.isEmpty()) {
        card(
            trendingCards,
            trending.name,
            trending.detail,
            trendSongs,
            trending.color,
            () -> listener.mix(trending));
        mixCards.put(trending.id, (LinearLayout) trendingCards.getChildAt(0));
      } else {
        for (int trend = 0; trend < Math.min(6, trendSongs.size()); trend++) {
          final int at = trend;
          songCard(
              trendingCards, trendSongs.get(at), () -> listener.mixPlay(trending, trendSongs, at));
        }
      }
      gap(body, 26);
      String str9 =
          (z4 && regionalFeed.ranked)
              ? "Trending near you"
              : RegionPolicy.global(str) ? "Global viral picks" : "Regional viral picks";
      String str10 = z ? "Updating…" : "Refresh";
      Objects.requireNonNull(listener);
      section(
          str9,
          str10,
          new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
              listener.refresh();
            }
          });
      if (z4) {
        this.body.addView(
            text(regionalFeed.source + " · " + RegionPolicy.name(str), 12, Visuals.MUTED, false));
        gap(this.body, 12);
        LinearLayout linearLayoutShelf4 = shelf();
        for (int position = 0; position < Math.min(10, regionalFeed.tracks.size()); position++) {
          final int i6 = position;
          songCard(
              linearLayoutShelf4,
              regionalFeed.tracks.get(i6),
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.play(regionalFeed.tracks, i6);
                }
              });
        }
        z3 = false;
      } else {
        this.body.addView(
            text(
                z
                    ? RegionPolicy.global(str)
                        ? "Finding music worldwide…"
                        : "Finding music near you…"
                    : RegionPolicy.global(str)
                        ? "Find fresh music from around the world"
                        : "Find fresh music from your region",
                15,
                Visuals.TEXT,
                true));
        gap(this.body, 8);
        LinearLayout linearLayout2 = this.body;
        String str11 =
            z
                ? "Loading songs…"
                : RegionPolicy.global(str) ? "Load global songs" : "Load regional songs";
        Objects.requireNonNull(listener);
        z3 = false;
        linearLayout2.addView(
            action(
                str11,
                false,
                new Runnable() {
                  @Override // java.lang.Runnable
                  public final void run() {
                    listener.refresh();
                  }
                }));
      }
      z2 = z3;
      if (!str2.isEmpty()) {
        gap(this.body, 10);
        this.body.addView(text(str2, 12, Visuals.MUTED, z3));
        z2 = z3;
      }
    }
    capture(linkedHashMap, "Regional music", childCount4);
    try {
      jSONObject = new SmartStore(context).preferences();
    } catch (IllegalStateException unused2) {
      jSONObject = new JSONObject();
    }
    JSONObject jSONObject2 = jSONObject;
    Iterator<List<View>> it = linkedHashMap.values().iterator();
    while (it.hasNext()) {
      Iterator<View> it2 = it.next().iterator();
      while (it2.hasNext()) {
        this.body.removeView(it2.next());
      }
    }
    org.json.JSONArray OptJSONArray = jSONObject2.optJSONArray("pins");
    if (OptJSONArray != null && OptJSONArray.length() > 0) {
      section("Pinned playlists", null, null);
      LinearLayout linearLayoutShelf5 = shelf();
      for (int r14 = 0; r14 < OptJSONArray.length(); r14++) {
        final String strOptString = OptJSONArray.optString(r14);
        if (libraryStore.playlists().contains(strOptString)) {
          card(
              linearLayoutShelf5,
              strOptString,
              "Your favourite place to start",
              libraryStore.tracks("playlist:" + strOptString),
              -10068335,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  MusicHomePanel.Listener listener2 = listener;
                  String str12 = strOptString;
                  listener2.collection(str12, "playlist:" + str12);
                }
              });
        }
      }
      gap(this.body, 18);
    }
    for (String name : SmartExperiencePanel.order(jSONObject2)) {
      if (jSONObject2.optBoolean("hide:" + name)
          || jSONObject2.optBoolean("focus") && !name.equals("Shortcuts")) continue;
      List<View> views = linkedHashMap.get(name);
      if (views != null) for (View view : views) body.addView(view);
    }
    gap(this.body, 24);
  }

  static /* synthetic */ boolean lambda$new$4(Listener listener, View view) {
    listener.pureSettings();
    return true;
  }

  void personal(
      MusicInterests interests,
      List<Track> songs,
      boolean loading,
      boolean failed,
      Runnable refresh,
      Runnable tune,
      YouTubeConnection.Player play,
      Runnable radio) {
    if (personalSlot == null) return;
    personalSlot.removeAllViews();
    personalSlot.setVisibility(View.VISIBLE);
    MusicSurface part = new MusicSurface(getContext());
    part.section("Made for you", loading ? "Updating…" : "Refresh", refresh);

    if (songs.isEmpty()) {
      part.body.addView(
          part.text(
              loading
                  ? "Finding music around your interests…"
                  : "Save favourite songs or choose artists and genres to shape your music.",
              14,
              Visuals.TEXT,
              false));
      part.gap(part.body, 10);
      part.body.addView(part.action("Choose your music interests", false, tune));
    } else {
      LinearLayout cards = part.shelf();
      for (int i = 0; i < Math.min(12, songs.size()); i++) {
        final int position = i;
        songCard(cards, songs.get(i), () -> play.play(songs, position));
      }

      if (failed) {
        part.gap(part.body, 8);
        part.body.addView(
            part.text(
                "Showing saved music. Refresh to try discovery again.", 12, Visuals.MUTED, false));
      }
    }
    part.gap(part.body, 20);
    part.section("Your daily soundtrack", null, null);
    LinearLayout dailyCards = part.shelf();
    for (MixCatalogue.Mix mix : MixCatalogue.discovery()) {
      part.card(
          dailyCards,
          mix.name,
          mix.detail,
          catalogue.cached(mix, country),
          mix.color,
          () -> listener.mix(mix));
      mixCards.put(mix.id, (LinearLayout) dailyCards.getChildAt(dailyCards.getChildCount() - 1));
    }
    part.gap(part.body, 20);
    while (part.body.getChildCount() > 0) {
      View child = part.body.getChildAt(0);
      part.body.removeViewAt(0);
      personalSlot.addView(child);
    }
  }

  void youtube(YouTubeTaste.Snapshot snapshot, Runnable manage, YouTubeConnection.Player play) {
    if (!snapshot.connected) return;
    section("Your YouTube taste", "Manage", manage);
    body.addView(
        text("Connected YouTube music helps shape your Home and radio", 12, Visuals.MUTED, false));
    gap(body, 12);
    body.addView(action("Manage connected music & refresh", false, manage));
    gap(body, 24);
  }

  private void feature(
      LinearLayout linearLayout,
      MixCatalogue.Mix mix,
      List<Track> list,
      int i,
      final Runnable runnable) {
    int i2;
    int i3;
    String str;
    int iMax = Math.max(244, Math.min(324, getResources().getConfiguration().screenWidthDp - 64));
    FrameLayout frameLayout = new FrameLayout(getContext());
    if (i == 0) {
      i2 = -3825176;
    } else {
      i2 = i == 1 ? -2954870 : -5646881;
    }
    GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TL_BR;
    if (i == 0) {
      i3 = -5797423;
    } else {
      i3 = i == 1 ? -5849500 : -9197636;
    }
    GradientDrawable gradientDrawable = new GradientDrawable(orientation, new int[] {i2, i3});
    gradientDrawable.setCornerRadius(dp(26.0f));
    frameLayout.setBackground(gradientDrawable);
    frameLayout.setClipToOutline(true);
    LinearLayout linearLayoutColumn = column();
    linearLayoutColumn.setPadding(dp(20.0f), dp(20.0f), dp(20.0f), dp(20.0f));
    if (i == 0) {
      str = "THE DAILY EDIT";
    } else {
      str = i == 1 ? "SLOW IT DOWN" : "FIND YOUR ENERGY";
    }
    TextView textViewText = text(str, 10, -13294525, true);
    textViewText.setLetterSpacing(0.1f);
    linearLayoutColumn.addView(textViewText);
    gap(linearLayoutColumn, 12);
    TextView textViewText2 = text(mix.name, 25, -15002843, true);
    textViewText2.setMaxLines(2);
    textViewText2.setEllipsize(TextUtils.TruncateAt.END);
    linearLayoutColumn.addView(textViewText2, new LinearLayout.LayoutParams(-1, -2));
    gap(linearLayoutColumn, 8);
    TextView textViewText3 = text(mix.detail, 13, -13030583, false);
    textViewText3.setMaxLines(2);
    linearLayoutColumn.addView(textViewText3);
    gap(linearLayoutColumn, 20);
    LinearLayout linearLayoutRow = row();
    TextView textViewText4 = text("Explore mix  ›", 13, android.graphics.Color.WHITE, true);
    textViewText4.setGravity(17);
    textViewText4.setPadding(dp(15.0f), dp(12.0f), dp(15.0f), dp(12.0f));
    textViewText4.setMinHeight(dp(48.0f));
    textViewText4.setBackground(Visuals.round(-13360577, dp(24.0f)));
    textViewText4.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    textViewText4.setMaxLines(2);
    linearLayoutRow.addView(textViewText4, new LinearLayout.LayoutParams(0, -2, 1.0f));
    linearLayoutColumn.addView(linearLayoutRow);
    frameLayout.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-1, -2));
    frameLayout.setContentDescription("Explore featured " + mix.name);
    frameLayout.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp((float) iMax), -2);
    layoutParams.rightMargin = dp(14.0f);
    linearLayout.addView(frameLayout, layoutParams);
  }

  private void playlistRow(
      LinearLayout linearLayout,
      String str,
      List<Track> list,
      final Runnable runnable,
      final Runnable runnable2) {
    LinearLayout linearLayoutRow = row();
    linearLayoutRow.setPadding(0, dp(8.0f), 0, dp(8.0f));
    linearLayoutRow.addView(cover(str, list, 60, -8887667));
    LinearLayout linearLayoutColumn = column();
    linearLayoutColumn.setPadding(dp(14.0f), 0, dp(8.0f), 0);
    TextView textViewText = text(str, 15, Visuals.TEXT, true);
    textViewText.setMaxLines(2);
    textViewText.setEllipsize(TextUtils.TruncateAt.END);
    linearLayoutColumn.addView(textViewText);
    gap(linearLayoutColumn, 6);
    StringBuilder sb = new StringBuilder();
    sb.append(list.size());
    sb.append(list.size() == 1 ? " song" : " songs");
    sb.append(" · Your collection");
    linearLayoutColumn.addView(text(sb.toString(), 12, Visuals.MUTED, false));
    linearLayoutRow.addView(linearLayoutColumn, new LinearLayout.LayoutParams(0, -2, 1.0f));
    Visuals.Icon icon = new Visuals.Icon(getContext(), "play", Visuals.TEXT);
    icon.setGlyphSize(19.0f);
    Glass.apply(icon, 24.0f, false);
    icon.setContentDescription("Play playlist " + str);
    icon.setEnabled(list.isEmpty() ^ true);
    icon.setAlpha(list.isEmpty() ? 0.4f : 1.0f);
    icon.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable2.run();
          }
        });
    linearLayoutRow.addView(icon, new LinearLayout.LayoutParams(dp(48.0f), dp(48.0f)));
    linearLayoutRow.setContentDescription("Open " + str);
    linearLayoutRow.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    linearLayout.addView(linearLayoutRow, new LinearLayout.LayoutParams(-1, -2));
  }

  private void capture(Map<String, List<View>> map, String str, int i) {
    ArrayList arrayList = new ArrayList();
    while (i < this.body.getChildCount()) {
      arrayList.add(this.body.getChildAt(i));
      i++;
    }
    map.put(str, arrayList);
  }

  private void quick(
      LinearLayout linearLayout,
      String str,
      List<Track> list,
      String str2,
      int i,
      final Runnable runnable) {
    LinearLayout linearLayoutRow = row();
    linearLayoutRow.setBackground(Visuals.round(Visuals.PANEL, dp(8)));
    linearLayoutRow.setPadding(dp(8.0f), dp(10.0f), dp(8.0f), dp(10.0f));
    linearLayoutRow.setMinimumHeight(dp(68.0f));
    FrameLayout frameLayoutCover = cover(str, list, 44, i);
    if (list.isEmpty()) {
      frameLayoutCover.removeAllViews();
      Context context = getContext();
      if (str2.equals("history")) {
        str2 = "timer";
      }
      Visuals.Icon icon = new Visuals.Icon(context, str2, android.graphics.Color.WHITE);
      icon.setGlyphSize(19.0f);
      frameLayoutCover.addView(icon, new FrameLayout.LayoutParams(-1, -1));
    }
    linearLayoutRow.addView(frameLayoutCover);
    TextView textViewText = text(str, 12, Visuals.TEXT, true);
    textViewText.setMaxLines(2);
    textViewText.setEllipsize(TextUtils.TruncateAt.END);
    textViewText.setPadding(dp(8.0f), 0, 0, 0);
    linearLayoutRow.addView(textViewText, new LinearLayout.LayoutParams(0, -2, 1.0f));
    linearLayoutRow.setContentDescription("Open " + str);
    linearLayoutRow.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
    layoutParams.setMargins(0, 0, linearLayout.getChildCount() == 0 ? dp(8.0f) : 0, 0);
    linearLayout.addView(linearLayoutRow, layoutParams);
  }

  private void songCard(LinearLayout linearLayout, Track track, final Runnable runnable) {
    LinearLayout linearLayoutColumn = column();
    FrameLayout frameLayoutCover =
        cover(track.title, Collections.singletonList(track), shelfCardSize(), -12165769);
    linearLayoutColumn.addView(frameLayoutCover);
    gap(linearLayoutColumn, 9);
    TextView textViewText = text(track.title, 14, Visuals.TEXT, true);
    textViewText.setMaxLines(2);
    textViewText.setEllipsize(TextUtils.TruncateAt.END);
    linearLayoutColumn.addView(textViewText);
    gap(linearLayoutColumn, 5);
    TextView textViewText2 = text(track.artist, 12, Visuals.MUTED, false);
    textViewText2.setMaxLines(1);
    textViewText2.setEllipsize(TextUtils.TruncateAt.END);
    linearLayoutColumn.addView(textViewText2);
    linearLayoutColumn.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    linearLayoutColumn.setContentDescription("Play " + track.title + " by " + track.artist);
    LinearLayout.LayoutParams layoutParams2 =
        new LinearLayout.LayoutParams(dp((float) shelfCardSize()), -2);
    layoutParams2.setMargins(0, 0, dp(12), 0);
    linearLayout.addView(linearLayoutColumn, layoutParams2);
  }
}
