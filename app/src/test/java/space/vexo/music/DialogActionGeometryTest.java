package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.res.Configuration;
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
public class DialogActionGeometryTest {
  @Test public void fullButtonTargetsFitShortLongAndScrollableDialogs() throws Exception {
    try (var activity = Robolectric.buildActivity(Activity.class).setup()) {
      for (float scale : new float[] {1f, 1.6f}) {
        ContextThemeWrapper c = new ContextThemeWrapper(activity.get(), R.style.AppTheme);
        Configuration config = new Configuration(c.getResources().getConfiguration()); config.fontScale = scale;
        ContextThemeWrapper themed = new ContextThemeWrapper(activity.get(), R.style.AppTheme);
        themed.applyOverrideConfiguration(config);
        for (String kind : new String[] {"short", "long", "list"}) {
          VexoDialogs.Builder builder = new VexoDialogs.Builder(themed);
          builder.setTitle(kind.equals("list") ? "Music region" : "What’s new");
          if (kind.equals("list")) {
            String[] regions = new String[120];
            for (int i=0; i<regions.length; i++) regions[i] = i == 0 ? "Global" : "Music region " + i;
            builder.setItems(regions, (d,w) -> {});
          } else builder.setMessage(kind.equals("short") ? "VEXO Music 0.25.3" : "Your music and settings stay safe. ".repeat(35));
          builder.setPositiveButton(kind.equals("short") ? "OK" : "Let’s listen", null);
          if (kind.equals("long")) builder.setNeutralButton("More details", null);
          if (!kind.equals("short")) builder.setNegativeButton("Cancel", null);
          AlertDialog dialog = builder.show();
          Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
          View decor = dialog.getWindow().getDecorView();
          int width = dialog.getWindow().getAttributes().width;
          decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(580, View.MeasureSpec.AT_MOST));
          decor.layout(0,0,width,decor.getMeasuredHeight());
          capture(decor, "popup-action-" + kind + "-" + scale);
          for (int which : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button == null || button.getVisibility() != View.VISIBLE) continue;
            Rect visible = new Rect(); assertTrue(button.getLocalVisibleRect(visible));
            assertEquals("Button top must not be clipped", 0, visible.top);
            assertEquals("Button bottom must not be clipped", button.getHeight(), visible.bottom);
            assertEquals("Button left must not be clipped", 0, visible.left);
            assertEquals("Button right must not be clipped", button.getWidth(), visible.right);
            assertTrue("Touch target must be at least 48dp", button.getHeight() >= 48);
          }
          dialog.dismiss();
        }
      }
    }
  }

  @Test public void standaloneLogoHasTransparentRoundedCorners() throws Exception {
    var c = RuntimeEnvironment.getApplication();
    var drawable = c.getDrawable(R.drawable.vexo_logo_v2);
    Bitmap bitmap = Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888);
    drawable.setBounds(0,0,108,108); drawable.draw(new Canvas(bitmap));
    assertEquals(0, Color.alpha(bitmap.getPixel(0,0)));
    assertEquals(0, Color.alpha(bitmap.getPixel(107,0)));
    assertEquals(0, Color.alpha(bitmap.getPixel(0,107)));
    assertEquals(0, Color.alpha(bitmap.getPixel(107,107)));
    assertEquals(255, Color.alpha(bitmap.getPixel(54,5)));
    captureBitmap(bitmap, "rounded-vexo-logo"); bitmap.recycle();
  }

  void capture(View view, String name) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap)); captureBitmap(bitmap,name); bitmap.recycle();
  }
  void captureBitmap(Bitmap bitmap, String name) throws Exception {
    File dir = new File("build/ui-checks"); dir.mkdirs();
    try(var out=new FileOutputStream(new File(dir,name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
  }
}
