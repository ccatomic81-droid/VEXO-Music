package space.vexo.music;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Window;
import androidx.core.view.WindowCompat;

/** One saved choice for the activity, feature panels and platform widgets. */
final class AppAppearance {
  static final String[] VALUES = {"system", "dark", "light"};
  static final String[] LABELS = {"Follow device", "Dark", "Light"};

  static String choice(Context c) {
    String value = c.getSharedPreferences("settings", 0).getString("appearance", "dark");
    for (String allowed : VALUES) if (allowed.equals(value)) return value;
    return "dark";
  }

  static boolean light(Context c) {
    String value = choice(c);
    return "light".equals(value)
        || "system".equals(value)
            && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
  }

  static void save(Context c, int index) {
    c.getSharedPreferences("settings", 0).edit().putString("appearance", VALUES[index]).apply();
  }

  static String label(Context c) {
    for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(choice(c))) return LABELS[i];
    return LABELS[0];
  }

  static void bars(Window window) {
    if (window == null) return;
    var controller = WindowCompat.getInsetsController(window, window.getDecorView());
    controller.setAppearanceLightStatusBars(Visuals.light);
    controller.setAppearanceLightNavigationBars(Visuals.light);
  }
}
