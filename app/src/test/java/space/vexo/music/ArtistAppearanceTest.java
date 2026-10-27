package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArtistAppearanceTest {
  @After
  public void resetPalette() {
    Visuals.appearance(false);
  }

  Track song(String artist) {
    return new Track("https://www.youtube.com/watch?v=fixture-song", "A song", artist, "", 210);
  }

  @Test
  public void artistSearchHistoryIsDeduplicatedBoundedAndClearedTogether() {
    Context c = RuntimeEnvironment.getApplication();
    SearchHistory history = new SearchHistory(c);
    SearchArtists artists = new SearchArtists(c);
    history.add("a song");
    artists.record(
        "a song",
        Arrays.asList(
            song("Billie Eilish - Topic"),
            song("Billie Eilish"),
            song("Unknown artist"),
            song("Nova SkyVEVO")));
    assertEquals(2, artists.entries().size());
    assertEquals("Billie Eilish", artists.entries().get(0).name);
    assertEquals("Nova Sky", artists.entries().get(1).name);
    for (int i = 0; i < 20; i++) {
      history.add("query " + i);
      artists.record("query " + i, Arrays.asList(song("Artist " + i)));
    }
    assertEquals(12, artists.entries().size());
    assertEquals("Artist 19", artists.entries().get(0).name);
    history.remove("QUERY 19");
    assertEquals("Artist 18", artists.entries().get(0).name);
    history.clear();
    assertTrue(artists.entries().isEmpty());
    artists.record("query 18", Arrays.asList(song("Late response")));
    assertTrue(
        "Clearing during a search must not repopulate artist history", artists.entries().isEmpty());
    history.add("https://youtu.be/abcdefghijk");
    artists.record("https://youtu.be/abcdefghijk", Arrays.asList(song("Private link")));
    assertTrue(artists.entries().isEmpty());
  }

  @Test
  public void storyCirclesOpenTheArtistAndAppearBeforeMadeForYou() throws Exception {
    DiscoveryUiTest helper = new DiscoveryUiTest();
    helper.seed();
    Context c = RuntimeEnvironment.getApplication();
    new SearchHistory(c).add("a song");
    new SearchArtists(c).record("a song", Arrays.asList(song("Billie Eilish"), song("Nova Sky")));
    try (var a = Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = a.get().findViewById(android.R.id.content);
      ArtistStoriesPanel stories = findType(root, ArtistStoriesPanel.class);
      assertNotNull(stories);
      MusicHomePanel home = findType(root, MusicHomePanel.class);
      home.personal(
          MusicInterests.read(c),
          Arrays.asList(helper.remote),
          false,
          false,
          () -> {},
          () -> {},
          (songs, index) -> {},
          () -> {});
      TextView made = helper.text(root, "Made for you");
      assertNotNull(made);
      ViewGroup homeBody = (ViewGroup) stories.getParent();
      View personal = made;
      while (personal.getParent() != homeBody) personal = (View) personal.getParent();
      assertTrue(homeBody.indexOfChild(stories) < homeBody.indexOfChild(personal));
      assertNotNull(helper.find(stories, "Find songs by Billie Eilish"));
    }
    List<String> selected = new ArrayList<>();
    ArtistStoriesPanel panel =
        new ArtistStoriesPanel(
            c, new SearchArtists(c).entries(), selected::add, () -> selected.add("search"));
    View card = helper.find(panel, "Find songs by Billie Eilish");
    assertTrue(card.performClick());
    assertEquals(Arrays.asList("Billie Eilish"), selected);
    ImageView image = findType(card, ImageView.class);
    assertTrue(image.getClipToOutline());
    capture(panel, 320, 220, "artist-circles");
  }

  @Test
  public void deviceChoiceAndManualOverridesPersist() {
    Context c = RuntimeEnvironment.getApplication();
    AppAppearance.save(c, 0);
    Configuration config = new Configuration(c.getResources().getConfiguration());
    config.uiMode = Configuration.UI_MODE_NIGHT_YES;
    Context night = c.createConfigurationContext(config);
    assertFalse(AppAppearance.light(night));
    AppAppearance.save(night, 2);
    assertTrue(AppAppearance.light(night));
    assertEquals("Light", AppAppearance.label(c));
    AppAppearance.save(c, 1);
    config.uiMode = Configuration.UI_MODE_NIGHT_NO;
    assertFalse(AppAppearance.light(c.createConfigurationContext(config)));
    AppAppearance.save(c, 0);
    assertTrue(AppAppearance.light(c.createConfigurationContext(config)));
  }

  @Test
  public void lightAndDarkSurfacesHaveReadableTextAndConsistentDialogs() throws Exception {
    DiscoveryUiTest helper = new DiscoveryUiTest();
    helper.seed();
    Context c = RuntimeEnvironment.getApplication();
    new SearchHistory(c).add("a song");
    new SearchArtists(c).record("a song", Arrays.asList(song("Billie Eilish"), song("Nova Sky")));
    for (int choice : new int[] {1, 2}) {
      AppAppearance.save(c, choice);
      try (var a = Robolectric.buildActivity(MainActivity.class).setup()) {
        assertEquals(choice == 2, Visuals.light);
        assertContrast(Visuals.TEXT, Visuals.BG, 7);
        assertContrast(Visuals.MUTED, Visuals.BG, 4.5);
        assertContrast(Visuals.MUTED, Visuals.PANEL, 4.5);
        assertContrast(Visuals.LIME, Visuals.BG, 4.5);
        assertContrast(Visuals.BG, Visuals.LIME, 4.5);
        String theme = choice == 2 ? "light" : "dark";
        View root = a.get().findViewById(android.R.id.content);
        MusicHomePanel home = findType(root, MusicHomePanel.class);
        home.personal(
            MusicInterests.read(c),
            Arrays.asList(helper.remote),
            false,
            false,
            () -> {},
            () -> {},
            (songs, index) -> {},
            () -> {});
        capture(root, 360, 800, "home-" + theme);
        helper.find(root, "Settings tab").performClick();
        assertNotNull(helper.text(root, "Appearance"));
        capture(root, 360, 800, "settings-" + theme);
        AlertDialog dialog =
            new VexoDialogs.Builder(a.get())
                .setTitle("Appearance")
                .setSingleChoiceItems(AppAppearance.LABELS, choice, (d, w) -> {})
                .setNegativeButton("Cancel", null)
                .show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View decor = dialog.getWindow().getDecorView();
        capture(decor, dialog.getWindow().getAttributes().width, 400, "popup-" + theme);
        assertEquals(
            Visuals.TEXT, dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor());
        dialog.dismiss();
      }
    }
  }

  @Test
  public void playerThumbIsFullyVisibleAtBothEndsInBothThemes() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    for (boolean light : new boolean[] {false, true}) {
      Visuals.appearance(light);
      Context themed =
          new ContextThemeWrapper(c, light ? R.style.VexoLightTheme : R.style.VexoBaseTheme);
      NowPlayingPanel panel =
          new NowPlayingPanel(
              themed,
              new NowPlayingPanel.Actions() {
                public void action(String name) {}

                public void seek(int value, boolean finished) {}
              });
      panel.title.setText("Happier Than Ever");
      panel.artist.setText("Billie Eilish");
      panel.radioStatus.setText("Related songs follow · View queue");
      for (int width : new int[] {320, 420}) {
        capture(panel, width, 800, "player-" + (light ? "light" : "dark") + "-" + width);
        SeekBar seek = panel.seek;
        for (int progress : new int[] {0, 1000}) {
          seek.setProgress(progress);
          Bitmap raster =
              Bitmap.createBitmap(seek.getWidth(), seek.getHeight(), Bitmap.Config.ARGB_8888);
          seek.draw(new Canvas(raster));
          Rect thumb = seek.getThumb().getBounds();
          int left = thumb.left + seek.getPaddingLeft() - seek.getThumbOffset();
          int right = thumb.right + seek.getPaddingLeft() - seek.getThumbOffset();
          assertTrue(
              "Entire circle stays inside the slider", left >= 0 && right <= seek.getWidth());
          assertTrue(thumb.width() >= 12);
          // The outer columns must remain free of thumb pixels at both endpoints.
          assertTrue(
              "Left edge remains clear",
              Color.alpha(raster.getPixel(0, raster.getHeight() / 2)) == 0);
          assertTrue(
              "Right edge remains clear",
              Color.alpha(raster.getPixel(raster.getWidth() - 1, raster.getHeight() / 2)) == 0);
          save(raster, "seek-" + (light ? "light" : "dark") + "-" + width + "-" + progress);
          raster.recycle();
        }
      }
    }
  }

  void assertContrast(int foreground, int background, double min) {
    assertTrue(
        "Contrast " + ColorUtils.calculateContrast(foreground, background),
        ColorUtils.calculateContrast(foreground, background) >= min);
  }

  <T extends View> T findType(View v, Class<T> type) {
    if (type.isInstance(v)) return type.cast(v);
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        T found = findType(((ViewGroup) v).getChildAt(i), type);
        if (found != null) return found;
      }
    return null;
  }

  void capture(View view, int width, int height, String name) throws Exception {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(image));
    save(image, name);
    image.recycle();
  }

  void save(Bitmap bitmap, String name) throws Exception {
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }
}
