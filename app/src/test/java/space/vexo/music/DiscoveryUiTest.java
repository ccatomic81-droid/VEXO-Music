package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DiscoveryUiTest {
  final Track saved =
      new Track("https://www.youtube.com/watch?v=fixture-saved", "Sky lights", "Nova Sky", "", 205);
  final Track remote =
      new Track(
          "https://www.youtube.com/watch?v=fixture-remote",
          "Skyline after dark",
          "Nova Sky",
          "",
          192);
  final SearchUiTest helper = new SearchUiTest();

  @Before
  public void seed() {
    Application app = RuntimeEnvironment.getApplication();
    new UpdateChecker(app).automatic(false);
    new UserProfile(app).saveName("Listener");
    new UpdateChecker(app).seen(BuildConfig.VERSION_CODE);
    app.getSharedPreferences("settings", 0).edit().putString("region", "LK").commit();
    Shadows.shadowOf(app)
        .declareComponentUnbindable(new android.content.ComponentName(app, PlaybackService.class));
    LibraryStore store = new LibraryStore(app);
    store.recent(saved);
    store.toggle("favourites", saved);
    store.createPlaylist("My evening");
    store.addToPlaylist("My evening", saved);
    new RegionalFeed(
            "LK",
            "Regional YouTube search · not a ranked chart",
            false,
            System.currentTimeMillis(),
            Arrays.asList(saved, remote))
        .persist(app);
    MixCatalogue catalogue = new MixCatalogue(app);
    for (MixCatalogue.Mix mix : MixCatalogue.mixes("LK"))
      catalogue.save(mix, "LK", Arrays.asList(saved, remote));
  }

  void fakeSources(MainActivity activity, SearchSuggestionsTest.Work songsWork) throws Exception {
    Field phrases = MainActivity.class.getDeclaredField("suggestions");
    phrases.setAccessible(true);
    ((SearchSuggestions) phrases.get(activity)).close();
    phrases.set(
        activity,
        new SearchSuggestions(
            new Handler(Looper.getMainLooper()),
            new SearchSuggestionsTest.Work(),
            (q, c) -> Collections.emptyList()));
    Field songs = MainActivity.class.getDeclaredField("musicPreview");
    songs.setAccessible(true);
    ((MusicPreview) songs.get(activity)).close();
    songs.set(
        activity,
        new MusicPreview(
            new Handler(Looper.getMainLooper()), songsWork, (q, c) -> Arrays.asList(remote)));
  }

  View find(View v, String description) {
    return helper.description(v, description);
  }

  TextView text(View v, String text) {
    return helper.text(v, text);
  }

  void capture(View root, int width, int height, String name) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, width, height);
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(b));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
    View nav = find(root, "Settings tab");
    assertNotNull(nav);
    int[] at = new int[2];
    nav.getLocationOnScreen(at);
    assertTrue(at[1] + nav.getHeight() <= height);
  }

  @Test
  public void homeMixSavingAndPlaylistAddFlowPreserveActualSongs() throws Exception {
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = a.get();
      SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
      fakeSources(activity, work);
      View root = activity.findViewById(android.R.id.content);
      assertNotNull(text(root, "Mixes for your mood"));
      assertNotNull(find(root, "Open My evening"));
      capture(root, 360, 800, "home-discovery");
      find(root, "Open Sinhala favourites").performClick();
      assertNotNull(text(root, "2 songs · Music from YouTube"));
      assertNotNull(find(root, "Play Skyline after dark"));
      capture(root, 360, 800, "regional-mix-playlist");
      text(root, "Save to your playlists").performClick();
      assertEquals(2, new LibraryStore(activity).tracks("playlist:Sinhala favourites").size());
      text(root, "Add songs").performClick();
      EditText input = (EditText) find(root, "Search songs or artists");
      input.setText("sky");
      assertNotNull(find(root, "Play Sky lights"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      work.runNext();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertEquals("sky", input.getText().toString());
      assertNotNull(find(root, "Play Skyline after dark"));
      find(root, "Add Skyline after dark to Sinhala favourites").performClick();
      assertEquals(2, new LibraryStore(activity).tracks("playlist:Sinhala favourites").size());
      text(root, "Done · Sinhala favourites").performClick();
      assertNotNull(text(root, "2 songs · On this device"));
      find(root, "Back from playlist").performClick();
      assertNotNull(text(root, "Your library"));
    }
  }

  @Test
  public void keyboardKeepsPlayableNamesVisibleAndOldResultsDoNotRebuildTheField()
      throws Exception {
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = a.get();
      SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
      fakeSources(activity, work);
      View root = activity.findViewById(android.R.id.content);
      find(root, "Search tab").performClick();
      EditText input = (EditText) find(root, "Search songs or artists");
      input.setText("sky");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      work.runNext();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      capture(root, 320, 568, "search-real-song-matches");
      View appRoot = ((ViewGroup) root).getChildAt(0);
      WindowInsetsCompat keyboard =
          new WindowInsetsCompat.Builder()
              .setInsets(
                  WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 250))
              .build();
      ViewCompat.dispatchApplyWindowInsets(appRoot, keyboard);
      capture(root, 320, 568, "search-with-keyboard-space");
      assertEquals(View.GONE, text(root, "Search").getVisibility());
      Field page = MainActivity.class.getDeclaredField("content");
      page.setAccessible(true);
      View song = find((View) page.get(activity), "Play Sky lights");
      assertTrue(song.isShown());
      int[] xy = new int[2];
      song.getLocationOnScreen(xy);
      assertTrue("A playable match must fit above the keyboard", xy[1] + song.getHeight() <= 318);
      assertSame(input, find(root, "Search songs or artists"));
      assertEquals("sky", input.getText().toString());
    }
  }

  @Test
  @Config(qualifiers = "w320dp-h640dp-mdpi")
  public void homeAndPlaylistScrollWithLargeText() throws Exception {
    Context app = RuntimeEnvironment.getApplication();
    android.content.res.Configuration cfg =
        new android.content.res.Configuration(app.getResources().getConfiguration());
    cfg.fontScale = 1.6f;
    app.getResources().updateConfiguration(cfg, null);
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = a.get().findViewById(android.R.id.content);
      capture(root, 320, 640, "home-discovery-large");
      find(root, "Open My evening").performClick();
      capture(root, 320, 640, "playlist-large");
      assertNotNull(find(root, "Play Sky lights"));
    }
  }

  @Test
  public void openingAPlaylistSurvivesActivityRecreation() throws Exception {
    Bundle state = new Bundle();
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = a.get().findViewById(android.R.id.content);
      find(root, "Open My evening").performClick();
      a.saveInstanceState(state);
    }
    try (ActivityController<MainActivity> b =
        Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible()) {
      View root = b.get().findViewById(android.R.id.content);
      assertNotNull(text(root, "My evening"));
      assertNotNull(text(root, "1 song · On this device"));
      assertNotNull(find(root, "Play Sky lights"));
      assertEquals(1, new LibraryStore(b.get()).tracks("playlist:My evening").size());
    }
  }
}
