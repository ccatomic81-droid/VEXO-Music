package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesignAuditTest {
  @After public void reset() { Visuals.appearance(false); }

  @Test public void emptyArtistPromptLeavesRoomForRecommendations() throws Exception {
    List<String> actions = new ArrayList<>();
    ArtistStoriesPanel panel = new ArtistStoriesPanel(RuntimeEnvironment.getApplication(), Collections.emptyList(), actions::add, () -> actions.add("search"));
    new ArtistAppearanceTest().capture(panel, 288, 120, "audit-empty-artists");
    View find = new SearchUiTest().description(panel, "Find artists by searching songs");
    assertTrue(find.getHeight() >= 48);
    assertTrue(find.performClick());
    assertEquals(Arrays.asList("search"), actions);
    panel.measure(View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    assertTrue("Empty prompt should not push music off Home", panel.getMeasuredHeight() <= 110);
  }

  @Test public void libraryHasOneImportActionAndAnAccessibleFileAlternative() throws Exception {
    new UiSmokeTest().isolateUiFromMediaService();
    for (float scale : new float[] {1, 1.6f}) {
      for (int theme : new int[] {1, 2}) {
        Context c = RuntimeEnvironment.getApplication();
        android.content.res.Configuration config = new android.content.res.Configuration(c.getResources().getConfiguration());
        config.fontScale = scale;
        c.getResources().updateConfiguration(config, c.getResources().getDisplayMetrics());
        AppAppearance.save(c, theme);
        try (var a = Robolectric.buildActivity(MainActivity.class).setup()) {
          View root = a.get().findViewById(android.R.id.content);
          SearchUiTest finder = new SearchUiTest();
          finder.description(root, "Library tab").performClick();
          assertEquals(1, count(root, "Import device music"));
          assertEquals(1, count(root, "Choose files"));
          new ArtistAppearanceTest().capture(root, 320, 640, "audit-library-" + theme + "-" + scale);
          TextView choose = finder.text(root, "Choose files");
          choose.requestRectangleOnScreen(new android.graphics.Rect(0, 0, choose.getWidth(), choose.getHeight()), true);
          assertTrue("File picker target remains usable", choose.getHeight() >= 48);
          View libraryHeader = (View) finder.text(root, "Your library").getParent();
          finder.text(libraryHeader, "Playlists").performClick();
          assertEquals(1, count(root, "Create a playlist"));
          assertEquals(0, count(root, "+  Create playlist"));
          finder.description(root, "Settings tab").performClick();
          for (String group : new String[] {"Your VEXO", "Playback & discovery", "Library & data", "App & support"})
            assertNotNull(finder.text(root, group));
          assertEquals("No transport controls in Settings", 0, descriptions(root, "Next song"));
          new ArtistAppearanceTest().capture(root, 320, 640, "audit-settings-" + theme + "-" + scale);
        }
      }
    }
  }

  @Test public void upNextCountsOnlyTracksAfterTheCurrentSong() {
    QueuePanel p = new QueuePanel(RuntimeEnvironment.getApplication(), true, new QueuePanel.Actions() {
      public void close() {} public void play(int i, String id) {} public void options(int i, Track t) {}
      public void clearUpcoming() {} public void clearAll() {} public void autoplay(boolean b) {}
    });
    List<Track> songs = Arrays.asList(AutoplayManagerTest.song("First"), AutoplayManagerTest.song("Second"), AutoplayManagerTest.song("Third"));
    SearchUiTest find = new SearchUiTest();
    p.refresh(songs, 1, true, "");
    assertNotNull(find.text(p, "Now playing · 1 song up next"));
    assertTrue(find.text(p, "Clear upcoming").isEnabled());
    p.refresh(songs, 2, false, "");
    assertNotNull(find.text(p, "Ready to play · 0 songs up next"));
    assertFalse(find.text(p, "Clear upcoming").isEnabled());
    p.refresh(songs, -1, false, "");
    assertNotNull(find.text(p, "Ready to play · 3 songs up next"));
    p.refresh(Collections.emptyList(), -1, false, "");
    assertNotNull(find.text(p, "Your queue is empty"));
    assertFalse(find.text(p, "Clear queue").isEnabled());
  }

  private int count(View v, String value) {
    int n = v instanceof TextView && value.contentEquals(((TextView)v).getText()) ? 1 : 0;
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) n += count(((ViewGroup)v).getChildAt(i), value);
    return n;
  }
  private int descriptions(View v, String value) {
    int n = value.contentEquals(v.getContentDescription()) ? 1 : 0;
    if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) n += descriptions(((ViewGroup)v).getChildAt(i), value);
    return n;
  }
}
