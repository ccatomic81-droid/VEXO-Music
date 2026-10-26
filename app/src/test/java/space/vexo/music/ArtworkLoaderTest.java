package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArtworkLoaderTest {
  Context context = RuntimeEnvironment.getApplication();

  byte[] image(int color) throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
    bitmap.recycle();
    return bytes.toByteArray();
  }

  ImageView view() {
    ImageView view = new ImageView(context);
    view.setLayoutParams(new LinearLayout.LayoutParams(128, 128));
    return view;
  }

  Response response(Interceptor.Chain chain, int code, byte[] bytes) {
    return new Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fixture")
        .body(ResponseBody.create(bytes, MediaType.get("image/png")))
        .build();
  }

  void await(ImageView view, int color) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < end) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      if (view.getDrawable() instanceof BitmapDrawable
          && ((BitmapDrawable) view.getDrawable()).getBitmap().getPixel(40, 40) == color) return;
      Thread.sleep(10);
    }
    fail("Artwork did not arrive on the target");
  }

  @After
  public void clean() {
    Visuals.clearArtwork();
  }

  @Test
  public void duplicateCoversShareOneFetchAndMemoryCacheDoesNotFlash() throws Exception {
    byte[] bytes = image(Color.BLUE);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    ArtworkLoader.configure(
        context,
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  calls.incrementAndGet();
                  started.countDown();
                  try {
                    release.await(3, TimeUnit.SECONDS);
                  } catch (InterruptedException e) {
                    throw new IOException(e);
                  }
                  return response(chain, 200, bytes);
                })
            .build());
    String url = "https://covers.example/one-" + System.nanoTime() + ".png";
    ImageView a = view(), b = view();
    Visuals.artwork(a, url);
    Visuals.artwork(b, url);
    try {
      assertTrue(started.await(3, TimeUnit.SECONDS));
    } finally {
      release.countDown();
    }
    await(a, Color.BLUE);
    await(b, Color.BLUE);
    assertEquals(1, calls.get());
    assertEquals(url, a.getTag());
    Visuals.artwork(a, url);
    await(a, Color.BLUE);
    assertEquals(1, calls.get());
  }

  @Test
  public void missingResizedCoverFallsBackAndRebindingCannotShowOldArt() throws Exception {
    byte[] blue = image(Color.BLUE), red = image(Color.RED);
    AtomicInteger fallback = new AtomicInteger();
    ArtworkLoader.configure(
        context,
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  String url = chain.request().url().toString();
                  if (url.contains("w128-h128")) return response(chain, 404, new byte[0]);
                  if (url.contains("w60-h60")) {
                    fallback.incrementAndGet();
                    return response(chain, 200, blue);
                  }
                  return response(chain, 200, red);
                })
            .build());
    ImageView a = view();
    String url = "https://lh3.googleusercontent.com/fixture-" + System.nanoTime() + "=w60-h60";
    Visuals.artwork(a, url);
    await(a, Color.BLUE);
    assertEquals(1, fallback.get());
    Visuals.artwork(a, "https://covers.example/red-" + System.nanoTime() + ".png");
    await(a, Color.RED);
    assertFalse(url.equals(a.getTag()));
    Visuals.clearArtwork();
    assertFalse(a.getDrawable() instanceof BitmapDrawable);
  }

  @Test
  public void smallRowsUseSmallCoversAndAccentMatchesTheVioletTheme() {
    ImageView small = view();
    assertEquals(128, ArtworkLoader.size(small));
    assertEquals(
        "https://lh3.googleusercontent.com/a=w128-h128",
        ArtworkQuality.forSize("https://lh3.googleusercontent.com/a=w60-h60", 128));
    assertEquals(
        "https://i.ytimg.com/vi/id/hqdefault.jpg",
        ArtworkQuality.forSize("https://i.ytimg.com/vi/id/hqdefault.jpg", 128));
    assertEquals(Color.rgb(201, 174, 245), Visuals.ACCENT);
  }
}
