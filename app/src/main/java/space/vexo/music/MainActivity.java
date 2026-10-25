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
  private UserProfile profile;
  private ProfilePanel profilePanel;
  private boolean profileEditing, profilePhotoLoading;
  private String profileDraft = "";
  private final ActivityResultLauncher<String[]> profilePhotoPicker =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri == null || profilePhotoLoading) return;
            profilePhotoLoading = true;
            io.execute(
                () -> {
                  String error = "";
                  try {
                    profile.importPhoto(uri);
                  } catch (Exception e) {
                    AppDiagnostics.record(MainActivity.this, "profile_photo", e);
                    error =
                        e instanceof java.io.IOException && e.getMessage() != null
                            ? e.getMessage()
                            : "Couldn’t use that photo. Try a different image.";
                  }
                  String message = error;
                  main.post(
                      () -> {
                        profilePhotoLoading = false;
                        if (MainActivity.this.destroyed) return;
                        if (profilePanel != null) profilePanel.refreshPhoto();
                        if (MainActivity.this.homePanel != null)
                          MainActivity.this.homePanel.personalize();
                        if (!message.isEmpty()) message(message);
                      });
                });
          });
  private boolean clockRegistered;
  private int discoveryEpoch;
  private Future<?> homeTask;
  private final BroadcastReceiver clockChanged =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (!destroyed && homePanel != null) homePanel.personalize();
        }
      };
  private MediaController controller;
  private ListenableFuture<MediaController> controllerFuture;
  private LinearLayout root, content, mini, nav;
  private TextView miniTitle, miniArtist;
  private ImageView miniArt;
  private Icon miniPlay;
  private ProgressBar miniProgress;
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
  private MusicPreview musicPreview;
  private MixCatalogue mixCatalogue;
  private MusicHomePanel homePanel;
  private final ThreadPoolExecutor mixIo = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
  private final Set<String> prefetchingMixes = new HashSet<>();
  private final Set<String> attemptedMixes = new HashSet<>();
  private String homeFilter = "All",
      activeCollection = "",
      collectionTitle = "",
      activeMix = "",
      addingToPlaylist = "";
  private String mixError = "";
  private boolean mixLoading;
  private int mixGeneration;
  private String previewTerm = "";
  private List<Track> previewSongs = new ArrayList<>();
  private List<String> previewTerms = new ArrayList<>();
  private boolean previewLoading, previewFailed, phraseFailed, keyboardOpen;
  private View searchTitleView, searchSources;

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
    profile = new UserProfile(this);
    store = new LibraryStore(this);
    searchHistory = new SearchHistory(this);
    suggestions =
        new SearchSuggestions(main, Executors.newSingleThreadExecutor(), SearchSuggestions.ONLINE);
    musicPreview = new MusicPreview(main, Executors.newFixedThreadPool(2), OnlineMusic::preview);
    mixCatalogue = new MixCatalogue(this);
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
      activeCollection = state.getString("collection", "");
      collectionTitle = state.getString("collection_title", "");
      activeMix = state.getString("mix", "");
      homeFilter = state.getString("home_filter", "All");
      addingToPlaylist = state.getString("adding_playlist", "");
      profileEditing = state.getBoolean("profile_editing", false);
      profileDraft = state.getString("profile_draft", profile.name());
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
          keyboardOpen = keyboard.bottom > bars.bottom;
          updateSearchChrome();
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
            AppDiagnostics.record(this, "player_connection", e);
            message("Could not connect to the player. Try Refresh & repair in Settings.");
          }
        },
        androidx.core.content.ContextCompat.getMainExecutor(this));
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (!profile.complete()) {
                  finish();
                  return;
                }
                if (profileEditing) {
                  profileEditing = false;
                  profileDraft = profile.name();
                  render();
                  return;
                }
                if (!activeCollection.isEmpty() || !activeMix.isEmpty()) {
                  closeCollection();
                  return;
                }
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
    if (!activeMix.isEmpty()) loadMix(false);
    main.postDelayed(this::announceOrCheckUpdates, 700);
  }

  private void announceOrCheckUpdates() {
    if (destroyed || isFinishing() || !profile.complete() || profileEditing) return;
    if (updates.shouldAnnounceInstalled(BuildConfig.VERSION_CODE)) showWhatsNew(true);
    else if (updates.automatic() && updates.shouldCheck(System.currentTimeMillis()))
      checkUpdates(false);
  }

  @android.annotation.SuppressLint(
      "UnspecifiedRegisterReceiverFlag") // Only protected system actions below.
  @Override
  protected void onResume() {
    super.onResume();
    main.removeCallbacks(tick);
    main.post(tick);
    if (homePanel != null) homePanel.personalize();
    if (!clockRegistered) {
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
      filter.addAction(Intent.ACTION_TIME_CHANGED);
      filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      // All three actions are protected system broadcasts. Older Android versions do not
      // require AndroidX's app-private receiver permission for this system-only receiver.
      if (Build.VERSION.SDK_INT >= 33)
        registerReceiver(clockChanged, filter, Context.RECEIVER_NOT_EXPORTED);
      else registerReceiver(clockChanged, filter);
      clockRegistered = true;
    }
  }

  private void stopClock() {
    if (clockRegistered) {
      unregisterReceiver(clockChanged);
      clockRegistered = false;
    }
  }

  @Override
  protected void onPause() {
    stopClock();
    main.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    if (searchField != null) query = searchField.getText().toString();
    b.putString("tab", tab);
    b.putString("shelf", shelf);
    b.putString("query", query);
    b.putBoolean("online", online);
    b.putString("collection", activeCollection);
    b.putString("collection_title", collectionTitle);
    b.putString("mix", activeMix);
    b.putString("home_filter", homeFilter);
    b.putString("adding_playlist", addingToPlaylist);
    b.putBoolean("profile_editing", profileEditing);
    b.putString("profile_draft", profilePanel == null ? profileDraft : profilePanel.draft());
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    stopClock();
    discoveryEpoch++;
    if (homeTask != null) homeTask.cancel(true);
    homeGeneration++;
    suggestions.close();
    musicPreview.close();
    mixIo.shutdownNow();
    mixGeneration++;
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
      item.addView(i, new LinearLayout.LayoutParams(dp(48), dp(48)));
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
            activeCollection = "";
            activeMix = "";
            mixGeneration++;
            mixLoading = false;
            if (!label.equals("Search")) addingToPlaylist = "";
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(root.getWindowToken(), 0);
            render();
          });
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
  }

  private void buildMini() {
    mini = column();
    android.graphics.drawable.GradientDrawable background =
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {0xff222d2b, PANEL});
    background.setCornerRadius(dp(18));
    background.setStroke(dp(1), 0xff303e39);
    mini.setBackground(background);
    mini.setPadding(dp(8), dp(8), dp(4), dp(5));
    mini.setMinimumHeight(dp(66));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(dp(14), dp(6), dp(14), dp(4));
    root.addView(mini, p);
    mini.setVisibility(View.GONE);
    LinearLayout line = row();
    mini.addView(line, new LinearLayout.LayoutParams(-1, -2));
    miniArt = new ImageView(this);
    miniArt.setBackground(round(PANEL, dp(9)));
    miniArt.setClipToOutline(true);
    line.addView(miniArt, new LinearLayout.LayoutParams(dp(44), dp(44)));
    LinearLayout copy = column();
    copy.setPadding(dp(10), 0, dp(4), 0);
    miniTitle = text("", 14, TEXT, true);
    miniTitle.setSingleLine();
    miniTitle.setEllipsize(TextUtils.TruncateAt.END);
    miniArtist = text("", 12, MUTED, false);
    miniArtist.setSingleLine();
    miniArtist.setEllipsize(TextUtils.TruncateAt.END);
    copy.addView(miniTitle);
    gap(copy, 4);
    copy.addView(miniArtist);
    line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    miniPlay = icon("play", LIME, this::togglePlay);
    line.addView(miniPlay);
    line.addView(
        icon(
            "next",
            TEXT,
            () -> {
              if (controller != null) controller.seekToNextMediaItem();
            }));
    miniProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    miniProgress.setMax(1000);
    miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
    miniProgress.setProgressBackgroundTintList(
        android.content.res.ColorStateList.valueOf(0xff3b4544));
    miniProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams progress = new LinearLayout.LayoutParams(-1, dp(2));
    progress.setMargins(dp(5), dp(5), dp(5), 0);
    mini.addView(miniProgress, progress);
    mini.setContentDescription("Open now playing");
    mini.setOnClickListener(v -> showPlayer());
  }

  private void render() {
    if (destroyed) return;
    suggestions.cancel();
    musicPreview.cancel();
    searchTitleView = null;
    searchSources = null;
    homePanel = null;
    suggestionsVisible = false;
    suggestionScroll = null;
    suggestionBody = null;
    if (profilePanel != null) profileDraft = profilePanel.draft();
    profilePanel = null;
    content.removeAllViews();
    listAdapter = null;
    searchField = null;
    status = null;
    offlineSummary = null;
    refreshNav();
    if (!profile.complete() || profileEditing) {
      nav.setVisibility(View.GONE);
      mini.setVisibility(View.GONE);
      renderProfile();
      return;
    }
    nav.setVisibility(View.VISIBLE);
    if (!activeCollection.isEmpty() || !activeMix.isEmpty()) {
      renderCollection();
      return;
    }
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

  private void openProfile() {
    profileEditing = true;
    profileDraft = profile.name();
    render();
  }

  private void renderProfile() {
    profilePanel =
        new ProfilePanel(
            this,
            profile,
            !profile.complete(),
            profileDraft,
            new ProfilePanel.Listener() {
              public void photo() {
                if (!profilePhotoLoading) profilePhotoPicker.launch(new String[] {"image/*"});
              }

              public void removePhoto() {
                profile.removePhoto();
                if (profilePanel != null) profilePanel.refreshPhoto();
              }

              public void save(String name) {
                if (!profile.saveName(name)) {
                  if (profilePanel != null) profilePanel.nameError();
                  return;
                }
                profileDraft = profile.name();
                profileEditing = false;
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(root.getWindowToken(), 0);
                render();
                updatePlayback();
                loadHome(false);
                main.postDelayed(MainActivity.this::announceOrCheckUpdates, 600);
              }

              public void back() {
                profileEditing = false;
                profileDraft = profile.name();
                render();
                updatePlayback();
              }
            });
    content.addView(profilePanel, new LinearLayout.LayoutParams(-1, 0, 1));
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
    if (!profile.complete()) return;
    String country = RegionPolicy.current(this);
    if (homeLoading && !force) return;
    if (force && homeTask != null) homeTask.cancel(true);
    if (!force
        && homeFeed != null
        && homeFeed.country.equals(country)
        && System.currentTimeMillis() >= homeFeed.fetchedAt
        && System.currentTimeMillis() - homeFeed.fetchedAt < 30 * 60_000L) return;
    homeLoading = true;
    homeError = "";
    int generation = ++homeGeneration;
    if (tab.equals("Home")) render();
    homeTask =
        io.submit(
            () -> {
              try {
                RegionalFeed found = RegionalFeed.fetch(country);
                main.post(
                    () -> {
                      if (destroyed || generation != homeGeneration) return;
                      found.persist(this);
                      homeFeed = found;
                      homeLoading = false;
                      if (tab.equals("Home")) render();
                    });
              } catch (Exception error) {
                if (!Thread.currentThread().isInterrupted())
                  AppDiagnostics.record(this, "home_refresh", error);
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
    MusicHomePanel panel =
        new MusicHomePanel(
            this,
            store,
            mixCatalogue,
            homeFeed,
            RegionPolicy.current(this),
            homeLoading,
            homeError,
            homeFilter,
            new MusicHomePanel.Listener() {
              public void smart() {
                showSmart(null);
              }

              public void region() {
                chooseRegion();
              }

              public void profile() {
                openProfile();
              }

              public void refresh() {
                refreshApp();
              }

              public void search() {
                tab = "Search";
                render();
              }

              public void library(String section) {
                tab = "Library";
                shelf = section;
                render();
              }

              public void collection(String name, String key) {
                showCollection(name, key);
              }

              public void mix(MixCatalogue.Mix mix) {
                openMix(mix);
              }

              public void create() {
                createPlaylist(null);
              }

              public void play(List<Track> songs, int index) {
                MainActivity.this.play(songs, index);
              }

              public void filter(String value) {
                homeFilter = value;
                render();
              }
            });
    homePanel = panel;
    content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    main.postDelayed(this::prefetchHomeMixes, 600);
  }

  private void prefetchHomeMixes() {
    try {
      org.json.JSONObject smart = new SmartStore(this).preferences();
      if (smart.optBoolean("focus") || smart.optBoolean("hide:Mood mixes")) return;
    } catch (IllegalStateException unavailable) {
      /* Existing Home remains available. */
    }

    if (destroyed || !tab.equals("Home") || !activeMix.isEmpty() || !activeCollection.isEmpty())
      return;
    String country = RegionPolicy.current(this);
    final int epoch = discoveryEpoch;
    List<MixCatalogue.Mix> mixes = MixCatalogue.mixes(country);
    for (MixCatalogue.Mix mix : mixes.subList(0, Math.min(3, mixes.size()))) {
      String key = country + ":" + mix.id;
      if (mixCatalogue.fresh(mix, country, System.currentTimeMillis())
          || attemptedMixes.contains(key)) continue;
      attemptedMixes.add(key);
      prefetchingMixes.add(key);
      mixIo.execute(
          () -> {
            List<Track> songs = Collections.emptyList();
            boolean failed = false;
            try {
              songs = MixCatalogue.songs(OnlineMusic.preview(mix.query, country));
              if (songs.isEmpty()) failed = true;
              // Cache only after the foreground generation check below.
            } catch (Exception error) {
              failed = true;
              if (!Thread.currentThread().isInterrupted())
                AppDiagnostics.record(this, "mix_refresh", error);
            }
            List<Track> found = songs;
            boolean unavailable = failed;
            main.post(
                () -> {
                  if (destroyed
                      || epoch != discoveryEpoch
                      || !country.equals(RegionPolicy.current(this))) return;
                  prefetchingMixes.remove(key);
                  if (!unavailable) mixCatalogue.save(mix, country, found);
                  if (homePanel != null && !unavailable) homePanel.updateMix(mix, found);
                  if (activeMix.equals(mix.id)) {
                    mixLoading = false;
                    mixError =
                        unavailable
                            ? "Couldn’t refresh this mix. Check your connection and try again."
                            : "";
                    render();
                  }
                });
          });
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
              discoveryEpoch++;
              mixIo.getQueue().clear();
              attemptedMixes.clear();
              prefetchingMixes.clear();
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
    LinearLayout h = column();
    h.setPadding(dp(20), dp(12), dp(20), dp(8));
    searchTitleView = text("Search music", 23, TEXT, true);
    h.addView(searchTitleView);
    gap(h, 10);
    content.addView(h);
    if (!addingToPlaylist.isEmpty()) {
      TextView done =
          button(
              "Done · " + addingToPlaylist,
              () -> {
                String name = addingToPlaylist;
                addingToPlaylist = "";
                tab = "Library";
                shelf = "Playlists";
                showCollection(name, "playlist:" + name);
              });
      h.addView(done);
      gap(h, 8);
    }
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
    searchSources = source;
    gap(h, 6);
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
    updateSearchChrome();
    if (online && query.isEmpty()) requestSuggestions();
  }

  private void updateSearchChrome() {
    if (searchTitleView != null)
      searchTitleView.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
    if (searchSources != null) searchSources.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
    if (tab.equals("Search") && status != null)
      status.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
  }

  private void requestSuggestions() {
    if (searchField == null || suggestionBody == null || !online || !tab.equals("Search")) return;
    final String value = SearchHistory.clean(searchField.getText().toString());
    previewTerm = value;
    previewTerms = new ArrayList<>();
    previewSongs = new ArrayList<>();
    previewLoading = RadioPolicy.normalize(value).length() >= 2 && !value.contains("://");
    previewFailed = false;
    phraseFailed = false;
    showSuggestions(value, previewTerms, false);
    suggestions.request(
        value,
        RegionPolicy.current(this),
        (term, remote, unavailable) -> {
          if (!previewCurrent(term)) return;
          previewTerms = remote;
          phraseFailed = unavailable;
          showSuggestions(term, previewTerms, phraseFailed);
        });
    musicPreview.request(
        value,
        RegionPolicy.current(this),
        (term, songs, unavailable) -> {
          if (!previewCurrent(term)) return;
          previewSongs = songs;
          previewLoading = false;
          previewFailed = unavailable;
          showSuggestions(term, previewTerms, phraseFailed);
        });
  }

  private boolean previewCurrent(String term) {
    return !destroyed
        && tab.equals("Search")
        && online
        && suggestionsVisible
        && searchField != null
        && SearchHistory.clean(searchField.getText().toString()).equals(term)
        && previewTerm.equals(term);
  }

  private List<Track> cachedSearchSongs(String term) {
    List<Track> cached = new ArrayList<>(store.tracks("history"));
    cached.addAll(store.tracks("favourites"));
    cached.addAll(OfflineLibrary.get(this).saved());
    if (homeFeed != null && homeFeed.country.equals(RegionPolicy.current(this)))
      cached.addAll(homeFeed.tracks);
    for (String name : store.playlists()) cached.addAll(store.tracks("playlist:" + name));
    return MusicPreview.combine(term, cached, previewSongs, 6);
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
    List<Track> matches = term.trim().isEmpty() ? Collections.emptyList() : cachedSearchSongs(term);
    if (!matches.isEmpty()) {
      TextView songsLabel = text("Songs", 13, LIME, true);
      suggestionBody.addView(songsLabel);
      TrackList adapter = new TrackList(matches, null);
      for (int i = 0; i < matches.size(); i++) {
        final int index = i;
        TrackHolder holder = adapter.onCreateViewHolder(suggestionBody, 0);
        adapter.onBindViewHolder(holder, i);
        holder.itemView.setOnClickListener(
            v -> {
              searchHistory.add(term);
              ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(root.getWindowToken(), 0);
              play(matches, index);
            });
        suggestionBody.addView(holder.itemView, new LinearLayout.LayoutParams(-1, -2));
      }
      LinkedHashSet<String> artists = new LinkedHashSet<>();
      for (Track t : matches)
        if (RadioPolicy.normalize(t.artist).contains(RadioPolicy.normalize(term)))
          artists.add(t.artist);
      if (!artists.isEmpty()) {
        TextView artistLabel = text("Artists & channels", 13, LIME, true);
        artistLabel.setPadding(0, dp(12), 0, dp(8));
        suggestionBody.addView(artistLabel);
        int count = 0;
        for (String artist : artists) {
          if (count++ >= 2) break;
          TextView name = text(artist, 15, TEXT, true);
          name.setMinHeight(dp(48));
          name.setGravity(Gravity.CENTER_VERTICAL);
          name.setContentDescription("Find music by " + artist);
          name.setOnClickListener(
              v -> {
                searchField.setText(artist);
                search();
              });
          suggestionBody.addView(name);
        }
      }
    }
    if (previewLoading) {
      TextView loading = text("Finding matching songs…", 12, MUTED, false);
      loading.setPadding(0, dp(8), 0, dp(12));
      suggestionBody.addView(loading);
    } else if (previewFailed && !term.trim().isEmpty()) {
      TextView failed =
          text(
              matches.isEmpty()
                  ? "Couldn’t load song matches. Tap search to retry."
                  : "Showing saved matches. Online matches are unavailable.",
              12,
              MUTED,
              false);
      failed.setPadding(0, dp(8), 0, dp(12));
      suggestionBody.addView(failed);
    }
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
    for (String choice : choices.subList(0, Math.min(matches.isEmpty() ? 8 : 4, choices.size()))) {
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
    musicPreview.cancel();
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
              String artwork = ArtworkQuality.best(info.getThumbnails());
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
              if (found.tracks.isEmpty()) {
                for (String variant : SmartPolicy.variants(request)) {
                  if (variant.equals(request)) continue;
                  OnlineMusic.Results alternative = OnlineMusic.search(variant);
                  if (!alternative.tracks.isEmpty()) {
                    found = alternative;
                    break;
                  }
                }
              }
              final OnlineMusic.Results delivered = found;
              main.post(
                  () -> {
                    if (destroyed || generation != searchGeneration) return;
                    onlinePage = delivered;
                    results = new ArrayList<>(delivered.tracks);
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
                                          try {
                                            store.deletePlaylist(name);
                                          } catch (IllegalStateException error) {
                                            message(error.getMessage());
                                            return;
                                          }
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
      h.more.set("more", MUTED);
      h.more.setContentDescription("Options for " + t.title);
      h.more.setOnClickListener(v -> songMenu(t, collection));
      if (tab.equals("Search") && !addingToPlaylist.isEmpty()) {
        h.more.set("add", LIME);
        h.more.setContentDescription("Add " + t.title + " to " + addingToPlaylist);
        h.more.setOnClickListener(
            v -> {
              try {
                store.addToPlaylist(addingToPlaylist, t);
              } catch (IllegalStateException error) {
                message(error.getMessage());
                return;
              }
              message("Added to " + addingToPlaylist);
            });
      }
      h.itemView.setOnLongClickListener(
          v -> {
            songMenu(t, collection);
            return true;
          });
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
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    if (tab.equals("Search") && searchField != null)
      searchHistory.add(searchField.getText().toString());
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
    options.add("Smart feedback & song memory");
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
              if (action.equals("Smart feedback & song memory")) {
                showSmart(track);
                return;
              }
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
                  if (tab.equals("Library") || tab.equals("Home") || !activeCollection.isEmpty())
                    render();
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
                    try {
                      store.save(collection, saved);
                    } catch (IllegalStateException error) {
                      message(error.getMessage());
                      return;
                    }
                    if (tab.equals("Library") || tab.equals("Home") || !activeCollection.isEmpty())
                      render();
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
                try {
                  store.addToPlaylist(names.get(w), track);
                } catch (IllegalStateException error) {
                  message(error.getMessage());
                  return;
                }
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
                      if (store.playlists().stream().anyMatch(n -> n.equalsIgnoreCase(name))) {
                        input.setError("That playlist already exists");
                        return;
                      }
                      store.createPlaylist(name);
                      if (track != null) store.addToPlaylist(name, track);
                      dialog.dismiss();
                      if (tab.equals("Library")
                          || tab.equals("Home")
                          || !activeCollection.isEmpty()) render();
                      message("Playlist created");
                    }));
    dialog.show();
  }

  private void showCollection(String title, String key) {
    activeCollection = key;
    collectionTitle = title;
    activeMix = "";
    addingToPlaylist = "";
    mixGeneration++;
    mixLoading = false;
    mixError = "";
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    render();
  }

  private void closeCollection() {
    activeCollection = "";
    activeMix = "";
    mixGeneration++;
    mixLoading = false;
    mixError = "";
    render();
  }

  private void openMix(MixCatalogue.Mix mix) {
    activeCollection = "";
    activeMix = mix.id;
    collectionTitle = mix.name;
    mixError = "";
    mixLoading = false;
    render();
    loadMix(false);
  }

  private void loadMix(boolean force) {
    if (activeMix.isEmpty() || mixLoading) return;
    String country = RegionPolicy.current(this);
    MixCatalogue.Mix mix = MixCatalogue.find(activeMix, country);
    if (mix == null) return;
    if (!force && mixCatalogue.fresh(mix, country, System.currentTimeMillis())) return;
    if (prefetchingMixes.contains(country + ":" + mix.id)) {
      mixLoading = true;
      render();
      return;
    }
    mixLoading = true;
    mixError = "";
    int generation = ++mixGeneration;
    render();
    io.execute(
        () -> {
          try {
            List<Track> songs = MixCatalogue.songs(OnlineMusic.regionalSearch(mix.query, country));
            if (songs.isEmpty()) throw new java.io.IOException("No songs in this mix");
            main.post(
                () -> {
                  if (destroyed
                      || generation != mixGeneration
                      || !country.equals(RegionPolicy.current(this))) return;
                  mixCatalogue.save(mix, country, songs);
                  mixLoading = false;
                  mixError = "";
                  if (activeMix.equals(mix.id)) render();
                });
          } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted())
              AppDiagnostics.record(this, "mix_refresh", e);
            main.post(
                () -> {
                  if (destroyed || generation != mixGeneration) return;
                  mixLoading = false;
                  mixError = "Couldn’t refresh this mix. Check your connection and try again.";
                  if (activeMix.equals(mix.id)) render();
                });
          }
        });
  }

  private void renderCollection() {
    String country = RegionPolicy.current(this);
    boolean isMix = !activeMix.isEmpty();
    MixCatalogue.Mix mix = isMix ? MixCatalogue.find(activeMix, country) : null;
    if (isMix && mix == null) {
      activeMix = "";
      renderHome();
      return;
    }
    List<Track> songs = isMix ? mixCatalogue.cached(mix, country) : store.tracks(activeCollection);
    if (isMix
        && songs.isEmpty()
        && mix.id.equals("regional")
        && homeFeed != null
        && homeFeed.country.equals(country)) songs = MixCatalogue.songs(homeFeed.tracks);
    List<Track> collection = new ArrayList<>(songs);
    boolean editable = activeCollection.startsWith("playlist:");
    PlaylistPanel panel =
        new PlaylistPanel(
            this,
            isMix ? mix.name : collectionTitle,
            isMix ? mix.detail : "Your music, together.",
            collection,
            isMix,
            editable,
            isMix && mixLoading,
            isMix ? mixError : "",
            isMix ? mix.color : 0xff665a9b,
            new PlaylistPanel.Listener() {
              public void back() {
                closeCollection();
              }

              public void play(int index) {
                if (controller != null) controller.setShuffleModeEnabled(false);
                MainActivity.this.play(collection, index);
              }

              public void shuffle() {
                if (collection.isEmpty()) return;
                List<Track> shuffled = new ArrayList<>(collection);
                try {
                  shuffled = SmartPolicy.shuffle(shuffled, new SmartStore(MainActivity.this));
                } catch (IllegalStateException unavailable) {
                  message(unavailable.getMessage());
                  return;
                }
                if (shuffled.isEmpty()) {
                  message(
                      "All songs are restricted by anti-repeat or feedback. Adjust Smart experience"
                          + " controls.");
                  return;
                }
                if (controller != null) controller.setShuffleModeEnabled(false);
                MainActivity.this.play(shuffled, 0);
              }

              public void options(Track track) {
                songMenu(track, isMix ? null : activeCollection);
              }

              public void add() {
                addingToPlaylist = editable ? collectionTitle : "";
                activeCollection = "";
                activeMix = "";
                tab = "Search";
                online = true;
                query = "";
                render();
                if (searchField != null) searchField.requestFocus();
              }

              public void edit() {
                editPlaylist();
              }

              public void save() {
                if (collection.isEmpty()) return;
                String name = store.savePlaylistCopy(mix.name, collection);
                message("Saved to " + name);
                showCollection(name, "playlist:" + name);
              }

              public void retry() {
                loadMix(true);
              }
            });
    content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private void editPlaylist() {
    if (!activeCollection.startsWith("playlist:")) return;
    String name = collectionTitle;
    new AlertDialog.Builder(this)
        .setTitle(name)
        .setItems(
            new String[] {
              "Rename playlist", "Remove songs", "Delete playlist", "Time Machine / undo"
            },
            (d, which) -> {
              if (which == 3) {
                showSmart(null);
                return;
              }
              if (which == 0) renamePlaylist(name);
              else if (which == 1) {
                List<Track> songs = store.tracks("playlist:" + name);
                String[] labels = songs.stream().map(t -> t.title).toArray(String[]::new);
                new AlertDialog.Builder(this)
                    .setTitle("Remove a song")
                    .setItems(
                        labels,
                        (x, index) -> {
                          List<Track> current = store.tracks("playlist:" + name);
                          current.removeIf(t -> t.url.equals(songs.get(index).url));
                          try {
                            store.save("playlist:" + name, current);
                          } catch (IllegalStateException error) {
                            message(error.getMessage());
                            return;
                          }
                          render();
                        })
                    .setNegativeButton("Close", null)
                    .show();
              } else
                new AlertDialog.Builder(this)
                    .setTitle("Delete playlist?")
                    .setMessage("Your music files stay on this phone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                        "Delete",
                        (x, y) -> {
                          try {
                            store.deletePlaylist(name);
                          } catch (IllegalStateException error) {
                            message(error.getMessage());
                            return;
                          }
                          closeCollection();
                        })
                    .show();
            })
        .show();
  }

  private void renamePlaylist(String oldName) {
    EditText input = new EditText(this);
    input.setText(oldName);
    input.setSingleLine();
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Rename playlist")
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
                      String name = input.getText().toString().trim();
                      boolean renamed;
                      try {
                        renamed = store.renamePlaylist(oldName, name);
                      } catch (IllegalStateException error) {
                        message(error.getMessage());
                        return;
                      }
                      if (!renamed) {
                        input.setError("Use a unique name between 1 and 60 characters");
                        return;
                      }
                      collectionTitle = name;
                      activeCollection = "playlist:" + name;
                      dialog.dismiss();
                      render();
                    }));
    dialog.show();
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

  private void refreshApp() {
    if (destroyed) return;
    discoveryEpoch++;
    mixIo.getQueue().clear();
    homeGeneration++;
    mixGeneration++;
    if (homeTask != null) homeTask.cancel(true);
    homeLoading = false;
    mixLoading = false;
    attemptedMixes.clear();
    prefetchingMixes.clear();
    suggestions.clearCache();
    musicPreview.clearCache();
    AppRecovery.refresh(this);
    homeFeed = null;
    homeError = "";
    mixError = "";
    activeMix = "";
    activeCollection = "";
    tab = "Home";
    render();
    updatePlayback();
    loadHome(true);
    scanPhone();
    message("Temporary data refreshed. Reloading music… Your saved music stays.");
  }

  private void reportProblem() {
    String report = new AppDiagnostics(this).report();
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = column();
    body.setPadding(dp(20), dp(8), dp(20), dp(16));
    scroll.addView(body);
    TextView intro =
        text(
            "To: "
                + AppDiagnostics.EMAIL
                + "\n\n"
                + "Review the report below. Your email app opens so you can choose whether to send"
                + " it.",
            14,
            TEXT,
            false);
    body.addView(intro);
    gap(body, 16);
    EditText description = new EditText(this);
    description.setTextColor(TEXT);
    description.setHintTextColor(MUTED);
    description.setHint("What happened? (optional)");
    description.setContentDescription("Describe the problem");
    description.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    description.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(2000)});
    body.addView(description);
    gap(body, 14);
    TextView details = text(report, 12, MUTED, false);
    details.setTextIsSelectable(true);
    body.addView(details);
    new AlertDialog.Builder(this)
        .setTitle("Report a problem")
        .setView(scroll)
        .setNegativeButton("Cancel", null)
        .setNeutralButton(
            "Copy report", (d, w) -> copyReport(description.getText() + "\n\n" + report))
        .setPositiveButton(
            "Open email",
            (d, w) -> {
              try {
                startActivity(AppDiagnostics.email(report, description.getText().toString()));
              } catch (ActivityNotFoundException noEmail) {
                new AlertDialog.Builder(this)
                    .setTitle("No email app available")
                    .setMessage(
                        "Copy this report and send it to "
                            + AppDiagnostics.EMAIL
                            + " from your email service.")
                    .setPositiveButton(
                        "Copy report",
                        (x, y) -> copyReport(description.getText() + "\n\n" + report))
                    .setNegativeButton("Close", null)
                    .show();
              }
            })
        .show();
  }

  private void copyReport(String report) {
    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
        .setPrimaryClip(android.content.ClipData.newPlainText("VEXO diagnostic report", report));
    message("Report copied");
  }

  private void showSmart(Track track) {
    Dialog dialog = new Dialog(this);
    SmartStore smart = new SmartStore(this);
    final boolean[] closed = {false};
    final int[] generation = {0};
    SmartExperiencePanel panel =
        new SmartExperiencePanel(
            this,
            smart,
            store,
            new SmartExperiencePanel.Listener() {
              public void changed() {
                radioChanged();
                if (tab.equals("Home")) render();
              }

              public void play(List<Track> songs, int index, long moment) {
                dialog.dismiss();
                MainActivity.this.play(songs, index);
                if (controller != null && moment > 0) controller.seekTo(moment);
              }

              public void discover(String query, boolean newArtists) {
                int token = ++generation[0];
                message("Finding your music…");
                io.execute(
                    () -> {
                      try {
                        LinkedHashMap<String, Track> pool = new LinkedHashMap<>();
                        for (String variant : SmartPolicy.variants(query)) {
                          if (closed[0] || destroyed || token != generation[0]) return;
                          for (Track t : OnlineMusic.search(variant).tracks)
                            pool.putIfAbsent(t.url, t);
                        }
                        List<Track> picks =
                            SmartPolicy.rank(
                                new ArrayList<>(pool.values()),
                                smart,
                                store.tracks("history"),
                                newArtists);
                        if (newArtists) {
                          Set<String> known = new HashSet<>();
                          for (Track t : store.tracks("history")) known.add(RadioPolicy.artist(t));
                          picks.removeIf(t -> known.contains(RadioPolicy.artist(t)));
                        }
                        main.post(
                            () -> {
                              if (closed[0] || destroyed || token != generation[0]) return;
                              if (picks.isEmpty()) {
                                message(
                                    "No eligible songs found. Try another clue or reduce"
                                        + " anti-repeat restrictions.");
                                return;
                              }
                              new AlertDialog.Builder(MainActivity.this)
                                  .setTitle(
                                      newArtists ? "Your discovery trail" : "Your MindRadio picks")
                                  .setItems(
                                      picks.stream()
                                          .map(t -> t.title + " · " + t.artist)
                                          .toArray(String[]::new),
                                      (d, which) -> {
                                        dialog.dismiss();
                                        MainActivity.this.play(picks, which);
                                      })
                                  .setNegativeButton("Close", null)
                                  .show();
                            });
                      } catch (Exception error) {
                        main.post(
                            () -> {
                              if (!closed[0] && !destroyed)
                                message(
                                    "Couldn’t load music. Check your connection and try again.");
                            });
                      }
                    });
              }
            });
    if (track != null)
      panel.track(
          track,
          controller != null
                  && controller.getCurrentMediaItem() != null
                  && controller.getCurrentMediaItem().mediaId.equals(track.url)
              ? controller.getCurrentPosition()
              : 0);
    panel.setBackgroundColor(BG);
    dialog.setContentView(panel);
    dialog.setOnDismissListener(
        d -> {
          closed[0] = true;
          generation[0]++;
        });
    dialog.show();
    if (dialog.getWindow() != null)
      dialog
          .getWindow()
          .setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .88f));
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
        "Smart experience",
        "MindRadio, memories, discovery, Time Machine & Home",
        false,
        () -> showSmart(null));
    setting(body, "Your profile", profile.name() + " · name and photo", false, this::openProfile);
    setting(
        body,
        "Refresh & repair",
        "Reload music and temporary data · saved music stays",
        false,
        this::refreshApp);
    setting(
        body,
        "Report a problem",
        "Review device diagnostics and email " + AppDiagnostics.EMAIL,
        false,
        this::reportProblem);
    setting(
        body,
        "Automatic recovery",
        getSharedPreferences("settings", 0).getBoolean("auto_repair", true)
            ? "On · refresh temporary data after an app crash"
            : "Off · use Refresh & repair manually",
        false,
        () -> {
          android.content.SharedPreferences prefs = getSharedPreferences("settings", 0);
          prefs.edit().putBoolean("auto_repair", !prefs.getBoolean("auto_repair", true)).apply();
          render();
        });
    setting(
        body,
        "Music region",
        RegionPolicy.name(RegionPolicy.current(this)) + " · tap to change",
        false,
        this::chooseRegion);
    setting(
        body,
        "Offline playback authorization",
        "Imported audio works offline. New online saves need provider authorization.",
        false,
        () -> {
          new AlertDialog.Builder(this)
              .setTitle("Authorized offline music")
              .setMessage(
                  "Your imported audio and existing saved music stay available. This source has no"
                      + " verified download entitlement, so new online saves are disabled. A"
                      + " licensed provider must authorize downloads before this can be enabled.")
              .setPositiveButton("Close", null)
              .show();
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
        "Installed " + BuildConfig.VERSION_NAME + " · tap to check",
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
                        + " library. Your name and optional profile photo stay on this phone. No"
                        + " account is required.\n\n"
                        + "Search suggestions, searches and online playback contact YouTube. Recent"
                        + " search terms stay on this phone and can be cleared in Settings. Update"
                        + " checks contact the built-in VEXO release service. Artwork is loaded"
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

  private void checkUpdates(boolean manual) {
    if (updateChecking) {
      if (manual) message("Checking for updates…");
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
            AppDiagnostics.record(this, "update_check", e);
            main.post(
                () -> {
                  updateChecking = false;
                  if (!destroyed && manual)
                    message(
                        "Couldn’t check for updates. Check your connection, then" + " try again.");
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
    mini.setVisibility(
        item == null || !profile.complete() || profileEditing ? View.GONE : View.VISIBLE);
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
                      : Track.from(item).online() ? "YOUTUBE AUDIO" : "ON THIS PHONE");
    }
  }

  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (destroyed) return;
          if (controller != null && miniProgress != null) {
            long duration = controller.getDuration();
            miniProgress.setProgress(
                duration > 0
                    ? (int) Math.min(1000, controller.getCurrentPosition() * 1000 / duration)
                    : 0);
          }
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
