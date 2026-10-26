package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SearchArrangementTest {
  final SearchUiTest find = new SearchUiTest();

  @Before
  public void isolate() {
    new SearchUiTest().isolate();
    Context app = RuntimeEnvironment.getApplication();
    SearchHistory recent = new SearchHistory(app);
    recent.clear();
    recent.add("pawiyai");
    recent.add("ranthi");
  }

  private Object field(MainActivity a, String key) throws Exception {
    Field field = MainActivity.class.getDeclaredField(key);
    field.setAccessible(true);
    return field.get(a);
  }

  private void layout(View root, int width, int height, String name) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, width, height);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(bitmap));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  private void assertArrangement(MainActivity activity, int width, int height, String name)
      throws Exception {
    View root = activity.findViewById(android.R.id.content);
    find.description(root, "Search tab").performClick();
    View mini = (View) field(activity, "mini");
    mini.setVisibility(View.VISIBLE);
    ((TextView) field(activity, "miniTitle")).setText("Rathinamo (Female Version)");
    ((TextView) field(activity, "miniArtist")).setText("Saurav Srishan");
    layout(root, width, height, name);
    View recent = find.text(root, "Recent searches"), browse = find.text(root, "Browse all");
    int[] xyRecent = new int[2], xyBrowse = new int[2];
    recent.getLocationOnScreen(xyRecent);
    browse.getLocationOnScreen(xyBrowse);
    assertTrue("Recent searches should appear before the browse grid", xyRecent[1] < xyBrowse[1]);
    View pop = find.description(root, "Browse Pop music"),
        sleep = find.description(root, "Browse Sleep music"),
        regional = find.description(root, "Browse Sri Lankan music");
    assertEquals("Odd rows must keep the same card width", pop.getWidth(), sleep.getWidth());
    assertEquals(pop.getWidth(), regional.getWidth());
    View rock = find.description(root, "Browse Rock music"),
        hip = find.description(root, "Browse Hip-hop music");
    assertEquals(pop.getHeight(), hip.getHeight());
    assertTrue(rock.getTop() >= 0);
    for (MusicCategory category : MusicCategory.ALL) {
      TextView card = (TextView) find.description(root, "Browse " + category.name + " music");
      assertTrue(card.getWidth() > 90);
      assertTrue(card.getHeight() >= 80);
      assertTrue(
          "Category label must fit vertically",
          card.getLayout().getLineBottom(card.getLayout().getLineCount() - 1)
              <= card.getHeight() - card.getPaddingBottom());
    }
    View remove = find.description(root, "Remove recent search ranthi");
    assertTrue(remove.getWidth() >= 48 && remove.getHeight() >= 48);
    View nav = find.description(root, "Settings tab");
    int[] navXY = new int[2], miniXY = new int[2];
    nav.getLocationOnScreen(navXY);
    mini.getLocationOnScreen(miniXY);
    assertTrue(navXY[1] + nav.getHeight() <= height);
    assertTrue(miniXY[1] + mini.getHeight() <= navXY[1]);
    assertEquals(20, miniXY[0]);
    assertEquals(width - 20, miniXY[0] + mini.getWidth());
  }

  @Test
  public void phoneAndSmallScreenHaveAConsistentBrowseGridWithMiniPlayer() throws Exception {
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      assertArrangement(a.get(), 393, 800, "search-polish-393-mini");
      assertArrangement(a.get(), 320, 568, "search-polish-320-mini");
      View root = a.get().findViewById(android.R.id.content);
      find.description(root, "Choose search language. Current: Any").performClick();
      AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
      dialog.getListView().performItemClick(null, 2, 2);
      assertNotNull(find.description(root, "Choose search language. Current: Tamil"));
      assertNotNull(find.text(root, "Tamil ▾"));
    }
  }

  @Test
  @Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
  public void largeTextKeepsCategoryLabelsAndBottomControlsInsideTheirBounds() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    android.content.res.Configuration config =
        new android.content.res.Configuration(c.getResources().getConfiguration());
    config.fontScale = 1.6f;
    c.getResources().updateConfiguration(config, null);
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      assertArrangement(a.get(), 320, 640, "search-polish-320-font160");
    }
  }
}
