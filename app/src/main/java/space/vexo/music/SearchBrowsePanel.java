package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Empty-query content: short recent list, followed by a consistent two-column browse grid. */
final class SearchBrowsePanel extends LinearLayout {
  interface Listener {
    void category(MusicCategory category);

    void recent(String query);

    void remove(String query);

    void clear();

    void allRecent();
  }

  SearchBrowsePanel(Context context, List<String> recent, Listener listener) {
    super(context);
    setOrientation(VERTICAL);
    if (!recent.isEmpty()) {
      LinearLayout heading = row();
      heading.addView(text("Recent searches", 17, TEXT, true), new LayoutParams(0, -2, 1));
      TextView clear = text("Clear all", 12, MUTED, true);
      clear.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
      clear.setPadding(dp(12), 0, 0, 0);
      clear.setMinHeight(dp(48));
      clear.setMinWidth(dp(48));
      clear.setContentDescription("Clear recent searches");
      clear.setOnClickListener(v -> listener.clear());
      heading.addView(clear);
      addView(heading);
      LinearLayout entries = new LinearLayout(context);
      entries.setOrientation(VERTICAL);
      for (String term : recent.subList(0, Math.min(4, recent.size()))) {
        LinearLayout entry = row();
        Icon history = new Icon(context, "timer", MUTED);
        history.setGlyphSize(20);
        entry.addView(history, new LayoutParams(dp(44), dp(48)));
        entry.setMinimumHeight(dp(48));
        TextView query = text(term, 14, TEXT, false);
        query.setSingleLine();
        query.setEllipsize(TextUtils.TruncateAt.END);

        query.setMinHeight(dp(48));
        query.setGravity(Gravity.CENTER_VERTICAL);
        query.setPadding(dp(14), dp(8), 0, dp(8));
        query.setContentDescription("Search for " + term);
        query.setOnClickListener(v -> listener.recent(term));
        entry.addView(query, new LayoutParams(0, -2, 1));
        Icon remove = new Icon(context, "close", MUTED);
        remove.setGlyphSize(18);
        remove.setContentDescription("Remove recent search " + term);
        remove.setOnClickListener(v -> listener.remove(term));
        entry.addView(remove, new LayoutParams(dp(48), dp(48)));
        LayoutParams params = new LayoutParams(-1, -2);
        params.rightMargin = dp(8);
        entries.addView(entry, params);
      }
      if (recent.size() > 4) {
        TextView all = text("See all", 12, LIME, true);
        all.setMinHeight(dp(48));
        all.setMinWidth(dp(72));
        all.setGravity(Gravity.CENTER);
        all.setContentDescription("See all recent searches");
        all.setOnClickListener(v -> listener.allRecent());
        entries.addView(all);
      }
      addView(entries, new LayoutParams(-1, -2));
      gap(20);
    }
    addView(text("Browse all", 20, TEXT, true));
    gap(16);
    for (String group : new String[] {"Genres", "Moods & activities", "Regional"}) {
      TextView title = text(group, 12, MUTED, true);
      title.setLetterSpacing(.025f);
      addView(title);
      gap(10);
      List<MusicCategory> categories = new ArrayList<>();
      for (MusicCategory category : MusicCategory.ALL)
        if (group.equals(category.group)) categories.add(category);
      for (int i = 0; i < categories.size(); i += 2) {
        LinearLayout line = row();
        for (int column = 0; column < 2; column++) {
          int index = i + column;
          LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
          if (column == 0) params.rightMargin = dp(12);
          if (index < categories.size()) {
            MusicCategory category = categories.get(index);
            TextView card = text(category.name, 16, TEXT, true);
            card.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(16), dp(14), dp(16));
            card.setMinHeight(dp(92));
            card.setMaxLines(3);
            card.setBackground(round(category.color, dp(10)));
            card.setContentDescription("Browse " + category.name + " music");
            card.setOnClickListener(v -> listener.category(category));
            line.addView(card, params);
          } else line.addView(new Space(context), params);
        }
        addView(line, new LayoutParams(-1, -2));
        gap(12);
      }
      gap(12);
    }
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private LinearLayout row() {
    LinearLayout row = new LinearLayout(getContext());
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView view = new TextView(getContext());
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(color);
    view.setIncludeFontPadding(false);
    if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return view;
  }

  private void gap(int value) {
    addView(new View(getContext()), new LayoutParams(1, dp(value)));
  }
}
