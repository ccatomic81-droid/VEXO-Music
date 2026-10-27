package space.vexo.music;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Album-style cover crops; removes only symmetric, near-black video letterboxing. */
final class CoverArtwork extends BitmapTransformation {
  private static final byte[] KEY = "space.vexo.music.CoverArtwork.v1".getBytes(StandardCharsets.UTF_8);

  static Rect contentBounds(Bitmap image) {
    int w = image.getWidth(), h = image.getHeight();
    Rect bounds = new Rect(0, 0, w, h);
    // Square album covers are already intentional compositions.
    if (w > h * 1.15f) {
      int top = border(image, true, false), bottom = border(image, true, true);
      if (paired(top, bottom, h)) { bounds.top = top; bounds.bottom = h - bottom; }
    } else if (h > w * 1.15f) {
      int left = border(image, false, false), right = border(image, false, true);
      if (paired(left, right, w)) { bounds.left = left; bounds.right = w - right; }
    }
    return bounds;
  }

  private static boolean paired(int a, int b, int dimension) {
    return a >= Math.max(2, dimension / 100) && b >= Math.max(2, dimension / 100)
        && a <= dimension / 5 && b <= dimension / 5
        && Math.abs(a - b) <= Math.max(2, dimension / 40);
  }

  private static int border(Bitmap image, boolean rows, boolean reverse) {
    int depth = rows ? image.getHeight() : image.getWidth();
    int length = rows ? image.getWidth() : image.getHeight();
    int samples = Math.min(64, length);
    for (int offset = 0; offset <= depth / 5; offset++) {
      int at = reverse ? depth - offset - 1 : offset, dark = 0;
      for (int i = 0; i < samples; i++) {
        int along = (int) ((long) i * (length - 1) / Math.max(1, samples - 1));
        int color = image.getPixel(rows ? along : at, rows ? at : along);
        if (((color >> 16) & 255) <= 20 && ((color >> 8) & 255) <= 20
            && (color & 255) <= 20) dark++;
      }
      if (dark < Math.ceil(samples * .97)) return offset;
    }
    return depth / 5 + 1; // A dark photograph is not a removable border.
  }

  static Bitmap render(Bitmap image, BitmapPool pool, int width, int height) {
    Rect bounds = contentBounds(image);
    float scale = Math.max((float) width / bounds.width(), (float) height / bounds.height());
    float w = bounds.width() * scale, h = bounds.height() * scale;
    Bitmap result = pool.get(width, height, Bitmap.Config.ARGB_8888);
    result.eraseColor(android.graphics.Color.TRANSPARENT);
    result.setHasAlpha(image.hasAlpha());
    Canvas canvas = new Canvas(result);
    canvas.drawBitmap(image, bounds, new RectF((width - w) / 2, (height - h) / 2,
        (width + w) / 2, (height + h) / 2), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
    canvas.setBitmap(null);
    return result;
  }

  @Override protected Bitmap transform(BitmapPool pool, Bitmap image, int width, int height) {
    return render(image, pool, width, height);
  }
  @Override public void updateDiskCacheKey(MessageDigest digest) { digest.update(KEY); }
  @Override public boolean equals(Object other) { return other instanceof CoverArtwork; }
  @Override public int hashCode() { return 0x56455824; }
}
