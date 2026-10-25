package space.vexo.music;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Visuals {
  public static final int BG = Color.rgb(13, 16, 23),
      PANEL = Color.rgb(25, 29, 39),
      TEXT = Color.rgb(246, 247, 242),
      MUTED = Color.rgb(151, 160, 177),
      LIME = Color.rgb(212, 252, 121),
      PURPLE = Color.rgb(157, 141, 241);

  public static GradientDrawable round(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  private static final LruCache<String, Bitmap> cache =
      new LruCache<String, Bitmap>(
          (int)
              Math.min(
                  24L * 1024 * 1024,
                  Math.max(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16))) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
          return b.getByteCount();
        }
      };
  private static final ThreadPoolExecutor images =
      new ThreadPoolExecutor(
          2,
          2,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(48),
          new ThreadPoolExecutor.DiscardOldestPolicy());
  private static final AtomicInteger imageGeneration = new AtomicInteger();

  static void clearArtwork() {
    synchronized (cache) {
      imageGeneration.incrementAndGet();
      images.getQueue().clear();
      cache.evictAll();
    }
  }

  static int pendingArtwork() {
    return images.getQueue().size();
  }

  public static void artwork(ImageView view, String url) {
    view.setTag(url);
    view.setImageDrawable(null);
    view.setBackground(new CoverPlaceholder());
    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
    view.setClipToOutline(true);
    if (url == null || url.isEmpty()) return;
    Bitmap hit = cache.get(url);
    if (hit != null) {
      view.setImageBitmap(hit);
      return;
    }
    Context context = view.getContext().getApplicationContext();
    WeakReference<ImageView> target = new WeakReference<>(view);
    int generation = imageGeneration.get();
    images.execute(
        () -> {
          try {
            ImageView current = target.get();
            if (current == null
                || !url.equals(current.getTag())
                || generation != imageGeneration.get()) return;
            current = null;
            Bitmap bitmap;
            if (url.startsWith("content://")) {
              try (java.io.InputStream in =
                  context.getContentResolver().openInputStream(android.net.Uri.parse(url))) {
                bitmap = ArtworkQuality.decode(in);
              }
            } else {
              if (!url.startsWith("https://")) return;
              bitmap = null;
              String better = ArtworkQuality.higherResolution(url);
              for (String candidate :
                  better.equals(url) ? new String[] {url} : new String[] {better, url}) {
                try (okhttp3.Response r =
                    OnlineMusic.HTTP
                        .newCall(new okhttp3.Request.Builder().url(candidate).build())
                        .execute()) {
                  if (!r.isSuccessful() || r.body() == null) continue;
                  try (java.io.InputStream in = r.body().byteStream()) {
                    bitmap = ArtworkQuality.decode(in);
                  }
                  if (bitmap != null && !candidate.equals(url) && bitmap.getWidth() <= 120) {
                    bitmap.recycle();
                    bitmap = null;
                    continue;
                  }
                  if (bitmap != null) break;
                } catch (Exception ignored) {
                }
              }
            }
            if (bitmap == null) return;
            synchronized (cache) {
              if (generation != imageGeneration.get()) {
                bitmap.recycle();
                return;
              }
              cache.put(url, bitmap);
            }
            final Bitmap loaded = bitmap;
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(
                    () -> {
                      ImageView destination = target.get();
                      if (destination != null
                          && generation == imageGeneration.get()
                          && url.equals(destination.getTag())) destination.setImageBitmap(loaded);
                    });
          } catch (OutOfMemoryError lowMemory) {
            clearArtwork();
            AppDiagnostics.record(context, "artwork_memory", lowMemory);
          } catch (Exception ignored) {
          }
        });
  }

  private static final class CoverPlaceholder extends android.graphics.drawable.Drawable {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas canvas) {
      android.graphics.Rect b = getBounds();
      float w = b.width(), h = b.height(), r = Math.min(w, h);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(PANEL);
      canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, r * .12f, r * .12f, paint);
      float x = b.left + w / 2, y = b.top + h / 2;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Math.max(1, r * .012f));
      paint.setColor(0xff4d5266);
      canvas.drawCircle(x, y, r * .32f, paint);
      canvas.drawCircle(x, y, r * .24f, paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(PURPLE);
      canvas.drawCircle(x, y, r * .10f, paint);
      paint.setColor(BG);
      canvas.drawCircle(x, y, r * .025f, paint);
    }

    @Override
    public void setAlpha(int alpha) {
      paint.setAlpha(alpha);
      invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
      paint.setColorFilter(filter);
      invalidateSelf();
    }

    @Override
    public int getOpacity() {
      return PixelFormat.OPAQUE;
    }
  }

  public static final class Icon extends View {
    String name;
    int color;
    private float glyphDp = 24;

    public void setGlyphSize(float dp) {
      glyphDp = dp;
      invalidate();
    }

    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Icon(Context c, String n, int tint) {
      super(c);
      name = n;
      color = tint;
      setContentDescription(description(n));
      int target = Math.round(48 * getResources().getDisplayMetrics().density);
      setMinimumWidth(target);
      setMinimumHeight(target);
    }

    public void set(String n, int tint) {
      name = n;
      color = tint;
      setContentDescription(description(n));
      invalidate();
    }

    private static String description(String name) {
      switch (name) {
        case "play":
          return "Play";
        case "pause":
          return "Pause";
        case "previous":
          return "Previous song";
        case "next":
          return "Next song";
        case "queue":
          return "Up next";
        case "timer":
          return "Sleep timer";
        case "down":
          return "Close player";
        case "more":
          return "More options";
        case "heart":
          return "Save to favourites";
        case "liked":
          return "Remove from favourites";
        case "repeat_one":
          return "Repeat one song";
        case "repeat":
          return "Repeat";
        case "shuffle":
          return "Shuffle";
        default:
          return name;
      }
    }

    @Override
    protected void onDraw(Canvas original) {
      super.onDraw(original);
      Canvas c = original;
      c.save();
      float size =
          Math.min(
              Math.min(getWidth(), getHeight()),
              glyphDp * getResources().getDisplayMetrics().density);
      c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
      c.scale(size / 24, size / 24);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.9f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      Path a = new Path();
      switch (name) {
        case "download":
          c.drawLine(12, 4, 12, 16, p);
          a.moveTo(7, 11);
          a.lineTo(12, 16);
          a.lineTo(17, 11);
          c.drawPath(a, p);
          a.reset();
          a.moveTo(5, 17);
          a.lineTo(5, 20);
          a.lineTo(19, 20);
          a.lineTo(19, 17);
          c.drawPath(a, p);
          break;
        case "refresh":
          c.drawArc(5, 5, 19, 19, 45, 280, false, p);
          a.moveTo(19, 4);
          a.lineTo(19, 10);
          a.lineTo(13, 10);
          c.drawPath(a, p);
          break;
        case "add":
          c.drawLine(5, 12, 19, 12, p);
          c.drawLine(12, 5, 12, 19, p);
          break;
        case "play":
          p.setStyle(Paint.Style.FILL);
          a.moveTo(8, 5);
          a.lineTo(19, 12);
          a.lineTo(8, 19);
          a.close();
          c.drawPath(a, p);
          break;
        case "pause":
          p.setStyle(Paint.Style.FILL);
          c.drawRoundRect(7, 5, 10, 19, 1.3f, 1.3f, p);
          c.drawRoundRect(14, 5, 17, 19, 1.3f, 1.3f, p);
          break;
        case "next":
          a.moveTo(4, 4);
          a.lineTo(16, 12);
          a.lineTo(4, 20);
          a.close();
          p.setStyle(Paint.Style.FILL);
          c.drawPath(a, p);
          p.setStrokeWidth(3);
          c.drawLine(20, 4, 20, 20, p);
          break;
        case "previous":
          a.moveTo(20, 4);
          a.lineTo(8, 12);
          a.lineTo(20, 20);
          a.close();
          p.setStyle(Paint.Style.FILL);
          c.drawPath(a, p);
          p.setStrokeWidth(3);
          c.drawLine(4, 4, 4, 20, p);
          break;
        case "home":
          a.moveTo(2, 11);
          a.lineTo(12, 2);
          a.lineTo(22, 11);
          a.moveTo(5, 9);
          a.lineTo(5, 22);
          a.lineTo(10, 22);
          a.lineTo(10, 15);
          a.lineTo(14, 15);
          a.lineTo(14, 22);
          a.lineTo(19, 22);
          a.lineTo(19, 9);
          c.drawPath(a, p);
          break;
        case "search":
          c.drawCircle(10, 10, 7, p);
          c.drawLine(15, 15, 22, 22, p);
          break;
        case "library":
          c.drawRoundRect(2, 4, 7, 22, 1, 1, p);
          c.drawLine(12, 3, 12, 22, p);
          c.drawLine(17, 4, 22, 21, p);
          break;
        case "settings":
          c.drawCircle(12, 12, 4, p);
          for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45, 12, 12);
            c.drawLine(12, 1, 12, 4, p);
            c.restore();
          }
          c.drawCircle(12, 12, 9, p);
          break;
        case "heart":
        case "liked":
          a.moveTo(12, 21);
          a.lineTo(10.55f, 19.68f);
          a.cubicTo(5.4f, 15.02f, 2, 11.94f, 2, 8.16f);
          a.cubicTo(2, 5.08f, 4.42f, 2.67f, 7.5f, 2.67f);
          a.cubicTo(9.24f, 2.67f, 10.91f, 3.48f, 12, 4.75f);
          a.cubicTo(13.09f, 3.48f, 14.76f, 2.67f, 16.5f, 2.67f);
          a.cubicTo(19.58f, 2.67f, 22, 5.08f, 22, 8.16f);
          a.cubicTo(22, 11.94f, 18.6f, 15.02f, 13.45f, 19.68f);
          a.close();
          if (name.equals("liked")) p.setStyle(Paint.Style.FILL);
          c.drawPath(a, p);
          break;
        case "more":
          p.setStyle(Paint.Style.FILL);
          for (int i = 0; i < 3; i++) c.drawCircle(12, 5 + i * 7, 1.5f, p);
          break;
        case "close":
          c.drawLine(5, 5, 19, 19, p);
          c.drawLine(19, 5, 5, 19, p);
          break;
        case "back":
          a.moveTo(16, 3);
          a.lineTo(7, 12);
          a.lineTo(16, 21);
          c.drawPath(a, p);
          break;
        case "down":
          a.moveTo(4, 8);
          a.lineTo(12, 16);
          a.lineTo(20, 8);
          c.drawPath(a, p);
          break;
        case "plus":
          c.drawLine(12, 3, 12, 21, p);
          c.drawLine(3, 12, 21, 12, p);
          break;
        case "queue":
          c.drawLine(3, 5, 21, 5, p);
          c.drawLine(3, 10, 14, 10, p);
          c.drawLine(3, 15, 11, 15, p);
          a.moveTo(16, 13);
          a.lineTo(22, 17);
          a.lineTo(16, 21);
          a.close();
          p.setStyle(Paint.Style.FILL);
          c.drawPath(a, p);
          break;
        case "radio":
          c.drawCircle(12, 12, 2, p);
          c.drawArc(6, 6, 18, 18, -50, 100, false, p);
          c.drawArc(6, 6, 18, 18, 130, 100, false, p);
          c.drawArc(2, 2, 22, 22, -50, 100, false, p);
          c.drawArc(2, 2, 22, 22, 130, 100, false, p);
          break;
        case "tune":
          c.drawLine(3, 6, 21, 6, p);
          c.drawLine(3, 12, 21, 12, p);
          c.drawLine(3, 18, 21, 18, p);
          p.setStyle(Paint.Style.FILL);
          c.drawCircle(8, 6, 2.5f, p);
          c.drawCircle(16, 12, 2.5f, p);
          c.drawCircle(10, 18, 2.5f, p);
          break;
        case "shuffle":
          a.moveTo(2, 4);
          a.cubicTo(13, 4, 11, 20, 22, 20);
          a.moveTo(2, 20);
          a.cubicTo(13, 20, 11, 4, 22, 4);
          a.moveTo(18, 1);
          a.lineTo(22, 4);
          a.lineTo(18, 7);
          a.moveTo(18, 17);
          a.lineTo(22, 20);
          a.lineTo(18, 23);
          c.drawPath(a, p);
          break;
        case "repeat":
        case "repeat_one":
          a.moveTo(3, 10);
          a.lineTo(3, 7);
          a.lineTo(20, 7);
          a.moveTo(17, 4);
          a.lineTo(20, 7);
          a.lineTo(17, 10);
          a.moveTo(21, 14);
          a.lineTo(21, 17);
          a.lineTo(4, 17);
          a.moveTo(7, 14);
          a.lineTo(4, 17);
          a.lineTo(7, 20);
          c.drawPath(a, p);
          if (name.equals("repeat_one")) {
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(9);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("1", 12, 15, p);
            p.setTextAlign(Paint.Align.LEFT);
          }
          break;
        case "timer":
          c.drawCircle(12, 14, 7, p);
          c.drawLine(12, 14, 12, 10, p);
          c.drawLine(9, 3, 15, 3, p);
          c.drawLine(12, 3, 12, 6, p);
          c.drawLine(17, 7, 19, 5, p);
          break;
        default:
          c.drawCircle(12, 12, 9, p);
          c.drawLine(8, 12, 16, 12, p);
      }
      c.restore();
    }
  }

  public static final class Orbit extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Orbit(Context c) {
      super(c);
      setContentDescription("VEXO sound orbit artwork");
    }

    @Override
    protected void onDraw(Canvas c) {
      float w = getWidth(), h = getHeight();
      p.setShader(
          new LinearGradient(
              0,
              0,
              w,
              h,
              new int[] {0xff263431, 0xff30304d, 0xff4b4264},
              null,
              Shader.TileMode.CLAMP));
      c.drawRoundRect(0, 0, w, h, 28, 28, p);
      p.setShader(null);
      c.save();
      c.translate(w * .62f, h * .5f);
      c.rotate(-28);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.2f);
      for (int i = 0; i < 8; i++) {
        p.setColor(Color.argb(45 + i * 8, 212, 252, 121));
        float r = h * (.15f + i * .055f);
        c.drawOval(-r * 1.5f, -r, r * 1.5f, r, p);
      }
      c.restore();
      p.setStyle(Paint.Style.FILL);
      p.setColor(LIME);
      c.drawCircle(w * .62f, h * .5f, h * .095f, p);
      p.setColor(BG);
      c.drawCircle(w * .62f, h * .5f, h * .026f, p);
    }
  }
}
