package space.vexo.music;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QueuePanelTest {
  private View text(View v, String label) {
    if (v instanceof TextView && label.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View result = text(g.getChildAt(i), label);
        if (result != null) return result;
      }
    }
    return null;
  }

  @Test
  public void rendersCurrentSongAndClearActionsAtNarrowWidths() throws Exception {
    List<String> actions = new ArrayList<>();
    QueuePanel panel =
        new QueuePanel(
            RuntimeEnvironment.getApplication(),
            true,
            new QueuePanel.Actions() {
              public void close() {
                actions.add("close");
              }

              public void play(int i, String id) {
                actions.add("play");
              }

              public void options(int i, Track t) {
                actions.add("options");
              }

              public void clearUpcoming() {
                actions.add("upcoming");
              }

              public void clearAll() {
                actions.add("all");
              }

              public void retry() {
                actions.add("retry");
              }

              public void autoplay(boolean enabled) {
                actions.add("autoplay");
              }
            });
    panel.refresh(
        Arrays.asList(
            AutoplayManagerTest.song("First song"), AutoplayManagerTest.song("Second song")),
        0,
        true,
        "Autoplay on");
    for (int width : new int[] {320, 360}) {
      panel.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
      panel.layout(0, 0, width, 640);
      assertNotNull(text(panel, "Playing now · Artist"));
      View clear = text(panel, "Clear upcoming");
      assertTrue(clear.getRight() <= width);
      Bitmap b = Bitmap.createBitmap(width, 640, Bitmap.Config.ARGB_8888);
      panel.draw(new Canvas(b));
      File dir = new File("build/ui-checks");
      dir.mkdirs();
      try (FileOutputStream f = new FileOutputStream(new File(dir, "queue-" + width + ".png"))) {
        b.compress(Bitmap.CompressFormat.PNG, 100, f);
      }
      b.recycle();
    }
    text(panel, "Clear upcoming").performClick();
    text(panel, "Clear queue").performClick();
    assertNull(text(panel, "Tune radio"));
    assertEquals(View.GONE, text(panel, "Retry recommendations").getVisibility());
    panel.status("Couldn’t load related songs · retry from Up next");
    assertEquals(View.VISIBLE, text(panel, "Retry recommendations").getVisibility());
    text(panel, "Retry recommendations").performClick();
    panel.status("Autoplay added 10 related songs");
    assertEquals(View.GONE, text(panel, "Retry recommendations").getVisibility());
    panel.autoplayState(false);
    assertEquals(Arrays.asList("upcoming", "all", "retry"), actions);
  }
}
