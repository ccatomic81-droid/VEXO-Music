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
  public void wideAndPortraitArtworkFillsSquareCoversWithoutStretching() throws Exception {
    for (int[] dimensions : new int[][] {{320, 180}, {180, 320}}) {
      Bitmap source = Bitmap.createBitmap(dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
      source.eraseColor(Color.BLUE);
      Canvas canvas = new Canvas(source);
      Paint edge = new Paint();
      edge.setColor(Color.RED);
      canvas.drawRect(0, 0, dimensions[0], 16, edge);
      canvas.drawRect(0, dimensions[1] - 16, dimensions[0], dimensions[1], edge);
      canvas.drawRect(0, 0, 16, dimensions[1], edge);
      canvas.drawRect(dimensions[0] - 16, 0, dimensions[0], dimensions[1], edge);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      source.compress(Bitmap.CompressFormat.PNG, 100, bytes);
      byte[] data = bytes.toByteArray();
      ArtworkLoader.configure(context, new OkHttpClient.Builder().addInterceptor(
          chain -> response(chain, 200, data)).build());
      ImageView target = view();
      Visuals.artwork(target, "https://covers.example/fit-" + System.nanoTime() + ".png");
      await(target, Color.BLUE);
      Bitmap actual = ((BitmapDrawable) target.getDrawable()).getBitmap();
      assertEquals(128, actual.getWidth());
      assertEquals(128, actual.getHeight());
      assertEquals(Color.BLUE, actual.getPixel(64, 64));
      // Uniform scaling crops only the longer axis; the short-axis edges remain.
      if (dimensions[0] > dimensions[1]) {
        assertEquals(Color.RED, actual.getPixel(64, 1));
        assertEquals(Color.RED, actual.getPixel(64, 126));
      } else {
        assertEquals(Color.RED, actual.getPixel(1, 64));
        assertEquals(Color.RED, actual.getPixel(126, 64));
      }
      assertEquals(ImageView.ScaleType.CENTER_CROP, target.getScaleType());
      source.recycle();
    }
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
  public void repairAllowsTheSameArtworkToLoadAgain() throws Exception {
    byte[] bytes = image(Color.GREEN);
    ArtworkLoader.configure(context, new OkHttpClient.Builder().addInterceptor(
        chain -> response(chain, 200, bytes)).build());
    ImageView target = view();
    String url = "https://covers.example/repair-" + System.nanoTime() + ".png";
    Visuals.artwork(target, url);
    await(target, Color.GREEN);
    Visuals.clearArtwork();
    assertNull("Clearing must invalidate the URL marker used by Now Playing", target.getTag());
    assertFalse(target.getDrawable() instanceof BitmapDrawable);
    Visuals.artwork(target, url);
    await(target, Color.GREEN);
    assertNotSame("Placeholder must not also be the mutable clipping background",
        target.getBackground(), target.getDrawable());
  }

  @Test
  public void missingAndExpiredMetadataUseTheSameSongsPublicThumbnail() throws Exception {
    byte[] bytes = image(Color.MAGENTA);
    java.util.List<String> requests = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    ArtworkLoader.configure(context, new OkHttpClient.Builder().addInterceptor(chain -> {
      requests.add(chain.request().url().toString());
      return chain.request().url().host().equals("i.ytimg.com")
          ? response(chain, 200, bytes) : response(chain, 404, new byte[0]);
    }).build());
    Track expired = new Track("https://www.youtube.com/watch?v=abcdefghijk", "Song", "Artist",
        "https://lh3.googleusercontent.com/expired-" + System.nanoTime() + "=w60-h60", 180);
    ImageView a = view(), b = view();
    Visuals.artwork(a, expired);
    await(a, Color.MAGENTA);
    Track missing = new Track(expired.url, "Song", "Artist", "", 180);
    Visuals.artwork(b, missing);
    await(b, Color.MAGENTA);
    assertTrue(requests.contains("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"));
    assertEquals("track:" + expired.url, b.getTag());
    assertTrue(ArtworkQuality.candidates("", "https://unrelated.example/abcdefghijk", 128).isEmpty());
    assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
        ArtworkQuality.normalizeSource("//i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"));
    assertEquals("", ArtworkQuality.normalizeSource("http://unrelated.example/picture.jpg"));
  }

  @Test
  public void transientFailuresRetryOnAttachedViewsAndCannotReplaceReboundArtwork() throws Exception {
    byte[] blue = image(Color.BLUE), red = image(Color.RED);
    AtomicInteger calls = new AtomicInteger();
    ArtworkLoader.configure(context, new OkHttpClient.Builder().addInterceptor(chain -> {
      if (chain.request().url().encodedPath().contains("retry"))
        return calls.incrementAndGet() == 1 ? response(chain, 503, new byte[0])
            : response(chain, 200, blue);
      return response(chain, 200, red);
    }).build());
    try (var activity = Robolectric.buildActivity(android.app.Activity.class).setup()) {
      ImageView target = view();
      activity.get().setContentView(target);
      Visuals.artwork(target, "https://covers.example/retry-" + System.nanoTime());
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (calls.get() < 2 && System.nanoTime() < end) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
        Thread.sleep(10);
      }
      await(target, Color.BLUE);
      assertEquals(2, calls.get());
      Visuals.artwork(target, "https://covers.example/red-after-retry-" + System.nanoTime());
      // Rebinding owns the target even when an earlier retry callback is still pending.
      Visuals.artwork(target, "https://covers.example/new-cover-" + System.nanoTime());
      await(target, Color.RED);
    }
  }

  @Test
  public void smallRowsUseSmallCoversAndAccentMatchesTheGreenPalette() {
    ImageView small = view();
    assertEquals(128, ArtworkLoader.size(small));
    assertEquals(
        "https://lh3.googleusercontent.com/a=w128-h128",
        ArtworkQuality.forSize("https://lh3.googleusercontent.com/a=w60-h60", 128));
    assertEquals(
        "https://i.ytimg.com/vi/id/hqdefault.jpg",
        ArtworkQuality.forSize("https://i.ytimg.com/vi/id/hqdefault.jpg", 128));
    assertEquals(0xff1ed760, Visuals.ACCENT);
  }
}
