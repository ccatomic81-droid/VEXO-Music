package space.vexo.music;

import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.Executor;
import org.json.*;

/** One compact editor per mix. Saving changes future picks, never starts playback. */
final class MixTuner {
  static final String[] BALANCE = {"Discover more", "Balanced", "More familiar"};
  static final String[] LANGUAGES = {"Follow my interests", "Sinhala", "Tamil", "English", "Hindi", "Korean", "Spanish"};
  static final String[] MOODS = {"Match this mix", "calm relaxing", "happy uplifting", "lofi focus instrumental", "acoustic chill", "upbeat workout", "romantic"};

  static AlertDialog show(Context c, MixCatalogue.Mix mix, Executor worker, Runnable changed) {
    JSONObject saved = new SmartStore(c).preferences().optJSONObject("mix_tune:" + mix.id);
    MixDiscovery.Tuning tuning = new MixDiscovery.Tuning(saved == null ? new JSONObject() : saved);
    MusicSurface view = new MusicSurface(c) {
      @Override protected void onMeasure(int width, int height) {
        int limit = dp(Math.max(200, Math.min(440, getResources().getConfiguration().screenHeightDp - 260)));
        if (View.MeasureSpec.getMode(height) != View.MeasureSpec.UNSPECIFIED)
          limit = Math.min(limit, View.MeasureSpec.getSize(height));
        super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
      }
    };
    view.setFillViewport(false);
    view.body.addView(view.text(mix.name, 22, Visuals.TEXT, true));
    view.gap(view.body, 10);
    view.body.addView(view.text("Shape the next picks. Your playing song and manual queue stay in place.", 13, Visuals.MUTED, false));
    Spinner balance = spinner(view, "Familiarity", BALANCE, tuning.familiarity);
    Spinner language = spinner(view, "Language", LANGUAGES, index(LANGUAGES, tuning.language));
    Spinner mood = mix.id.equals("trending") ? null : spinner(view, "Mood", MOODS, index(MOODS, tuning.mood));
    Switch repeats = new Switch(c); repeats.setText("Avoid recently played songs");
    repeats.setTextColor(Visuals.TEXT); repeats.setChecked(tuning.avoidRecent);
    repeats.setMinHeight(view.dp(48)); repeats.setPadding(0, view.dp(12), 0, view.dp(12));
    view.body.addView(repeats);
    view.gap(view.body, 8);
    view.body.addView(view.text("Listening learning can be paused or cleared in Smart → Recommendation controls.", 12, Visuals.MUTED, false));
    TextView reset = view.action("Reset mix preferences", false, () -> {});
    view.gap(view.body, 10); view.body.addView(reset);
    AlertDialog dialog = new AlertDialog.Builder(c).setTitle("Tune My Mix").setView(view)
        .setNegativeButton("Cancel", null).setPositiveButton("Save mix", null).create();
    dialog.setOnShowListener(ignored -> {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
        reset.setEnabled(false);
        try {
          JSONObject value = new JSONObject().put("familiarity", balance.getSelectedItemPosition())
              .put("language", language.getSelectedItemPosition() == 0 ? "" : LANGUAGES[language.getSelectedItemPosition()])
              .put("mood", mood == null || mood.getSelectedItemPosition() == 0 ? "" : MOODS[mood.getSelectedItemPosition()])
              .put("avoid_recent", repeats.isChecked());
          save(c, mix, value, worker, changed, dialog);
        } catch (JSONException impossible) { throw new IllegalArgumentException(impossible); }
      });
      reset.setOnClickListener(v -> { reset.setEnabled(false); save(c, mix, null, worker, changed, dialog); });
    });
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog.getWindow().setBackgroundDrawable(Visuals.round(0xff202020, view.dp(20)));
      dialog.getWindow().setLayout(Math.min(view.dp(420), c.getResources().getDisplayMetrics().widthPixels - view.dp(32)), -2);
    }
    return dialog;
  }

  private static void save(Context c, MixCatalogue.Mix mix, JSONObject value, Executor worker, Runnable changed, AlertDialog dialog) {
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    Handler main = new Handler(Looper.getMainLooper());
    worker.execute(() -> {
      try {
        new SmartStore(c).preference("mix_tune:" + mix.id, value == null ? JSONObject.NULL : value);
        new MixCatalogue(c).tuned(mix);
        main.post(() -> { dialog.dismiss(); changed.run(); });
      } catch (IllegalStateException error) {
        main.post(() -> { dialog.dismiss(); new AlertDialog.Builder(c).setMessage(error.getMessage()).setPositiveButton("Close", null).show(); });
      }
    });
  }

  private static int index(String[] values, String value) {
    for (int i = 1; i < values.length; i++) if (values[i].equals(value)) return i;
    return 0;
  }
  private static Spinner spinner(MusicSurface view, String label, String[] values, int selected) {
    view.gap(view.body, 18); view.body.addView(view.text(label, 14, Visuals.TEXT, true));
    Spinner spinner = new Spinner(view.getContext());
    ArrayAdapter<String> adapter = new ArrayAdapter<>(view.getContext(), android.R.layout.simple_spinner_dropdown_item, values);
    spinner.setAdapter(adapter); spinner.setSelection(selected); spinner.setContentDescription(label);
    view.body.addView(spinner, new LinearLayout.LayoutParams(-1, view.dp(52)));
    return spinner;
  }
}
