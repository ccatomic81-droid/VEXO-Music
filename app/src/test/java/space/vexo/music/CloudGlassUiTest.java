package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CloudGlassUiTest {
  @Before
  public void seed() {
    new DiscoveryUiTest().seed();
  }

  private void capture(View root, String name) throws Exception {
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

  @Test
  public void smartIsFifthNavigationDestinationAndLiveRoomScreenIsUsable() throws Exception {
    SearchUiTest find = new SearchUiTest();
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = a.get();
      View root = activity.findViewById(android.R.id.content);
      capture(root, "glass-home-320");
      View smart = find.description(root, "Smart Experience tab"),
          settings = find.description(root, "Settings tab");
      assertNotNull(smart);
      assertNotNull(settings);
      assertTrue(
          ((ViewGroup) smart.getParent()).indexOfChild(smart)
              > ((ViewGroup) settings.getParent()).indexOfChild(settings));
      smart.performClick();
      assertNotNull(find.text(root, "Your music. Your way."));
      capture(root, "glass-smart-320");
      find.text(root, "VEXO MindRadio").performClick();
      assertNotNull(find.text(root, "Ask MindRadio"));
      assertNotNull(find.text(root, "Build local radio"));
      capture(root, "glass-mindradio-320");
      find.text(root, "‹ Smart experience").performClick();
      find.text(root, "FairPlay · listen together").performClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      assertNotNull(find.text(dialog.getWindow().getDecorView(), "Create a listening room"));
      capture(dialog.getWindow().getDecorView(), "glass-room-320");
      dialog.dismiss();
    }
  }
}
