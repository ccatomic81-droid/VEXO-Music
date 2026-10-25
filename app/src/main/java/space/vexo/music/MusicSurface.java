package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared native surfaces, sized by content so narrow screens and large text can scroll. */
class MusicSurface extends ScrollView {
  final LinearLayout body;

  MusicSurface(Context context) {
    super(context);
    setFillViewport(true);
    setVerticalScrollBarEnabled(false);
    setClipToPadding(false);
    body = column();
    body.setPadding(dp(20), dp(14), dp(20), dp(28));
    addView(body);
  }

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(getContext());
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(getContext());
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(getContext());
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  void gap(LinearLayout parent, int size) {
    View v = new View(getContext());
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(size)));
  }

  TextView action(String label, boolean primary, Runnable click) {
    TextView t = text(label, 14, primary ? BG : TEXT, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(16), dp(13), dp(16), dp(13));
    t.setMinHeight(dp(48));
    t.setBackground(round(primary ? LIME : PANEL, dp(24)));
    t.setOnClickListener(v -> click.run());
    return t;
  }

  void section(String title, String action, Runnable click) {
    LinearLayout row = row();
    TextView label = text(title, 21, TEXT, true);
    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    if (action != null) {
      TextView more = text(action, 12, LIME, true);
      more.setGravity(Gravity.CENTER);
      more.setPadding(dp(8), dp(14), 0, dp(14));
      more.setMinHeight(dp(48));
      more.setOnClickListener(v -> click.run());
      row.addView(more);
    }
    body.addView(row);
    gap(body, 12);
  }

  FrameLayout cover(String name, List<Track> songs, int size, int color) {
    FrameLayout box = new FrameLayout(getContext());
    GradientDrawable background =
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {color, 0xff202633});
    background.setCornerRadius(dp(12));
    box.setBackground(background);
    box.setClipToOutline(true);
    String art = "";
    for (Track t : songs)
      if (!t.artwork.isEmpty()) {
        art = t.artwork;
        break;
      }
    if (!art.isEmpty()) {
      ImageView image = new ImageView(getContext());
      box.addView(image, new FrameLayout.LayoutParams(-1, -1));
      artwork(image, art);
    } else {
      TextView letter =
          text(
              name.isEmpty() ? "V" : name.substring(0, 1).toUpperCase(Locale.ROOT),
              Math.max(20, size / 3),
              0xfff5f5f5,
              true);
      letter.setGravity(Gravity.CENTER);
      letter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      box.addView(letter, new FrameLayout.LayoutParams(-1, -1));
    }
    box.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return box;
  }

  LinearLayout shelf() {
    HorizontalScrollView scroll = new HorizontalScrollView(getContext());
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setClipToPadding(false);
    LinearLayout cards = row();
    cards.setGravity(Gravity.TOP);
    scroll.addView(cards);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    return cards;
  }

  int shelfCardSize() {
    int width = getResources().getConfiguration().screenWidthDp;
    if (width <= 0)
      width =
          Math.round(
              getResources().getDisplayMetrics().widthPixels
                  / getResources().getDisplayMetrics().density);
    return Math.max(124, Math.min(176, (width - 40 - 14) / 2));
  }

  void card(
      LinearLayout shelf, String name, String detail, List<Track> songs, int color, Runnable open) {
    LinearLayout card = column();
    int size = shelfCardSize();
    card.addView(cover(name, songs, size, color));
    gap(card, 10);
    TextView title = text(name, 15, TEXT, true);
    title.setMaxLines(2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    card.addView(title);
    gap(card, 5);
    TextView subtitle = text(detail, 12, MUTED, false);
    subtitle.setMaxLines(2);
    subtitle.setEllipsize(TextUtils.TruncateAt.END);
    card.addView(subtitle);
    card.setPadding(0, 0, 0, dp(6));
    card.setContentDescription("Open " + name);
    card.setOnClickListener(v -> open.run());
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), -2);
    params.setMargins(0, 0, dp(14), 0);
    shelf.addView(card, params);
  }
}
