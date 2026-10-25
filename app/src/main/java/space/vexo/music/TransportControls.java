package space.vexo.music;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** Five transport buttons that fit the measured width, including split-screen layouts. */
public final class TransportControls extends LinearLayout {
  public TransportControls(Context c) {
    super(c);
    setGravity(Gravity.CENTER_VERTICAL);
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
    int play = Math.max(0, Math.min(dp(80), width - dp(48) * 4));
    if (play < dp(56)) play = Math.min(dp(64), Math.max(0, width / 3));
    int side = Math.max(0, (width - play) / 4);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) child.getLayoutParams();
      p.width = i == 2 ? play : side;
      p.height = i == 2 ? play : dp(48);
      p.leftMargin = 0;
      p.rightMargin = 0;
      child.setLayoutParams(p);
    }
    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(dp(80), MeasureSpec.EXACTLY));
  }
}
