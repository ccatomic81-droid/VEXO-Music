package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExperienceControlsUiTest {
  private final SearchUiTest find = new SearchUiTest();

  private void field(MainActivity activity, String key, Object value) throws Exception {
    Field f = MainActivity.class.getDeclaredField(key);
    f.setAccessible(true);
    f.set(activity, value);
  }

  private Object call(MainActivity activity, String name, Class<?>[] types, Object... args)
      throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(activity, args);
  }

  @Before
  public void isolate() {
    new SearchUiTest().isolate();
  }

  @Test
  public void homeToggleAndSharedSettingsPersistWithoutChangingNavigation() throws Exception {
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = activity.get();
      View root = a.findViewById(android.R.id.content);
      View toggle = find.description(root, "Skip intros, off");
      assertNotNull(toggle);
      find.capture(root, "experience-home-intros-off-320", 640);
      toggle.performClick();
      assertTrue(new PureSong(new SmartStore(a)).enabled());
      assertNotNull(find.description(root, "Skip intros, on"));
      find.capture(root, "experience-home-intros-on-320", 640);
      find.description(root, "Skip intros, on").performLongClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      View controls = dialog.getWindow().getDecorView();
      assertNotNull(find.text(controls, "Skip intros: ON"));
      find.text(controls, "Skip intros: ON").performClick();
      assertFalse(new PureSong(new SmartStore(a)).enabled());
      assertNotNull(find.description(root, "Skip intros, off"));
      find.capture(controls, "experience-puresong-controls-320", 640);
      dialog.dismiss();
      find.description(root, "Settings tab").performClick();
      assertNotNull(find.text(root, "PureSong & skip intros"));
      find.description(root, "Smart Experience tab").performClick();
      assertNotNull(find.text(root, "PureSong & skip intros"));
    }
  }

  @Test
  public void categoriesLanguageAndKeyboardFitAndKeepTheirSearchScope() throws Exception {
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = activity.get();
      View root = a.findViewById(android.R.id.content);
      find.description(root, "Search tab").performClick();
      assertNotNull(find.description(root, "Browse Chill music"));
      find.capture(root, "experience-categories-320", 640);
      field(a, "searchCategory", MusicCategory.find("chill"));
      field(a, "searchLanguage", "Sinhala");
      field(a, "query", "Amaradeva");
      field(
          a,
          "results",
          new ArrayList<>(
              Collections.singletonList(
                  new Track(
                      "https://www.youtube.com/watch?v=category",
                      "Category song",
                      "Artist",
                      "",
                      180))));
      call(a, "render", new Class<?>[] {});
      assertNotNull(find.description(root, "Search in Chill"));
      assertNotNull(find.text(root, "Play Chill mix"));
      assertEquals(
          "Amaradeva Sinhala chill relaxing music",
          call(a, "categoryQuery", new Class<?>[] {String.class}, "Amaradeva"));
      assertEquals(
          "https://youtu.be/explicit",
          call(a, "categoryQuery", new Class<?>[] {String.class}, "https://youtu.be/explicit"));
      find.capture(root, "experience-category-results-320", 640);
      field(a, "keyboardOpen", true);
      call(a, "updateSearchChrome", new Class<?>[] {});
      assertEquals(
          View.GONE,
          find.description(root, "Choose search language. Current: Sinhala").getVisibility());
      find.capture(root, "experience-category-keyboard-320", 420);
      View nav = find.description(root, "Settings tab");
      int[] xy = new int[2];
      nav.getLocationOnScreen(xy);
      assertTrue(xy[1] + nav.getHeight() <= 420);
    }
  }
}
