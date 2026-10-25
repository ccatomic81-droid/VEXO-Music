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
public class RadioTunerTest {
  @Test
  public void tuningIsSelectableAndFitsNarrowScreensWithLargeText() throws Exception {
    List<RadioPolicy.Mode> changes = new ArrayList<>();
    android.content.Context context = RuntimeEnvironment.getApplication();
    android.content.res.Configuration config =
        new android.content.res.Configuration(context.getResources().getConfiguration());
    config.fontScale = 1.6f;
    context = context.createConfigurationContext(config);
    RadioTuner tuner = new RadioTuner(context, RadioPolicy.Mode.BALANCED, changes::add);
    RadioGroup group = (RadioGroup) tuner.getChildAt(1);
    assertTrue(changes.isEmpty());
    ((RadioButton) group.getChildAt(2)).performClick();
    assertEquals(Arrays.asList(RadioPolicy.Mode.DISCOVER), changes);
    assertFalse(((RadioButton) group.getChildAt(0)).isChecked());
    assertTrue(((RadioButton) group.getChildAt(2)).isChecked());
    for (int i = 0; i < group.getChildCount(); i++)
      group.getChildAt(i).jumpDrawablesToCurrentState();
    tuner.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    tuner.layout(0, 0, 320, tuner.getMeasuredHeight());
    for (int i = 0; i < group.getChildCount(); i++) {
      TextView b = (TextView) group.getChildAt(i);
      assertTrue(b.getHeight() >= 78);
      assertTrue(b.getLayout().getLineCount() >= 2);
      assertEquals(0, b.getLayout().getEllipsisCount(b.getLayout().getLineCount() - 1));
    }
    Bitmap image = Bitmap.createBitmap(320, tuner.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
    tuner.setBackgroundColor(Visuals.BG);
    tuner.draw(new Canvas(image));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, "radio-tuner-320-large.png"))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
  }
}
