package space.vexo.music;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small current-song marker; frames stop when detached, hidden, or paused. */
final class PlayingIndicator extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean playing;
  PlayingIndicator(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }
  void playing(boolean value) { playing = value; invalidate(); }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    paint.setColor(Visuals.TEXT);
    float unit = getWidth() / 7f;
    double phase = android.os.SystemClock.uptimeMillis() / 180.0;
    for (int i = 0; i < 3; i++) {
      float height = getHeight() * (playing ? .35f + .55f * (float) Math.abs(Math.sin(phase + i * 1.4)) : .45f);
      canvas.drawRoundRect(unit * (i * 2 + .5f), getHeight() - height,
          unit * (i * 2 + 1.5f), getHeight(), unit / 2, unit / 2, paint);
    }
    if (playing && isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE)
      postInvalidateDelayed(100);
  }
}
