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

/** Story-sized shortcuts with verified artist portraits or readable initials. */
final class ArtistStoriesPanel extends LinearLayout {
  private HorizontalScrollView storiesScroll;

  int scrollPosition() {
    return storiesScroll.getScrollX();
  }

  void restoreScroll(int position) {
    storiesScroll.post(() -> storiesScroll.scrollTo(position, 0));
  }

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
                ? (new SearchArtists(c).entries().isEmpty()
                    ? "Search a song to start your artist circle."
                    : "Finding your artists…")
                : "From your song searches",
            12,
            MUTED);
    detail.setPadding(0, dp(6), 0, dp(14));
    addView(detail);
    HorizontalScrollView scroll =
        new HorizontalScrollView(c) {
          private float startX, startY;

          @Override
          public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
              startX = event.getX();
              startY = event.getY();
            }
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE
                && Math.abs(event.getX() - startX) > Math.abs(event.getY() - startY)
                && Math.abs(event.getX() - startX)
                    > android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop())
              getParent().requestDisallowInterceptTouchEvent(true);
            return super.onInterceptTouchEvent(event);
          }
        };
    scroll.setSmoothScrollingEnabled(true);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    storiesScroll = scroll;
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
      ArtworkLoader.load(image, artist.artwork, new Initials(artist.name));
      ring.addView(inner, new FrameLayout.LayoutParams(-1, -1));
      card.addView(ring, new LayoutParams(dp(76), dp(76)));
      TextView name = text(artist.name, 12, TEXT);
      name.setGravity(Gravity.CENTER);
      name.setMaxLines(2);
      card.setTooltipText(artist.name);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setPadding(dp(2), dp(9), dp(2), dp(6));
      card.addView(name, new LayoutParams(-1, -2));
      card.setOnTouchListener(
          (v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
              v.animate().scaleX(.96f).scaleY(.96f).setDuration(120).start();
            else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
              v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            return false;
          });
      card.setOnClickListener(v -> open.accept(artist.name));
      LayoutParams params = new LayoutParams(dp(96), -2);
      params.rightMargin = dp(4);
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

  private static final class Initials extends android.graphics.drawable.Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String letters;

    Initials(String name) {
      String[] words = name.trim().split("\\s+");
      String first =
          words.length == 0 || words[0].isEmpty()
              ? "?"
              : new String(Character.toChars(words[0].codePointAt(0)));
      String last =
          words.length < 2
              ? ""
              : new String(Character.toChars(words[words.length - 1].codePointAt(0)));
      letters = (first + last).toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public void draw(Canvas canvas) {
      Rect b = getBounds();
      paint.setColor(SELECTED);
      canvas.drawOval(b.left, b.top, b.right, b.bottom, paint);
      paint.setColor(TEXT);
      paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize(Math.min(b.width(), b.height()) * .32f);
      Paint.FontMetrics metrics = paint.getFontMetrics();
      canvas.drawText(
          letters,
          b.exactCenterX(),
          b.exactCenterY() - (metrics.ascent + metrics.descent) / 2,
          paint);
    }

    @Override
    public void setAlpha(int value) {
      paint.setAlpha(value);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
      paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
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
