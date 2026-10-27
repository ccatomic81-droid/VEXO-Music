package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import androidx.core.view.*;

/** Full-height feature panels with one safe-area owner and an optional fixed close header. */
final class PanelWindow {
  static void content(Dialog dialog, View panel) {
    content(dialog, panel, null);
  }

  static void content(Dialog dialog, View panel, String title) {
    LinearLayout root = new LinearLayout(dialog.getContext());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);
    if (title != null) {
      float density = root.getResources().getDisplayMetrics().density;
      LinearLayout header = new LinearLayout(dialog.getContext());
      header.setGravity(Gravity.CENTER_VERTICAL);
      header.setPadding(
          Math.round(20 * density),
          Math.round(6 * density),
          Math.round(8 * density),
          Math.round(6 * density));
      TextView label = new TextView(dialog.getContext());
      label.setText(title);
      label.setTextSize(22);
      label.setTextColor(TEXT);
      label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      label.setMaxLines(2);
      header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      Icon close = new Icon(dialog.getContext(), "close", TEXT);
      close.setContentDescription("Close " + title);
      close.setOnClickListener(v -> dialog.dismiss());
      int target = Math.round(48 * density);
      header.addView(close, new LinearLayout.LayoutParams(target, target));
      root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }
    panel.setBackgroundColor(BG);
    panel.setClipToOutline(false);
    root.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
          view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard));
          return WindowInsetsCompat.CONSUMED;
        });
    dialog.setContentView(root);
    configure(dialog);
    ViewCompat.requestApplyInsets(root);
  }

  static void expand(Dialog dialog) {
    VexoNotice.host(dialog);
    configure(dialog);
    Window window = dialog.getWindow();
    if (window != null) {
      window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      ViewCompat.requestApplyInsets(window.getDecorView());
    }
  }

  private static void configure(Dialog dialog) {
    Window window = dialog.getWindow();
    if (window == null) return;
    WindowCompat.setDecorFitsSystemWindows(window, false);
    window.setBackgroundDrawable(new ColorDrawable(BG));
    window.getDecorView().setPadding(0, 0, 0, 0);
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    window.setStatusBarColor(Color.TRANSPARENT);
    window.setNavigationBarColor(Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(window, window.getDecorView());
    controller.setAppearanceLightStatusBars(false);
    controller.setAppearanceLightNavigationBars(false);
  }
}
