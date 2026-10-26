package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
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
public class YouTubeNavigationUiTest {
  private final SearchUiTest find = new SearchUiTest();

  @Before
  public void seed() {
    new DiscoveryUiTest().seed();
  }

  @Test
  public void navigationHasNoPlateOrSystemScrimAndYouTubeIsOptional() throws Exception {
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = activity.get();
      View root = a.findViewById(android.R.id.content);
      View nav = (View) find.description(root, "Settings tab").getParent();
      assertEquals(Color.TRANSPARENT, ((ColorDrawable) nav.getBackground()).getColor());
      assertEquals(0f, nav.getElevation(), .001f);
      assertEquals(Color.TRANSPARENT, a.getWindow().getNavigationBarColor());
      assertFalse(a.getWindow().isNavigationBarContrastEnforced());
      assertFalse(new YouTubeTaste(a).read().connected);
      assertNull(find.text(root, "Your YouTube taste"));
      new DiscoveryUiTest().capture(root, 320, 640, "navigation-transparent-320");
      find.description(root, "Settings tab").performClick();
      ((View) find.text(root, "YouTube taste").getParent().getParent()).performClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      assertNotNull(find.text(dialog.getWindow().getDecorView(), "Connect YouTube"));
      captureDialog(dialog.getWindow().getDecorView());
      find.text(dialog.getWindow().getDecorView(), "Close").performClick();
      assertFalse(new YouTubeTaste(a).read().connected);
    }
  }

  private void captureDialog(View root) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 320, 640);
    android.graphics.Bitmap image =
        android.graphics.Bitmap.createBitmap(320, 640, android.graphics.Bitmap.Config.ARGB_8888);
    root.draw(new android.graphics.Canvas(image));
    java.io.File folder = new java.io.File("build/ui-checks");
    folder.mkdirs();
    try (java.io.FileOutputStream out =
        new java.io.FileOutputStream(new java.io.File(folder, "youtube-consent-320.png"))) {
      image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }

  @Test
  public void optedInHomeAndDisconnectRemoveOnlyYouTubeData() throws Exception {
    Track like = YouTubeTasteTest.song("abcdefghijk", "Night song", "First artist");
    Track pick = YouTubeTasteTest.song("bcdefghijkl", "New suggestion", "Second artist");
    new YouTubeTaste(RuntimeEnvironment.getApplication())
        .save(
            Collections.singletonList(like),
            Collections.singletonList(pick),
            System.currentTimeMillis());
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = activity.get();
      View root = a.findViewById(android.R.id.content);
      assertNotNull(find.text(root, "Your YouTube taste"));
      assertNotNull(find.text(root, "New suggestion"));
      java.lang.reflect.Field source = MainActivity.class.getDeclaredField("youtube");
      source.setAccessible(true);
      ((YouTubeConnection) source.get(a)).cancel();
      assertTrue(new YouTubeTaste(a).read().connected);
      assertEquals(like.url, new YouTubeTaste(a).read().likes.get(0).url);
      find.text(root, "Manage").performClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      assertNotNull(find.text(dialog.getWindow().getDecorView(), "Liked on YouTube"));
      find.text(dialog.getWindow().getDecorView(), "Disconnect & remove imported data")
          .performClick();
      assertFalse(new YouTubeTaste(a).read().connected);
      assertNull(find.text(root, "Your YouTube taste"));
      assertEquals(1, new LibraryStore(a).tracks("favourites").size());
      assertNotNull(find.text(dialog.getWindow().getDecorView(), "Connect YouTube"));
    }
  }

  @Test
  @Config(sdk = 26, qualifiers = "w320dp-h640dp-mdpi")
  public void olderAndroidKeepsNavigationAndSettingsReachable() throws Exception {
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = activity.get().findViewById(android.R.id.content);
      new DiscoveryUiTest().capture(root, 320, 640, "navigation-transparent-api26");
      assertNotNull(find.description(root, "Home tab"));
      find.description(root, "Settings tab").performClick();
      assertNotNull(find.text(root, "YouTube taste"));
    }
  }
}
