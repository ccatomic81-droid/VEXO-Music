package space.vexo.music;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

class MusicSurface extends ScrollView {
  final LinearLayout body;

  MusicSurface(Context context) {
    super(context);
    setFillViewport(true);
    setVerticalScrollBarEnabled(false);
    setClipToPadding(false);
    LinearLayout linearLayoutColumn = column();
    this.body = linearLayoutColumn;
    linearLayoutColumn.setPadding(dp(16), dp(8), dp(16), dp(24));
    addView(linearLayoutColumn);
  }

  int dp(float f) {
    return Math.round(f * getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout linearLayout = new LinearLayout(getContext());
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    return linearLayout;
  }

  LinearLayout row() {
    LinearLayout linearLayout = new LinearLayout(getContext());
    linearLayout.setGravity(16);
    return linearLayout;
  }

  TextView text(String str, int i, int i2, boolean z) {
    TextView textView = new TextView(getContext());
    textView.setText(str);
    textView.setTextSize(i);
    textView.setTextColor(i2);
    textView.setIncludeFontPadding(false);
    if (z) {
      textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }
    return textView;
  }

  void gap(LinearLayout linearLayout, int i) {
    linearLayout.addView(new View(getContext()), new LinearLayout.LayoutParams(1, dp(i)));
  }

  TextView action(String str, boolean z, final Runnable runnable) {
    TextView textViewText = text(str, 14, z ? Visuals.BG : Visuals.TEXT, true);
    textViewText.setGravity(17);
    textViewText.setPadding(dp(16.0f), dp(13.0f), dp(16.0f), dp(13.0f));
    textViewText.setMinHeight(dp(48.0f));
    if (z) {
      textViewText.setBackground(Visuals.round(Visuals.LIME, dp(24.0f)));
    } else {
      textViewText.setBackground(Visuals.round(Visuals.CONTROL, dp(24)));
    }
    textViewText.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    return textViewText;
  }

  void section(String str, String str2, final Runnable runnable) {
    LinearLayout linearLayoutRow = row();
    linearLayoutRow.addView(
        text(str, 22, Visuals.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1.0f));
    if (str2 != null) {
      TextView textViewText = text(str2, 12, Visuals.MUTED, true);
      textViewText.setGravity(17);
      textViewText.setPadding(dp(8.0f), dp(14.0f), 0, dp(14.0f));
      textViewText.setMinHeight(dp(48.0f));
      textViewText.setOnClickListener(
          new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
              runnable.run();
            }
          });
      linearLayoutRow.addView(textViewText);
    }
    this.body.addView(linearLayoutRow);
    gap(this.body, 8);
  }

  FrameLayout cover(String str, List<Track> list, int i, int i2) {
    Track selected = null;
    String str2;
    FrameLayout frameLayout = new FrameLayout(getContext());
    GradientDrawable gradientDrawable =
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {i2, -14670285});
    gradientDrawable.setCornerRadius(dp(6));
    frameLayout.setBackground(gradientDrawable);
    frameLayout.setClipToOutline(true);
    Iterator<Track> it = list.iterator();
    while (true) {
      if (!it.hasNext()) {
        str2 = "";
        break;
      }
      Track next = it.next();
      if (!ArtworkQuality.candidates(next.artwork, next.url, i).isEmpty()) {
        selected = next;
        str2 = next.artwork;
        break;
      }
    }
    if (selected != null) {
      ImageView imageView = new ImageView(getContext());
      frameLayout.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
      Visuals.artwork(imageView, selected);
    } else {
      TextView textViewText =
          text(
              str.isEmpty() ? "V" : str.substring(0, 1).toUpperCase(Locale.ROOT),
              Math.max(20, i / 3),
              -657931,
              true);
      textViewText.setGravity(17);
      textViewText.setImportantForAccessibility(2);
      frameLayout.addView(textViewText, new FrameLayout.LayoutParams(-1, -1));
    }
    float f = i;
    frameLayout.setLayoutParams(new LinearLayout.LayoutParams(dp(f), dp(f)));
    return frameLayout;
  }

  LinearLayout shelf() {
    HorizontalScrollView horizontalScrollView =
        new HorizontalScrollView(getContext()) {
          @Override
          protected void onMeasure(int w, int h) {
            int available = MeasureSpec.getSize(w) - getPaddingLeft() - getPaddingRight();
            int side = Math.min(dp(176), Math.max(dp(96), (available - dp(12)) / 2));
            if (getChildCount() > 0 && getChildAt(0) instanceof LinearLayout) {
              LinearLayout cards = (LinearLayout) getChildAt(0);
              for (int i = 0; i < cards.getChildCount(); i++) {
                View card = cards.getChildAt(i);
                android.view.ViewGroup.LayoutParams cp = card.getLayoutParams();
                cp.width = side;
                if (card instanceof LinearLayout && ((LinearLayout) card).getChildCount() > 0) {
                  View cover = ((LinearLayout) card).getChildAt(0);
                  android.view.ViewGroup.LayoutParams art = cover.getLayoutParams();
                  art.width = side;
                  art.height = side;
                }
              }
            }
            super.onMeasure(w, h);
          }
        };
    horizontalScrollView.setHorizontalScrollBarEnabled(false);
    horizontalScrollView.setClipToPadding(false);
    LinearLayout linearLayoutRow = row();
    linearLayoutRow.setGravity(48);
    horizontalScrollView.addView(linearLayoutRow);
    this.body.addView(horizontalScrollView, new LinearLayout.LayoutParams(-1, -2));
    return linearLayoutRow;
  }

  int shelfCardSize() {
    int iRound = getResources().getConfiguration().screenWidthDp;
    if (iRound <= 0) {
      iRound =
          Math.round(
              getResources().getDisplayMetrics().widthPixels
                  / getResources().getDisplayMetrics().density);
    }
    return Math.max(124, Math.min(176, (iRound - 54) / 2));
  }

  void card(
      LinearLayout linearLayout,
      String str,
      String str2,
      List<Track> list,
      int i,
      final Runnable runnable) {
    LinearLayout linearLayoutColumn = column();
    int iShelfCardSize = shelfCardSize();
    linearLayoutColumn.addView(cover(str, list, iShelfCardSize, i));
    gap(linearLayoutColumn, 10);
    TextView textViewText = text(str, 15, Visuals.TEXT, true);
    textViewText.setMaxLines(2);
    textViewText.setEllipsize(TextUtils.TruncateAt.END);
    linearLayoutColumn.addView(textViewText);
    gap(linearLayoutColumn, 5);
    TextView textViewText2 = text(str2, 12, Visuals.MUTED, false);
    textViewText2.setMaxLines(2);
    textViewText2.setEllipsize(TextUtils.TruncateAt.END);
    linearLayoutColumn.addView(textViewText2);
    linearLayoutColumn.setPadding(0, 0, 0, dp(6.0f));
    linearLayoutColumn.setContentDescription("Open " + str);
    linearLayoutColumn.setOnClickListener(
        new View.OnClickListener() {
          @Override // android.view.View.OnClickListener
          public final void onClick(View view) {
            runnable.run();
          }
        });
    LinearLayout.LayoutParams layoutParams =
        new LinearLayout.LayoutParams(dp((float) iShelfCardSize), -2);
    layoutParams.setMargins(0, 0, dp(12), 0);
    linearLayout.addView(linearLayoutColumn, layoutParams);
  }
}
