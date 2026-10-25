package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;

/** Saved explicitly by the dialog's Save action; cancelling never changes playback settings. */
public final class CrossfadeSettingsPanel extends ScrollView {
  private final SeekBar slider;
  private final TextView value;

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String label, int size, int color) {
    TextView t = new TextView(getContext());
    t.setText(label);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  public CrossfadeSettingsPanel(Context c, int seconds) {
    super(c);
    LinearLayout body = new LinearLayout(c);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), dp(8), dp(20), dp(20));
    addView(body);
    TextView detail =
        text(
            "Fade out the ending song while the next song fades in. Choose Off or 1–12 seconds.",
            14,
            MUTED);
    body.addView(detail);
    value = text("", 24, LIME);
    value.setGravity(Gravity.CENTER);
    value.setPadding(0, dp(20), 0, dp(8));
    body.addView(value, new LinearLayout.LayoutParams(-1, -2));
    slider = new SeekBar(c);
    slider.setMax(12);
    slider.setProgress(CrossfadeEngine.clampSeconds(seconds));
    slider.setContentDescription("Crossfade duration in seconds, zero is off");
    slider.setMinimumHeight(dp(48));
    slider.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
    slider.setThumbTintList(android.content.res.ColorStateList.valueOf(LIME));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}

          public void onProgressChanged(SeekBar s, int n, boolean user) {
            refresh();
          }
        });
    body.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
    LinearLayout ends = new LinearLayout(c);
    TextView off = text("Off", 12, MUTED), max = text("12 seconds", 12, MUTED);
    ends.addView(off, new LinearLayout.LayoutParams(0, -2, 1));
    ends.addView(max);
    body.addView(ends);
    TextView note =
        text(
            "Applies at natural song endings. If the next song is not ready, normal playback"
                + " continues. Short songs use a shorter fade. Changes apply to the next"
                + " transition.",
            12,
            MUTED);
    note.setPadding(0, dp(18), 0, 0);
    body.addView(note);
    refresh();
  }

  private void refresh() {
    int n = seconds();
    value.setText(n == 0 ? "Off" : n + (n == 1 ? " second" : " seconds"));
  }

  public int seconds() {
    return CrossfadeEngine.clampSeconds(slider.getProgress());
  }
}
