package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;
import java.util.Locale;

/** Real byte progress; the separate checking phase never claims installation is complete. */
final class UpdateProgressPanel extends LinearLayout {
  final TextView status, amount, percent;
  final ProgressBar bar, spinner;
  final TextView cancel;

  UpdateProgressPanel(Context context, String version, Runnable cancel) {
    super(context);
    setOrientation(VERTICAL);
    setBackgroundColor(BG);
    MusicSurface surface = new MusicSurface(context);
    LinearLayout body = surface.body;
    addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
    body.setPadding(surface.dp(24), surface.dp(20), surface.dp(24), surface.dp(24));
    body.addView(surface.text("VEXO UPDATE", 11, LIME, true));
    surface.gap(body, 16);
    body.addView(surface.text("A better listening experience.", 24, TEXT, true));
    surface.gap(body, 12);
    body.addView(surface.text("VEXO Music " + version, 15, MUTED, false));
    surface.gap(body, 24);
    spinner = new ProgressBar(context);
    spinner.setIndeterminateTintList(ColorStateList.valueOf(LIME));
    spinner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    body.addView(spinner, new LinearLayout.LayoutParams(surface.dp(44), surface.dp(44)));
    surface.gap(body, 24);
    status = surface.text("Connecting securely…", 20, TEXT, true);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    body.addView(status);
    surface.gap(body, 16);
    percent = surface.text("Preparing download", 14, LIME, true);
    body.addView(percent);
    surface.gap(body, 12);
    bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100);
    bar.setProgressTintList(ColorStateList.valueOf(LIME));
    bar.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
    body.addView(bar, new LinearLayout.LayoutParams(-1, surface.dp(12)));
    surface.gap(body, 12);
    amount = surface.text("Waiting for the download to start", 14, MUTED, false);
    body.addView(amount);
    surface.gap(body, 20);
    TextView note =
        surface.text(
            "Your music and settings stay safe. Android will ask before installing.",
            14,
            MUTED,
            false);
    note.setLineSpacing(surface.dp(4), 1);
    body.addView(note);
    surface.gap(body, 20);
    this.cancel = surface.action("Cancel download", false, cancel);
    LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(-1, -2);
    footer.setMargins(surface.dp(24), surface.dp(12), surface.dp(24), surface.dp(20));
    addView(this.cancel, footer);
  }

  void progress(long downloaded, long total, boolean verifying) {
    int value = total > 0 ? (int) Math.min(100, Math.max(0, downloaded) * 100 / total) : 0;
    status.setText(verifying ? "Verifying update…" : "Downloading update…");
    percent.setText(verifying ? "Checking integrity and signing identity" : value + "% downloaded");
    amount.setText(
        String.format(
            Locale.getDefault(), "%.1f MB of %.1f MB", downloaded / 1048576.0, total / 1048576.0));
    bar.setProgress(value, true);
    bar.setContentDescription("Update download " + value + " percent");
  }
}
