package space.vexo.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

final class RoundedArtwork extends ImageView {
  private final Path clip;
  private final float radius;

  RoundedArtwork(Context context) {
    super(context);
    this.clip = new Path();
    this.radius = getResources().getDisplayMetrics().density * 11.0f;
    setOutlineProvider(
        new ViewOutlineProvider() {
          @Override // android.view.ViewOutlineProvider
          public void getOutline(View view, Outline outline) {
            outline.setRoundRect(
                0, 0, view.getWidth(), view.getHeight(), RoundedArtwork.this.radius);
          }
        });
    setClipToOutline(true);
  }

  @Override // android.view.View
  protected void onSizeChanged(int i, int i2, int i3, int i4) {
    super.onSizeChanged(i, i2, i3, i4);
    this.clip.reset();
    float f = this.radius;
    this.clip.addRoundRect(0.0f, 0.0f, i, i2, f, f, Path.Direction.CW);
    invalidateOutline();
  }

  @Override // android.view.View
  public void draw(Canvas canvas) {
    int iSave = canvas.save();
    canvas.clipPath(this.clip);
    super.draw(canvas);
    canvas.restoreToCount(iSave);
  }
}
