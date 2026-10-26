package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import java.io.*;
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
public class FullHeightPanelTest {
  SearchUiTest find = new SearchUiTest();

  @Before
  public void seed() {
    new DiscoveryUiTest().seed();
  }

  Dialog open(MainActivity activity) {
    View root = activity.findViewById(android.R.id.content);
    find.description(root, "Settings tab").performClick();
    ((View) find.text(root, "YouTube taste").getParent().getParent()).performClick();
    return ShadowDialog.getLatestDialog();
  }

  LinearLayout panelRoot(Dialog dialog) {
    return (LinearLayout) ((ViewGroup) dialog.findViewById(android.R.id.content)).getChildAt(0);
  }

  void layout(View root, String name) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 320, 640);
    Bitmap bitmap = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(bitmap));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  WindowInsetsCompat insets(int bottom, int keyboard) {
    return new WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, bottom))
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
        .build();
  }

  @Test
  public void longYouTubeListFillsWindowAndCloseStaysVisibleWhileScrolling() throws Exception {
    List<Track> likes = new ArrayList<>();
    for (int i = 0; i < 35; i++)
      likes.add(
          YouTubeTasteTest.song(
              String.format(Locale.ROOT, "%011d", i), "Saved song " + i, "Artist " + i));
    new YouTubeTaste(RuntimeEnvironment.getApplication())
        .save(likes, Collections.emptyList(), System.currentTimeMillis());
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      Dialog dialog = open(c.get());
      assertEquals(-1, dialog.getWindow().getAttributes().height);
      assertEquals(-1, dialog.getWindow().getAttributes().width);
      LinearLayout root = panelRoot(dialog);
      ViewCompat.dispatchApplyWindowInsets(root, insets(24, 0));
      layout(root, "youtube-full-height-connected-320");
      MusicSurface scroll = (MusicSurface) root.getChildAt(1);
      assertEquals(640 - 24, scroll.getBottom());
      assertEquals(24, root.getPaddingTop());
      View close = find.description(root, "Close Your YouTube taste");
      int top = close.getTop();
      scroll.fullScroll(View.FOCUS_DOWN);
      layout(root, "youtube-full-height-list-bottom-320");
      assertTrue(close.isShown());
      assertEquals(top, close.getTop());
      assertTrue(find.text(root, "Close").isShown());
      int[] footer = new int[2], origin = new int[2];
      View last = find.text(root, "Close");
      last.getLocationOnScreen(footer);
      root.getLocationOnScreen(origin);
      assertTrue(
          footer[1] + last.getHeight() <= origin[1] + root.getHeight() - root.getPaddingBottom());
      close.performClick();
      assertFalse(dialog.isShowing());
    }
  }

  @Test
  public void keyboardInsetsAreAppliedOnceAndDisappearWithoutLeavingBottomSpace() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      Dialog dialog = open(c.get());
      LinearLayout root = panelRoot(dialog);
      ViewCompat.dispatchApplyWindowInsets(root, insets(24, 250));
      layout(root, "youtube-full-height-keyboard-320");
      assertEquals(250, root.getPaddingBottom());
      assertEquals(390, root.getChildAt(1).getBottom());
      ViewCompat.dispatchApplyWindowInsets(root, insets(24, 0));
      layout(root, "youtube-full-height-no-keyboard-320");
      assertEquals(24, root.getPaddingBottom());
      assertEquals(616, root.getChildAt(1).getBottom());
      dialog.dismiss();
    }
  }

  @Test
  @Config(sdk = 26)
  public void android8ThreeButtonNavigationKeepsTheFullPanelAndCloseReachable() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      Dialog dialog = open(c.get());
      LinearLayout root = panelRoot(dialog);
      ViewCompat.dispatchApplyWindowInsets(root, insets(48, 0));
      layout(root, "youtube-full-height-android8-320");
      assertEquals(592, root.getChildAt(1).getBottom());
      assertTrue(find.description(root, "Close Your YouTube taste").isShown());
      assertEquals(0xff1ed760, Visuals.ACCENT);
      assertEquals(0xff121212, Visuals.BG);
      dialog.dismiss();
    }
  }
}
