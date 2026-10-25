package space.vexo.music;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Opaque VEXO surfaces. The helper name is retained for existing view integrations. */
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
    p.setColor(accent ? 0xff253526 : Visuals.PANEL);
    c.drawRoundRect(b, radius, radius, p);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f);
    p.setColor(accent ? 0xff40543a : 0xff343d4c);
    c.drawRoundRect(b, radius, radius, p);
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
    android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
    d.setColor(Visuals.PANEL);
    d.setCornerRadius(24 * context.getResources().getDisplayMetrics().density);
    return d;
  }

  static Drawable backdrop() {
    return new android.graphics.drawable.ColorDrawable(Visuals.BG);
  }
}
