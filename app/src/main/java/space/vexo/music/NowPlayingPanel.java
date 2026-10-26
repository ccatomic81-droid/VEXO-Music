package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** The actual player view, independently measurable without a media-session connection. */
public final class NowPlayingPanel extends LinearLayout {
  public interface Actions {
    void action(String name);

    void seek(int progress, boolean finished);
  }

  public final ImageView art;
  public final TextView title, artist, elapsed, duration, state, speed, radioStatus;
  public final Icon favourite, shuffle, play, repeat;
  public final SeekBar seek;
  public final View cover;
  public final ScrollView scroll;

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout row() {
    LinearLayout r = new LinearLayout(getContext());
    r.setGravity(Gravity.CENTER_VERTICAL);
    return r;
  }

  private TextView text(String label, int size, int color, boolean bold) {
    TextView t = new TextView(getContext());
    t.setText(label);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  private void gap(LinearLayout parent, int height) {
    parent.addView(new View(getContext()), new LayoutParams(1, dp(height)));
  }

  private Icon icon(String name, int color, Actions actions) {
    Icon v = new Icon(getContext(), name, color);
    v.setLayoutParams(new LayoutParams(dp(48), dp(48)));
    v.setFocusable(true);
    v.setOnClickListener(w -> actions.action(name));
    return v;
  }

  public NowPlayingPanel(Context c, Actions actions) {
    super(c);
    setOrientation(VERTICAL);
    setBackgroundColor(BG);
    LinearLayout top = row();
    top.setPadding(dp(12), dp(2), dp(12), dp(2));
    top.setBackgroundColor(BG);
    top.setElevation(dp(2));
    top.addView(icon("down", TEXT, actions));
    TextView heading = text("NOW PLAYING", 11, LIME, true);
    heading.setGravity(Gravity.CENTER);
    heading.setLetterSpacing(.12f);
    top.addView(heading, new LayoutParams(0, -2, 1));
    top.addView(icon("queue", TEXT, actions));
    addView(top, new LayoutParams(-1, -2));
    scroll = new ScrollView(c);
    scroll.setClipToPadding(true);
    scroll.setClipChildren(true);
    scroll.setFillViewport(false);
    LinearLayout body =
        new LinearLayout(c) {
          @Override
          protected void onMeasure(int w, int h) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(
                    Math.min(MeasureSpec.getSize(w), dp(416)), MeasureSpec.EXACTLY),
                h);
          }
        };
    body.setOrientation(VERTICAL);
    body.setGravity(Gravity.CENTER_HORIZONTAL);
    body.setPadding(dp(24), dp(12), dp(24), dp(24));
    scroll.addView(body, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL));
    addView(scroll, new LayoutParams(-1, 0, 1));
    FrameLayout artwork =
        new FrameLayout(c) {
          private final android.graphics.Path clip = new android.graphics.Path();

          @Override
          protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            clip.reset();
            clip.addRoundRect(0, 0, w, h, dp(18), dp(18), android.graphics.Path.Direction.CW);
          }

          @Override
          protected void dispatchDraw(android.graphics.Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(clip);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
          }

          @Override
          protected void onMeasure(int w, int h) {
            int max =
                getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    ? dp(192)
                    : dp(320);
            int side = Math.min(MeasureSpec.getSize(w), max);
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY));
          }
        };
    artwork.setBackground(round(PANEL, dp(18)));
    artwork.setClipToOutline(true);
    artwork.setOutlineProvider(
        new ViewOutlineProvider() {
          @Override
          public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
          }
        });
    art = new ImageView(c);
    art.setContentDescription("Album artwork");
    art.setScaleType(ImageView.ScaleType.FIT_CENTER);
    artwork.addView(art, new FrameLayout.LayoutParams(-1, -1));
    body.addView(artwork, new LayoutParams(-1, -2));
    cover = artwork;
    gap(body, 22);
    LinearLayout titles = row();
    titles.setGravity(Gravity.TOP);
    LinearLayout copy = new LinearLayout(c);
    copy.setOrientation(VERTICAL);
    title = text("", 22, TEXT, true);
    title.setMaxLines(2);
    title.setEllipsize(TextUtils.TruncateAt.END);
    artist = text("", 14, MUTED, false);
    artist.setMaxLines(2);
    artist.setEllipsize(TextUtils.TruncateAt.END);
    copy.addView(title);
    gap(copy, 8);
    copy.addView(artist);
    titles.addView(copy, new LayoutParams(0, -2, 1));
    favourite = icon("heart", LIME, actions);
    LayoutParams fp = new LayoutParams(dp(48), dp(48));
    fp.leftMargin = dp(8);
    fp.topMargin = -dp(6);
    titles.addView(favourite, fp);
    body.addView(titles, new LayoutParams(-1, -2));
    gap(body, 14);
    seek = new SeekBar(c);
    seek.setMax(1000);
    seek.setPadding(dp(8), 0, dp(8), 0);
    seek.setContentDescription("Song position");
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(LIME));
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {
            actions.action("seek-start");
          }

          public void onStopTrackingTouch(SeekBar s) {
            actions.seek(s.getProgress(), true);
          }

          public void onProgressChanged(SeekBar s, int value, boolean user) {
            if (user) actions.seek(value, false);
          }
        });
    body.addView(seek, new LayoutParams(-1, dp(40)));
    LinearLayout times = row();
    times.setPadding(dp(8), 0, dp(8), 0);
    elapsed = text("0:00", 12, MUTED, false);
    duration = text("--:--", 12, MUTED, false);
    times.addView(elapsed, new LayoutParams(0, -2, 1));
    times.addView(duration);
    body.addView(times, new LayoutParams(-1, -2));
    gap(body, 18);
    TransportControls transport = new TransportControls(c);
    transport.setGravity(Gravity.CENTER);
    shuffle = icon("shuffle", MUTED, actions);
    transport.addView(shuffle);
    transport.addView(icon("previous", TEXT, actions));
    play = icon("play", BG, actions);
    play.setGlyphSize(44);
    play.setBackground(round(LIME, dp(48)));
    transport.addView(play);
    transport.addView(icon("next", TEXT, actions));
    repeat = icon("repeat", MUTED, actions);
    transport.addView(repeat);
    body.addView(transport, new LayoutParams(-1, dp(80)));
    gap(body, 14);
    LinearLayout tools = row();
    tools.addView(icon("timer", MUTED, actions), new LayoutParams(0, dp(48), 1));
    speed = text("1×", 13, TEXT, true);
    speed.setGravity(Gravity.CENTER);
    speed.setMinHeight(dp(48));
    speed.setBackground(
        new android.graphics.drawable.InsetDrawable(round(PANEL, dp(14)), dp(4), 0, dp(4), 0));
    speed.setContentDescription("Playback speed");
    speed.setFocusable(true);
    speed.setOnClickListener(v -> actions.action("speed"));
    LayoutParams sp = new LayoutParams(0, dp(48), 1);
    sp.setMargins(0, 0, 0, 0);
    tools.addView(speed, sp);
    tools.addView(icon("more", MUTED, actions), new LayoutParams(0, dp(48), 1));
    body.addView(tools, new LayoutParams(-1, -2));
    gap(body, 14);
    radioStatus = text("", 12, MUTED, false);
    radioStatus.setMaxLines(4);
    radioStatus.setEllipsize(TextUtils.TruncateAt.END);
    radioStatus.setPadding(dp(2), dp(14), dp(2), 0);
    body.addView(radioStatus, new LayoutParams(-1, -2));
    gap(body, 12);
    state = text("", 11, MUTED, false);
    state.setGravity(Gravity.CENTER);
    body.addView(state, new LayoutParams(-1, -2));
  }

}
