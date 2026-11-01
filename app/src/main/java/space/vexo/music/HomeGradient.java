package space.vexo.music;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Three soft colour pools, built only when the viewport changes. No bitmap blur or animation. */
final class HomeGradient extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final boolean light;
  private final float density;
  private Shader[] pools = new Shader[0];

  HomeGradient(boolean light, float density) {
    this.light = light;
    this.density = density;
  }

  @Override protected void onBoundsChange(Rect bounds) {
    float w = Math.max(1, bounds.width());
    float h = Math.max(1, Math.min(bounds.height(), 660 * density));
    pools = new Shader[] {
      pool(w * .12f, h * .02f, w * .92f, light ? 0x55d999a9 : 0x995d394e),
      pool(w * .92f, h * .04f, w * .95f, light ? 0x66899fdc : 0xaa354965),
      pool(w * .12f, h * .36f, w * .92f, light ? 0x667ac8c4 : 0xaa155561)
    };
  }

  private Shader pool(float x, float y, float radius, int color) {
    return new RadialGradient(x, y, Math.max(1, radius),
        new int[] {color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP);
  }

  @Override public void draw(Canvas canvas) {
    Rect b = getBounds();
    paint.setShader(null);
    paint.setColor(light ? 0xfff7f9f7 : 0xff121212);
    canvas.drawRect(b, paint);
    for (Shader pool : pools) {
      paint.setShader(pool);
      canvas.drawRect(b, paint);
    }
    paint.setShader(null);
  }
  @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
  @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
  @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
