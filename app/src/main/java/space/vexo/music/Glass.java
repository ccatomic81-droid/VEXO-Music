package space.vexo.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

final class Glass extends Drawable {
  private final boolean accent;
  private final Paint p = new Paint(1);
  private final float radius;

  @Override // android.graphics.drawable.Drawable
  public int getOpacity() {
    return android.graphics.PixelFormat.TRANSLUCENT;
  }

  Glass(float f, boolean z) {
    this.radius = f;
    this.accent = z;
  }

  @Override // android.graphics.drawable.Drawable
  public void draw(Canvas canvas) {
    RectF rectF = new RectF(getBounds());
    rectF.inset(1.0f, 1.0f);
    this.p.setStyle(Paint.Style.FILL);
    this.p.setShader(
        new LinearGradient(
            rectF.left,
            rectF.top,
            rectF.right,
            rectF.bottom,
            this.accent ? new int[] {0xff453552, 0xff2d253c} : new int[] {-231198139, -266594010},
            (float[]) null,
            Shader.TileMode.CLAMP));
    float f = this.radius;
    canvas.drawRoundRect(rectF, f, f, this.p);
    this.p.setShader(null);
    this.p.setStyle(Paint.Style.STROKE);
    this.p.setStrokeWidth(1.2f);
    this.p.setColor(this.accent ? 0xff8a6fad : -11450785);
    float f2 = this.radius;
    canvas.drawRoundRect(rectF, f2, f2, this.p);
    this.p.setStyle(Paint.Style.FILL);
  }

  @Override // android.graphics.drawable.Drawable
  public void setAlpha(int i) {
    this.p.setAlpha(i);
    invalidateSelf();
  }

  @Override // android.graphics.drawable.Drawable
  public void setColorFilter(ColorFilter colorFilter) {
    this.p.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override // android.graphics.drawable.Drawable
  public void getOutline(Outline outline) {
    outline.setRoundRect(getBounds(), this.radius);
  }

  static void apply(View view, float f, boolean z) {
    float f2 = view.getResources().getDisplayMetrics().density;
    view.setBackground(new Glass(f * f2, z));
    view.setElevation(f2 * 2.0f);
  }

  static Drawable dialogBackdrop(Context context) {
    GradientDrawable gradientDrawable = new GradientDrawable();
    gradientDrawable.setColor(Visuals.PANEL);
    gradientDrawable.setCornerRadius(context.getResources().getDisplayMetrics().density * 24.0f);
    return gradientDrawable;
  }

  static Drawable backdrop() {
    return new GradientDrawable(
        GradientDrawable.Orientation.TL_BR, new int[] {-14412242, Visuals.BG, Visuals.BG});
  }
}
