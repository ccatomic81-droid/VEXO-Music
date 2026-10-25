package space.vexo.music;

import android.graphics.*;
import java.io.*;
import java.util.*;
import okhttp3.HttpUrl;
import org.schabi.newpipe.extractor.Image;

final class ArtworkQuality {
  static String best(List<Image> images) {
    Image best = null;
    long score = -1;
    for (Image image : images) {
      long size = (long) Math.max(0, image.getWidth()) * Math.max(0, image.getHeight());
      if (best == null || size >= score) {
        best = image;
        score = size;
      }
    }
    return best == null ? "" : best.getUrl();
  }

  static String higherResolution(String original) {
    HttpUrl url = HttpUrl.parse(original == null ? "" : original);
    if (url == null) return original;
    String host = url.host();
    if (host.equals("lh3.googleusercontent.com")
        || host.equals("yt3.googleusercontent.com")
        || host.equals("yt3.ggpht.com"))
      return original.replaceFirst("=w\\d+-h\\d+", "=w1024-h1024").replaceFirst("=s\\d+", "=s1024");
    if (host.equals("i.ytimg.com") || host.equals("img.youtube.com"))
      return original.replaceFirst(
          "/(default|mqdefault|hqdefault|sddefault)\\.(jpg|webp)(?=\\?|$)", "/maxresdefault.$2");
    return original;
  }

  static Bitmap decode(InputStream input) throws IOException {
    if (input == null) return null;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[16384];
    int count;
    while ((count = input.read(buffer)) != -1) {
      if (out.size() + count > 8 * 1024 * 1024) throw new IOException("Artwork is too large");
      out.write(buffer, 0, count);
    }
    byte[] bytes = out.toByteArray();
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    options.inSampleSize = 1;
    while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= 1024)
      options.inSampleSize *= 2;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap != null && Math.max(bitmap.getWidth(), bitmap.getHeight()) > 1024) {
      float scale = 1024f / Math.max(bitmap.getWidth(), bitmap.getHeight());
      Bitmap resized =
          Bitmap.createScaledBitmap(
              bitmap,
              Math.max(1, Math.round(bitmap.getWidth() * scale)),
              Math.max(1, Math.round(bitmap.getHeight() * scale)),
              true);
      if (resized != bitmap) bitmap.recycle();
      bitmap = resized;
    }
    return bitmap;
  }
}
