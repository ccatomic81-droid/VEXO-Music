package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.widget.*;
import java.util.function.Consumer;

public final class RadioTuner extends LinearLayout {
  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  public RadioTuner(Context c, RadioPolicy.Mode selected, Consumer<RadioPolicy.Mode> choose) {
    super(c);
    setOrientation(VERTICAL);
    setPadding(dp(20), dp(8), dp(20), dp(16));
    TextView intro = new TextView(c);
    intro.setText(
        "Keep the mood. Choose how much to explore.\nTuning applies to the next radio refill.");
    intro.setTextColor(MUTED);
    intro.setTextSize(14);
    intro.setPadding(0, 0, 0, dp(14));
    addView(intro);
    RadioGroup group = new RadioGroup(c);
    group.setOrientation(VERTICAL);
    for (RadioPolicy.Mode mode : RadioPolicy.Mode.values()) {
      RadioButton button = new RadioButton(c);
      button.setId(android.view.View.generateViewId());
      button.setText(mode.label + "\n" + mode.detail);
      button.setTextSize(14);
      button.setTextColor(TEXT);
      button.setMinHeight(dp(78));
      button.setPadding(dp(4), dp(12), dp(4), dp(12));
      group.addView(button, new LayoutParams(-1, -2));
      if (mode == selected) button.setChecked(true);
      button.setOnCheckedChangeListener(
          (b, checked) -> {
            if (checked) choose.accept(mode);
          });
    }
    addView(group);
    TextView privacy = new TextView(c);
    privacy.setText(
        "Uses related results, your favourites and listening history on this device. No"
            + " music-account login needed.");
    privacy.setTextColor(MUTED);
    privacy.setTextSize(12);
    privacy.setPadding(0, dp(16), 0, dp(4));
    addView(privacy);
  }
}
