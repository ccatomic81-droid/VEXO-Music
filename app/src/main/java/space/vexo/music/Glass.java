package space.vexo.music;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Lightweight glass material: refraction-style highlights, translucent fill, no per-frame blur. */
final class Glass extends Drawable {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float radius;
  private final boolean accent;

  Glass(float radius, boolean accent) {
    this.radius = radius;
    this.accent = accent;
  }

  @Override
  public void draw(Canvas c) {
    RectF b = new RectF(getBounds());
    b.inset(1, 1);
    p.setStyle(Paint.Style.FILL);
    p.setShader(
        new LinearGradient(
            b.left,
            b.top,
            b.right,
            b.bottom,
            new int[] {accent ? 0x454e6647 : 0x503d475d, 0xb91b2333, 0xd2151c2a},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(b, radius, radius, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f);
    p.setShader(
        new LinearGradient(
            b.left,
            b.top,
            b.right,
            b.bottom,
            new int[] {0x77ffffff, 0x154f7c8d, 0x395f7288},
            null,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(b, radius, radius, p);
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
  }

  @Override
  public void setAlpha(int value) {
    p.setAlpha(value);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    p.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public void getOutline(Outline out) {
    out.setRoundRect(getBounds(), radius);
  }

  static void apply(View view, float radius, boolean accent) {
    float d = view.getResources().getDisplayMetrics().density;
    view.setBackground(new Glass(radius * d, accent));
    view.setElevation(2 * d);
  }

  static Drawable dialogBackdrop(Context context) {
    android.graphics.drawable.GradientDrawable d =
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[] {0xdf203a3b, 0xee131c2c, 0xe729243d});
    d.setCornerRadius(24 * context.getResources().getDisplayMetrics().density);
    return d;
  }

  static Drawable backdrop() {
    android.graphics.drawable.GradientDrawable d =
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[] {0xff172b2d, 0xff101522, 0xff211b31});
    return d;
  }
}
