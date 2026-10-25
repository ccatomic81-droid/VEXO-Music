package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.*;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import java.util.concurrent.*;

@UnstableApi
public final class MainActivity extends ComponentActivity {
  private final ExecutorService io = Executors.newFixedThreadPool(3);
  private final Handler main = new Handler(Looper.getMainLooper());
  private LibraryStore store;
  private MediaController controller;
  private ListenableFuture<MediaController> controllerFuture;
  private LinearLayout root, content, mini, nav;
  private TextView miniTitle, miniArtist;
  private ImageView miniArt;
  private Icon miniPlay;
  private String tab = "Home", shelf = "Phone", query = "";
  private List<Track> local = new ArrayList<>(), results = new ArrayList<>();
  private OnlineMusic.Results onlinePage;
  private boolean searching = false, destroyed = false, online = true;
  private int searchGeneration = 0, scanGeneration = 0;
  private TrackList listAdapter;
  private TextView status, offlineSummary;
  private RegionalFeed homeFeed;
  private boolean homeLoading = false;
  private String homeError = "";
  private int homeGeneration;
  private final Runnable offlineChanged =
      () -> {
        if (destroyed) return;
        if (tab.equals("Library") && shelf.equals("Offline")) render();
        if (offlineSummary != null) offlineSummary.setText(offlineDescription());
        updatePlayback();
      };
  private EditText searchField;
  private SearchHistory searchHistory;
  private SearchSuggestions suggestions;
  private ScrollView suggestionScroll;
  private LinearLayout suggestionBody;
  private boolean suggestionsVisible;
  private UpdateChecker updates;
  private Dialog updateDialog;
  private boolean updateChecking;

  private Dialog playerDialog, queueDialog;
  private QueuePanel queuePanel;
  private NowPlayingPanel playerPanel;
  private List<Integer> queueViewIndices = new ArrayList<>();
  private TextView speedButton, autoplayButton, radioStatus;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
      playbackStateChanged =
          (prefs, key) -> {
            if (!destroyed && radioStatus != null)
              radioStatus.setText(prefs.getString("autoplay_status", ""));
            if (!destroyed && queuePanel != null && queueDialog != null && queueDialog.isShowing())
              queuePanel.status(prefs.getString("autoplay_status", "Autoplay on"));
          };
  private ImageView playerArt;
  private TextView playerTitle, playerArtist, playerElapsed, playerDuration, playerState;
  private SeekBar seek;
  private Icon playerPlay, playerLike, playerShuffle, playerRepeat;
  private boolean dragging = false;
  private final ActivityResultLauncher<String> permission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            if (granted) scanPhone();
            else message("Audio permission was declined. You can still use Import audio.");
          });
  private final ActivityResultLauncher<String[]> importer =
      registerForActivityResult(
          new ActivityResultContracts.OpenMultipleDocuments(),
          uris -> {
            if (uris.isEmpty()) return;
            io.execute(
                () -> {
                  List<Track> imported = store.tracks("imports");
                  int count = 0;
                  for (Uri uri : uris) {
                    try {
                      getContentResolver()
                          .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                      String name = "Imported audio";
                      try (Cursor c =
                          getContentResolver()
                              .query(uri, new String[] {"_display_name"}, null, null, null)) {
                        if (c != null && c.moveToFirst()) name = c.getString(0);
                      }
                      Track t = new Track(uri.toString(), name, "Imported from your phone", "", 0);
                      if (imported.stream().noneMatch(x -> x.url.equals(t.url))) {
                        imported.add(t);
                        count++;
                      }
                    } catch (Exception ignored) {
                    }
                  }
                  store.save("imports", imported);
                  int added = count;
                  main.post(
                      () -> {
                        if (destroyed) return;
                        message(added + " audio files imported");
                        scanPhone();
                      });
                });
          });

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new LibraryStore(this);
    searchHistory = new SearchHistory(this);
    suggestions =
        new SearchSuggestions(main, Executors.newSingleThreadExecutor(), SearchSuggestions.ONLINE);
    updates = new UpdateChecker(this);
    getSharedPreferences("playback_state", 0)
        .registerOnSharedPreferenceChangeListener(playbackStateChanged);
    homeFeed = RegionalFeed.cached(this, RegionPolicy.current(this));
    OfflineLibrary.get(this).addListener(offlineChanged);
    if (state != null) {
      tab = state.getString("tab", "Home");
      shelf = state.getString("shelf", "Phone");
      query = state.getString("query", "");
      online = state.getBoolean("online", true);
    }
    root = column();
    root.setBackgroundColor(BG);
    setContentView(root);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          androidx.core.graphics.Insets keyboard =
              insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
          v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
          return insets;
        });
    androidx.core.view.ViewCompat.requestApplyInsets(root);
    content = column();
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    buildMini();
    buildNav();
    render();
    controllerFuture =
        new MediaController.Builder(
                this, new SessionToken(this, new ComponentName(this, PlaybackService.class)))
            .buildAsync();
    controllerFuture.addListener(
        () -> {
          if (destroyed) return;
          try {
            controller = controllerFuture.get();
            controller.addListener(
                new Player.Listener() {
                  @Override
                  public void onEvents(Player p, Player.Events e) {
                    updatePlayback();
                  }

                  @Override
                  public void onPlayerError(PlaybackException e) {
                    showPlaybackError();
                  }
                });
            updatePlayback();
          } catch (Exception e) {
            message("Could not connect to the player. Reopen the app.");
          }
        },
        androidx.core.content.ContextCompat.getMainExecutor(this));
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (tab.equals("Search") && suggestionsVisible) {
                  hideSuggestions();
                  if (searchField != null) searchField.clearFocus();
                  return;
                }
                if (!tab.equals("Home")) {
                  tab = "Home";
                  render();
                } else {
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });
    scanPhone();
    loadHome(false);
    main.post(tick);
    main.postDelayed(
        () -> {
          if (destroyed || isFinishing()) return;
          if (updates.shouldAnnounceInstalled(BuildConfig.VERSION_CODE)) showWhatsNew(true);
          else if (updates.automatic() && updates.shouldCheck(System.currentTimeMillis()))
            checkUpdates(false);
        },
        700);
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    if (searchField != null) query = searchField.getText().toString();
    b.putString("tab", tab);
    b.putString("shelf", shelf);
    b.putString("query", query);
    b.putBoolean("online", online);
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    homeGeneration++;
    suggestions.close();
    if (updateDialog != null) updateDialog.dismiss();
    OfflineLibrary.get(this).removeListener(offlineChanged);
    searchGeneration++;
    scanGeneration++;
    main.removeCallbacksAndMessages(null);
    if (playerDialog != null) playerDialog.dismiss();
    if (queueDialog != null) queueDialog.dismiss();
    getSharedPreferences("playback_state", 0)
        .unregisterOnSharedPreferenceChangeListener(playbackStateChanged);
    if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
    io.shutdownNow();
    super.onDestroy();
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setIncludeFontPadding(false);
    return t;
  }

  private TextView button(String s, Runnable action) {
    TextView t = text(s, 14, BG, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), dp(13), dp(16), dp(13));
    t.setBackground(round(LIME, dp(18)));
    t.setMinHeight(dp(48));
    t.setOnClickListener(v -> action.run());
    return t;
  }

  private void gap(LinearLayout p, int height) {
    View v = new View(this);
    p.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
  }

  private Icon icon(String name, int tint, Runnable action) {
    Icon i = new Icon(this, name, tint);
    i.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    i.setOnClickListener(v -> action.run());
    return i;
  }

  private void message(String s) {
    if (!destroyed) Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private void buildNav() {
    nav = row();
    nav.setPadding(dp(10), dp(2), dp(10), dp(4));
    nav.setMinimumHeight(dp(64));
    root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
  }

  private void refreshNav() {
    nav.removeAllViews();
    String[] labels = {"Home", "Search", "Library", "Settings"};
    String[] names = {"home", "search", "library", "settings"};
    for (int j = 0; j < labels.length; j++) {
      String label = labels[j];
      boolean selected = label.equals(tab);
      LinearLayout item = column();
      item.setGravity(Gravity.CENTER);
      Icon i = new Icon(this, names[j], selected ? LIME : MUTED);
      item.addView(i, new LinearLayout.LayoutParams(dp(42), dp(38)));
      i.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
      if (selected) i.setBackground(round(PANEL, dp(16)));
      i.setContentDescription(label);
      i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      item.setMinimumHeight(dp(56));
      item.setSelected(selected);
      item.setFocusable(true);
      item.setTooltipText(label);
      item.setContentDescription(label + " tab");
      item.setOnClickListener(
          v -> {
            if (searchField != null) query = searchField.getText().toString();
            tab = label;
            render();
          });
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
  }

  private void buildMini() {
    mini = row();
    mini.setPadding(dp(10), dp(5), dp(4), dp(5));
    mini.setBackground(round(PANEL, dp(18)));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(68));
    p.setMargins(dp(14), dp(6), dp(14), dp(4));
    root.addView(mini, p);
    mini.setVisibility(View.GONE);
    miniArt = new ImageView(this);
    mini.addView(miniArt, new LinearLayout.LayoutParams(dp(46), dp(46)));
    LinearLayout copy = column();
    copy.setPadding(dp(12), 0, 0, 0);
    miniTitle = text("", 14, TEXT, true);
    miniTitle.setSingleLine();
    miniTitle.setEllipsize(TextUtils.TruncateAt.END);
    miniArtist = text("", 12, MUTED, false);
    miniArtist.setSingleLine();
    miniArtist.setEllipsize(TextUtils.TruncateAt.END);
    copy.addView(miniTitle);
    gap(copy, 5);
    copy.addView(miniArtist);
    mini.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    miniPlay =
        icon(
            "play",
            LIME,
            () -> {
              togglePlay();
              showPlayer();
            });
    mini.addView(miniPlay);
    mini.addView(
        icon(
            "next",
            TEXT,
            () -> {
              if (controller != null) controller.seekToNextMediaItem();
            }));
    mini.setOnClickListener(v -> showPlayer());
  }

  private void render() {
    if (destroyed) return;
    suggestions.cancel();
    suggestionsVisible = false;
    suggestionScroll = null;
    suggestionBody = null;
    content.removeAllViews();
    listAdapter = null;
    searchField = null;
    status = null;
    offlineSummary = null;
    refreshNav();
    switch (tab) {
      case "Search":
        renderSearch();
        break;
      case "Library":
        renderLibrary();
        break;
      case "Settings":
        renderSettings();
        break;
      default:
        renderHome();
    }
  }

  private LinearLayout header(String eyebrow, String title) {
    LinearLayout h = column();
    h.setPadding(dp(22), dp(18), dp(22), dp(12));
    TextView label = text(eyebrow, 11, LIME, true);
    label.setLetterSpacing(.15f);
    h.addView(label);
    gap(h, 10);
    TextView heading = text(title, 28, TEXT, true);
    heading.setMaxLines(2);
    heading.setEllipsize(TextUtils.TruncateAt.END);
    h.addView(heading);
    gap(h, 18);
    content.addView(h);
    return h;
  }

  private void loadHome(boolean force) {
    String country = RegionPolicy.current(this);
    if (homeLoading) return;
    if (!force
        && homeFeed != null
        && homeFeed.country.equals(country)
        && System.currentTimeMillis() - homeFeed.fetchedAt < 30 * 60_000L) return;
    homeLoading = true;
    homeError = "";
    int generation = ++homeGeneration;
    if (tab.equals("Home")) render();
    io.execute(
        () -> {
          try {
            RegionalFeed found = RegionalFeed.fetch(country);
            found.persist(this);
            main.post(
                () -> {
                  if (destroyed || generation != homeGeneration) return;
                  homeFeed = found;
                  homeLoading = false;
                  if (tab.equals("Home")) render();
                });
          } catch (Exception error) {
            main.post(
                () -> {
                  if (destroyed || generation != homeGeneration) return;
                  homeLoading = false;
                  homeError = "Couldn’t refresh this region. Check your connection and retry.";
                  if (tab.equals("Home")) render();
                });
          }
        });
  }

  private void renderHome() {
    String country = RegionPolicy.current(this);
    LinearLayout h = header("VEXO MUSIC", "Made for your day.");
    content.removeView(h);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.addView(h);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout region = row();
    TextView regionLabel = text("Your music region", 13, MUTED, false);
    regionLabel.setSingleLine();
    regionLabel.setEllipsize(TextUtils.TruncateAt.END);
    region.addView(regionLabel, new LinearLayout.LayoutParams(0, -2, 1));
    TextView choose = chip(RegionPolicy.name(country) + "  ▾", false, this::chooseRegion);
    choose.setMaxWidth(dp(190));
    choose.setSingleLine();
    choose.setEllipsize(TextUtils.TruncateAt.END);
    choose.setContentDescription("Change music region. Current: " + RegionPolicy.name(country));
    region.addView(choose);
    h.addView(region);
    gap(h, 22);
    boolean available =
        homeFeed != null && homeFeed.country.equals(country) && !homeFeed.tracks.isEmpty();
    LinearLayout title = row();
    TextView heading =
        text(
            available && homeFeed.ranked ? "Trending near you" : "Regional viral picks",
            21,
            TEXT,
            true);
    heading.setMaxLines(2);
    title.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    TextView refresh = text(homeLoading ? "Updating…" : "Refresh", 12, LIME, true);
    refresh.setGravity(Gravity.CENTER);
    refresh.setPadding(dp(10), dp(14), 0, dp(14));
    refresh.setMinHeight(dp(48));
    refresh.setOnClickListener(v -> loadHome(true));
    title.addView(refresh);
    h.addView(title);
    if (available) {
      TextView source =
          text(homeFeed.source + " · " + RegionPolicy.name(country), 12, MUTED, false);
      source.setLineSpacing(dp(3), 1);
      h.addView(source);
      String time =
          android.text.format.DateUtils.getRelativeTimeSpanString(
                  homeFeed.fetchedAt, System.currentTimeMillis(), 60_000)
              .toString();
      gap(h, 6);
      h.addView(
          text((homeError.isEmpty() ? "Updated " : "Saved list · ") + time, 11, MUTED, false));
      gap(h, 12);
      appendHomeSongs(h, homeFeed.tracks, Math.min(12, homeFeed.tracks.size()), homeFeed.ranked);
    } else {
      LinearLayout loading = column();
      loading.setPadding(dp(18), dp(20), dp(18), dp(20));
      loading.setBackground(round(PANEL, dp(18)));
      loading.addView(
          text(
              homeLoading ? "Finding your region’s music…" : "Your regional feed is ready to load",
              16,
              TEXT,
              true));
      gap(loading, 10);
      loading.addView(
          text(
              homeLoading
                  ? "Fresh music picks will appear here."
                  : "Tap Refresh to load songs from YouTube.",
              13,
              MUTED,
              false));
      h.addView(loading);
    }
    if (!homeError.isEmpty()) {
      gap(h, 12);
      h.addView(text(homeError, 12, MUTED, false));
    }
    gap(h, 24);
    h.addView(text("Jump into a mood", 18, TEXT, true));
    gap(h, 12);
    HorizontalScrollView moods = new HorizontalScrollView(this);
    moods.setHorizontalScrollBarEnabled(false);
    LinearLayout moodRow = row();
    for (String mood : new String[] {"Sinhala", "Tamil", "English", "Lo-fi", "Acoustic"})
      moodRow.addView(
          chip(
              mood,
              false,
              () -> {
                tab = "Search";
                query = mood + " music";
                online = true;
                render();
                search();
              }));
    moods.addView(moodRow);
    h.addView(moods);
    gap(h, 24);
    LinearLayout recentTitle = row();
    recentTitle.addView(
        text("Recently played", 20, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
    TextView library = text("View all", 12, LIME, true);
    library.setPadding(dp(8), dp(14), 0, dp(14));
    library.setMinHeight(dp(48));
    library.setOnClickListener(
        v -> {
          tab = "Library";
          shelf = "History";
          render();
        });
    recentTitle.addView(library);
    h.addView(recentTitle);
    List<Track> recent = store.tracks("history");
    if (recent.isEmpty()) {
      TextView intro = text("Your listening history will appear here.", 13, MUTED, false);
      intro.setPadding(0, dp(8), 0, dp(16));
      h.addView(intro);
    } else appendHomeSongs(h, recent, Math.min(5, recent.size()), false);
    gap(h, 24);
  }

  private void appendHomeSongs(LinearLayout parent, List<Track> songs, int count, boolean ranked) {
    TrackList adapter = new TrackList(songs, null);
    for (int index = 0; index < count; index++) {
      int position = index;
      TrackHolder holder = adapter.onCreateViewHolder(parent, 0);
      adapter.onBindViewHolder(holder, index);
      if (ranked) holder.title.setText((index + 1) + ". " + songs.get(index).title);
      holder.itemView.setOnClickListener(v -> play(songs, position));
      parent.addView(holder.itemView, new LinearLayout.LayoutParams(-1, -2));
    }
  }

  private void chooseRegion() {
    List<String> codes = new ArrayList<>(Arrays.asList(Locale.getISOCountries()));
    codes.sort(Comparator.comparing(RegionPolicy::name));
    for (String first : new String[] {"GB", "US", "AU", "IN", "LK"}) {
      codes.remove(first);
      codes.add(0, first);
    }
    String[] names = new String[codes.size() + 1];
    names[0] = "Automatic · network / phone locale";
    for (int i = 0; i < codes.size(); i++)
      names[i + 1] = RegionPolicy.name(codes.get(i)) + " (" + codes.get(i) + ")";
    new AlertDialog.Builder(this)
        .setTitle("Music region")
        .setItems(
            names,
            (d, w) -> {
              getSharedPreferences("settings", 0)
                  .edit()
                  .putString("region", w == 0 ? "" : codes.get(w - 1))
                  .apply();
              homeGeneration++;
              homeLoading = false;
              homeError = "";
              homeFeed = RegionalFeed.cached(this, RegionPolicy.current(this));
              render();
              loadHome(false);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private TextView chip(String label, boolean selected, Runnable action) {
    TextView t = text(label, 13, selected ? BG : MUTED, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), dp(10), dp(16), dp(10));
    t.setMinHeight(dp(48));
    t.setBackground(round(selected ? LIME : PANEL, dp(24)));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
    p.setMargins(0, 0, dp(8), 0);
    t.setLayoutParams(p);
    t.setOnClickListener(v -> action.run());
    return t;
  }

  private void renderSearch() {
    LinearLayout h = header("DISCOVER", "Search music");
    LinearLayout box = row();
    box.setBackground(round(PANEL, dp(18)));
    box.setPadding(dp(8), 0, dp(4), 0);
    Icon find = icon("search", LIME, this::search);
    box.addView(find);
    searchField = new EditText(this);
    searchField.setTextColor(TEXT);
    searchField.setHintTextColor(MUTED);
    searchField.setTextSize(15);
    searchField.setSingleLine();
    searchField.setHint("Songs, artists, or YouTube link");
    searchField.setBackgroundColor(Color.TRANSPARENT);
    searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    searchField.setText(query);
    searchField.setSelectAllOnFocus(false);
    searchField.setContentDescription("Search songs or artists");
    searchField.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

          public void onTextChanged(CharSequence value, int start, int before, int count) {
            query = value.toString();
            searchGeneration++;
            searching = false;
            results = new ArrayList<>();
            onlinePage = null;
            if (listAdapter != null && online) {
              listAdapter.tracks.clear();
              listAdapter.notifyDataSetChanged();
            }
            if (status != null) status.setText("Find a song or artist");
            requestSuggestions();
          }

          public void afterTextChanged(Editable value) {}
        });
    searchField.setOnFocusChangeListener(
        (view, focused) -> {
          if (focused && online) requestSuggestions();
        });
    searchField.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_SEARCH) {
            search();
            return true;
          }
          return false;
        });
    box.addView(searchField, new LinearLayout.LayoutParams(0, dp(56), 1));
    box.addView(
        icon(
            "close",
            MUTED,
            () -> {
              searchField.setText("");
              query = "";
            }));
    h.addView(box);
    gap(h, 14);
    LinearLayout source = row();
    source.addView(
        chip(
            "YouTube",
            online,
            () -> {
              online = true;
              render();
            }));
    source.addView(
        chip(
            "On this phone",
            !online,
            () -> {
              online = false;
              searchGeneration++;
              searching = false;
              render();
            }));
    h.addView(source);
    gap(h, 14);
    status =
        text(
            searching
                ? "Searching YouTube…"
                : query.isEmpty()
                    ? "Find a song to get started."
                    : online ? results.size() + " results" : "Your phone’s music",
            13,
            MUTED,
            false);
    h.addView(status);
    suggestionScroll = new ScrollView(this);
    suggestionBody = column();
    suggestionBody.setPadding(dp(22), 0, dp(22), dp(12));
    suggestionScroll.addView(suggestionBody);
    suggestionScroll.setVisibility(View.GONE);
    content.addView(suggestionScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (online) {
      addList(results, null);
      if (!searching
          && onlinePage != null
          && org.schabi.newpipe.extractor.Page.isValid(onlinePage.next)) {
        TextView more = button("Load more songs", this::loadMore);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(22), dp(8), dp(22), dp(8));
        content.addView(more, p);
      }
    } else {
      List<Track> filtered = filter(local, query);
      if (filtered.isEmpty())
        empty(
            "Your music, anywhere",
            "Scan your audio library or import selected files.",
            "Import audio",
            this::importAudio);
      else addList(filtered, null);
    }
    if (online && query.isEmpty()) requestSuggestions();
  }

  private void requestSuggestions() {
    if (searchField == null || suggestionBody == null || !online || !tab.equals("Search")) return;
    final String value = searchField.getText().toString();
    showSuggestions(value, Collections.emptyList(), false);
    suggestions.request(
        value,
        RegionPolicy.current(this),
        (term, remote, unavailable) -> {
          if (destroyed
              || !tab.equals("Search")
              || !online
              || searchField == null
              || !SearchHistory.clean(searchField.getText().toString()).equals(term)) return;
          showSuggestions(term, remote, unavailable);
        });
  }

  private void showSuggestions(String term, List<String> remote, boolean unavailable) {
    if (suggestionBody == null) return;
    suggestionsVisible = true;
    suggestionScroll.setVisibility(View.VISIBLE);
    for (int i = 2; i < content.getChildCount(); i++)
      content.getChildAt(i).setVisibility(View.GONE);
    suggestionBody.removeAllViews();
    List<String> recent = searchHistory.entries();
    List<String> choices = SearchHistory.merge(term, recent, remote);
    TextView title =
        text(term.trim().isEmpty() ? "Recent searches" : "Suggestions", 14, TEXT, true);
    title.setPadding(0, dp(8), 0, dp(10));
    suggestionBody.addView(title);
    if (!recent.isEmpty() && term.trim().isEmpty()) {
      TextView clear = text("Clear recent searches", 13, LIME, true);
      clear.setMinHeight(dp(48));
      clear.setGravity(Gravity.CENTER_VERTICAL);
      clear.setOnClickListener(
          v -> {
            searchHistory.clear();
            requestSuggestions();
          });
      suggestionBody.addView(clear);
    }
    for (String choice : choices) {
      boolean isRecent = recent.stream().anyMatch(t -> t.equalsIgnoreCase(choice));
      LinearLayout row = row();
      row.setMinimumHeight(dp(52));
      TextView label = text(choice, 15, TEXT, false);
      label.setMaxLines(2);
      label.setEllipsize(TextUtils.TruncateAt.END);
      label.setMinHeight(dp(48));
      label.setGravity(Gravity.CENTER_VERTICAL);
      label.setPadding(dp(12), dp(6), dp(4), dp(6));
      label.setContentDescription("Search for " + choice);
      label.setOnClickListener(
          v -> {
            searchField.setText(choice);
            search();
          });
      row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      if (isRecent) {
        Icon remove =
            icon(
                "close",
                MUTED,
                () -> {
                  searchHistory.remove(choice);
                  requestSuggestions();
                });
        remove.setContentDescription("Remove recent search " + choice);
        row.addView(remove);
      }
      Icon fill =
          icon(
              "search",
              LIME,
              () -> {
                searchField.setText(choice);
                searchField.setSelection(choice.length());
                searchField.requestFocus();
              });
      fill.setContentDescription("Use suggestion " + choice);
      row.addView(fill);
      suggestionBody.addView(row);
    }
    if (choices.isEmpty()) {
      TextView hint =
          text(
              term.trim().isEmpty()
                  ? "Search for a song or artist. Your recent searches stay on this phone."
                  : unavailable
                      ? "Suggestions are unavailable. You can still tap search."
                      : term.trim().length() < 2
                          ? "Keep typing, or tap search."
                          : "Tap search to find this song or artist.",
              13,
              MUTED,
              false);
      hint.setPadding(0, dp(12), 0, dp(12));
      suggestionBody.addView(hint);
    }
    if (!term.trim().isEmpty()) {
      TextView find = button("Search music", this::search);
      suggestionBody.addView(find);
    }
  }

  private void hideSuggestions() {
    suggestions.cancel();
    suggestionsVisible = false;
    if (suggestionScroll != null) suggestionScroll.setVisibility(View.GONE);
    for (int i = 2; i < content.getChildCount(); i++)
      content.getChildAt(i).setVisibility(View.VISIBLE);
  }

  private List<Track> filter(List<Track> tracks, String q) {
    if (q.trim().isEmpty()) return tracks;
    String needle = q.toLowerCase(Locale.ROOT);
    List<Track> out = new ArrayList<>();
    for (Track t : tracks)
      if ((t.title + " " + t.artist).toLowerCase(Locale.ROOT).contains(needle)) out.add(t);
    return out;
  }

  private void search() {
    if (searchField != null) query = searchField.getText().toString().trim();
    if (query.isEmpty()) {
      message("Enter a song or artist first");
      return;
    }
    hideSuggestions();
    if (online) searchHistory.add(query);
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    if (!online) {
      render();
      return;
    }
    int generation = ++searchGeneration;
    String request = query;
    searching = true;
    results = new ArrayList<>();
    onlinePage = null;
    render();
    io.execute(
        () -> {
          try {
            OnlineMusic.initialize();
            if (isYouTubeLink(request)) {
              org.schabi.newpipe.extractor.stream.StreamInfo info =
                  org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
                      org.schabi.newpipe.extractor.ServiceList.YouTube, request);
              String artwork =
                  info.getThumbnails().isEmpty() ? "" : info.getThumbnails().get(0).getUrl();
              List<Track> tracks =
                  Collections.singletonList(
                      new Track(
                          info.getUrl(),
                          info.getName(),
                          info.getUploaderName(),
                          artwork,
                          info.getDuration()));
              main.post(
                  () -> {
                    if (destroyed || generation != searchGeneration) return;
                    results = tracks;
                    searching = false;
                    if (tab.equals("Search")) render();
                  });
            } else {
              OnlineMusic.Results found = OnlineMusic.search(request);
              main.post(
                  () -> {
                    if (destroyed || generation != searchGeneration) return;
                    onlinePage = found;
                    results = new ArrayList<>(found.tracks);
                    searching = false;
                    if (tab.equals("Search")) render();
                  });
            }
          } catch (Exception e) {
            main.post(
                () -> {
                  if (destroyed || generation != searchGeneration) return;
                  searching = false;
                  if (tab.equals("Search")) {
                    render();
                    if (status != null)
                      status.setText(
                          "Couldn’t reach YouTube. Check your connection and tap search to retry.");
                  }
                });
          }
        });
  }

  private boolean isYouTubeLink(String q) {
    try {
      Uri u = Uri.parse(q);
      String h = u.getHost();
      return "https".equals(u.getScheme())
          && ("youtube.com".equals(h)
              || "www.youtube.com".equals(h)
              || "music.youtube.com".equals(h)
              || "youtu.be".equals(h));
    } catch (Exception e) {
      return false;
    }
  }

  private void loadMore() {
    if (searching || onlinePage == null) return;
    int generation = ++searchGeneration;
    OnlineMusic.Results previous = onlinePage;
    searching = true;
    render();
    io.execute(
        () -> {
          try {
            OnlineMusic.Results next = OnlineMusic.more(previous);
            main.post(
                () -> {
                  if (destroyed || generation != searchGeneration) return;
                  results.addAll(next.tracks);
                  onlinePage = next;
                  searching = false;
                  if (tab.equals("Search")) render();
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (destroyed || generation != searchGeneration) return;
                  searching = false;
                  if (tab.equals("Search")) render();
                  message("Could not load more. Tap Load more to retry.");
                });
          }
        });
  }

  private void renderLibrary() {
    LinearLayout h = header("YOUR COLLECTION", "Your library");
    HorizontalScrollView sc = new HorizontalScrollView(this);
    sc.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = row();
    for (String s : new String[] {"Phone", "Offline", "Favourites", "Playlists", "History"})
      chips.addView(
          chip(
              s,
              s.equals(shelf),
              () -> {
                shelf = s;
                render();
              }));
    sc.addView(chips);
    h.addView(sc);
    gap(h, 12);
    if (shelf.equals("Playlists")) {
      h.addView(button("+  Create playlist", () -> createPlaylist(null)));
      List<String> names = store.playlists();
      if (names.isEmpty()) {
        empty(
            "A home for every mood",
            "Create a playlist, then add songs from the song menu.",
            "Create a playlist",
            () -> createPlaylist(null));
        return;
      }
      ScrollView playlistScroll = new ScrollView(this);
      LinearLayout playlistBody = column();
      playlistScroll.addView(playlistBody);
      content.addView(playlistScroll, new LinearLayout.LayoutParams(-1, 0, 1));
      for (String name : names) {
        LinearLayout r = row();
        r.setPadding(dp(22), dp(10), dp(12), dp(10));
        LinearLayout title = column();
        title.addView(text(name, 17, TEXT, true));
        gap(title, 5);
        title.addView(text(store.tracks("playlist:" + name).size() + " songs", 12, MUTED, false));
        r.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        r.addView(
            icon(
                "more",
                MUTED,
                () ->
                    new AlertDialog.Builder(this)
                        .setTitle(name)
                        .setItems(
                            new String[] {"Open playlist", "Delete playlist"},
                            (d, w) -> {
                              if (w == 0) showCollection(name, "playlist:" + name);
                              else
                                new AlertDialog.Builder(this)
                                    .setTitle("Delete playlist?")
                                    .setMessage(
                                        "Only this playlist will be removed. Your music files stay"
                                            + " on your phone.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton(
                                        "Delete",
                                        (x, y) -> {
                                          store.deletePlaylist(name);
                                          render();
                                        })
                                    .show();
                            })
                        .show()));
        r.setOnClickListener(v -> showCollection(name, "playlist:" + name));
        playlistBody.addView(r);
      }
      return;
    }
    List<Track> tracks;
    if (shelf.equals("Phone")) {
      LinearLayout actions = row();
      actions.addView(
          button("Scan phone", this::requestScan), new LinearLayout.LayoutParams(0, -2, 1));
      TextView imp = button("Import audio", this::importAudio);
      imp.setTextColor(TEXT);
      imp.setBackground(round(PANEL, dp(18)));
      LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1);
      ip.setMargins(dp(8), 0, 0, 0);
      actions.addView(imp, ip);
      h.addView(actions);
      gap(h, 10);
      h.addView(text(local.size() + " audio files · stays on your device", 12, MUTED, false));
      tracks = local;
    } else if (shelf.equals("Offline")) {
      tracks = OfflineLibrary.get(this).saved();
      offlineSummary = text(offlineDescription(), 12, MUTED, false);
      offlineSummary.setLineSpacing(dp(4), 1);
      h.addView(offlineSummary);
      gap(h, 10);
    } else tracks = store.tracks(shelf.equals("Favourites") ? "favourites" : "history");
    if (tracks.isEmpty())
      empty(
          shelf.equals("Phone")
              ? "Bring your music along"
              : shelf.equals("Offline") ? "Offline songs appear here" : "Nothing here yet",
          shelf.equals("Phone")
              ? "Allow access to audio files, or import only the songs you choose."
              : shelf.equals("Offline")
                  ? "Play online music with Auto-save on. Complete saves will be available without"
                      + " internet."
                  : "Play a song or save a favourite to start your collection.",
          shelf.equals("Phone") ? "Import audio" : "Find music",
          () -> {
            if (shelf.equals("Phone")) importAudio();
            else {
              tab = "Search";
              render();
            }
          });
    else addList(tracks, shelf.equals("Favourites") ? "favourites" : null);
  }

  private void importAudio() {
    importer.launch(new String[] {"audio/*"});
  }

  private void requestScan() {
    String p =
        Build.VERSION.SDK_INT >= 33
            ? Manifest.permission.READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;
    if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) scanPhone();
    else permission.launch(p);
  }

  private void scanPhone() {
    int generation = ++scanGeneration;
    io.execute(
        () -> {
          LinkedHashMap<String, Track> found = new LinkedHashMap<>();
          for (Track t : store.tracks("imports")) found.put(t.url, t);
          String p =
              Build.VERSION.SDK_INT >= 33
                  ? Manifest.permission.READ_MEDIA_AUDIO
                  : Manifest.permission.READ_EXTERNAL_STORAGE;
          if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) {
            String[] projection = {
              MediaStore.Audio.Media._ID,
              MediaStore.Audio.Media.TITLE,
              MediaStore.Audio.Media.ARTIST,
              MediaStore.Audio.Media.ALBUM_ID,
              MediaStore.Audio.Media.DURATION
            };
            try (Cursor c =
                getContentResolver()
                    .query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        MediaStore.Audio.Media.DURATION + ">0",
                        null,
                        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
              if (c != null)
                while (c.moveToNext()) {
                  String url =
                      ContentUris.withAppendedId(
                              MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                          .toString();
                  String artist = c.getString(2);
                  Track t =
                      new Track(
                          url,
                          c.getString(1),
                          "<unknown>".equals(artist) ? "Unknown artist" : artist,
                          "content://media/external/audio/albumart/" + c.getLong(3),
                          c.getLong(4) / 1000);
                  found.put(url, t);
                }
            } catch (Exception ignored) {
            }
          }
          main.post(
              () -> {
                if (destroyed || generation != scanGeneration) return;
                local = new ArrayList<>(found.values());
                if (tab.equals("Library") && shelf.equals("Phone")
                    || tab.equals("Search") && !online) render();
              });
        });
  }

  private void empty(String title, String description, String action, Runnable click) {
    LinearLayout e = column();
    e.setGravity(Gravity.CENTER);
    e.setPadding(dp(30), dp(20), dp(30), dp(20));
    TextView t = text(title, 21, TEXT, true);
    t.setGravity(Gravity.CENTER);
    e.addView(t);
    gap(e, 12);
    TextView detail = text(description, 14, MUTED, false);
    detail.setGravity(Gravity.CENTER);
    detail.setLineSpacing(dp(4), 1);
    e.addView(detail);
    gap(e, 22);
    e.addView(button(action, click));
    ScrollView emptyScroll = new ScrollView(this);
    emptyScroll.setFillViewport(true);
    emptyScroll.addView(e);
    content.addView(emptyScroll, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private void addList(List<Track> tracks, String collection) {
    RecyclerView list = new RecyclerView(this);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setClipToPadding(false);
    list.setPadding(dp(14), 0, dp(14), dp(8));
    listAdapter = new TrackList(tracks, collection);
    list.setAdapter(listAdapter);
    content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private final class TrackList extends RecyclerView.Adapter<TrackHolder> {
    final List<Track> tracks;
    final String collection;

    TrackList(List<Track> t, String c) {
      tracks = new ArrayList<>(t);
      collection = c;
    }

    @Override
    public TrackHolder onCreateViewHolder(ViewGroup parent, int type) {
      LinearLayout row = row();
      row.setPadding(dp(8), dp(8), dp(2), dp(8));
      row.setMinimumHeight(dp(80));
      row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      ImageView cover = new ImageView(MainActivity.this);
      row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
      LinearLayout labels = column();
      labels.setPadding(dp(12), 0, dp(4), 0);
      TextView title = text("", 15, TEXT, true), detail = text("", 12, MUTED, false);
      title.setMaxLines(2);
      title.setEllipsize(TextUtils.TruncateAt.END);
      detail.setMaxLines(1);
      detail.setEllipsize(TextUtils.TruncateAt.END);
      labels.addView(title);
      gap(labels, 5);
      labels.addView(detail);
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      Icon start = icon("play", LIME, () -> {});
      row.addView(start);
      Icon more = icon("more", MUTED, () -> {});
      row.addView(more);
      return new TrackHolder(row, cover, title, detail, more, start);
    }

    @Override
    public void onBindViewHolder(TrackHolder h, int pos) {
      Track t = tracks.get(pos);
      h.title.setText(t.title);
      h.detail.setText(t.artist + (t.duration > 0 ? " · " + format(t.duration) : ""));
      artwork(h.art, t.artwork);
      h.itemView.setContentDescription("Play " + t.title + " by " + t.artist);
      h.itemView.setOnClickListener(v -> play(tracks, h.getBindingAdapterPosition()));
      h.start.setContentDescription("Play " + t.title);
      h.start.setOnClickListener(
          v -> {
            int index = h.getBindingAdapterPosition();
            if (index < 0) index = pos;
            play(tracks, index);
          });
      h.more.setContentDescription("Options for " + t.title);
      h.more.setOnClickListener(v -> songMenu(t, collection));
    }

    @Override
    public int getItemCount() {
      return tracks.size();
    }
  }

  private static final class TrackHolder extends RecyclerView.ViewHolder {
    final ImageView art;
    final TextView title, detail;
    final Icon more, start;

    TrackHolder(View v, ImageView a, TextView t, TextView d, Icon m, Icon start) {
      super(v);
      art = a;
      title = t;
      detail = d;
      more = m;
      this.start = start;
    }
  }

  private void play(List<Track> tracks, int index) {
    if (controller == null) {
      message("Player is connecting. Try again in a moment.");
      return;
    }
    if (index < 0 || index >= tracks.size()) return;
    List<MediaItem> queue = new ArrayList<>();
    int first = Math.max(0, index - 10),
        end = Math.min(tracks.size(), first + QueuePolicy.MAX_ITEMS);
    for (Track t : tracks.subList(first, end)) queue.add(t.mediaItem());
    Bundle queueArgs = new Bundle();
    queueArgs.putString("track", tracks.get(index).json().toString());
    controller.sendCustomCommand(
        new SessionCommand(PlaybackService.NEW_QUEUE, Bundle.EMPTY), queueArgs);
    controller.setMediaItems(queue, index - first, 0);
    controller.prepare();
    controller.play();
    showPlayer();
  }

  private void songMenu(Track track, String collection) {
    boolean liked = store.contains("favourites", track);
    ArrayList<String> options =
        new ArrayList<>(
            Arrays.asList(
                liked ? "Remove from favourites" : "Save to favourites",
                "Add to playlist",
                "Play next",
                "Add to queue"));
    if (track.online()) {
      options.add("Start song radio");
      options.add("Don’t recommend in radio");
      options.add("Open in YouTube");
    }
    if (collection != null) options.add("Remove from this collection");
    if (OfflineLibrary.get(this).complete(track.url)) options.add("Remove offline audio");
    new AlertDialog.Builder(this)
        .setTitle(track.title)
        .setItems(
            options.toArray(new String[0]),
            (d, which) -> {
              String action = options.get(which);
              if (action.equals("Remove offline audio")) {
                OfflineLibrary.get(this).remove(track.url);
                return;
              }
              if (action.equals("Start song radio")) {
                startRadio(track);
                return;
              }
              if (action.equals("Don’t recommend in radio")) {
                hideFromRadio(track);
                return;
              }
              switch (which) {
                case 0:
                  store.toggle("favourites", track);
                  radioChanged();
                  if (tab.equals("Library")) render();
                  updatePlayback();
                  break;
                case 1:
                  pickPlaylist(track);
                  break;
                case 2:
                  if (controller != null && queueHasRoom()) {
                    controller.setShuffleModeEnabled(false);
                    controller.addMediaItem(
                        Math.max(0, controller.getCurrentMediaItemIndex() + 1), track.mediaItem());
                    message("Added to play next");
                  }
                  break;
                case 3:
                  if (controller != null && queueHasRoom()) {
                    controller.addMediaItem(track.mediaItem());
                    message("Added to queue");
                  }
                  break;
                default:
                  if (action.equals("Open in YouTube")) openUrl(track.url);
                  else if (collection != null) {
                    List<Track> saved = store.tracks(collection);
                    saved.removeIf(x -> x.url.equals(track.url));
                    store.save(collection, saved);
                    if (tab.equals("Library")) render();
                  }
              }
            })
        .show();
  }

  private void pickPlaylist(Track track) {
    List<String> names = store.playlists();
    List<String> all = new ArrayList<>(names);
    all.add("+ New playlist");
    new AlertDialog.Builder(this)
        .setTitle("Add to playlist")
        .setItems(
            all.toArray(new String[0]),
            (d, w) -> {
              if (w == names.size()) createPlaylist(track);
              else {
                store.addToPlaylist(names.get(w), track);
                message("Added to " + names.get(w));
              }
            })
        .show();
  }

  private void createPlaylist(Track track) {
    EditText input = new EditText(this);
    input.setHint("Playlist name");
    input.setSingleLine();
    input.setPadding(dp(24), dp(16), dp(24), dp(16));
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Create playlist")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create();
    dialog.setOnShowListener(
        x ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String name = input.getText().toString().trim();
                      if (name.isEmpty() || name.length() > 60) {
                        input.setError("Use a name between 1 and 60 characters");
                        return;
                      }
                      if (store.playlists().contains(name)) {
                        input.setError("That playlist already exists");
                        return;
                      }
                      store.createPlaylist(name);
                      if (track != null) store.addToPlaylist(name, track);
                      dialog.dismiss();
                      if (tab.equals("Library")) render();
                      message("Playlist created");
                    }));
    dialog.show();
  }

  private void showCollection(String title, String key) {
    List<Track> tracks = store.tracks(key);
    if (tracks.isEmpty()) {
      message("This playlist is empty. Add songs from the song menu.");
      return;
    }
    String[] labels = new String[tracks.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = tracks.get(i).title;
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(labels, (d, w) -> play(tracks, w))
        .setNeutralButton(
            "Edit songs",
            (d, w) -> {
              new AlertDialog.Builder(this)
                  .setTitle("Remove a song")
                  .setItems(
                      labels,
                      (x, y) -> {
                        List<Track> current = store.tracks(key);
                        current.removeIf(t -> t.url.equals(tracks.get(y).url));
                        store.save(key, current);
                        message("Song removed from playlist");
                        if (tab.equals("Library")) render();
                      })
                  .setNegativeButton("Cancel", null)
                  .show();
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private String offlineDescription() {
    OfflineLibrary offline = OfflineLibrary.get(this);
    int count = offline.saved().size();
    String usage =
        String.format(
            Locale.ROOT,
            "%.1f MB / 500 MB · %d %s",
            offline.bytes() / 1048576.0,
            count,
            count == 1 ? "saved song" : "saved songs");
    return tab.equals("Settings") ? usage : usage + "\n" + offline.status();
  }

  private void renderSettings() {
    header("MAKE IT YOURS", "Settings");
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = column();
    body.setPadding(dp(22), dp(8), dp(22), dp(24));
    scroll.addView(body);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    setting(
        body,
        "Music region",
        RegionPolicy.name(RegionPolicy.current(this)) + " · tap to change",
        false,
        this::chooseRegion);
    setting(
        body,
        "Auto-save listened songs",
        OfflineLibrary.get(this).enabled()
            ? "On · automatically save online songs for offline listening"
            : "Off · online songs won’t be saved",
        false,
        () -> {
          boolean enabled = !OfflineLibrary.get(this).enabled();
          getSharedPreferences("settings", 0).edit().putBoolean("auto_save", enabled).apply();
          if (!enabled) OfflineLibrary.get(this).cancel();
          if (controller != null)
            controller.sendCustomCommand(
                new SessionCommand(PlaybackService.OFFLINE_SETTINGS, Bundle.EMPTY), Bundle.EMPTY);
          render();
        });
    offlineSummary = text(offlineDescription(), 12, MUTED, false);
    offlineSummary.setPadding(dp(12), dp(4), dp(12), dp(16));
    body.addView(offlineSummary);
    setting(
        body,
        "Clear offline storage",
        "Remove saved audio · favourites and playlists stay",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Clear offline audio?")
                .setMessage(
                    "Saved songs will need internet again. Your phone’s original audio files will"
                        + " stay.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> OfflineLibrary.get(this).clear())
                .show());
    setting(
        body,
        "Data saver",
        "Prefer a lower bitrate for new online streams",
        true,
        () -> {
          boolean enabled = !getSharedPreferences("settings", 0).getBoolean("data_saver", false);
          getSharedPreferences("settings", 0).edit().putBoolean("data_saver", enabled).apply();
          OnlineMusic.clearCache();
          render();
        });
    setting(
        body,
        "Autoplay related songs",
        autoplayEnabled()
            ? "On · keep music going after the online queue"
            : "Off · stop at the end of your queue",
        false,
        () -> {
          setAutoplay(!autoplayEnabled());
          render();
        });
    setting(
        body,
        "Song radio tuner",
        new RadioStore(this).mode().label + " · choose familiarity and variety",
        false,
        this::tuneRadio);
    setting(
        body,
        "Reset radio feedback",
        new RadioStore(this).hiddenCount() + " hidden songs · allow them in future radio picks",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Reset radio feedback?")
                .setMessage(
                    "Previously hidden songs can appear in radio again. Favourites and history"
                        + " stay.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Reset",
                    (d, w) -> {
                      new RadioStore(this).clearFeedback();
                      radioChanged();
                      render();
                    })
                .show());
    int crossfade =
        CrossfadeEngine.clampSeconds(getSharedPreferences("settings", 0).getInt("crossfade", 4));
    setting(
        body,
        "Crossfade",
        crossfade == 0
            ? "Off · natural song transitions"
            : crossfade + (crossfade == 1 ? " second" : " seconds") + " · smoothly overlap songs",
        false,
        this::crossfadeSettings);
    setting(body, "Playback speed", "Choose 0.75× to 2×", false, this::playbackSpeed);
    setting(body, "Sleep timer", "Pause playback after a time you choose", false, this::sleepTimer);
    setting(
        body,
        "Refresh phone library",
        "Scan audio files and update your library",
        false,
        this::requestScan);
    setting(
        body,
        "Clear listening history",
        "Your favourites and playlists will stay",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Clear listening history?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Clear",
                    (d, w) -> {
                      store.save("history", Collections.emptyList());
                      message("History cleared");
                    })
                .show());
    setting(
        body,
        "What’s new",
        "Version " + BuildConfig.VERSION_NAME + " · latest improvements",
        false,
        () -> showWhatsNew(false));
    setting(
        body,
        "Check for updates",
        updates.source().isEmpty()
            ? "Connect a public update feed to receive new versions"
            : "Installed " + BuildConfig.VERSION_NAME + " · tap to check",
        false,
        () -> checkUpdates(true));
    setting(
        body,
        "Automatic update checks",
        updates.automatic()
            ? "On · check once a day when you open the app"
            : "Off · check manually",
        false,
        () -> {
          updates.automatic(!updates.automatic());
          render();
        });
    setting(
        body,
        "Update source",
        updates.source().isEmpty() ? "Not connected" : "Public release feed connected",
        false,
        this::chooseUpdateSource);
    setting(
        body,
        "Clear recent searches",
        "Remove search terms saved on this phone",
        false,
        () -> {
          searchHistory.clear();
          message("Recent searches cleared");
        });
    setting(
        body,
        "About VEXO Music",
        "Version " + BuildConfig.VERSION_NAME + " · Kavindu Kavishka",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("VEXO Music " + BuildConfig.VERSION_NAME)
                .setMessage(
                    "Developer: Kavindu Kavishka\n\n"
                        + "An independent music player with YouTube search and a private phone"
                        + " library. No account is required.\n\n"
                        + "Search suggestions, searches and online playback contact YouTube. Recent"
                        + " search terms stay on this phone and can be cleared in Settings. Update"
                        + " checks contact the configured public release feed. Artwork is loaded"
                        + " from the source shown by YouTube. Favourites, playlists and listening"
                        + " history are stored on this device. No analytics server is used.\n\n"
                        + "VEXO Music is not affiliated with YMusic or YouTube. Online availability"
                        + " can change when YouTube changes its service. Local playback remains"
                        + " available.")
                .setPositiveButton("OK", null)
                .show());
    setting(
        body,
        "Open-source licences",
        "GPL-3.0 · NewPipe Extractor · AndroidX · OkHttp",
        false,
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Open-source licences")
                .setMessage(
                    "VEXO Music is distributed under GPL-3.0-or-later.\n\n"
                        + "NewPipe Extractor v0.26.5: GPL-3.0-or-later\n"
                        + "AndroidX / Media3: Apache-2.0\n"
                        + "OkHttp: Apache-2.0\n"
                        + "Guava: Apache-2.0\n\n"
                        + "The source package includes licence notices and the build instructions."
                        + " When distributing this app, provide its corresponding source code.")
                .setPositiveButton("OK", null)
                .setNeutralButton(
                    "NewPipe source",
                    (d, w) ->
                        openUrl("https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5"))
                .show());
    gap(body, 24);
    TextView developer = text("Developer: Kavindu Kavishka", 12, MUTED, false);
    body.addView(developer);
    gap(body, 12);
    TextView foot = text("LISTEN IN YOUR OWN ORBIT", 10, MUTED, true);
    foot.setLetterSpacing(.15f);
    body.addView(foot);
  }

  private void showWhatsNew(boolean automatic) {
    if (destroyed || isFinishing()) return;
    updates.seen(BuildConfig.VERSION_CODE);
    updateDialog =
        new AlertDialog.Builder(this)
            .setTitle("What’s new in " + BuildConfig.VERSION_NAME)
            .setMessage(AppRelease.NOTES)
            .setPositiveButton("Let’s listen", null)
            .create();
    updateDialog.setOnDismissListener(
        d -> {
          updateDialog = null;
          if (automatic
              && !destroyed
              && updates.automatic()
              && updates.shouldCheck(System.currentTimeMillis())) checkUpdates(false);
        });
    updateDialog.show();
  }

  private void chooseUpdateSource() {
    EditText input = new EditText(this);
    input.setSingleLine();
    input.setText(updates.source());
    input.setHint("https://…/stable.json");
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Update source")
            .setMessage("Public HTTPS release feed. Leave empty to disconnect.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        updates.source(input.getText().toString());
                        dialog.dismiss();
                        render();
                      } catch (java.io.IOException e) {
                        input.setError(e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private void checkUpdates(boolean manual) {
    if (updateChecking) {
      if (manual) message("Checking for updates…");
      return;
    }
    if (updates.source().isEmpty()) {
      if (manual)
        new AlertDialog.Builder(this)
            .setTitle("Update source not connected")
            .setMessage("A public release feed is needed to check for new versions.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Connect", (d, w) -> chooseUpdateSource())
            .show();
      return;
    }
    final String updateSource = updates.source();
    updateChecking = true;
    if (manual) message("Checking for updates…");
    io.execute(
        () -> {
          try {
            AppRelease release = updates.fetch();
            main.post(
                () -> {
                  updateChecking = false;
                  if (destroyed || isFinishing() || !updateSource.equals(updates.source())) return;
                  if (!release.newerThan(BuildConfig.VERSION_CODE)) {
                    if (manual) message("You’re using the latest version");
                    return;
                  }
                  if (!manual && updates.snoozed(release)) return;
                  if (updateDialog != null && updateDialog.isShowing()) return;
                  updateDialog =
                      new AlertDialog.Builder(this)
                          .setTitle("VEXO Music " + release.version + " is available")
                          .setMessage(
                              release.summary + "\n\nInstalled: " + BuildConfig.VERSION_NAME)
                          .setNegativeButton("Later", (d, w) -> updates.snooze(release))
                          .setPositiveButton("Get update", (d, w) -> openUrl(release.url))
                          .create();
                  updateDialog.setOnDismissListener(
                      d -> {
                        updates.snooze(release);
                        updateDialog = null;
                      });
                  updateDialog.show();
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  updateChecking = false;
                  if (!destroyed && manual)
                    message(
                        "Couldn’t check for updates. Check your connection and update source, then"
                            + " try again.");
                });
          }
        });
  }

  private void setting(
      LinearLayout parent, String title, String detail, boolean toggle, Runnable action) {
    LinearLayout r = row();
    r.setPadding(dp(16), dp(16), dp(8), dp(16));
    r.setBackground(round(PANEL, dp(18)));
    LinearLayout labels = column();
    labels.addView(text(title, 15, TEXT, true));
    gap(labels, 5);
    TextView description = text(detail, 12, MUTED, false);
    description.setLineSpacing(dp(3), 1);
    labels.addView(description);
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (toggle) {
      boolean on = getSharedPreferences("settings", 0).getBoolean("data_saver", false);
      TextView value = text(on ? "ON" : "OFF", 12, on ? LIME : MUTED, true);
      value.setPadding(dp(12), 0, dp(8), 0);
      r.addView(value);
    } else r.addView(icon("next", MUTED, action));
    r.setOnClickListener(v -> action.run());
    parent.addView(r);
    gap(parent, 10);
  }

  private void openUrl(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (Exception e) {
      message("No browser is available to open this link");
    }
  }

  private void togglePlay() {
    if (controller == null) return;
    if (controller.isPlaying()) controller.pause();
    else {
      if (controller.getPlaybackState() == Player.STATE_ENDED) controller.seekToDefaultPosition();
      if (controller.getPlayerError() != null
          || controller.getPlaybackState() == Player.STATE_IDLE
          || controller.getPlaybackState() == Player.STATE_ENDED) {
        OnlineMusic.clearCache();
        controller.prepare();
      }
      controller.play();
    }
  }

  static String speedLabel(float speed) {
    return new java.math.BigDecimal(Float.toString(speed)).stripTrailingZeros().toPlainString()
        + "×";
  }

  private void updatePlayback() {
    if (controller == null || destroyed) return;
    refreshQueue();
    MediaItem item = controller.getCurrentMediaItem();
    mini.setVisibility(item == null ? View.GONE : View.VISIBLE);
    if (item == null) {
      if (playerDialog != null) playerDialog.dismiss();
      return;
    }
    miniTitle.setText(item.mediaMetadata.title);
    miniArtist.setText(
        controller.getPlaybackState() == Player.STATE_BUFFERING
            ? "Loading audio…"
            : item.mediaMetadata.artist);
    miniPlay.set(controller.isPlaying() ? "pause" : "play", LIME);
    String art =
        item.mediaMetadata.artworkUri == null ? "" : item.mediaMetadata.artworkUri.toString();
    if (!art.equals(miniArt.getTag())) artwork(miniArt, art);
    if (playerDialog != null && playerDialog.isShowing()) {
      if (speedButton != null)
        speedButton.setText(speedLabel(controller.getPlaybackParameters().speed));
      if (playerPanel != null) playerPanel.autoplayState(autoplayEnabled());
      playerTitle.setText(item.mediaMetadata.title);
      playerArtist.setText(item.mediaMetadata.artist);
      if (!art.equals(playerArt.getTag())) artwork(playerArt, art);
      playerPlay.set(controller.isPlaying() ? "pause" : "play", BG);
      playerLike.set(store.contains("favourites", Track.from(item)) ? "liked" : "heart", LIME);
      playerShuffle.set("shuffle", controller.getShuffleModeEnabled() ? LIME : MUTED);
      playerRepeat.set(
          controller.getRepeatMode() == Player.REPEAT_MODE_ONE ? "repeat_one" : "repeat",
          controller.getRepeatMode() == Player.REPEAT_MODE_OFF ? MUTED : LIME);
      playerState.setText(
          controller.getPlayerError() != null
              ? "Audio couldn’t load · Tap Retry or try another song"
              : controller.getPlaybackState() == Player.STATE_BUFFERING
                  ? "Loading audio…"
                  : OfflineLibrary.get(this).complete(item.mediaId)
                      ? "SAVED OFFLINE"
                      : Track.from(item).online()
                          ? OfflineLibrary.get(this).enabled()
                              ? "YOUTUBE AUDIO · AUTO-SAVE ON"
                              : "YOUTUBE AUDIO"
                          : "ON THIS PHONE");
    }
  }

  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (destroyed) return;
          if (controller != null
              && seek != null
              && playerDialog != null
              && playerDialog.isShowing()
              && !dragging) {
            long duration = controller.getDuration();
            long position = controller.getCurrentPosition();
            seek.setEnabled(duration > 0);
            seek.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
            playerElapsed.setText(format(position / 1000));
            playerDuration.setText(duration > 0 ? format(duration / 1000) : "--:--");
          }
          main.postDelayed(this, 500);
        }
      };

  private String format(long seconds) {
    return String.format(
        Locale.ROOT,
        seconds >= 3600 ? "%d:%02d:%02d" : "%d:%02d",
        seconds >= 3600
            ? new Object[] {seconds / 3600, (seconds % 3600) / 60, seconds % 60}
            : new Object[] {Math.max(0, seconds) / 60, Math.max(0, seconds) % 60});
  }

  private void showPlayer() {
    if (controller == null || controller.getCurrentMediaItem() == null) return;
    if (playerDialog != null && playerDialog.isShowing()) return;
    playerDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
    NowPlayingPanel p =
        new NowPlayingPanel(
            this,
            new NowPlayingPanel.Actions() {
              public void action(String name) {
                switch (name) {
                  case "down":
                    playerDialog.dismiss();
                    break;
                  case "queue":
                    showQueue();
                    break;
                  case "heart":
                    MediaItem item = controller.getCurrentMediaItem();
                    if (item != null) {
                      store.toggle("favourites", Track.from(item));
                      radioChanged();
                      updatePlayback();
                    }
                    break;
                  case "shuffle":
                    controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
                    updatePlayback();
                    break;
                  case "previous":
                    controller.seekToPrevious();
                    break;
                  case "play":
                    togglePlay();
                    break;
                  case "next":
                    controller.seekToNextMediaItem();
                    break;
                  case "repeat":
                    int mode = controller.getRepeatMode();
                    controller.setRepeatMode(
                        mode == Player.REPEAT_MODE_OFF
                            ? Player.REPEAT_MODE_ALL
                            : mode == Player.REPEAT_MODE_ALL
                                ? Player.REPEAT_MODE_ONE
                                : Player.REPEAT_MODE_OFF);
                    updatePlayback();
                    break;
                  case "timer":
                    sleepTimer();
                    break;
                  case "speed":
                    playbackSpeed();
                    break;
                  case "more":
                    MediaItem current = controller.getCurrentMediaItem();
                    if (current != null) songMenu(Track.from(current), null);
                    break;
                  case "autoplay":
                    setAutoplay(!autoplayEnabled());
                    break;
                  case "start-radio":
                    MediaItem seed = controller.getCurrentMediaItem();
                    if (seed != null) startRadio(Track.from(seed));
                    break;
                  case "tune-radio":
                    tuneRadio();
                    break;
                  case "seek-start":
                    dragging = true;
                    break;
                }
              }

              public void seek(int value, boolean finished) {
                long duration = controller.getDuration();
                if (duration > 0) {
                  if (finished) controller.seekTo(duration * value / 1000);
                  else playerElapsed.setText(format(duration * value / 1_000_000));
                }
                if (finished) dragging = false;
              }
            });
    playerPanel = p;
    playerArt = p.art;
    playerTitle = p.title;
    playerArtist = p.artist;
    playerElapsed = p.elapsed;
    playerDuration = p.duration;
    playerState = p.state;
    speedButton = p.speed;
    autoplayButton = p.autoplay;
    radioStatus = p.radioStatus;
    playerLike = p.favourite;
    playerShuffle = p.shuffle;
    playerPlay = p.play;
    playerRepeat = p.repeat;
    seek = p.seek;
    p.autoplayState(autoplayEnabled());
    radioStatus.setText(getSharedPreferences("playback_state", 0).getString("autoplay_status", ""));
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        p,
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    playerDialog.setContentView(p);
    Window window = playerDialog.getWindow();
    if (window != null) {
      window.setStatusBarColor(BG);
      window.setNavigationBarColor(BG);
      window.setLayout(-1, -1);
    }
    playerDialog.show();
    if (window != null) window.setLayout(-1, -1);
    p.requestApplyInsets();
    updatePlayback();
  }

  private void radioChanged() {
    if (controller != null)
      controller.sendCustomCommand(
          new SessionCommand(PlaybackService.AUTOPLAY_CHANGED, Bundle.EMPTY), Bundle.EMPTY);
  }

  private void startRadio(Track track) {
    if (!RadioPolicy.music(track)) {
      message("Start radio from an online song between 30 seconds and 15 minutes.");
      return;
    }
    if (controller == null) {
      message("Player is connecting. Try again in a moment.");
      return;
    }
    Bundle args = new Bundle();
    args.putString("track", track.json().toString());
    ListenableFuture<SessionResult> result =
        controller.sendCustomCommand(
            new SessionCommand(PlaybackService.START_RADIO, Bundle.EMPTY), args);
    result.addListener(
        () -> {
          try {
            if (result.get().resultCode == SessionResult.RESULT_SUCCESS && !destroyed) {
              if (queueDialog != null) queueDialog.dismiss();
              showPlayer();
              updatePlayback();
              message("Song radio started · " + track.title);
            } else if (!destroyed) message("Radio could not start. Try again.");
          } catch (Exception e) {
            message("Radio could not start. Try again.");
          }
        },
        main::post);
  }

  private void hideFromRadio(Track track) {
    new RadioStore(this).hide(track);
    if (controller != null) {
      Bundle args = new Bundle();
      args.putString("track", track.json().toString());
      controller.sendCustomCommand(
          new SessionCommand(PlaybackService.HIDE_RADIO, Bundle.EMPTY), args);
    }
    message("Hidden from future radio picks. Your current queue stays.");
  }

  private void tuneRadio() {
    RadioStore radio = new RadioStore(this);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(
        new RadioTuner(
            this,
            radio.mode(),
            mode -> {
              radio.mode(mode);
              radioChanged();
              message(mode.label + " · applies to the next radio refill");
            }));
    new AlertDialog.Builder(this)
        .setTitle("Song radio tuner")
        .setView(scroll)
        .setPositiveButton(
            "Done",
            (d, w) -> {
              if (tab.equals("Settings")) render();
            })
        .show();
  }

  private boolean autoplayEnabled() {
    return getSharedPreferences("settings", 0).getBoolean("autoplay", true);
  }

  private void setAutoplay(boolean enabled) {
    getSharedPreferences("settings", 0).edit().putBoolean("autoplay", enabled).apply();
    if (controller != null)
      controller.sendCustomCommand(
          new SessionCommand(PlaybackService.AUTOPLAY_CHANGED, Bundle.EMPTY), Bundle.EMPTY);
    if (playerPanel != null) playerPanel.autoplayState(enabled);
  }

  private boolean queueHasRoom() {
    if (controller != null && controller.getMediaItemCount() >= QueuePolicy.MAX_ITEMS) {
      message("Your queue is full. Remove a few songs first.");
      return false;
    }
    return true;
  }

  private void crossfadeSettings() {
    CrossfadeSettingsPanel panel =
        new CrossfadeSettingsPanel(
            this, getSharedPreferences("settings", 0).getInt("crossfade", 4));
    new AlertDialog.Builder(this)
        .setTitle("Crossfade")
        .setView(panel)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Save",
            (dialog, which) -> {
              getSharedPreferences("settings", 0)
                  .edit()
                  .putInt("crossfade", panel.seconds())
                  .apply();
              if (controller != null)
                controller.sendCustomCommand(
                    new SessionCommand(PlaybackService.CROSSFADE_CHANGED, Bundle.EMPTY),
                    Bundle.EMPTY);
              if (tab.equals("Settings")) render();
            })
        .show();
  }

  private void playbackSpeed() {
    if (controller == null) {
      message("Player is connecting");
      return;
    }
    float[] values = {.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    String[] labels = {"0.75×", "1× · Normal", "1.25×", "1.5×", "1.75×", "2×"};
    new AlertDialog.Builder(this)
        .setTitle("Playback speed")
        .setItems(
            labels,
            (d, w) -> {
              controller.setPlaybackSpeed(values[w]);
              updatePlayback();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private List<Track> currentQueue() {
    List<Track> songs = new ArrayList<>();
    if (controller != null)
      for (int i = 0; i < controller.getMediaItemCount(); i++)
        songs.add(Track.from(controller.getMediaItemAt(i)));
    return songs;
  }

  private void refreshQueue() {
    if (controller != null
        && queuePanel != null
        && queueDialog != null
        && queueDialog.isShowing()) {
      Timeline timeline = controller.getCurrentTimeline();
      int count = controller.getMediaItemCount();
      queueViewIndices =
          QueuePolicy.order(
              count,
              controller.getCurrentMediaItemIndex(),
              at ->
                  timeline.getWindowCount() == count
                      ? timeline.getNextWindowIndex(
                          at,
                          controller.getRepeatMode() == Player.REPEAT_MODE_ALL
                              ? Player.REPEAT_MODE_ALL
                              : Player.REPEAT_MODE_OFF,
                          controller.getShuffleModeEnabled())
                      : at + 1 < count ? at + 1 : -1);
      List<Track> displayed = new ArrayList<>();
      for (int index : queueViewIndices)
        displayed.add(Track.from(controller.getMediaItemAt(index)));
      queuePanel.autoplayState(autoplayEnabled());
      queuePanel.refresh(
          displayed,
          0,
          controller.isPlaying(),
          getSharedPreferences("playback_state", 0)
              .getString("autoplay_status", autoplayEnabled() ? "Autoplay on" : "Autoplay off"));
    }
  }

  private boolean queueEntryMatches(int index, String url) {
    return controller != null
        && index >= 0
        && index < controller.getMediaItemCount()
        && controller.getMediaItemAt(index).mediaId.equals(url);
  }

  private void queueOptions(int index, Track track) {
    new AlertDialog.Builder(this)
        .setTitle(track.title)
        .setItems(
            new String[] {"Play next", "Move up", "Move down", "Remove from queue", "Song options"},
            (d, w) -> {
              if (!queueEntryMatches(index, track.url)) {
                message("The queue changed. Open this song’s menu again.");
                refreshQueue();
                return;
              }
              switch (w) {
                case 0:
                  controller.setShuffleModeEnabled(false);
                  if (index != controller.getCurrentMediaItemIndex())
                    controller.moveMediaItem(
                        index,
                        QueuePolicy.playNextTarget(index, controller.getCurrentMediaItemIndex()));
                  break;
                case 1:
                  controller.setShuffleModeEnabled(false);
                  if (index > 0) controller.moveMediaItem(index, index - 1);
                  break;
                case 2:
                  controller.setShuffleModeEnabled(false);
                  if (index + 1 < controller.getMediaItemCount())
                    controller.moveMediaItem(index, index + 1);
                  break;
                case 3:
                  controller.removeMediaItem(index);
                  break;
                default:
                  songMenu(track, null);
              }
              refreshQueue();
            })
        .show();
  }

  private void showQueue() {
    if (controller == null) return;
    if (queueDialog != null && queueDialog.isShowing()) return;
    queueDialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
    queuePanel =
        new QueuePanel(
            this,
            autoplayEnabled(),
            new QueuePanel.Actions() {
              public void close() {
                queueDialog.dismiss();
              }

              public void play(int index, String url) {
                if (index < 0 || index >= queueViewIndices.size()) return;
                index = queueViewIndices.get(index);
                if (!queueEntryMatches(index, url)) {
                  refreshQueue();
                  return;
                }
                if (index == controller.getCurrentMediaItemIndex()) {
                  boolean wasPlaying = controller.isPlaying();
                  togglePlay();
                  if (!wasPlaying) {
                    queueDialog.dismiss();
                    showPlayer();
                  }
                  return;
                }
                controller.seekTo(index, 0);
                controller.prepare();
                controller.play();
                queueDialog.dismiss();
                showPlayer();
              }

              public void options(int index, Track track) {
                if (index >= 0 && index < queueViewIndices.size())
                  queueOptions(queueViewIndices.get(index), track);
              }

              public void clearUpcoming() {
                controller.sendCustomCommand(
                    new SessionCommand(PlaybackService.CLEAR_UPCOMING, Bundle.EMPTY), Bundle.EMPTY);
              }

              public void clearAll() {
                controller.sendCustomCommand(
                    new SessionCommand(PlaybackService.CLEAR_QUEUE, Bundle.EMPTY), Bundle.EMPTY);
              }

              public void tune() {
                tuneRadio();
              }

              public void retry() {
                radioChanged();
              }

              public void autoplay(boolean enabled) {
                setAutoplay(enabled);
              }
            });
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        queuePanel,
        (v, in) -> {
          androidx.core.graphics.Insets bars =
              in.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
          v.setPadding(
              dp(18) + bars.left, dp(6) + bars.top, dp(18) + bars.right, dp(12) + bars.bottom);
          return in;
        });
    queueDialog.setContentView(queuePanel);
    queueDialog.show();
    Window w = queueDialog.getWindow();
    if (w != null) {
      w.setLayout(-1, -1);
      w.setStatusBarColor(BG);
      w.setNavigationBarColor(BG);
    }
    queuePanel.requestApplyInsets();
    refreshQueue();
  }

  private void sleepTimer() {
    if (controller == null) return;
    String[] options = {
      "Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "90 minutes"
    };
    int[] minutes = {0, 15, 30, 45, 60, 90};
    new AlertDialog.Builder(this)
        .setTitle("Sleep timer")
        .setItems(
            options,
            (d, w) -> {
              Bundle args = new Bundle();
              args.putInt("minutes", minutes[w]);
              controller.sendCustomCommand(
                  new SessionCommand(PlaybackService.SLEEP, Bundle.EMPTY), args);
              message(
                  w == 0
                      ? "Sleep timer turned off"
                      : "Playback will pause in " + minutes[w] + " minutes");
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showPlaybackError() {
    if (destroyed) return;
    new AlertDialog.Builder(this)
        .setTitle("Couldn’t play this song")
        .setMessage(
            "Check your connection or try another song. YouTube may restrict this track or require"
                + " an extractor update. A local file may have been moved or permission may have"
                + " changed.")
        .setPositiveButton(
            "Retry",
            (d, w) -> {
              OnlineMusic.clearCache();
              if (controller != null) {
                controller.prepare();
                controller.play();
              }
            })
        .setNeutralButton(
            "Song options",
            (d, w) -> {
              if (controller != null && controller.getCurrentMediaItem() != null)
                songMenu(Track.from(controller.getCurrentMediaItem()), null);
            })
        .setNegativeButton("Close", null)
        .show();
  }
}
