package space.vexo.music;

import static org.junit.Assert.*;

import android.content.*;
import android.content.res.Configuration;
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
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NowPlayingPanelTest {
  private NowPlayingPanel panel(int width, int height, float font, List<String> actions) {
    Context base = RuntimeEnvironment.getApplication();
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.fontScale = font;
    config.orientation =
        width > height ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
    Context themed =
        new android.view.ContextThemeWrapper(
            base.createConfigurationContext(config), R.style.AppTheme);
    NowPlayingPanel p =
        new NowPlayingPanel(
            themed,
            new NowPlayingPanel.Actions() {
              public void action(String action) {
                actions.add(action);
              }

              public void seek(int progress, boolean finished) {
                actions.add("seek:" + progress + ":" + finished);
              }
            });
    p.title.setText("Rathinamo (Female Version)");
    p.artist.setText("Saurav Srisan");
    p.elapsed.setText("1:53");
    p.duration.setText("2:57");
    p.seek.setProgress(640);
    p.play.set("pause", Visuals.BG);
    p.favourite.set("liked", Visuals.LIME);
    p.radioStatus.setText(
        "Radio · Rathinamo (Female Version) · Balanced\nRelated songs follow your queue");
    p.state.setText("Saved offline");
    Bitmap thumbnail = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(thumbnail);
    Paint paint = new Paint();
    paint.setShader(
        new LinearGradient(
            0,
            0,
            480,
            270,
            new int[] {0xff3a4939, 0xff7e4f40, 0xff232f4a},
            null,
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 480, 270, paint);
    paint.setShader(null);
    paint.setColor(Visuals.LIME);
    c.drawCircle(240, 135, 70, paint);
    paint.setColor(Visuals.BG);
    c.drawCircle(240, 135, 24, paint);
    p.art.setImageBitmap(thumbnail);
    p.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    p.layout(0, 0, width, height);
    return p;
  }

  private void render(NowPlayingPanel p, int width, int height, String name) throws Exception {
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    p.draw(new Canvas(image));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }

  @Test
  public void realPlayerFitsFullArtworkAndKeepsEssentialControls() throws Exception {
    for (int width : new int[] {320, 360, 420}) {
      List<String> actions = new ArrayList<>();
      NowPlayingPanel p = panel(width, 800, 1f, actions);
      assertEquals(p.cover.getWidth(), p.cover.getHeight());
      assertEquals(p.cover.getWidth(), p.art.getWidth());
      assertEquals(p.cover.getHeight(), p.art.getHeight());
      RectF fitted = new RectF(0, 0, 480, 270);
      p.art.getImageMatrix().mapRect(fitted);
      assertTrue("Full image must fit horizontally", fitted.left >= -.1f && fitted.right <= p.art.getWidth() + .1f);
      assertTrue("Full image must fit vertically", fitted.top >= -.1f && fitted.bottom <= p.art.getHeight() + .1f);
      assertEquals(480f / 270f, fitted.width() / fitted.height(), .01f);
      ViewGroup tools = (ViewGroup) p.speed.getParent();
      float previous = -1, delta = -1;
      for (int i = 0; i < tools.getChildCount(); i++) {
        View child = tools.getChildAt(i);
        float center = (child.getLeft() + child.getRight()) / 2f;
        if (i > 0) {
          if (delta >= 0) assertEquals(delta, center - previous, 1f);
          delta = center - previous;
        }
        previous = center;
        assertTrue(child.getWidth() >= 48);
      }
      assertEquals("Pause", p.play.getContentDescription());
      assertEquals("Remove from favourites", p.favourite.getContentDescription());
      assertEquals("1×", MainActivity.speedLabel(1f));
      assertEquals("1.25×", MainActivity.speedLabel(1.25f));
      assertTrue(actions.isEmpty());
      p.play.performClick();
      assertEquals(Arrays.asList("play"), actions);
      render(p, width, 800, "now-playing-" + width);
    }
  }

  @Test
  public void largeTextAndLandscapeRemainScrollableWithoutHorizontalClipping() throws Exception {
    for (int[] size : new int[][] {{320, 640}, {640, 360}}) {
      NowPlayingPanel p = panel(size[0], size[1], 1.6f, new ArrayList<>());
      assertEquals(p.cover.getWidth(), p.cover.getHeight());
      View body = p.scroll.getChildAt(0);
      assertTrue(body.getHeight() > p.scroll.getHeight());
      assertTrue(body.getWidth() <= size[0]);
      render(p, size[0], size[1], "now-playing-large-" + size[0]);
      Bitmap before = Bitmap.createBitmap(size[0], 52, Bitmap.Config.ARGB_8888);
      p.draw(new Canvas(before));
      p.scroll.fullScroll(View.FOCUS_DOWN);
      p.scroll.scrollTo(0, body.getHeight());
      Bitmap after = Bitmap.createBitmap(size[0], 52, Bitmap.Config.ARGB_8888);
      p.draw(new Canvas(after));
      assertTrue("Scrolling must not cover the fixed header", before.sameAs(after));
      before.recycle();
      after.recycle();
      render(p, size[0], size[1], "now-playing-footer-large-" + size[0]);
    }
  }

  @Test
  public void glyphsStayTheSameSizeInsideDifferentTouchTargets() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    String[] names = {
      "queue",
      "shuffle",
      "repeat",
      "repeat_one",
      "timer",
      "liked",
      "heart",
      "play",
      "pause",
      "next",
      "previous",
      "radio",
      "tune"
    };
    Bitmap sheet = Bitmap.createBitmap(48 * names.length, 96, Bitmap.Config.ARGB_8888);
    Canvas output = new Canvas(sheet);
    output.drawColor(Visuals.BG);
    for (int i = 0; i < names.length; i++) {
      int[] widths = new int[2], heights = new int[2];
      for (int j = 0; j < 2; j++) {
        int size = j == 0 ? 48 : 80;
        Visuals.Icon icon = new Visuals.Icon(c, names[i], Visuals.TEXT);
        icon.layout(0, 0, size, size);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(b));
        int left = size, top = size, right = -1, bottom = -1;
        for (int y = 0; y < size; y++)
          for (int x = 0; x < size; x++)
            if (Color.alpha(b.getPixel(x, y)) > 0) {
              left = Math.min(left, x);
              right = Math.max(right, x);
              top = Math.min(top, y);
              bottom = Math.max(bottom, y);
            }
        widths[j] = right - left;
        heights[j] = bottom - top;
        assertTrue(right >= left);
        assertTrue(right - left <= 24);
        assertTrue(bottom - top <= 24);
        if (j == 0) {
          output.save();
          output.translate(i * 48, 0);
          icon.draw(output);
          output.restore();
        }
        b.recycle();
      }
      assertEquals(names[i], widths[0], widths[1]);
      assertEquals(names[i], heights[0], heights[1]);
    }
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, "player-icons.png"))) {
      sheet.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    sheet.recycle();
  }
}
