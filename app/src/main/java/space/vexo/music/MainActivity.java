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
  private final ThreadPoolExecutor searchIo = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
  private Future<?> searchTask;
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
  private volatile int discoveryEpoch;
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
  private FrameLayout root, pageHost;
  private final Map<String, TabPage> tabPages = new LinkedHashMap<>();
  private String renderedTab = "";
  private boolean navigationBuild;
  private LinearLayout content, mini, nav, floatingDock;
  private final Map<View, Integer> dockPadding = new IdentityHashMap<>();
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
        invalidateTab("Library");
        invalidateTab("Settings");
        if (offlineSummary != null) offlineSummary.setText(offlineDescription());
        updatePlayback();
      };
  private EditText searchField;
  private SearchHistory searchHistory;
  private ArtistPortraits artistPortraits;
  interface ArtistSource {
    List<SearchArtists.Artist> search(String query, String country) throws Exception;
    ArtistSongs.Page songs(SearchArtists.Artist artist, String country, ArtistSongs.Page previous)
        throws Exception;
  }
  private ArtistSource artistSource = new ArtistSource() {
    public List<SearchArtists.Artist> search(String query, String country) throws Exception {
      return OnlineMusic.searchArtists(query, country);
    }
    public ArtistSongs.Page songs(SearchArtists.Artist artist, String country, ArtistSongs.Page previous)
        throws Exception { return ArtistSongs.load(artist, country, previous); }
  };
  private ExecutorService artistIo = Executors.newSingleThreadExecutor();
  private Future<?> artistTask;
  private int artistGeneration;
  private SearchArtists.Artist activeArtist;
  private ArtistSongs.Page artistPage;
  private List<Track> artistSongs = new ArrayList<>();
  private List<SearchArtists.Artist> artistResults = new ArrayList<>();
  private String artistsQuery = "", artistError = "";
  private boolean artistsSearched, artistsLoading;
  private ArtistsPanel artistsPanel;
  private ArtistPanel artistPanel;
  private Parcelable artistLayoutState;
  private static final class ArtistListing {
    final ArtistSongs.Page page;
    final List<Track> songs;
    final long at = System.currentTimeMillis();
    ArtistListing(ArtistSongs.Page page, List<Track> songs) {
      this.page = page; this.songs = new ArrayList<>(songs);
    }
  }
  private final Map<String, ArtistListing> artistCatalogues = new LinkedHashMap<>();
  private SearchSuggestions suggestions;
  private MusicPreview musicPreview;
  private MixCatalogue mixCatalogue;
  private MusicHomePanel homePanel;
  private MusicInterests personalInterests;
  private List<Track> personalSongs = new ArrayList<>();
  private Future<?> personalTask;
  private int personalGeneration;
  private String personalKey = "";
  private long personalAt;
  private boolean personalLoading, personalFailed;
  private org.json.JSONObject pendingAiPlan;
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
  private View searchTitleView, searchSources, searchLanguages, searchCategoryBack;

  private ScrollView suggestionScroll;
  private LinearLayout suggestionBody;
  private boolean suggestionsVisible;
  private UpdateChecker updates;
  private Dialog updateDialog;
  private boolean updateChecking;

  private Dialog playerDialog, queueDialog;
  private NotificationPermissionPrompt notificationPrompt;
  private AccountAuth account;
  private YouTubeConnection youtube;
  private boolean accountBusy;
  private android.os.CancellationSignal googleCancellation;
  private final ActivityResultLauncher<String> notificationPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            PushNotifications.permissionsChanged(this);
            if (granted) message("Notifications enabled");
          });
  private QueuePanel queuePanel;
  private NowPlayingPanel playerPanel;
  private List<Integer> queueViewIndices = new ArrayList<>();
  private TextView speedButton, radioStatus;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
      playbackStateChanged =
          (prefs, key) -> {
            if (!destroyed && "connection_status".equals(key)) updatePlayback();
            if (!destroyed && radioStatus != null)
              radioStatus.setText(
                  NowPlayingPanel.statusSummary(prefs.getString("autoplay_status", "")));
            if (!destroyed && queuePanel != null && queueDialog != null && queueDialog.isShowing())
              queuePanel.status(prefs.getString("autoplay_status", "Autoplay on"));
          };
  private ImageView playerArt;
  private TextView playerTitle, playerArtist, playerElapsed, playerDuration, playerState;
  private SeekBar seek;
  private Icon playerPlay, playerLike, playerShuffle, playerRepeat;
  private boolean dragging = false, audioAccess;
  private final ActivityResultLauncher<String> permission =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          granted -> {
            audioAccess = granted;
            scanPhone();
            if (!granted)
              message(
                  "Music access was not allowed. Tap Import device music to retry, or Choose"
                      + " files.");
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
    boolean lightMode = AppAppearance.light(this);
    Visuals.appearance(lightMode);
    setTheme(lightMode ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    super.onCreate(state);
    profile = new UserProfile(this);
    account = new AccountAuth(this);
    youtube = new YouTubeConnection(this, state, this::youtubeChanged, this::play);
    store = new LibraryStore(this);
    searchHistory = new SearchHistory(this);
    artistPortraits =
        new ArtistPortraits(
            this, main, Executors.newSingleThreadExecutor(), ArtistCatalogue::resolve);
    suggestions =
        new SearchSuggestions(main, Executors.newSingleThreadExecutor(), SearchSuggestions.ONLINE);
    musicPreview =
        new MusicPreview(
            main,
            Executors.newFixedThreadPool(2),
            (term, country) -> searchCategory == null
                ? OnlineMusic.userPreview(term, country)
                : OnlineMusic.preview(categoryQuery(term), country));
    mixCatalogue = new MixCatalogue(this);
    updates = new UpdateChecker(this);
    notificationPrompt =
        new NotificationPermissionPrompt(
            this,
            () ->
                profile.complete()
                    && !profileEditing
                    && (updateDialog == null || !updateDialog.isShowing()),
            () -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS));
    getSharedPreferences("playback_state", 0)
        .registerOnSharedPreferenceChangeListener(playbackStateChanged);
    homeFeed = RegionalFeed.cached(this, RegionPolicy.current(this));
    OfflineLibrary.get(this).addListener(offlineChanged);
    if (state != null) {
      tab = state.getString("tab", "Home");
      shelf = state.getString("shelf", "Phone");
      query = state.getString("query", "");
      searchCategory = MusicCategory.find(state.getString("category", ""));
      searchLanguage = state.getString("search_language", "Any");
      online = state.getBoolean("online", true);
      activeCollection = state.getString("collection", "");
      collectionTitle = state.getString("collection_title", "");
      activeMix = state.getString("mix", "");
      try { activeArtist = SavedArtists.decode(new org.json.JSONObject(state.getString("artist", "{}"))); }
      catch (org.json.JSONException ignored) {}
      artistsQuery = state.getString("artists_query", "");
      artistsSearched = state.getBoolean("artists_searched", false);
      artistLayoutState = state.getParcelable("artist_layout");
      homeFilter = state.getString("home_filter", "All");
      addingToPlaylist = state.getString("adding_playlist", "");
      profileEditing = state.getBoolean("profile_editing", false);
      profileDraft = state.getString("profile_draft", profile.name());
    }
    if ("space.vexo.music.OPEN_UPDATES".equals(getIntent().getAction())) tab = "Settings";
    root = new FrameLayout(this);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    root.setBackground(Glass.backdrop());
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    AppAppearance.bars(getWindow());
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
    pageHost = new FrameLayout(this);
    pageHost.setClipChildren(true);
    root.addView(pageHost, new FrameLayout.LayoutParams(-1, -1));
    pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    floatingDock = column();
    floatingDock.setClipChildren(false);
    FrameLayout.LayoutParams dockLayout = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    root.addView(floatingDock, dockLayout);
    floatingDock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateDockSpace());
    configurePage(content);
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
                    if (e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        || e.errorCode
                            == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                      message("Connection interrupted · VEXO will try to resume");
                    else showPlaybackError();
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
                if (activeArtist != null) {
                  closeArtist();
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
                  navigate("Home");
                } else {
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });
    audioAccess = DeviceMusicAccess.granted(this);
    scanPhone();
    loadHome(false);
    if (!activeMix.isEmpty()) loadMix(false);
    if (activeArtist != null) loadArtist(false);
    else if (tab.equals("Library") && shelf.equals("Artists") && artistsSearched)
      searchArtists(artistsQuery);
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
    if (AppAppearance.light(this) != Visuals.light) {
      recreate();
      return;
    }
    boolean granted = DeviceMusicAccess.granted(this);
    if (granted != audioAccess) {
      audioAccess = granted;
      scanPhone();
    }
    if (notificationPrompt != null) notificationPrompt.resume();
    PushNotifications.permissionsChanged(this);
    main.removeCallbacks(tick);
    main.post(tick);
    if (homePanel != null) {
      homePanel.personalize();
      loadPersonal(false);
      main.postDelayed(this::prefetchHomeMixes, 600);
    }
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
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus && notificationPrompt != null) notificationPrompt.schedule();
  }

  @Override
  protected void onPause() {
    if (notificationPrompt != null) notificationPrompt.pause();
    stopClock();
    main.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    if (youtube != null) youtube.saveState(b);
    if (searchField != null) query = searchField.getText().toString();
    b.putString("tab", tab);
    b.putString("shelf", shelf);
    b.putString("query", query);
    b.putString("category", searchCategory == null ? "" : searchCategory.id);
    b.putString("search_language", searchLanguage);
    b.putBoolean("online", online);
    b.putString("collection", activeCollection);
    b.putString("collection_title", collectionTitle);
    b.putString("mix", activeMix);
    b.putString("artist", activeArtist == null ? "{}" : SavedArtists.encode(activeArtist).toString());
    if (artistsPanel != null) artistsQuery = artistsPanel.search.getText().toString();
    b.putString("artists_query", artistsQuery);
    b.putBoolean("artists_searched", artistsSearched);
    if (artistPanel != null && activeArtist != null)
      artistLayoutState = artistPanel.getLayoutManager().onSaveInstanceState();
    b.putParcelable("artist_layout", artistLayoutState);
    b.putString("home_filter", homeFilter);
    b.putString("adding_playlist", addingToPlaylist);
    b.putBoolean("profile_editing", profileEditing);
    b.putString("profile_draft", profilePanel == null ? profileDraft : profilePanel.draft());
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onDestroy() {
    invalidatePersonal();
    if (youtube != null) youtube.close();
    if (artistPortraits != null) artistPortraits.close();
    cancelArtistWork();
    artistIo.shutdownNow();
    if (googleCancellation != null) googleCancellation.cancel();
    if (notificationPrompt != null) notificationPrompt.close();
    destroyed = true;
    if (pageHost != null)
      for (int i = 0; i < pageHost.getChildCount(); i++) pageHost.getChildAt(i).animate().cancel();
    tabPages.clear();
    stopClock();
    discoveryEpoch++;
    if (homeTask != null) homeTask.cancel(true);
    homeGeneration++;
    suggestions.close();
    musicPreview.close();
    mixIo.shutdownNow();
    mixGeneration++;
    if (updateDialog != null) updateDialog.dismiss();
    if (updateTransfer != null) updateTransfer.cancel();
    if (updateProgressDialog != null) updateProgressDialog.dismiss();
    OfflineLibrary.get(this).removeListener(offlineChanged);
    searchGeneration++;
    cancelSearchWork();
    searchIo.shutdownNow();
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
    t.setBackground(round(LIME, dp(24)));
    t.setPadding(dp(16), dp(13), dp(16), dp(13));
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
    if (!destroyed) VexoNotice.show(this, s);
  }

  private void buildNav() {
    nav = row();
    nav.setPadding(dp(6), dp(2), dp(6), dp(2));
    nav.setMinimumHeight(dp(64));
    // Only the pill has a surface; the surrounding overlay stays transparent.
    nav.setBackgroundColor(BG);
    nav.setElevation(0);
    LinearLayout.LayoutParams navLayout = new LinearLayout.LayoutParams(-1, -2);
    navLayout.setMargins(0, 0, 0, 0);
    floatingDock.addView(nav, navLayout);
  }

  // Keep the last row reachable while allowing the page to scroll behind the floating pill.
  private void updateDockSpace() {
    if (content == null || floatingDock == null || nav == null) return;
    int space = nav.getVisibility() == View.VISIBLE ? floatingDock.getHeight() : 0;
    View last = null;
    for (int i = 0; i < content.getChildCount(); i++) {
      View child = content.getChildAt(i);
      if (child.getVisibility() != View.GONE) last = child;
    }
    boolean scrolls = last instanceof ScrollView || last instanceof RecyclerView;
    int contentSpace = scrolls ? 0 : space;
    if (content.getPaddingBottom() != contentSpace) content.setPadding(0, 0, 0, contentSpace);
    if (scrolls) {
      int original = dockPadding.computeIfAbsent(last, View::getPaddingBottom);
      int bottom = original + space;
      if (last.getPaddingBottom() != bottom)
        last.setPadding(
            last.getPaddingLeft(), last.getPaddingTop(), last.getPaddingRight(), bottom);
      // RecyclerView requests layout even when setClipToPadding receives its current value.
      // This callback also runs after layout, so avoid scheduling another frame needlessly.
      if (((ViewGroup) last).getClipToPadding()) ((ViewGroup) last).setClipToPadding(false);
    }
  }

  static boolean showMini(MediaItem item, int state) {
    return item != null && (state == Player.STATE_READY || state == Player.STATE_BUFFERING);
  }

  private void refreshNav() {
    String[] labels = {"Home", "Search", "Library", "Settings", "Smart"};
    String[] names = {"home", "search", "library", "settings", "smart"};
    if (nav.getChildCount() == labels.length) {
      for (int j = 0; j < labels.length; j++) {
        LinearLayout item = (LinearLayout) nav.getChildAt(j);
        boolean selected = labels[j].equals(tab);
        Icon glyph = (Icon) item.getChildAt(0);
        glyph.set(names[j], selected ? TEXT : MUTED);
        glyph.setBackground(null);
        TextView caption = (TextView) item.getChildAt(1);
        caption.setTextColor(selected ? TEXT : MUTED);
        item.setSelected(selected);
      }
      return;
    }
    for (int j = 0; j < labels.length; j++) {
      String label = labels[j];
      boolean selected = label.equals(tab);
      LinearLayout item = column();
      item.setGravity(Gravity.CENTER);
      Icon i = new Icon(this, names[j], selected ? TEXT : MUTED);
      i.setGlyphSize(24);
      item.addView(i, new LinearLayout.LayoutParams(dp(48), dp(40)));

      i.setContentDescription(label);
      i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      TextView caption = text(label, 10, selected ? TEXT : MUTED, selected);
      caption.setGravity(Gravity.CENTER);
      caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      item.addView(caption);
      caption.setOnClickListener(v -> item.performClick());
      item.setMinimumHeight(dp(60));
      item.setSelected(selected);
      item.setFocusable(true);
      item.setTooltipText(label);
      item.setContentDescription(label.equals("Smart") ? "Smart Experience tab" : label + " tab");
      item.setOnClickListener(
          v -> {
            navigate(label);
          });
      nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
  }

  private void configurePage(LinearLayout page) {
    page.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          if (page == content) updateDockSpace();
        });
    page.setOnHierarchyChangeListener(
        new ViewGroup.OnHierarchyChangeListener() {
          public void onChildViewAdded(View parent, View child) {
            if (page == content) updateDockSpace();
          }

          public void onChildViewRemoved(View parent, View child) {
            dockPadding.remove(child);
          }
        });
  }

  private final class TabPage {
    final LinearLayout page = content;
    final MusicHomePanel home = homePanel;
    final EditText search = searchField;
    final TrackList adapter = listAdapter;
    final TextView state = status, offline = offlineSummary;
    final View searchTitle = searchTitleView,
        sources = searchSources,
        languages = searchLanguages,
        category = searchCategoryBack;
    final ScrollView scroll = suggestionScroll;
    final LinearLayout suggestions = suggestionBody;
    final boolean showingSuggestions = suggestionsVisible;

    void restore() {
      content = page;
      homePanel = home;
      searchField = search;
      listAdapter = adapter;
      status = state;
      offlineSummary = offline;
      searchTitleView = searchTitle;
      searchSources = sources;
      searchLanguages = languages;
      searchCategoryBack = category;
      suggestionScroll = scroll;
      suggestionBody = suggestions;
      suggestionsVisible = showingSuggestions;
      profilePanel = null;
    }
  }

  private void discardInactivePages() {
    if (pageHost == null) return;
    Iterator<Map.Entry<String, TabPage>> entries = tabPages.entrySet().iterator();
    while (entries.hasNext()) {
      TabPage page = entries.next().getValue();
      if (page.page != content) {
        page.page.animate().cancel();
        for (int i = 0; i < page.page.getChildCount(); i++)
          dockPadding.remove(page.page.getChildAt(i));
        pageHost.removeView(page.page);
        entries.remove();
      }
    }
  }

  private void invalidateTab(String name) {
    TabPage page = tabPages.get(name);
    if (page != null && page.page != content) {
      tabPages.remove(name);
      page.page.animate().cancel();
      for (int i = 0; i < page.page.getChildCount(); i++)
        dockPadding.remove(page.page.getChildAt(i));
      pageHost.removeView(page.page);
    }
  }

  private void navigate(String target) {
    if (destroyed || target.equals(tab) && !renderedTab.isEmpty()) return;
    String previous = tab;
    if (searchField != null) query = searchField.getText().toString();
    suggestions.cancel();
    musicPreview.cancel();
    if (tab.equals("Search")) {
      if (searchField != null) searchField.clearFocus();
    }
    if (artistsPanel != null) artistsQuery = artistsPanel.search.getText().toString();
    cancelArtistWork();
    activeArtist = null;
    boolean keep = !renderedTab.isEmpty() && !(tab.equals("Library") && shelf.equals("Artists"));
    if (keep) tabPages.put(renderedTab, new TabPage());
    content.animate().cancel();
    content.setTranslationX(0);
    content.setAlpha(1);
    content.setVisibility(View.GONE);
    if (!keep) pageHost.removeView(content);
    tab = target;
    activeCollection = "";
    activeMix = "";
    mixGeneration++;
    mixLoading = false;
    if (!target.equals("Search")) addingToPlaylist = "";
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    TabPage saved = tabPages.get(target);
    if (saved != null) {
      saved.restore();
      renderedTab = target;
      content.setVisibility(View.VISIBLE);
      refreshNav();
      updatePlayback();
      updateSearchChrome();
      updateDockSpace();
      if (target.equals("Search")
          && online
          && (suggestionsVisible || query.trim().isEmpty() && searchCategory == null))
        requestSuggestions();
      if (homePanel != null) {
        homePanel.personalize();
        loadPersonal(false);
      }
    } else {
      content = column();
      configurePage(content);
      pageHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
      navigationBuild = true;
      try {
        render();
      } finally {
        navigationBuild = false;
      }
    }
    PageMotion.enter(
        content,
        Arrays.asList("Home", "Search", "Library", "Settings", "Smart").indexOf(target)
                >= Arrays.asList("Home", "Search", "Library", "Settings", "Smart").indexOf(previous)
            ? 1
            : -1);
  }

  private void buildMini() {
    mini = column();
    mini.setBackground(round(MINI, dp(8)));
    mini.setPadding(dp(8), dp(3), dp(4), dp(3));
    mini.setMinimumHeight(dp(58));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(dp(8), dp(6), dp(8), dp(2));
    floatingDock.addView(mini, p);
    mini.setVisibility(View.GONE);
    LinearLayout line = row();
    mini.addView(line, new LinearLayout.LayoutParams(-1, -2));
    miniArt = new RoundedArtwork(this);
    miniArt.setBackground(round(PANEL, dp(5)));
    miniArt.setClipToOutline(true);
    line.addView(miniArt, new LinearLayout.LayoutParams(dp(40), dp(40)));
    LinearLayout copy = column();
    copy.setPadding(dp(10), 0, dp(4), 0);
    miniTitle = text("", 13, TEXT, true);
    miniTitle.setSingleLine();
    miniTitle.setEllipsize(TextUtils.TruncateAt.END);
    miniArtist = text("", 11, MUTED, false);
    miniArtist.setSingleLine();
    miniArtist.setEllipsize(TextUtils.TruncateAt.END);
    copy.addView(miniTitle);
    gap(copy, 4);
    copy.addView(miniArtist);
    line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    miniPlay = icon("play", TEXT, this::togglePlay);
    miniPlay.setGlyphSize(24);
    line.addView(miniPlay);
    miniProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    miniProgress.setMax(1000);
    miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
    miniProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
    miniProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams progress = new LinearLayout.LayoutParams(-1, dp(2));
    progress.setMargins(0, dp(2), 0, 0);
    mini.addView(miniProgress, progress);
    mini.setContentDescription("Open now playing");
    mini.setOnClickListener(v -> showPlayer());
  }

  private void render() {
    if (destroyed) return;
    if (notificationPrompt != null) notificationPrompt.schedule();
    if (profile.complete() && !profileEditing && activeArtist != null
        && artistPanel != null && artistPanel.getParent() == content) {
      renderArtist();
      updatePlayback();
      updateDockSpace();
      return;
    }
    if (profile.complete() && !profileEditing && activeArtist == null
        && tab.equals("Library") && shelf.equals("Artists") && artistsPanel != null
        && artistsPanel.getParent() == content) {
      artistsPanel.update(artistResults, artistsSearched, artistsLoading, artistError);
      updatePlayback(); updateDockSpace();
      return;
    }
    content.animate().cancel();
    content.setTranslationX(0);
    content.setAlpha(1);
    content.setVisibility(View.VISIBLE);
    if (!navigationBuild) {
      discardInactivePages();
      tabPages.clear();
    }
    renderedTab = "";
    suggestions.cancel();
    musicPreview.cancel();
    searchTitleView = null;
    searchSources = null;
    searchLanguages = null;
    searchCategoryBack = null;
    homePanel = null;
    artistPanel = null;
    artistsPanel = null;
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
    updatePlayback();
    if (activeArtist != null) {
      renderArtist();
      updateDockSpace();
      return;
    }
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
      case "Smart":
        showSmart(null, true);
        break;
      case "Settings":
        renderSettings();
        break;
      default:
        renderHome();
    }
    renderedTab = tab;
    updateDockSpace();
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
    else invalidateTab("Home");
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
                      else invalidateTab("Home");
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
                      else invalidateTab("Home");
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
              public void pure() {
                togglePureSong();
              }

              public void pureSettings() {
                showPureSong(null);
              }

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
                navigate("Search");
              }

              public void artist(SearchArtists.Artist artist) {
                openArtist(artist);
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

              public void mixPlay(MixCatalogue.Mix mix, List<Track> songs, int index) {
                MainActivity.this.play(songs, index, false, mix.id);
              }

              public void filter(String value) {
                homeFilter = value;
                render();
              }
            });
    homePanel = panel;
    try {
      if (!homeFilter.equals("Playlists")
          && !new SmartStore(this).preferences().optBoolean("focus")) {
        drawPersonal();
        main.post(() -> loadPersonal(false));
      }
      if (!homeFilter.equals("Playlists")
          && !new SmartStore(this).preferences().optBoolean("focus"))
        panel.youtube(new YouTubeTaste(this).read(), () -> youtube.show(), this::play);
    } catch (IllegalStateException unavailable) {
      // A disconnected or unavailable optional source must never block Home.
    }
    content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    artistPortraits.refresh(
        RegionPolicy.current(this),
        () -> {
          invalidateTab("Home");
          if (tab.equals("Home") && homePanel != null) homePanel.artists();
        });
    main.postDelayed(this::prefetchHomeMixes, 600);
  }

  private void youtubeChanged() {
    try {
      if (!new YouTubeTaste(this).read().connected) OnlineMusic.clearCache();
    } catch (IllegalStateException unavailable) {
      /* Existing player remains usable. */
    }
    invalidatePersonal();
    radioChanged();
    render();
  }

  private void invalidatePersonal() {
    personalGeneration++;
    if (personalTask != null) personalTask.cancel(true);
    personalTask = null;
    personalLoading = false;
    personalKey = "";
    personalAt = 0;
    personalSongs = new ArrayList<>();
    personalInterests = null;
  }

  private void drawPersonal() {
    if (homePanel == null || personalInterests == null) return;
    List<Track> visible = personalSongs;
    if (visible.isEmpty()) {
      List<Track> saved = new ArrayList<>(personalInterests.cached);
      saved.addAll(personalInterests.favourites);
      visible = personalInterests.variety(saved, 12);
    }
    final List<Track> songs = visible;
    homePanel.personal(
        personalInterests,
        songs,
        personalLoading,
        personalFailed,
        () -> loadPersonal(true),
        () -> showSmart(null),
        this::play,
        () -> {
          Track seed =
              controller != null && controller.getCurrentMediaItem() != null
                  ? Track.from(controller.getCurrentMediaItem())
                  : songs.isEmpty() ? null : songs.get(0);
          if (seed != null && RadioPolicy.music(seed)) startRadio(seed);
          else message("Play or save a song to start your radio.");
        });
  }

  private void loadPersonal(boolean force) {
    if (destroyed || !tab.equals("Home") || homePanel == null || personalLoading && !force) return;
    if (personalTask != null) personalTask.cancel(true);
    int request = ++personalGeneration;
    personalLoading = true;
    personalFailed = false;
    drawPersonal();
    personalTask =
        io.submit(
            () -> {
              try {
                MusicInterests interests = MusicInterests.read(this);
                if (interests.preferences.optBoolean("focus")) {
                  main.post(
                      () -> {
                        if (!destroyed && request == personalGeneration) personalLoading = false;
                      });
                  return;
                }
                boolean fresh =
                    !force
                        && personalKey.equals(interests.signature)
                        && System.currentTimeMillis() - personalAt < 30 * 60_000L;
                if (fresh) {
                  main.post(
                      () -> {
                        if (destroyed || request != personalGeneration) return;
                        personalInterests = interests;
                        personalLoading = false;
                        drawPersonal();
                      });
                  return;
                }
                main.post(
                    () -> {
                      if (destroyed || request != personalGeneration) return;
                      personalInterests = interests;
                      personalSongs = new ArrayList<>();
                      drawPersonal();
                    });
                List<Track> found =
                    PersonalDiscovery.load(
                        interests,
                        new PersonalDiscovery.Source() {
                          public List<Track> related(Track song) throws Exception {
                            return OnlineMusic.relatedPreview(
                                song, RegionPolicy.current(MainActivity.this));
                          }

                          public List<Track> search(String query) throws Exception {
                            return OnlineMusic.preview(
                                query, RegionPolicy.current(MainActivity.this));
                          }
                        });
                // Recheck the source on the worker, never on the drawing/input thread.
                boolean current = interests.signature.equals(MusicInterests.read(this).signature);
                main.post(
                    () -> {
                      if (destroyed || request != personalGeneration) return;
                      if (!current) {
                        invalidatePersonal();
                        loadPersonal(false);
                        return;
                      }
                      personalInterests = interests;
                      personalKey = interests.signature;
                      personalSongs = found;
                      personalLoading = false;
                      personalAt = System.currentTimeMillis();
                      personalFailed = found.isEmpty() && !interests.seeds.isEmpty();
                      drawPersonal();
                    });
              } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
              } catch (IllegalStateException unavailable) {
                main.post(
                    () -> {
                      if (!destroyed && request == personalGeneration) {
                        personalLoading = false;
                        personalFailed = true;
                        drawPersonal();
                      }
                    });
              }
            });
  }

  private void prefetchHomeMixes() {
    if (destroyed || activeArtist != null || !tab.equals("Home") || !activeMix.isEmpty() || !activeCollection.isEmpty())
      return;
    try {
      if (new SmartStore(this).preferences().optBoolean("focus")) return;
    } catch (IllegalStateException unavailable) {
      return;
    }
    String country = RegionPolicy.current(this);
    List<MixCatalogue.Mix> mixes = new ArrayList<>(MixCatalogue.discovery());
    mixes.add(MixCatalogue.trending());
    List<MixCatalogue.Mix> moods = MixCatalogue.mixes(country);
    mixes.addAll(moods.subList(0, Math.min(2, moods.size())));
    for (MixCatalogue.Mix mix : mixes) {
      if (mixIo.getQueue().size() > 8) break;
      scheduleMix(mix, false, false);
    }
  }

  private void scheduleMix(MixCatalogue.Mix mix, boolean force, boolean foreground) {
    final String country = RegionPolicy.current(this);
    final String scope = mixCatalogue.scope(mix, country, System.currentTimeMillis());
    final String key = country + ":" + mix.id + ":" + scope;
    if (!force && mixCatalogue.fresh(mix, country, System.currentTimeMillis())) return;
    if (prefetchingMixes.contains(key)) {
      if (foreground) {
        mixLoading = true;
        render();
      }
      return;
    }
    if (!foreground && attemptedMixes.contains(key)) return;
    if (attemptedMixes.size() > 128) attemptedMixes.clear();
    attemptedMixes.add(key);
    prefetchingMixes.add(key);
    final int epoch = discoveryEpoch;
    if (foreground) {
      mixLoading = true;
      mixError = "";
      render();
    }
    mixIo.execute(
        () -> {
          List<Track> songs = Collections.emptyList();
          boolean failed = false;
          try {
            MusicInterests interests = MusicInterests.read(this);
            songs =
                MixDiscovery.load(
                    mix,
                    interests,
                    new PersonalDiscovery.Source() {
                      public List<Track> related(Track track) throws Exception {
                        return OnlineMusic.relatedPreview(
                            track, RegionPolicy.current(MainActivity.this));
                      }

                      public List<Track> trending(String query) throws Exception {
                        return TrendingMusic.load(query, country);
                      }

                      public List<Track> search(String query) throws Exception {
                        return OnlineMusic.preview(query, country);
                      }
                    },
                    country,
                    System.currentTimeMillis(),
                    Collections.emptySet());
            failed = songs.isEmpty();
            if (!failed
                && epoch == discoveryEpoch
                && scope.equals(mixCatalogue.scope(mix, country, System.currentTimeMillis())))
              mixCatalogue.save(mix, country, songs, scope);
          } catch (Exception error) {
            failed = true;
            if (!Thread.currentThread().isInterrupted())
              AppDiagnostics.record(this, "mix_refresh", error);
          }
          final List<Track> found = songs;
          final boolean unavailable = failed;
          main.post(
              () -> {
                prefetchingMixes.remove(key);
                if (destroyed
                    || epoch != discoveryEpoch
                    || !country.equals(RegionPolicy.current(this))) return;
                if (!scope.equals(mixCatalogue.scope(mix, country, System.currentTimeMillis()))) {
                  if (activeMix.equals(mix.id)) {
                    mixLoading = false;
                    loadMix(false);
                  }
                  return;
                }
                if (homePanel != null && !unavailable) homePanel.updateMix(mix, found);
                else if (!unavailable) invalidateTab("Home");
                if (activeMix.equals(mix.id)) {
                  mixLoading = false;
                  mixError =
                      unavailable
                          ? (mix.id.equals("discover")
                              ? "No unseen songs found yet. Try different interests or refresh"
                                  + " later."
                              : "Couldn’t find fresh songs. Saved picks stay available; try"
                                  + " Refresh.")
                          : "";
                  render();
                }
              });
        });
  }

  private void tuneMix(MixCatalogue.Mix mix) {
    MixTuner.show(
        this,
        mix,
        io,
        () -> {
          if (destroyed) return;
          discardInactivePages();
          if (controller != null) {
            Bundle args = new Bundle();
            args.putString("mix_id", mix.id);
            controller.sendCustomCommand(
                new SessionCommand(PlaybackService.MIX_CHANGED, Bundle.EMPTY), args);
          }
          if (activeMix.equals(mix.id)) {
            mixLoading = false;
            loadMix(true);
          } else {
            render();
            prefetchHomeMixes();
          }
        });
  }

  private void chooseRegion() {
    List<String> codes = new ArrayList<>(Arrays.asList(Locale.getISOCountries()));
    codes.sort(Comparator.comparing(RegionPolicy::name));
    for (String first : new String[] {"GB", "US", "AU", "IN", "LK"}) {
      codes.remove(first);
      codes.add(0, first);
    }
    String[] names = new String[codes.size() + 2];
    names[0] = "Automatic · network / phone locale";
    names[1] = "Global · music from around the world";
    for (int i = 0; i < codes.size(); i++)
      names[i + 2] = RegionPolicy.name(codes.get(i)) + " (" + codes.get(i) + ")";
    new VexoDialogs.Builder(this)
        .setTitle("Music region")
        .setItems(
            names,
            (d, w) -> {
              getSharedPreferences("settings", 0)
                  .edit()
                  .putString(
                      "region", w == 0 ? "" : w == 1 ? RegionPolicy.GLOBAL : codes.get(w - 2))
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

  private MusicCategory searchCategory;
  private String searchLanguage = "Any";

  private String categoryQuery(String term) {
    if (isYouTubeLink(term)) return term;
    if (searchCategory != null) return searchCategory.query(term, searchLanguage);
    return searchLanguage.equals("Any") || isYouTubeLink(term)
        ? term
        : term + " " + searchLanguage + " music";
  }

  private void chooseSearchLanguage() {
    String[] values = {"Any", "Sinhala", "Tamil", "English"};
    String[] labels = {"All languages", "Sinhala", "Tamil", "English"};
    int selected = Arrays.asList(values).indexOf(searchLanguage);
    new VexoDialogs.Builder(this)
        .setTitle("Search language")
        .setSingleChoiceItems(
            labels,
            Math.max(0, selected),
            (dialog, which) -> {
              searchLanguage = values[which];
              searchGeneration++;
              suggestions.cancel();
              musicPreview.cancel();
              dialog.dismiss();
              if (searchCategory != null || !query.trim().isEmpty()) search();
              else render();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void browseSearch(LinearLayout parent) {
    try {
      MusicInterests interests = personalInterests;
      if (interests != null && !interests.seeds.isEmpty()) {
        parent.addView(text("Your artists", 17, TEXT, true));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout artists = row();
        for (Track seed : interests.seeds.subList(0, Math.min(4, interests.seeds.size()))) {
          TextView artist =
              button(
                  seed.artist,
                  () -> {
                    searchField.setText(seed.artist);
                    search();
                  });
          artist.setContentDescription("Search your artist " + seed.artist);
          LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
          layout.setMargins(0, dp(10), dp(8), dp(16));
          artists.addView(artist, layout);
        }
        scroll.addView(artists);
        parent.addView(scroll);
      }
    } catch (IllegalStateException unavailable) {
      /* Search still works without private taste. */
    }
    parent.addView(
        new SearchBrowsePanel(
            this,
            searchHistory.entries(),
            new SearchBrowsePanel.Listener() {
              public void category(MusicCategory category) {
                searchCategory = category;
                query = "";
                online = true;
                searchGeneration++;
                suggestions.cancel();
                musicPreview.cancel();
                search();
              }

              public void recent(String term) {
                searchField.setText(term);
                search();
              }

              public void remove(String term) {
                searchHistory.remove(term);
                invalidateTab("Home");
                requestSuggestions();
              }

              public void clear() {
                searchHistory.clear();
                invalidateTab("Home");
                requestSuggestions();
              }

              public void allRecent() {
                List<String> terms = searchHistory.entries();
                new VexoDialogs.Builder(MainActivity.this)
                    .setTitle("Recent searches")
                    .setItems(
                        terms.toArray(new String[0]), (dialog, which) -> recent(terms.get(which)))
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Clear", (dialog, which) -> clear())
                    .show();
              }
            }),
        new LinearLayout.LayoutParams(-1, -2));
  }

  private TextView searchSource(String title, boolean selected, Runnable action) {
    TextView control = text(title, 13, selected ? TEXT : MUTED, selected);
    control.setGravity(Gravity.CENTER);
    control.setMinHeight(dp(48));
    control.setPadding(dp(8), dp(8), dp(8), dp(8));
    control.setMaxLines(2);
    if (selected) control.setBackground(round(SELECTED, dp(10)));
    control.setSelected(selected);
    control.setOnClickListener(v -> action.run());
    return control;
  }

  private void renderSearch() {
    LinearLayout h = column();
    h.setPadding(dp(20), dp(8), dp(20), dp(8));
    LinearLayout titleRow = row();
    TextView title = text(searchCategory == null ? "Search" : searchCategory.name, 28, TEXT, true);
    title.setMaxLines(2);
    titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    if (online) {
      TextView language =
          text(
              (searchLanguage.equals("Any") ? "All languages" : searchLanguage) + " ▾",
              12,
              MUTED,
              true);
      language.setMinHeight(dp(48));
      language.setMaxWidth(dp(132));
      language.setMaxLines(2);
      language.setPadding(dp(12), dp(8), 0, dp(8));
      language.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
      language.setContentDescription("Choose search language. Current: " + searchLanguage);
      language.setOnClickListener(v -> chooseSearchLanguage());
      titleRow.addView(language);
      searchLanguages = language;
    }
    searchTitleView = title;
    h.addView(titleRow);
    gap(h, 8);
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
    box.setBackground(round(PANEL, dp(28)));
    box.setPadding(dp(8), 0, dp(4), 0);
    Icon find = icon("search", LIME, this::search);
    find.setGlyphSize(22);
    box.addView(find);
    searchField = new EditText(this);
    searchField.setTextColor(TEXT);
    searchField.setHintTextColor(MUTED);
    searchField.setTextSize(15);
    searchField.setSingleLine();
    searchField.setHint(
        searchCategory == null
            ? "What do you want to listen to?"
            : "Search in " + searchCategory.name);
    searchField.setBackgroundColor(Color.TRANSPARENT);
    searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    searchField.setText(query);
    searchField.setSelectAllOnFocus(false);
    searchField.setContentDescription(
        searchCategory == null ? "Search songs or artists" : "Search in " + searchCategory.name);
    searchField.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

          public void onTextChanged(CharSequence value, int start, int before, int count) {
            query = value.toString();
            searchGeneration++;
            cancelSearchWork();
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
    Icon clearQuery =
        icon(
            "close",
            MUTED,
            () -> {
              searchField.setText("");
              query = "";
            });
    clearQuery.setGlyphSize(20);
    clearQuery.setContentDescription("Clear search query");
    box.addView(clearQuery);
    h.addView(box);
    gap(h, 12);
    LinearLayout source = row();
    source.setPadding(dp(4), dp(4), dp(4), dp(4));
    source.setBackground(round(PANEL, dp(14)));
    source.addView(
        searchSource(
            "YouTube",
            online,
            () -> {
              online = true;
              render();
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    source.addView(
        searchSource(
            "On this phone",
            !online,
            () -> {
              online = false;
              searchCategory = null;
              searchGeneration++;
              searching = false;
              render();
            }),
        new LinearLayout.LayoutParams(0, -2, 1));
    h.addView(source, new LinearLayout.LayoutParams(-1, -2));
    searchSources = source;
    if (online && searchCategory != null) {
      searchCategoryBack = text("‹ All categories", 13, LIME, true);
      ((TextView) searchCategoryBack).setMinHeight(dp(48));
      ((TextView) searchCategoryBack).setGravity(Gravity.CENTER_VERTICAL);
      searchCategoryBack.setOnClickListener(
          v -> {
            searchCategory = null;
            query = "";
            results = new ArrayList<>();
            onlinePage = null;
            searching = false;
            searchGeneration++;
            render();
          });
      h.addView(searchCategoryBack);
    }
    gap(h, 6);
    status =
        text(
            searching
                ? "Searching YouTube…"
                : query.isEmpty() && searchCategory == null
                    ? "Find a song to get started."
                    : online
                        ? results.isEmpty()
                            ? "No songs found. Try another artist or title."
                            : results.size() + " results"
                        : "Your phone’s music",
            13,
            MUTED,
            false);
    h.addView(status);
    suggestionScroll = new ScrollView(this);
    suggestionBody = column();
    suggestionBody.setPadding(dp(20), dp(8), dp(20), dp(20));
    suggestionScroll.addView(suggestionBody);
    suggestionScroll.setVisibility(View.GONE);
    content.addView(suggestionScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (online) {
      if (searchCategory != null && !results.isEmpty()) {
        TextView playCategory =
            button("Play " + searchCategory.name + " mix", () -> play(new ArrayList<>(results), 0));
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(-1, -2);
        categoryParams.setMargins(dp(22), 0, dp(22), dp(8));
        content.addView(playCategory, categoryParams);
      }
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
            "Allow access to find the music on this phone, or choose files in your library.",
            "Import device music",
            this::requestScan);
      else addList(filtered, null);
    }
    updateSearchChrome();
    if (online && query.isEmpty() && searchCategory == null) requestSuggestions();
  }

  private void updateSearchChrome() {
    if (searchTitleView != null)
      searchTitleView.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
    if (searchSources != null) searchSources.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
    if (searchLanguages != null)
      searchLanguages.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
    if (searchCategoryBack != null)
      searchCategoryBack.setVisibility(keyboardOpen ? View.GONE : View.VISIBLE);
    if (tab.equals("Search") && status != null)
      status.setVisibility(
          keyboardOpen
                  || suggestionsVisible
                  || online && query.trim().isEmpty() && searchCategory == null
              ? View.GONE
              : View.VISIBLE);
  }

  private void requestSuggestions() {
    if (searchField == null || suggestionBody == null || !online || !tab.equals("Search")) return;
    final String value = SearchHistory.clean(searchField.getText().toString());
    boolean changed = !previewTerm.equals(value);
    previewTerm = value;
    previewTerms = new ArrayList<>();
    previewSongs = new ArrayList<>();
    previewLoading = RadioPolicy.normalize(value).length() >= 2 && !value.contains("://");
    previewFailed = false;
    phraseFailed = false;
    showSuggestions(value, previewTerms, false);
    if (changed && suggestionScroll != null) suggestionScroll.scrollTo(0, 0);
    if (value.isEmpty()) {
      suggestions.cancel();
      musicPreview.cancel();
      return;
    }
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
    if (searchCategory != null)
      return MusicPreview.combine(term, Collections.emptyList(), previewSongs, 6);
    List<Track> cached = new ArrayList<>(store.tracks("history"));
    cached.addAll(store.tracks("favourites"));
    try {
      cached.addAll(new YouTubeTaste(this).read().sources());
    } catch (IllegalStateException unavailable) {
      /* Keep existing search usable. */
    }
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
    updateSearchChrome();
    for (int i = 2; i < content.getChildCount(); i++)
      content.getChildAt(i).setVisibility(View.GONE);
    suggestionBody.removeAllViews();
    if (term.trim().isEmpty() && searchCategory == null) {
      browseSearch(suggestionBody);
      return;
    }
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
              play(matches, index, true);
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
            invalidateTab("Home");
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
                  invalidateTab("Home");
                  requestSuggestions();
                });
        remove.setGlyphSize(18);
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
      fill.setGlyphSize(20);
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
    if (query.isEmpty() && searchCategory == null) {
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
    cancelSearchWork();
    int generation = ++searchGeneration;
    String searchedTerm = query;
    String request = categoryQuery(query);
    boolean userSearch = searchCategory == null;
    String country = RegionPolicy.current(this);
    searching = true;
    results = new ArrayList<>();
    onlinePage = null;
    render();
    searchTask =
        searchIo.submit(
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
                        rememberArtists(searchedTerm, tracks);
                        searching = false;
                        if (tab.equals("Search")) render();
                        else invalidateTab("Search");
                      });
                } else {
                  OnlineMusic.Results found = userSearch ? OnlineMusic.userSearch(request, country) : OnlineMusic.search(request, country);
                  if (found.tracks.isEmpty()) {
                    for (String variant : SmartPolicy.variants(request)) {
                      if (variant.equals(request)) continue;
                      OnlineMusic.Results alternative = userSearch ? OnlineMusic.userSearch(variant, country) : OnlineMusic.search(variant, country);
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
                        rememberArtists(searchedTerm, results);
                        searching = false;
                        if (tab.equals("Search")) render();
                        else invalidateTab("Search");
                      });
                }
              } catch (Exception e) {
                main.post(
                    () -> {
                      if (destroyed || generation != searchGeneration) return;
                      searching = false;
                      invalidateTab("Search");
                      if (tab.equals("Search")) {
                        render();
                        if (status != null)
                          status.setText(
                              "Couldn’t reach YouTube. Check your connection and tap search to"
                                  + " retry.");
                      }
                    });
              }
            });
  }

  private void rememberArtists(String term, List<Track> songs) {
    new SearchArtists(this).record(term, songs);
    invalidateTab("Home");
    if (homePanel != null && tab.equals("Home")) render();
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

  private void cancelSearchWork() {
    if (searchTask != null) searchTask.cancel(true);
    searchTask = null;
    searchIo.purge();
  }

  private void loadMore() {
    if (searching || onlinePage == null) return;
    cancelSearchWork();
    int generation = ++searchGeneration;
    OnlineMusic.Results previous = onlinePage;
    searching = true;
    render();
    searchTask =
        searchIo.submit(
            () -> {
              try {
                OnlineMusic.Results next = OnlineMusic.more(previous);
                main.post(
                    () -> {
                      if (destroyed || generation != searchGeneration) return;
                      Set<String> seen = new HashSet<>();
                      for (Track song : results) seen.add(QueuePolicy.identity(song.url));
                      for (Track song : next.tracks)
                        if (seen.add(QueuePolicy.identity(song.url))) results.add(song);
                      onlinePage = next;
                      searching = false;
                      if (tab.equals("Search")) render();
                      else invalidateTab("Search");
                    });
              } catch (Exception e) {
                main.post(
                    () -> {
                      if (destroyed || generation != searchGeneration) return;
                      searching = false;
                      if (tab.equals("Search")) render();
                      else invalidateTab("Search");
                      message("Could not load more. Tap Load more to retry.");
                    });
              }
            });
  }

  private void cancelArtistWork() {
    artistGeneration++;
    if (artistTask != null) artistTask.cancel(true);
    artistsLoading = false;
  }

  private void hideArtistKeyboard() {
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
  }

  private void searchArtists(String term) {
    cancelArtistWork();
    artistsQuery = SearchHistory.clean(term);
    if (artistsPanel != null && !artistsQuery.contentEquals(artistsPanel.search.getText()))
      artistsPanel.search.setText(artistsQuery);
    artistsSearched = !artistsQuery.isEmpty();
    artistResults = new ArrayList<>();
    artistError = "";
    hideArtistKeyboard();
    if (!artistsSearched) { render(); return; }
    artistsLoading = true;
    int request = artistGeneration;
    String text = artistsQuery, country = RegionPolicy.current(this);
    render();
    artistTask = artistIo.submit(() -> {
      try {
        List<SearchArtists.Artist> found = artistSource.search(text, country);
        main.post(() -> {
          if (destroyed || request != artistGeneration) return;
          artistResults = found;
          artistsLoading = false;
          if (tab.equals("Library") && shelf.equals("Artists") && activeArtist == null) render();
        });
      } catch (Exception error) {
        main.post(() -> {
          if (destroyed || request != artistGeneration) return;
          artistsLoading = false;
          artistError = "Couldn’t load artists. Check your connection and try again.";
          if (tab.equals("Library") && shelf.equals("Artists") && activeArtist == null) render();
        });
      }
    });
  }

  private void openArtist(SearchArtists.Artist artist) {
    if (artistsPanel != null) artistsQuery = artistsPanel.search.getText().toString();
    cancelArtistWork();
    activeCollection = "";
    activeMix = "";
    activeArtist = artist;
    artistPage = null;
    artistLayoutState = null;
    artistSongs = new ArrayList<>();
    artistError = "";
    hideArtistKeyboard();
    ArtistListing cached = artistCatalogues.get(RegionPolicy.current(this) + ":" + artist.key());
    if (cached != null && System.currentTimeMillis() - cached.at < 15 * 60 * 1000L) {
      activeArtist = cached.page.artist;
      artistPage = cached.page;
      artistSongs = new ArrayList<>(cached.songs);
      render();
    } else loadArtist(false);
  }

  private void closeArtist() {
    cancelArtistWork();
    activeArtist = null;
    artistPage = null;
    artistSongs = new ArrayList<>();
    artistError = "";
    render();
  }

  private void loadArtist(boolean more) {
    if (activeArtist == null || artistsLoading || more && (artistPage == null || !artistPage.more())) return;
    cancelArtistWork();
    artistsLoading = true;
    artistError = "";
    int request = artistGeneration;
    SearchArtists.Artist wanted = activeArtist;
    ArtistSongs.Page previous = more ? artistPage : null;
    String country = RegionPolicy.current(this);
    render();
    artistTask = artistIo.submit(() -> {
      try {
        ArtistSongs.Page page = artistSource.songs(wanted, country, previous);
        main.post(() -> {
          if (destroyed || request != artistGeneration || activeArtist == null) return;
          activeArtist = page.artist;
          artistPage = page;
          LinkedHashMap<String, Track> songs = new LinkedHashMap<>();
          if (more) for (Track song : artistSongs) songs.put(song.url, song);
          for (Track song : page.songs) songs.putIfAbsent(song.url, song);
          artistSongs = new ArrayList<>(songs.values());
          String cacheKey = country + ":" + page.artist.key();
          if (artistCatalogues.size() >= 6 && !artistCatalogues.containsKey(cacheKey))
            artistCatalogues.remove(artistCatalogues.keySet().iterator().next());
          artistCatalogues.put(cacheKey, new ArtistListing(page, artistSongs));
          artistsLoading = false;
          render();
        });
      } catch (Exception error) {
        main.post(() -> {
          if (destroyed || request != artistGeneration || activeArtist == null) return;
          artistsLoading = false;
          artistError = wanted.id.isEmpty()
              ? "Find this artist in Artists search to choose their official profile."
              : "Couldn’t load the artist catalogue. Check your connection and try again.";
          render();
        });
      }
    });
  }

  private void renderArtist() {
    if (artistPanel != null && artistPanel.getParent() == content) {
      artistPanel.update(activeArtist, new ArrayList<>(artistSongs),
          new SavedArtists(this).contains(activeArtist), artistsLoading,
          artistPage != null && artistPage.more(), artistError);
      return;
    }
    ArtistPanel panel = new ArtistPanel(this, activeArtist, new ArrayList<>(artistSongs),
        new SavedArtists(this).contains(activeArtist), artistsLoading,
        artistPage != null && artistPage.more(), artistError, new ArtistPanel.Listener() {
          public void back() { closeArtist(); }
          public void follow() {
            SavedArtists saved = new SavedArtists(MainActivity.this);
            if (saved.contains(activeArtist)) saved.unfollow(activeArtist);
            else if (!saved.follow(activeArtist)) { message("You can follow up to 100 artists."); return; }
            invalidateTab("Home");
            invalidateTab("Library");
            render();
          }
          public void play(int index) {
            if (controller != null) controller.setShuffleModeEnabled(false);
            MainActivity.this.play(new ArrayList<>(artistSongs), index);
          }
          public void shuffle() {
            List<Track> shuffled = new ArrayList<>(artistSongs);
            Collections.shuffle(shuffled);
            if (controller != null) controller.setShuffleModeEnabled(false);
            MainActivity.this.play(shuffled, 0);
          }
          public void options(Track song) { songMenu(song, null); }
          public void more() { loadArtist(true); }
          public void retry() { loadArtist(artistPage != null && artistPage.more()); }
        });
    artistPanel = panel;
    content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    if (artistLayoutState != null) panel.getLayoutManager().onRestoreInstanceState(artistLayoutState);
  }

  private void renderLibrary() {
    LinearLayout h;
    if (shelf.equals("Artists")) {
      h = column();
      h.setPadding(dp(16), dp(8), dp(16), 0);
      content.addView(h);
    } else h = header("YOUR COLLECTION", "Your library");
    HorizontalScrollView sc = new HorizontalScrollView(this);
    sc.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = row();
    for (String s : new String[] {"Phone", "Offline", "Favourites", "Artists", "Playlists", "History"})
      chips.addView(
          chip(
              s,
              s.equals(shelf),
              () -> {
                cancelArtistWork();
                shelf = s;
                render();
              }));
    sc.addView(chips);
    h.addView(sc);
    gap(h, 12);
    if (shelf.equals("Artists")) {
      artistsPanel = new ArtistsPanel(this, artistsQuery, artistResults, artistsSearched,
          artistsLoading, artistError, new ArtistsPanel.Listener() {
            public void search(String term) { searchArtists(term); }
            public void open(SearchArtists.Artist artist) { openArtist(artist); }
            public void edit(String term) { artistsQuery = term; }
          });
      content.addView(artistsPanel, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    if (shelf.equals("Playlists")) {
      List<String> names = store.playlists();
      if (names.isEmpty()) {
        empty(
            "A home for every mood",
            "Create a playlist, then add songs from the song menu.",
            "Create a playlist",
            () -> createPlaylist(null));
        return;
      }
      h.addView(button("+  Create playlist", () -> createPlaylist(null)));
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
                    new VexoDialogs.Builder(this)
                        .setTitle(name)
                        .setItems(
                            new String[] {"Open playlist", "Delete playlist"},
                            (d, w) -> {
                              if (w == 0) showCollection(name, "playlist:" + name);
                              else
                                new VexoDialogs.Builder(this)
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
    if (shelf.equals("Phone") && !local.isEmpty()) {
      LinearLayout actions = row();
      actions.addView(
          button("Import device music", this::requestScan),
          new LinearLayout.LayoutParams(0, -2, 1));
      TextView imp = button("Choose files", this::importAudio);
      imp.setTextColor(TEXT);
      imp.setBackground(round(PANEL, dp(18)));
      LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1);
      ip.setMargins(dp(8), 0, 0, 0);
      actions.addView(imp, ip);
      h.addView(actions);
      gap(h, 10);
      h.addView(text(local.size() + " audio files · stays on your device", 12, MUTED, false));
      tracks = local;
    } else if (shelf.equals("Phone")) {
      tracks = local;
    } else if (shelf.equals("Offline")) {
      tracks = OfflineLibrary.get(this).saved();
      offlineSummary = text(offlineDescription(), 12, MUTED, false);
      offlineSummary.setLineSpacing(dp(4), 1);
      h.addView(offlineSummary);
      gap(h, 10);
    } else tracks = store.tracks(shelf.equals("Favourites") ? "favourites" : "history");
    if (tracks.isEmpty()) {
      empty(
          shelf.equals("Phone")
              ? "Bring your music along"
              : shelf.equals("Offline") ? "Offline songs appear here" : "Nothing here yet",
          shelf.equals("Phone")
              ? "Allow music and audio access to show songs on this device, or choose individual"
                  + " files."
              : shelf.equals("Offline")
                  ? "Play online music with Auto-save on. Complete saves will be available without"
                      + " internet."
                  : "Play a song or save a favourite to start your collection.",
          shelf.equals("Phone") ? "Import device music" : "Find music",
          () -> {
            if (shelf.equals("Phone")) requestScan();
            else {
              tab = "Search";
              render();
            }
          },
          shelf.equals("Phone") ? "Choose files" : null,
          this::importAudio);
    } else addList(tracks, shelf.equals("Favourites") ? "favourites" : null);
  }

  private void importAudio() {
    importer.launch(new String[] {"audio/*"});
  }

  private void requestScan() {
    DeviceMusicAccess.request(
        this,
        () -> permission.launch(DeviceMusicAccess.permission()),
        this::scanPhone,
        this::importAudio);
  }

  private void scanPhone() {
    int generation = ++scanGeneration;
    io.execute(
        () -> {
          LinkedHashMap<String, Track> found = new LinkedHashMap<>();
          for (Track t : store.tracks("imports")) found.put(t.url, t);
          String p = DeviceMusicAccess.permission();
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
                invalidateTab("Library");
                if (tab.equals("Library") && shelf.equals("Phone")
                    || tab.equals("Search") && !online) render();
              });
        });
  }

  private void empty(String title, String description, String action, Runnable click) {
    empty(title, description, action, click, null, null);
  }

  private void empty(
      String title, String description, String action, Runnable click,
      String secondary, Runnable secondaryClick) {
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
    if (secondary != null) {
      gap(e, 10);
      TextView other = button(secondary, secondaryClick);
      other.setBackground(round(CONTROL, dp(24)));
      other.setTextColor(TEXT);
      other.setPadding(dp(16), dp(13), dp(16), dp(13));
      e.addView(other);
    }
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
      artwork(h.art, t);
      h.itemView.setContentDescription("Play " + t.title + " by " + t.artist);
      h.itemView.setOnClickListener(
          v -> playResult(tracks, h.getBindingAdapterPosition(), collection));
      h.start.setContentDescription("Play " + t.title);
      h.start.setOnClickListener(
          v -> {
            int index = h.getBindingAdapterPosition();
            if (index < 0) index = pos;
            playResult(tracks, index, collection);
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

  private void playResult(List<Track> tracks, int index, String collection) {
    play(tracks, index, tab.equals("Search") && collection == null && online);
  }

  private void play(List<Track> tracks, int index) {
    play(tracks, index, false);
  }

  private void play(List<Track> tracks, int index, boolean songOnly) {
    play(tracks, index, songOnly, activeMix);
  }

  private void play(List<Track> tracks, int index, boolean songOnly, String discoveryMix) {
    if (controller == null) {
      message("Player is connecting. Try again in a moment.");
      return;
    }
    if (index < 0 || index >= tracks.size()) return;
    songOnly = songOnly && tracks.get(index).online();
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    if (tab.equals("Search") && online && searchField != null) {
      String term = searchField.getText().toString();
      searchHistory.add(term);
      rememberArtists(term, Collections.singletonList(tracks.get(index)));
    }
    if (getSharedPreferences("playback_state", 0).getBoolean("room_active", false)) {
      controller.sendCustomCommand(
          new SessionCommand(PlaybackService.ROOM_LEAVE, Bundle.EMPTY), Bundle.EMPTY);
      message("Leaving the listening room for your personal queue");
    }
    List<MediaItem> queue = new ArrayList<>();
    int first = Math.max(0, index - 10),
        end = Math.min(tracks.size(), first + QueuePolicy.MAX_ITEMS);
    for (int at = first; at < end; at++)
      queue.add(tracks.get(at).mediaItem(!discoveryMix.isEmpty() && at != index));
    Bundle queueArgs = new Bundle();
    queueArgs.putString("track", tracks.get(index).json().toString());
    queueArgs.putBoolean("play_song", songOnly);
    queueArgs.putString("discovery_mix", songOnly ? "" : discoveryMix);
    queueArgs.putString("discovery_country", RegionPolicy.current(this));
    queueArgs.putString(
        "session_query",
        !songOnly && tab.equals("Search") && searchCategory != null
            ? searchCategory.query("", searchLanguage)
            : "");
    queueArgs.putString(
        "ai_plan", songOnly || pendingAiPlan == null ? "" : pendingAiPlan.toString());
    pendingAiPlan = null;
    controller.sendCustomCommand(
        new SessionCommand(PlaybackService.NEW_QUEUE, Bundle.EMPTY), queueArgs);
    if (!songOnly) {
      controller.setMediaItems(queue, index - first, 0);
      controller.prepare();
      controller.play();
    }
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
    options.add("PureSong & intro markers");
    if (getSharedPreferences("playback_state", 0).getBoolean("room_active", false))
      options.add("Request in listening room");
    if (track.online()) {
      options.add("Start song radio");
      options.add("Don’t recommend in radio");
      options.add("Open in YouTube");
    }
    if (collection != null) options.add("Remove from this collection");
    if (OfflineLibrary.get(this).complete(track.url)) options.add("Remove offline audio");
    new VexoDialogs.Builder(this)
        .setTitle(track.title)
        .setItems(
            options.toArray(new String[0]),
            (d, which) -> {
              String action = options.get(which);
              if (action.equals("Request in listening room")) {
                if (!RadioPolicy.music(track)) {
                  message("Live rooms support online catalogue songs");
                  return;
                }
                try {
                  controller.sendCustomCommand(
                      new SessionCommand(PlaybackService.ROOM_ACTION, Bundle.EMPTY),
                      roomArgs(
                          new org.json.JSONObject()
                              .put("type", "add")
                              .put("track", track.json())
                              .toString()));
                } catch (Exception error) {
                  message("Couldn’t add the room request");
                }
                return;
              }
              if (action.equals("PureSong & intro markers")) {
                showPureSong(track);
                return;
              }
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
                    manualQueue(track, true, -1);
                  }
                  break;
                case 3:
                  if (controller != null && queueHasRoom()) {
                    manualQueue(track, false, -1);
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
    new VexoDialogs.Builder(this)
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
        new VexoDialogs.Builder(this)
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
    cancelArtistWork();
    activeArtist = null;
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
    cancelArtistWork();
    activeArtist = null;
    activeCollection = "";
    activeMix = mix.id;
    collectionTitle = mix.name;
    mixError = "";
    mixLoading = false;
    render();
    loadMix(false);
  }

  private void loadMix(boolean force) {
    if (activeMix.isEmpty() || mixLoading && !force) return;
    MixCatalogue.Mix mix = MixCatalogue.find(activeMix, RegionPolicy.current(this));
    if (mix != null) scheduleMix(mix, force, true);
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
    if (isMix && personalInterests != null) {
      songs = new ArrayList<>(songs);
      songs.removeIf(personalInterests::blocked);
    }
    List<Track> collection = new ArrayList<>(songs);
    boolean editable = activeCollection.startsWith("playlist:");
    PlaylistPanel panel =
        new PlaylistPanel(
            this,
            isMix ? mix.name : collectionTitle,
            isMix ? mixCatalogue.description(mix, country) : "Your music, together.",
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

              public void tune() {
                if (mix != null) tuneMix(mix);
              }
            });
    content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private void editPlaylist() {
    if (!activeCollection.startsWith("playlist:")) return;
    String name = collectionTitle;
    new VexoDialogs.Builder(this)
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
                new VexoDialogs.Builder(this)
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
                new VexoDialogs.Builder(this)
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
        new VexoDialogs.Builder(this)
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
    cancelArtistWork();
    activeArtist = null;
    artistCatalogues.clear();
    invalidatePersonal();
    if (artistPortraits != null) artistPortraits.retry();
    if (destroyed) return;
    discoveryEpoch++;
    DiscoveryRevision.changed(this);
    for (MixCatalogue.Mix mix : MixCatalogue.discovery()) mixCatalogue.tuned(mix);
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
    new VexoDialogs.Builder(this)
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
                new VexoDialogs.Builder(this)
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

  private void pureCommand(String action) {
    if (controller == null) return;
    Bundle args = new Bundle();
    args.putString("action", action);
    controller.sendCustomCommand(
        new SessionCommand(PlaybackService.PURE_ACTION, Bundle.EMPTY), args);
  }

  private void togglePureSong() {
    try {
      PureSong pure = new PureSong(new SmartStore(this));
      pure.enabled(!pure.enabled());
      pureCommand("changed");
      render();
      message(
          pure.enabled()
              ? "Intro skipping on · recognized chapters and saved music starts"
              : "Intro skipping off · full songs");
    } catch (IllegalStateException unavailable) {
      message(unavailable.getMessage());
    }
  }

  private void showPureSong(Track selected) {
    Dialog dialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    Track track =
        selected != null
            ? selected
            : controller == null || controller.getCurrentMediaItem() == null
                ? null
                : Track.from(controller.getCurrentMediaItem());
    PureSongPanel panel =
        new PureSongPanel(
            this,
            new SmartStore(this),
            track,
            new PureSongPanel.Listener() {
              public Track current() {
                return controller == null || controller.getCurrentMediaItem() == null
                    ? null
                    : Track.from(controller.getCurrentMediaItem());
              }

              public long position() {
                return controller == null ? 0 : controller.getCurrentPosition();
              }

              public long duration() {
                return controller == null ? 0 : controller.getDuration();
              }

              public boolean inRoom() {
                return getSharedPreferences("playback_state", 0).getBoolean("room_active", false);
              }

              public void changed() {
                pureCommand("changed");
                radioChanged();
                if (tab.equals("Home") || tab.equals("Settings")) render();
              }

              public void transport(String action) {
                pureCommand(action);
              }

              public void close() {
                dialog.dismiss();
              }
            });
    PanelWindow.content(dialog, panel);
    dialog.show();
    PanelWindow.expand(dialog);
  }

  private void showSmart(Track track) {
    showSmart(track, false);
  }

  private void showSmart(Track track, boolean embedded) {
    Dialog dialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    SmartStore smart = new SmartStore(this);
    final boolean[] closed = {false};
    final int[] generation = {0};
    SmartExperiencePanel panel =
        new SmartExperiencePanel(
            this,
            smart,
            store,
            new SmartExperiencePanel.Listener() {
              public void pure() {
                showPureSong(null);
              }

              private org.json.JSONObject selectedAiPlan;

              public void changed() {
                radioChanged();
                if (tab.equals("Home")) render();
              }

              public void play(List<Track> songs, int index, long moment) {
                dialog.dismiss();
                MainActivity.this.play(songs, index);
                if (controller != null && moment > 0) controller.seekTo(moment);
              }

              public void fair(List<Track> tracks) {
                if (controller == null) {
                  message("Player is connecting. Try again in a moment.");
                  return;
                }
                play(tracks, 0, 0);
                controller.sendCustomCommand(
                    new SessionCommand(PlaybackService.FAIR_QUEUE, Bundle.EMPTY), Bundle.EMPTY);
              }

              public void remote() {
                showRemoteRoom();
              }

              public void discover(String query, boolean newArtists) {
                selectedAiPlan = null;
                discoverPlan(Collections.singletonList(query), Collections.emptySet(), newArtists);
              }

              public void radioPlan(org.json.JSONObject plan) {
                selectedAiPlan = plan;
                List<String> queries = new ArrayList<>();
                org.json.JSONArray a = plan.optJSONArray("queries");
                if (a != null) for (int i = 0; i < a.length(); i++) queries.add(a.optString(i));
                Set<String> excluded = new HashSet<>();
                org.json.JSONArray x = plan.optJSONArray("excludeArtists");
                if (x != null)
                  for (int i = 0; i < x.length(); i++)
                    excluded.add(RadioPolicy.normalize(x.optString(i)));
                discoverPlan(queries, excluded, false);
              }

              private void discoverPlan(
                  List<String> queries, Set<String> excluded, boolean newArtists) {
                int token = ++generation[0];
                org.json.JSONObject chosenAiPlan = selectedAiPlan;
                message("Finding your music…");
                io.execute(
                    () -> {
                      try {
                        LinkedHashMap<String, Track> pool = new LinkedHashMap<>();
                        for (String query : queries)
                          for (String variant : SmartPolicy.variants(query)) {
                            if (closed[0] || destroyed || token != generation[0]) return;
                            for (Track t : OnlineMusic.search(variant).tracks)
                              pool.putIfAbsent(t.url, t);
                          }
                        List<Track> smartPicks =
                            SmartPolicy.rank(
                                new ArrayList<>(pool.values()),
                                smart,
                                store.tracks("history"),
                                newArtists);
                        List<Track> picks =
                            MusicInterests.read(MainActivity.this).rank(smartPicks, newArtists);
                        picks.removeIf(
                            t ->
                                excluded.stream()
                                    .anyMatch(
                                        artist ->
                                            !artist.isEmpty()
                                                && RadioPolicy.artist(t).contains(artist)));
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
                              new VexoDialogs.Builder(MainActivity.this)
                                  .setTitle(
                                      newArtists ? "Your discovery trail" : "Your MindRadio picks")
                                  .setItems(
                                      picks.stream()
                                          .map(t -> t.title + " · " + t.artist)
                                          .toArray(String[]::new),
                                      (d, which) -> {
                                        dialog.dismiss();
                                        pendingAiPlan = chosenAiPlan;
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
    panel.setBackground(Glass.backdrop());
    panel.addOnAttachStateChangeListener(
        new View.OnAttachStateChangeListener() {
          public void onViewAttachedToWindow(View v) {}

          public void onViewDetachedFromWindow(View v) {
            closed[0] = true;
            generation[0]++;
          }
        });
    if (embedded) {
      content.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
      return;
    }
    PanelWindow.content(dialog, panel);
    dialog.setOnDismissListener(
        d -> {
          closed[0] = true;
          generation[0]++;
        });
    dialog.show();
    PanelWindow.expand(dialog);
  }

  private Bundle roomArgs(String action) {
    Bundle b = new Bundle();
    b.putString("action", action);
    return b;
  }

  private void showRemoteRoom() {
    Dialog dialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    RemoteRoomPanel panel =
        new RemoteRoomPanel(
            this,
            store,
            new RemoteRoomPanel.Actions() {
              private void command(String name, Bundle args) {
                if (controller == null) {
                  message("Player is connecting. Try again.");
                  return;
                }
                controller.sendCustomCommand(new SessionCommand(name, Bundle.EMPTY), args);
              }

              public void connect() {
                command(PlaybackService.ROOM_JOIN, Bundle.EMPTY);
              }

              public void action(org.json.JSONObject value) {
                command(PlaybackService.ROOM_ACTION, roomArgs(value.toString()));
              }

              public void follow(boolean value) {
                Bundle b = new Bundle();
                b.putBoolean("follow", value);
                command(PlaybackService.ROOM_FOLLOW, b);
              }

              public void leave() {
                command(PlaybackService.ROOM_LEAVE, Bundle.EMPTY);
              }
            });
    PanelWindow.content(dialog, panel);
    dialog.show();
    PanelWindow.expand(dialog);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if ("space.vexo.music.OPEN_UPDATES".equals(intent.getAction())) {
      tab = "Settings";
      activeCollection = "";
      activeMix = "";
      render();
    }
  }

  interface AccountWork {
    String run() throws Exception;
  }

  private void accountAction(AccountPanel panel, Dialog dialog, String mode, AccountWork work) {
    if (accountBusy || destroyed) return;
    accountBusy = true;
    panel.busy(true);
    io.execute(
        () -> {
          String result;
          boolean success;
          try {
            result = work.run();
            success = true;
          } catch (Exception e) {
            result =
                e instanceof java.io.IOException && e.getMessage() != null
                    ? e.getMessage()
                    : "Couldn’t securely complete this request. Your library is safe.";
            success = false;
          }
          final String message = result;
          final boolean ok = success;
          main.post(
              () -> {
                accountBusy = false;
                if (destroyed) return;
                if (ok && mode.equals("signout")) {
                  GoogleAccount.clear(this);
                  youtube.disconnect();
                }
                if (!dialog.isShowing()) return;
                if (ok
                    && (mode.equals("signout")
                        || (!mode.equals("validate") && account.signedIn()))) {
                  dialog.dismiss();
                  if (tab.equals("Settings")) render();
                  if (!message.isEmpty()) message(message);
                  showAccount();
                } else panel.result(message, ok);
              });
        });
  }

  private void showAccount() {
    Dialog dialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    AccountPanel[] panels = new AccountPanel[1];
    panels[0] =
        new AccountPanel(
            this,
            account,
            new AccountPanel.Listener() {
              public void close() {
                dialog.dismiss();
              }

              public void submit(String mode, String email, String password) {
                accountAction(panels[0], dialog, mode, () -> account.submit(mode, email, password));
              }

              public void google() {
                if (accountBusy || destroyed) return;
                accountBusy = true;
                panels[0].busy(true);
                googleCancellation =
                    GoogleAccount.start(
                        MainActivity.this,
                        new GoogleAccount.Listener() {
                          public void failed(String error) {
                            accountBusy = false;
                            googleCancellation = null;
                            if (!destroyed && dialog.isShowing()) panels[0].result(error, false);
                          }

                          public void token(String token) {
                            accountBusy = false;
                            googleCancellation = null;
                            if (!destroyed && dialog.isShowing())
                              accountAction(
                                  panels[0], dialog, "google", () -> account.google(token));
                          }
                        });
              }

              public void signOut() {
                accountAction(
                    panels[0],
                    dialog,
                    "signout",
                    () -> {
                      account.signOut();
                      return "Signed out on this phone. Your music is still here.";
                    });
              }

              public void sync() {
                if (accountBusy) return;
                new VexoDialogs.Builder(MainActivity.this)
                    .setTitle("Sync your library?")
                    .setMessage(
                        "Merge online favourites and playlists with this account. Phone audio,"
                            + " photos, history and Smart memories stay on this phone. Deletions"
                            + " are not synced.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Sync now", (d, w) -> syncAccountLibrary(panels[0], dialog))
                    .show();
              }
            });
    panels[0].busy(accountBusy);
    PanelWindow.content(dialog, panels[0]);
    dialog.show();
    PanelWindow.expand(dialog);
    if (account.signedIn() && !accountBusy)
      accountAction(
          panels[0],
          dialog,
          "validate",
          () -> {
            account.validate();
            return "";
          });
  }

  private void syncAccountLibrary(AccountPanel panel, Dialog dialog) {
    if (accountBusy || destroyed) return;
    accountBusy = true;
    panel.busy(true);
    io.execute(
        () -> {
          try {
            AccountAuth.Credentials identity = account.credentials();
            org.json.JSONObject merged =
                new LibrarySync().sync(identity, LibrarySync.snapshot(store));
            main.post(
                () -> {
                  accountBusy = false;
                  if (destroyed) return;
                  try {
                    if (!account.stillCurrent(identity))
                      throw new java.io.IOException("Account changed. Sign in and sync again.");
                    LibrarySync.apply(store, merged);
                    if (dialog.isShowing())
                      panel.result("Library synced. Your existing local songs were kept.", true);
                  } catch (java.io.IOException e) {
                    if (dialog.isShowing()) panel.result(e.getMessage(), false);
                  }
                });
          } catch (Exception e) {
            String error =
                e instanceof java.io.IOException && e.getMessage() != null
                    ? e.getMessage()
                    : "Couldn’t sync safely. Your local library stays unchanged.";
            main.post(
                () -> {
                  accountBusy = false;
                  if (!destroyed && dialog.isShowing()) panel.result(error, false);
                });
          }
        });
  }

  private void showNotifications() {
    Dialog dialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    MusicSurface panel = new MusicSurface(this);
    panel.body.addView(panel.text("NOTIFICATIONS", 11, LIME, true));
    panel.gap(panel.body, 12);
    panel.body.addView(panel.text("Your alerts, your choice.", 26, TEXT, true));
    panel.gap(panel.body, 16);
    panel.body.addView(
        panel.text(
            AppNotifications.enabled(this)
                ? "Android notifications are enabled."
                : "Android notifications are off.",
            14,
            MUTED,
            false));
    panel.gap(panel.body, 16);
    panel.body.addView(
        panel.action(
            "Android notification settings",
            false,
            () -> {
              if (Build.VERSION.SDK_INT >= 33
                  && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                      != PackageManager.PERMISSION_GRANTED
                  && !getSharedPreferences("settings", 0)
                      .getBoolean("notifications_asked", false)) {
                getSharedPreferences("settings", 0)
                    .edit()
                    .putBoolean("notifications_asked", true)
                    .apply();
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
              } else {
                try {
                  startActivity(AppNotifications.settings(this));
                } catch (ActivityNotFoundException e) {
                  message("Open Android Settings → Apps → VEXO Music → Notifications.");
                }
              }
            }));
    boolean alerts = AppDefaults.updateAlerts(this);
    panel.gap(panel.body, 10);
    panel.body.addView(
        panel.action(
            alerts ? "Update alerts: ON" : "Update alerts: OFF",
            false,
            () -> {
              getSharedPreferences("settings", 0)
                  .edit()
                  .putBoolean("update_alerts", !alerts)
                  .apply();
              dialog.dismiss();
              showNotifications();
            }));
    boolean push = PushNotifications.optedIn(this);
    panel.gap(panel.body, 10);
    panel.body.addView(
        panel.action(
            push ? "Push release alerts: ON" : "Push release alerts: OFF",
            false,
            () -> {
              PushNotifications.enabled(this, !push);
              dialog.dismiss();
              showNotifications();
            }));
    panel.gap(panel.body, 8);
    TextView status = panel.text(PushNotifications.status(this), 12, MUTED, false);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    panel.body.addView(status);
    android.content.SharedPreferences prefs = getSharedPreferences("settings", 0);
    android.content.SharedPreferences.OnSharedPreferenceChangeListener changes =
        (p, key) -> {
          if ("push_status".equals(key)) status.setText(PushNotifications.status(this));
        };
    prefs.registerOnSharedPreferenceChangeListener(changes);
    dialog.setOnDismissListener(d -> prefs.unregisterOnSharedPreferenceChangeListener(changes));
    panel.gap(panel.body, 12);
    panel.body.addView(
        panel.action(
            "Send a test notification",
            false,
            () ->
                message(
                    AppNotifications.post(
                            this, 43, "VEXO is ready", "Your notification alerts are working.")
                        ? "Test notification sent"
                        : "Enable notifications in Android settings, including the App updates"
                            + " channel.")));
    panel.gap(panel.body, 12);
    panel.body.addView(panel.action("Close", false, dialog::dismiss));
    PanelWindow.content(dialog, panel);
    dialog.show();
    PanelWindow.expand(dialog);
  }

  private void chooseAppearance() {
    int selected =
        java.util.Arrays.asList(AppAppearance.VALUES).indexOf(AppAppearance.choice(this));
    new VexoDialogs.Builder(this)
        .setTitle("Appearance")
        .setSingleChoiceItems(
            AppAppearance.LABELS,
            selected,
            (dialog, which) -> {
              AppAppearance.save(this, which);
              dialog.dismiss();
              recreate();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void renderSettings() {
    header("MAKE IT YOURS", "Settings");
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = column();
    body.setPadding(dp(22), dp(8), dp(22), dp(24));
    scroll.addView(body);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    settingsGroup(body, "Your VEXO");
    setting(
        body,
        "Appearance",
        AppAppearance.label(this) + " · light and dark themes",
        false,
        this::chooseAppearance);
    setting(body, "Your profile", profile.name() + " · name and photo", false, this::openProfile);
    setting(
        body,
        "VEXO account",
        account.signedIn() ? account.email() : "Sign in or continue as a guest",
        false,
        this::showAccount);
    setting(
        body,
        "Notifications",
        "Permissions and optional update alerts",
        false,
        this::showNotifications);
    setting(
        body,
        "YouTube taste",
        "Optional · liked music and VEXO picks · manage connection",
        false,
        () -> youtube.show());
    settingsGroup(body, "Playback & discovery");
    setting(
        body,
        "Music region",
        RegionPolicy.name(RegionPolicy.current(this)) + " · tap to change",
        false,
        this::chooseRegion);
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
            new VexoDialogs.Builder(this)
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
        AppDefaults.crossfadeSeconds(this);
    setting(
        body,
        "Crossfade",
        crossfade == 0
            ? "Off · natural song transitions"
            : crossfade + (crossfade == 1 ? " second" : " seconds") + " · smoothly overlap songs",
        false,
        this::crossfadeSettings);
    setting(
        body,
        "PureSong & skip intros",
        new PureSong(new SmartStore(this)).enabled()
            ? "On · recognized chapters and saved starts"
            : "Off · play full songs",
        false,
        () -> showPureSong(null));
    setting(body, "Playback speed", "Choose 0.75× to 2×", false, this::playbackSpeed);
    setting(body, "Sleep timer", "Pause playback after a time you choose", false, this::sleepTimer);
    settingsGroup(body, "Library & data");
    setting(
        body,
        "Auto-save offline music",
        "Save songs while listening · up to 500 MB · uses internet data",
        true,
        OfflineLibrary.get(this).enabled(),
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
            new VexoDialogs.Builder(this)
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
            new VexoDialogs.Builder(this)
                .setTitle("Clear listening history?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Clear",
                    (d, w) -> {
                      new SmartStore(this).clearHistory();
                      store.save("history", Collections.emptyList());
                      radioChanged();
                      message("History cleared");
                    })
                .show());
    setting(
        body,
        "Clear recent searches",
        "Remove search terms and artist shortcuts saved on this phone",
        false,
        () -> {
          searchHistory.clear();
          invalidateTab("Home");
          message("Recent searches cleared");
        });
    settingsGroup(body, "App & support");
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
        "About VEXO Music",
        "Version " + BuildConfig.VERSION_NAME + " · Kavindu Kavishka",
        false,
        () ->
            new VexoDialogs.Builder(this)
                .setTitle("VEXO Music " + BuildConfig.VERSION_NAME)
                .setMessage(
                    "Developer: Kavindu Kavishka\n\n"
                        + "An independent music player with YouTube search and a private phone"
                        + " library. Your name and optional profile photo stay on this phone. No"
                        + " account is required.\n\n"
                        + "Search suggestions, searches and online playback contact YouTube. Recent"
                        + " search terms and artist shortcuts stay on this phone and can be cleared"
                        + " in Settings. Update checks contact the built-in VEXO release service."
                        + " Opening lyrics sends the song title, artist and duration to LRCLIB."
                        + " Artwork is loaded from the source shown by YouTube. Favourites,"
                        + " playlists and listening history are stored on this device. No analytics"
                        + " server is used.\n\n"
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
            new VexoDialogs.Builder(this)
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
        new VexoDialogs.Builder(this)
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
                  AppNotifications.update(this, release);
                  if (!manual && updates.snoozed(release)) return;
                  if (updateDialog != null && updateDialog.isShowing()) return;
                  updateDialog =
                      new VexoDialogs.Builder(this)
                          .setTitle("VEXO Music " + release.version + " is available")
                          .setMessage(
                              release.summary + "\n\nInstalled: " + BuildConfig.VERSION_NAME)
                          .setNegativeButton("Later", (d, w) -> updates.snooze(release))
                          .setPositiveButton(
                              "Get update", (d, w) -> downloadVerifiedUpdate(release))
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

  private boolean updateDownloading;
  private UpdateInstaller.Transfer updateTransfer;
  private Dialog updateProgressDialog;

  private void closeUpdateProgress() {
    if (updateProgressDialog != null) updateProgressDialog.dismiss();
    updateProgressDialog = null;
    updateTransfer = null;
  }

  private void downloadVerifiedUpdate(AppRelease release) {
    if (updateDownloading) {
      message("An update download is already running");
      return;
    }
    if (!release.authenticated) {
      message("This update could not be authenticated");
      return;
    }
    updateDownloading = true;
    Dialog progressDialog =
        new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    UpdateProgressPanel panel =
        new UpdateProgressPanel(
            this,
            release.version,
            () -> {
              if (updateTransfer != null) updateTransfer.cancel();
              progressDialog.dismiss();
              message("Update download cancelled");
            });
    UpdateInstaller.Transfer transfer =
        new UpdateInstaller.Transfer(
            (downloaded, total, verifying) ->
                main.post(
                    () -> {
                      if (!destroyed
                          && updateTransfer != null
                          && !updateTransfer.cancelled
                          && progressDialog.isShowing())
                        panel.progress(downloaded, total, verifying);
                    }));
    updateTransfer = transfer;
    updateProgressDialog = progressDialog;
    progressDialog.setCancelable(false);
    progressDialog.setCanceledOnTouchOutside(false);
    PanelWindow.content(progressDialog, panel);
    progressDialog.show();
    PanelWindow.expand(progressDialog);
    io.execute(
        () -> {
          try {
            java.io.File file = UpdateInstaller.prepare(this, release, transfer);
            main.post(
                () -> {
                  updateDownloading = false;
                  closeUpdateProgress();
                  if (destroyed || isFinishing() || transfer.cancelled) return;
                  new VexoDialogs.Builder(this)
                      .setTitle("Verified update ready")
                      .setMessage(
                          "VEXO Music "
                              + release.version
                              + " passed download and signing checks. Android will ask you to"
                              + " approve installation.")
                      .setNegativeButton("Later", null)
                      .setPositiveButton(
                          "Install update",
                          (d, w) -> {
                            try {
                              if (Build.VERSION.SDK_INT >= 26
                                  && !getPackageManager().canRequestPackageInstalls()) {
                                new VexoDialogs.Builder(this)
                                    .setTitle("Allow VEXO updates")
                                    .setMessage(
                                        "Enable Allow from this source for VEXO, then return to"
                                            + " Settings → Check for updates. The verified download"
                                            + " will be reused.")
                                    .setNegativeButton("Later", null)
                                    .setPositiveButton(
                                        "Open Android settings",
                                        (p, q) ->
                                            startActivity(
                                                new Intent(
                                                    android.provider.Settings
                                                        .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                    Uri.parse("package:" + getPackageName()))))
                                    .show();
                              } else installVerifiedUpdate(file, release);
                            } catch (Exception e) {
                              message("Couldn’t open the verified update. Please try again.");
                            }
                          })
                      .show();
                });
          } catch (Exception error) {
            if (!transfer.cancelled) AppDiagnostics.record(this, "verified_update", error);
            main.post(
                () -> {
                  updateDownloading = false;
                  closeUpdateProgress();
                  if (destroyed || isFinishing() || transfer.cancelled) return;
                  new VexoDialogs.Builder(this)
                      .setTitle("Update couldn’t finish")
                      .setMessage(
                          "Check your connection and try again. Your existing app, music and"
                              + " settings are safe.")
                      .setNegativeButton("Later", null)
                      .setPositiveButton("Retry", (d, w) -> downloadVerifiedUpdate(release))
                      .show();
                });
          }
        });
  }

  private void installVerifiedUpdate(java.io.File file, AppRelease release) {
    message("Rechecking the verified update…");
    io.execute(
        () -> {
          try {
            Intent intent = UpdateInstaller.installIntent(this, file, release);
            main.post(
                () -> {
                  if (destroyed || isFinishing()) return;
                  try {
                    startActivity(intent);
                  } catch (ActivityNotFoundException unavailable) {
                    message("Android’s package installer is unavailable.");
                  }
                });
          } catch (Exception error) {
            main.post(
                () -> {
                  if (!destroyed)
                    message("Update verification failed. Check for updates and try again.");
                });
          }
        });
  }

  private void setting(
      LinearLayout parent, String title, String detail, boolean toggle, Runnable action) {
    setting(
        parent,
        title,
        detail,
        toggle,
        getSharedPreferences("settings", 0).getBoolean("data_saver", false),
        action);
  }

  private void setting(
      LinearLayout parent,
      String title,
      String detail,
      boolean toggle,
      boolean on,
      Runnable action) {
    LinearLayout r = row();
    r.setBackground(round(PANEL, dp(12)));
    r.setPadding(dp(16), dp(14), dp(8), dp(14));
    r.setMinimumHeight(dp(72));
    LinearLayout labels = column();
    labels.addView(text(title, 15, TEXT, true));
    gap(labels, 5);
    TextView description = text(detail, 12, MUTED, false);
    description.setLineSpacing(dp(3), 1);
    labels.addView(description);
    r.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (toggle) {
      TextView value = text(on ? "ON" : "OFF", 12, on ? LIME : MUTED, true);
      value.setPadding(dp(12), 0, dp(8), 0);
      r.addView(value);
    } else {
      Icon forward = new Icon(this, "chevron", MUTED);
      forward.setGlyphSize(20);
      forward.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      r.addView(forward, new LinearLayout.LayoutParams(dp(32), dp(40)));
    }
    r.setOnClickListener(v -> action.run());
    parent.addView(r);
    gap(parent, 6);
  }

  private void settingsGroup(LinearLayout parent, String title) {
    if (parent.getChildCount() > 0) gap(parent, 18);
    TextView label = text(title, 13, MUTED, true);
    label.setPadding(dp(4), dp(8), dp(4), dp(12));
    parent.addView(label);
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
        showMini(item, controller.getPlaybackState()) && profile.complete() && !profileEditing
            ? View.VISIBLE
            : View.GONE);
    updateDockSpace();
    if (item == null) {
      if (playerDialog != null) playerDialog.dismiss();
      return;
    }
    miniTitle.setText(item.mediaMetadata.title);
    miniArtist.setText(
        controller.getPlaybackState() == Player.STATE_BUFFERING
            ? "Loading audio…"
            : item.mediaMetadata.artist);
    miniPlay.set(controller.isPlaying() ? "pause" : "play", TEXT);
    String art =
        item.mediaMetadata.artworkUri == null ? "" : item.mediaMetadata.artworkUri.toString();
    artwork(miniArt, Track.from(item));
    if (playerDialog != null && playerDialog.isShowing()) {
      if (speedButton != null)
        speedButton.setText(speedLabel(controller.getPlaybackParameters().speed));
      playerTitle.setText(item.mediaMetadata.title);
      playerArtist.setText(item.mediaMetadata.artist);
      artwork(playerArt, Track.from(item));
      playerPlay.set(controller.isPlaying() ? "pause" : "play", BG);
      playerLike.set(store.contains("favourites", Track.from(item)) ? "liked" : "heart", LIME);
      playerShuffle.set("shuffle", controller.getShuffleModeEnabled() ? LIME : MUTED);
      playerRepeat.set(
          controller.getRepeatMode() == Player.REPEAT_MODE_ONE ? "repeat_one" : "repeat",
          controller.getRepeatMode() == Player.REPEAT_MODE_OFF ? MUTED : LIME);
      String connection =
          getSharedPreferences("playback_state", 0).getString("connection_status", "");
      playerState.setText(
          controller.getPlayerError() != null
              ? connection.isEmpty()
                  ? "Audio couldn’t load · Tap Retry or try another song"
                  : connection
              : controller.getPlaybackState() == Player.STATE_BUFFERING
                  ? "Loading audio…"
                  : OfflineLibrary.get(this).complete(item.mediaId)
                      ? "SAVED OFFLINE"
                      : Track.from(item).online() ? "" : "On this phone");
      playerState.setVisibility(playerState.getText().length() == 0 ? View.GONE : View.VISIBLE);
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

  private void showLyrics() {
    if (controller == null || controller.getCurrentMediaItem() == null) return;
    Dialog dialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LyricsPanel panel =
        new LyricsPanel(
            this,
            main,
            io,
            new LyricsPanel.Playback() {
              public Track track() {
                return controller == null || controller.getCurrentMediaItem() == null
                    ? null
                    : Track.from(controller.getCurrentMediaItem());
              }

              public long position() {
                return controller == null ? 0 : controller.getCurrentPosition();
              }

              public long duration() {
                return controller == null ? 0 : controller.getDuration();
              }

              public void seek(long position) {
                if (controller != null && controller.isCurrentMediaItemSeekable())
                  controller.seekTo(position);
              }

              public void close() {
                dialog.dismiss();
              }
            });
    PanelWindow.content(dialog, panel);
    // PanelWindow supplies safe-area handling; restore the lyrics artwork-inspired gradient.
    panel.setBackground(
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            light
                ? new int[] {0xffe4f2ea, 0xfff5f5f0, 0xffe2ece7}
                : new int[] {0xff21372f, 0xff302c38, 0xff111615}));
    dialog.setOnDismissListener(d -> panel.stop());
    dialog.show();
    PanelWindow.expand(dialog);
  }

  private void showPlayer() {
    if (controller == null || controller.getCurrentMediaItem() == null) return;
    if (playerDialog != null && playerDialog.isShowing()) return;
    playerDialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
    NowPlayingPanel p =
        new NowPlayingPanel(
            this,
            new NowPlayingPanel.Actions() {
              public void action(String name) {
                switch (name) {
                  case "down":
                    playerDialog.dismiss();
                    break;
                  case "lyrics":
                    showLyrics();
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
                    if (getSharedPreferences("playback_state", 0).getBoolean("room_active", false)
                        && getSharedPreferences("playback_state", 0).getBoolean("room_host", false))
                      controller.sendCustomCommand(
                          new SessionCommand(PlaybackService.ROOM_ACTION, Bundle.EMPTY),
                          roomArgs("{\"type\":\"next\"}"));
                    else controller.seekToNextMediaItem();
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
    radioStatus = p.radioStatus;
    playerLike = p.favourite;
    playerShuffle = p.shuffle;
    playerPlay = p.play;
    playerRepeat = p.repeat;
    seek = p.seek;
    radioStatus.setText(
        NowPlayingPanel.statusSummary(
            getSharedPreferences("playback_state", 0).getString("autoplay_status", "")));
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
      AppAppearance.bars(window);
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
    discardInactivePages();
    invalidatePersonal();
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
    new VexoDialogs.Builder(this)
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
  }

  private void manualQueue(Track track, boolean next, int existing) {
    if (controller == null) return;
    Bundle args = new Bundle();
    if (existing >= 0) {
      args.putInt("index", existing);
      args.putString("expected", track.url);
    } else {
      args.putString("track", track.json().toString());
      args.putBoolean("next", next);
    }
    ListenableFuture<SessionResult> result =
        controller.sendCustomCommand(
            new SessionCommand(PlaybackService.MANUAL_QUEUE, Bundle.EMPTY), args);
    result.addListener(
        () -> {
          if (destroyed) return;
          try {
            message(
                result.get().resultCode == SessionResult.RESULT_SUCCESS
                    ? next ? "Added to play next" : "Added before automatic recommendations"
                    : "The queue changed or is full. Try again.");
          } catch (Exception error) {
            message("Couldn’t change the queue. Try again.");
          }
          refreshQueue();
        },
        main::post);
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
            this, AppDefaults.crossfadeSeconds(this));
    new VexoDialogs.Builder(this)
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
    new VexoDialogs.Builder(this)
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
    new VexoDialogs.Builder(this)
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
                  manualQueue(track, true, index);
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
    queueDialog = new Dialog(this, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
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
      AppAppearance.bars(w);
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
    new VexoDialogs.Builder(this)
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
    new VexoDialogs.Builder(this)
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
