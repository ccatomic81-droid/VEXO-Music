package space.vexo.music;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiSmokeTest {
  @Before
  public void isolateUiFromMediaService() {
    // Robolectric does not implement Media3's real service binder. These checks
    // cover UI and storage; playback is verified separately on a device.
    android.app.Application app = org.robolectric.RuntimeEnvironment.getApplication();
    new UpdateChecker(app).automatic(false);
    new UserProfile(app).saveName("Listener");
    app.getSharedPreferences("settings", 0).edit().putString("region", "LK").commit();
    new RegionalFeed(
            "LK",
            "Regional YouTube search · not a ranked chart",
            false,
            System.currentTimeMillis(),
            java.util.Arrays.asList(
                new Track(
                    "https://www.youtube.com/watch?v=fixture",
                    "A very long example song title to verify alignment",
                    "A long artist name for layout testing",
                    "",
                    210),
                new Track(
                    "https://www.youtube.com/watch?v=fixture2",
                    "Second example song",
                    "Artist",
                    "",
                    180)))
        .persist(app);
    org.robolectric.Shadows.shadowOf(app)
        .declareComponentUnbindable(new android.content.ComponentName(app, PlaybackService.class));
  }

  private TextView find(View view, String text) {
    if (view instanceof TextView && text.contentEquals(((TextView) view).getText()))
      return (TextView) view;
    if (view instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) view;
      for (int i = 0; i < g.getChildCount(); i++) {
        TextView result = find(g.getChildAt(i), text);
        if (result != null) return result;
      }
    }
    return null;
  }

  private void render(View root, int width, int height, String file) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, width, height);
    View nav = findDescription(root, "Settings tab");
    assertNotNull(nav);
    int[] location = new int[2];
    nav.getLocationOnScreen(location);
    assertTrue("Navigation must fit vertically", location[1] + nav.getHeight() <= height);
    assertTrue("Navigation must fit horizontally", location[0] + nav.getWidth() <= width);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(bitmap));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, file + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  private View findDescription(View root, String description) {
    if (description.contentEquals(
        root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = findDescription(group.getChildAt(i), description);
        if (found != null) return found;
      }
    }
    return null;
  }

  @Test
  public void primaryScreensRenderAtPhoneSizes() throws Exception {
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = activity.get();
      View root = a.findViewById(android.R.id.content);
      assertNotNull(
          find(
              root,
              new UserProfile(org.robolectric.RuntimeEnvironment.getApplication()).greeting()
                  + ", Listener"));
      View nav = findDescription(root, "Settings tab");
      assertNotNull("Navigation labels should be readable", find((View) nav.getParent(), "Home"));
      assertNotNull(find((View) nav.getParent(), "Search"));
      assertNotNull(findDescription(root, "Play Second example song"));
      render(root, 360, 800, "home");
      assertNotNull(find(root, "Regional viral picks"));
      assertNotNull(find(root, "Regional YouTube search · not a ranked chart · Sri Lanka"));
      render(root, 320, 568, "home-small");
      findDescription(root, "Search tab").performClick();
      assertNotNull(find(root, "Search"));
      render(root, 360, 800, "search");
      findDescription(root, "Library tab").performClick();
      assertNotNull(find(root, "Your library"));
      render(root, 360, 800, "library");
      find(root, "Offline").performClick();
      assertNotNull(find(root, "Offline songs appear here"));
      render(root, 360, 800, "offline");
      find(root, "Favourites").performClick();
      assertNotNull(find(root, "Nothing here yet"));
      render(root, 320, 568, "favourites-small");
      OfflineLibrary offline = OfflineLibrary.get(a);
      java.lang.reflect.Field state = OfflineLibrary.class.getDeclaredField("state");
      state.setAccessible(true);
      String originalStatus = offline.status();
      try {
        state.set(offline, "Saving “Private song fixture” for offline listening");
        findDescription(root, "Settings tab").performClick();
        assertNotNull(find(root, "Data saver"));
        assertNotNull(
            "Settings must contain usage only while a song is saving",
            find(root, "0.0 MB / 500 MB · 0 saved songs"));
        render(root, 360, 800, "settings");
      } finally {
        state.set(offline, originalStatus);
      }
    }
  }

  @Test
  @Config(sdk = 26, qualifiers = "w360dp-h640dp-mdpi")
  public void startsOnAndroid8WithoutAudioPermission() throws Exception {
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = activity.get().findViewById(android.R.id.content);
      assertNotNull(
          find(
              root,
              new UserProfile(org.robolectric.RuntimeEnvironment.getApplication()).greeting()
                  + ", Listener"));
      render(root, 360, 640, "home-android8");
    }
  }

  @Test
  public void transportButtonsFitAtEveryNarrowWidth() throws Exception {
    android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
    TransportControls controls = new TransportControls(c);
    for (String name : new String[] {"shuffle", "previous", "play", "next", "repeat"})
      controls.addView(
          new Visuals.Icon(c, name, Visuals.TEXT),
          new android.widget.LinearLayout.LayoutParams(48, 48));
    for (int width : new int[] {240, 256, 264, 280, 320, 420}) {
      controls.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY));
      controls.layout(0, 0, width, 80);
      int right = 0;
      for (int i = 0; i < 5; i++) {
        View child = controls.getChildAt(i);
        assertTrue("Button overflow at " + width, child.getRight() <= width);
        assertTrue(child.getLeft() >= right);
        right = child.getRight();
        if (width >= 256) assertTrue("Touch target too small", child.getWidth() >= 48);
      }
    }
  }

  @Test
  @Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
  public void largeTextDoesNotClipNavigationOrSongRows() throws Exception {
    android.content.res.Configuration config =
        new android.content.res.Configuration(
            org.robolectric.RuntimeEnvironment.getApplication().getResources().getConfiguration());
    config.fontScale = 1.6f;
    org.robolectric.RuntimeEnvironment.getApplication()
        .getResources()
        .updateConfiguration(config, null);
    try (ActivityController<MainActivity> activity =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = activity.get().findViewById(android.R.id.content);
      render(root, 320, 640, "home-large-text");
      findDescription(root, "Search tab").performClick();
      render(root, 320, 640, "search-large-text");
      findDescription(root, "Library tab").performClick();
      render(root, 320, 640, "library-large-text");
    }
  }
}
