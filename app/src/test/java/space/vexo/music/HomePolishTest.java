package space.vexo.music;

import static org.junit.Assert.*;
import android.app.Application;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import okhttp3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomePolishTest {
  static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
  }
  static byte[] cover(int background) throws Exception {
    Bitmap image = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888);
    image.eraseColor(Color.BLACK);
    Canvas canvas = new Canvas(image); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(background); canvas.drawRect(0, 45, 480, 315, paint);
    paint.setColor(0xffe6d8b5); canvas.drawCircle(240, 174, 74, paint);
    paint.setColor(background); canvas.drawCircle(240, 174, 23, paint);
    paint.setColor(Color.WHITE); paint.setTextSize(22); paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText("VEXO SESSIONS", 240, 287, paint);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.PNG, 100, bytes);
    return bytes.toByteArray();
  }

  @Test public void personalizedHomeAndMiniPlayerFitMeasuredWidthWithRealLoadedCovers() throws Exception {
    DiscoveryUiTest setup = new DiscoveryUiTest(); setup.seed();
    Application app = RuntimeEnvironment.getApplication();
    new UserProfile(app).saveName("Kavindu Kavishka");
    byte[] red = cover(0xff7c302a), blue = cover(0xff254b67);
    ArtworkLoader.configure(app, new OkHttpClient.Builder().addInterceptor(chain ->
        new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("fixture").body(ResponseBody.create(
                chain.request().url().encodedPath().contains("blue") ? blue : red,
                MediaType.get("image/png"))).build()).build());
    Track a = new Track("https://www.youtube.com/watch?v=abcdefghijk", "Rathinamo X Yaarukkum Sollaama",
        "Saurav Srisan, Tanisha Gnanavel", "https://covers.example/red.png", 252);
    Track b = new Track("https://www.youtube.com/watch?v=bcdefghijkl", "Unnaaleyy", "Saurav Srisan",
        "https://covers.example/blue.png", 205);
    try (var activity = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity owner = activity.get();
      ((android.os.Handler) field(owner, "main")).removeCallbacksAndMessages(null);
      MusicHomePanel panel = (MusicHomePanel) field(owner, "homePanel");
      panel.personal(MusicInterests.read(app), Arrays.asList(a, b), false, false,
          () -> {}, () -> {}, (songs, index) -> {}, () -> {});
      View mini = (View) field(owner, "mini"); mini.setVisibility(View.VISIBLE);
      ((TextView) field(owner, "miniTitle")).setText(a.title);
      ((TextView) field(owner, "miniArtist")).setText(a.artist);
      Visuals.artwork((ImageView) field(owner, "miniArt"), a);
      View root = owner.findViewById(android.R.id.content);
      for (int width : new int[] {320, 360, 393}) {
        setup.capture(root, width, 800, "home-polished-" + width);
        for (int wait = 0; wait < 30; wait++) {
          Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        setup.capture(root, width, 800, "home-polished-" + width);
        assertNull(new SearchUiTest().text(root, "Start your radio"));
        assertTrue(mini.getHeight() >= 58);
        int[] miniAt = new int[2]; mini.getLocationOnScreen(miniAt);
        assertEquals(8, miniAt[0]); assertEquals(width - 8, miniAt[0] + mini.getWidth());
        LinearLayout slot = (LinearLayout) field(panel, "personalSlot");
        HorizontalScrollView shelf = null;
        for (int i = 0; i < slot.getChildCount(); i++)
          if (slot.getChildAt(i) instanceof HorizontalScrollView) shelf = (HorizontalScrollView) slot.getChildAt(i);
        assertNotNull(shelf);
        LinearLayout cards = (LinearLayout) shelf.getChildAt(0);
        assertTrue("Both covers must fit without clipping the second", cards.getChildAt(1).getRight() <= shelf.getWidth());
        ImageView image = (ImageView) ((FrameLayout) ((LinearLayout) cards.getChildAt(0)).getChildAt(0)).getChildAt(0);
        assertTrue(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
      }
    } finally { ArtworkLoader.clear(); }
  }
}
