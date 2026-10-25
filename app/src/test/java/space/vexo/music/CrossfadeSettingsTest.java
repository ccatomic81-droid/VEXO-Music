package space.vexo.music;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CrossfadeSettingsTest {
  @Test
  public void sliderClampsSupportsOffAndFitsLargeText() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    android.content.res.Configuration config =
        new android.content.res.Configuration(c.getResources().getConfiguration());
    config.fontScale = 1.6f;
    c = new ContextThemeWrapper(c.createConfigurationContext(config), R.style.AppTheme);
    CrossfadeSettingsPanel p = new CrossfadeSettingsPanel(c, 99);
    assertEquals(12, p.seconds());
    LinearLayout body = (LinearLayout) p.getChildAt(0);
    SeekBar slider = (SeekBar) body.getChildAt(2);
    slider.setProgress(0);
    assertEquals(0, p.seconds());
    slider.setProgress(4);
    assertEquals(4, p.seconds());
    p.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
    p.layout(0, 0, 320, p.getMeasuredHeight());
    assertTrue(slider.getWidth() <= 320);
    assertTrue(slider.getHeight() >= 48);
    Bitmap image = Bitmap.createBitmap(320, p.getHeight(), Bitmap.Config.ARGB_8888);
    p.setBackgroundColor(Visuals.BG);
    p.draw(new Canvas(image));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out =
        new FileOutputStream(new File(dir, "crossfade-settings-large.png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }

  @Test
  @Config(sdk = 26)
  public void opensOnAndroid8WithTheDefaultDuration() {
    CrossfadeSettingsPanel p = new CrossfadeSettingsPanel(RuntimeEnvironment.getApplication(), 4);
    assertEquals(4, p.seconds());
  }
}
