package space.vexo.music;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/** A single queue over the existing player: half height, full height, or back to compact playback. */
final class QueueSheet extends FrameLayout {
  private final QueuePanel panel;
  private final Runnable close;
  private boolean full, moving;
  private float down;
  private int initial;
  private int lastViewport;
  private android.animation.ValueAnimator settling;
  QueueSheet(Context context, QueuePanel panel, Runnable close) {
    super(context);
    this.panel = panel;
    this.close = close;
    setBackgroundColor(0x44000000);
    panel.setBackground(Visuals.round(Visuals.DIALOG, dp(24)));
    panel.setClipToOutline(true);
    addView(panel, new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM));
    setOnClickListener(v -> close.run());
    panel.setOnClickListener(v -> {});
    panel.handle.setOnClickListener(v -> snap(!full));
    panel.handle.setOnTouchListener((view, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          if (settling != null) settling.cancel();
          down = event.getRawY(); initial = panel.getHeight(); moving = false; return true;
        case MotionEvent.ACTION_MOVE:
          float delta = down - event.getRawY();
          if (Math.abs(delta) > ViewConfiguration.get(context).getScaledTouchSlop()) moving = true;
          if (moving) resize(Math.max(dp(180), Math.min(available(), initial + Math.round(delta))));
          return true;
        case MotionEvent.ACTION_UP:
          if (!moving) { view.performClick(); return true; }
          float distance = down - event.getRawY();
          if (panel.getHeight() < available() * .42f || !full && distance < -dp(70)) close.run();
          else snap(distance > dp(40) || distance >= -dp(40) && panel.getHeight() > available() * .82f);
          return true;
        case MotionEvent.ACTION_CANCEL:
          snap(full); return true;
        default: return false;
      }
    });
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(
          androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
      setPadding(bars.left, bars.top, bars.right, bars.bottom);
      requestLayout();
      return insets;
    });
    panel.expanded(false);
  }
  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
  private int available() { return Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom()); }
  private void resize(int height) {
    FrameLayout.LayoutParams size = (FrameLayout.LayoutParams) panel.getLayoutParams();
    if (size.height != height) { size.height = height; panel.setLayoutParams(size); }
  }
  private void snap(boolean expanded) {
    full = expanded;
    panel.expanded(full);
    int target = full ? available() : Math.min(available(), Math.max(dp(340), Math.round(available() * .68f)));
    if (settling != null) settling.cancel();
    if (!isAttachedToWindow() || !android.animation.ValueAnimator.areAnimatorsEnabled()) { resize(target); return; }
    settling = android.animation.ValueAnimator.ofInt(panel.getHeight(), target);
    settling.setDuration(180);
    settling.setInterpolator(new android.view.animation.DecelerateInterpolator());
    settling.addUpdateListener(frame -> resize((int) frame.getAnimatedValue()));
    settling.start();
  }
  @Override protected void onMeasure(int width, int height) {
    int viewport = Math.max(1, MeasureSpec.getSize(height) - getPaddingTop() - getPaddingBottom());
    if (viewport != lastViewport) {
      if (settling != null) settling.cancel();
      lastViewport = viewport;
      panel.getLayoutParams().height = full ? viewport
          : Math.min(viewport, Math.max(dp(340), Math.round(viewport * .68f)));
    }
    super.onMeasure(width, height);
  }
  @Override protected void onDetachedFromWindow() {
    if (settling != null) settling.cancel();
    super.onDetachedFromWindow();
  }
}
