package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.app.Activity;
import android.content.*;
import android.view.*;
import android.widget.*;

/** One replaceable in-app notice, with the same mark and palette as VEXO dialogs. */
final class VexoNotice {
  private static final String TAG = "vexo-notice";
  private static final java.util.List<java.lang.ref.WeakReference<android.app.Dialog>> windows =
      new java.util.ArrayList<>();

  static void host(android.app.Dialog dialog) {
    windows.removeIf(ref -> ref.get() == null || ref.get() == dialog || !ref.get().isShowing());
    windows.add(new java.lang.ref.WeakReference<>(dialog));
  }

  private static Activity owner(Context context) {
    Context cursor = context;
    for (int i = 0;
        i < 12 && !(cursor instanceof Activity) && cursor instanceof ContextWrapper;
        i++) cursor = ((ContextWrapper) cursor).getBaseContext();
    return cursor instanceof Activity ? (Activity) cursor : null;
  }

  static void show(Context context, String message) {
    Activity activity = owner(context);
    if (activity == null) return;
    activity.runOnUiThread(
        () -> {
          if (activity.isFinishing() || activity.isDestroyed()) return;
          View content = activity.findViewById(android.R.id.content);
          for (java.lang.ref.WeakReference<android.app.Dialog> ref : windows) {
            android.app.Dialog dialog = ref.get();
            if (dialog != null && dialog.isShowing() && owner(dialog.getContext()) == activity)
              content = dialog.findViewById(android.R.id.content);
          }
          if (!(content instanceof FrameLayout)) return;
          FrameLayout root = (FrameLayout) content;
          View previous = root.findViewWithTag(TAG);
          if (previous != null) root.removeView(previous);
          float density = activity.getResources().getDisplayMetrics().density;
          int inset = Math.round(16 * density);
          LinearLayout notice = new LinearLayout(activity);
          notice.setTag(TAG);
          notice.setGravity(Gravity.CENTER_VERTICAL);
          notice.setPadding(
              inset, Math.round(8 * density), Math.round(4 * density), Math.round(8 * density));
          notice.setBackground(round(SELECTED, Math.round(20 * density)));
          notice.setElevation(12 * density);
          notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
          ImageView mark = new ImageView(activity);
          mark.setImageResource(R.drawable.vexo_logo_v2);
          mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
          notice.addView(
              mark,
              new LinearLayout.LayoutParams(Math.round(30 * density), Math.round(30 * density)));
          TextView text = new TextView(activity);
          text.setText(message);
          text.setTextColor(TEXT);
          text.setTextSize(14);
          text.setPadding(Math.round(10 * density), 0, Math.round(6 * density), 0);
          notice.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
          TextView close = new TextView(activity);
          close.setText("×");
          close.setTextColor(TEXT);
          close.setTextSize(24);
          close.setGravity(Gravity.CENTER);
          close.setContentDescription("Dismiss message");
          close.setOnClickListener(v -> root.removeView(notice));
          notice.addView(
              close,
              new LinearLayout.LayoutParams(Math.round(48 * density), Math.round(48 * density)));
          FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
          int top =
              activity.getWindow().getDecorView().getRootWindowInsets() == null
                  ? 0
                  : activity
                      .getWindow()
                      .getDecorView()
                      .getRootWindowInsets()
                      .getSystemWindowInsetTop();
          params.setMargins(inset, top + inset, inset, 0);
          root.addView(notice, params);
          notice.setAlpha(0);
          notice.setTranslationY(-8 * density);
          notice.animate().alpha(1).translationY(0).setDuration(180).start();
          notice.postDelayed(
              () -> {
                if (notice.getParent() == root)
                  notice
                      .animate()
                      .alpha(0)
                      .setDuration(160)
                      .withEndAction(() -> root.removeView(notice))
                      .start();
              },
              6500);
        });
  }
}
