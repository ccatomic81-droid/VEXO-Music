package space.vexo.music;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Small, bounded palette sample; no blur or background animation on the playback thread. */
final class ArtworkPalette {
  static int lightShade(int shade) {
    return Color.rgb(
        224 + Color.red(shade) / 10, 224 + Color.green(shade) / 10, 224 + Color.blue(shade) / 10);
  }

  static int shade(Bitmap image) {
    long red = 0, green = 0, blue = 0;
    int samples = 0;
    if (image == null || image.isRecycled() || image.getConfig() == Bitmap.Config.HARDWARE)
      return 0xff263c33;
    for (int y = 1; y < 8; y++)
      for (int x = 1; x < 8; x++) {
        int pixel = image.getPixel(x * (image.getWidth() - 1) / 8, y * (image.getHeight() - 1) / 8);
        if (Color.alpha(pixel) < 200
            || Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel))) < 35)
          continue;
        red += Color.red(pixel);
        green += Color.green(pixel);
        blue += Color.blue(pixel);
        samples++;
      }
    if (samples == 0) return 0xff263c33;
    // A deliberately dark tint retains white-text contrast for even bright album covers.
    return Color.rgb(
        18 + (int) (red / samples * .24),
        18 + (int) (green / samples * .24),
        18 + (int) (blue / samples * .24));
  }
}
