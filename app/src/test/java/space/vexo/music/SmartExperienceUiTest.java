package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SmartExperienceUiTest {
  private TextView find(View view, String text) {
    if (view instanceof TextView && ((TextView) view).getText().toString().contains(text))
      return (TextView) view;
    if (view instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) view;
      for (int i = 0; i < g.getChildCount(); i++) {
        TextView hit = find(g.getChildAt(i), text);
        if (hit != null) return hit;
      }
    }
    return null;
  }

  private void capture(View view, String name) throws Exception {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 320, 640);
    Bitmap b = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(b));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }

  @Test
  public void nativeSmartToolsNavigateSaveMemoryAndRestoreRecoveryUi() throws Exception {
    try (ActivityController<Activity> activity =
        Robolectric.buildActivity(Activity.class).setup()) {
      Activity a = activity.get();
      a.setTheme(R.style.AppTheme);
      SmartStore smart = new SmartStore(a);
      LibraryStore library = new LibraryStore(a);
      Track song =
          new Track(
              "https://www.youtube.com/watch?v=smart-ui",
              "A song to remember",
              "An artist",
              "",
              180);
      library.createPlaylist("Evening");
      library.addToPlaylist("Evening", song);
      library.deletePlaylist("Evening");
      SmartExperiencePanel panel =
          new SmartExperiencePanel(
              a,
              smart,
              library,
              new SmartExperiencePanel.Listener() {
                public void changed() {}

                public void play(List<Track> songs, int index, long moment) {}

                public void discover(String q, boolean fresh) {}
              });
      a.setContentView(panel);
      capture(panel, "smart-menu-320");
      find(panel, "Recommendation controls").performClick();
      assertNotNull(find(panel, "Tune your recommendations"));
      capture(panel, "smart-controls-320");
      panel.track(song, 12000);
      find(panel, "Save a song memory").performClick();
      find(panel, "Save memory").performClick();
      assertEquals(1, smart.memories().length());
      assertNotNull(find(panel, "Your memory timeline"));
      capture(panel, "smart-memory-320");
      panel.menu();
      find(panel, "Playlist Time Machine").performClick();
      assertNotNull(find(panel, "Before deletion"));
      capture(panel, "smart-time-machine-320");
      panel.menu();
      find(panel, "Customize Home").performClick();
      find(panel, "Enable distraction-free mode").performClick();
      assertTrue(smart.preferences().optBoolean("focus"));
      capture(panel, "smart-home-controls-320");
    }
  }

  @Test
  @Config(sdk = 26, qualifiers = "w320dp-h640dp-mdpi")
  public void smartMenuAndKeystoreWorkAtMinimumSupportedApi() {
    Context c = RuntimeEnvironment.getApplication();
    SmartStore s = new SmartStore(c);
    s.preference("mood", "calm");
    assertEquals("calm", new SmartStore(c).preferences().optString("mood"));
  }
}
