package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.function.Consumer;

/** Story-sized artist shortcuts. Artwork comes from the artist's searched songs. */
final class ArtistStoriesPanel extends LinearLayout {
  ArtistStoriesPanel(
      Context c, List<SearchArtists.Artist> artists, Consumer<String> open, Runnable search) {
    super(c);
    setOrientation(VERTICAL);
    setPadding(0, dp(12), 0, dp(20));
    TextView heading = text("Your artists", 20, TEXT);
    heading.setTypeface(null, Typeface.BOLD);
    androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
    addView(heading);
    TextView detail =
        text(
            artists.isEmpty()
                ? "Search a song to start your artist circle."
                : "From your song searches",
            12,
            MUTED);
    detail.setPadding(0, dp(6), 0, dp(14));
    addView(detail);
    HorizontalScrollView scroll = new HorizontalScrollView(c);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout row = new LinearLayout(c);
    row.setGravity(Gravity.TOP);
    scroll.addView(row);
    addView(scroll, new LayoutParams(-1, -2));
    for (SearchArtists.Artist artist : artists) {
      LinearLayout card = new LinearLayout(c);
      card.setOrientation(VERTICAL);
      card.setGravity(Gravity.CENTER_HORIZONTAL);
      card.setFocusable(true);
      card.setContentDescription("Find songs by " + artist.name);
      FrameLayout ring = new FrameLayout(c);
      GradientDrawable border =
          new GradientDrawable(
              GradientDrawable.Orientation.TL_BR,
              new int[] {LIME, light ? 0xff4ca99a : 0xff60cdb2});
      border.setShape(GradientDrawable.OVAL);
      ring.setBackground(border);
      ring.setPadding(dp(3), dp(3), dp(3), dp(3));
      FrameLayout inner = new FrameLayout(c);
      GradientDrawable circle = new GradientDrawable();
      circle.setShape(GradientDrawable.OVAL);
      circle.setColor(PANEL);
      inner.setBackground(circle);
      inner.setClipToOutline(true);
      inner.setPadding(dp(3), dp(3), dp(3), dp(3));
      ImageView image = new CircleImage(c);
      GradientDrawable imageCircle = new GradientDrawable();
      imageCircle.setShape(GradientDrawable.OVAL);
      imageCircle.setColor(PANEL);
      image.setBackground(imageCircle);
      image.setClipToOutline(true);
      image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      inner.addView(image, new FrameLayout.LayoutParams(-1, -1));
      artwork(image, artist.artwork);
      ring.addView(inner, new FrameLayout.LayoutParams(-1, -1));
      card.addView(ring, new LayoutParams(dp(76), dp(76)));
      TextView name = text(artist.name, 12, TEXT);
      name.setGravity(Gravity.CENTER);
      name.setMaxLines(2);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setPadding(dp(2), dp(9), dp(2), dp(6));
      card.addView(name, new LayoutParams(-1, -2));
      card.setOnClickListener(v -> open.accept(artist.name));
      LayoutParams params = new LayoutParams(dp(88), -2);
      params.rightMargin = dp(10);
      row.addView(card, params);
    }
    if (artists.isEmpty()) {
      TextView find = text("＋  Find artists", 14, LIME);
      find.setGravity(Gravity.CENTER);
      find.setMinHeight(dp(48));
      find.setPadding(dp(18), dp(12), dp(18), dp(12));
      find.setBackground(round(SELECTED, dp(24)));
      find.setOnClickListener(v -> search.run());
      row.addView(find);
    }
  }

  private TextView text(String value, int size, int color) {
    TextView text = new TextView(getContext());
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(color);
    return text;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  /** Explicit clipping also works for software drawing and placeholder/error artwork. */
  private static final class CircleImage extends ImageView {
    private final Path circle = new Path();

    CircleImage(Context c) {
      super(c);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      super.onSizeChanged(w, h, oldW, oldH);
      circle.reset();
      circle.addOval(0, 0, w, h, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      int save = canvas.save();
      canvas.clipPath(circle);
      super.onDraw(canvas);
      canvas.restoreToCount(save);
    }
  }
}
