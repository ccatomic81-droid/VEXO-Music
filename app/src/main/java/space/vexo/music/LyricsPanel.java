package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

/** Lyrics follow playback; manual scrolling temporarily pauses automatic positioning. */
final class LyricsPanel extends LinearLayout {
  interface Playback {
    Track track();

    long position();

    long duration();

    void seek(long position);

    void close();
  }

  private final Playback playback;
  private final Handler main;
  private final ExecutorService io;
  private final TextView title, subtitle, status;
  private final ImageView artwork;
  private final ScrollView scroll;
  private final LinearLayout linesView;
  private final List<TextView> views = new ArrayList<>();
  private List<Lyrics.Line> lines = Collections.emptyList();
  private String key = "";
  private int generation, active = -2;
  private long manualUntil;
  private boolean closed;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (closed) return;
          refresh();
          main.postDelayed(this, 250);
        }
      };

  LyricsPanel(Context c, Handler main, ExecutorService io, Playback playback) {
    super(c);
    this.main = main;
    this.io = io;
    this.playback = playback;
    setOrientation(VERTICAL);
    setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            light
                ? new int[] {0xffe4f2ea, 0xfff5f5f0, 0xffe2ece7}
                : new int[] {0xff21372f, 0xff302c38, 0xff111615}));
    LinearLayout header = new LinearLayout(c);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(20), dp(10), dp(12), dp(10));
    artwork = new ImageView(c);
    artwork.setContentDescription("Album artwork");
    LayoutParams artParams = new LayoutParams(dp(48), dp(48));
    artParams.rightMargin = dp(12);
    header.addView(artwork, artParams);
    LinearLayout labels = new LinearLayout(c);
    labels.setOrientation(VERTICAL);
    title = text("Lyrics", 19, TEXT, true);
    subtitle = text("", 13, MUTED, false);
    labels.addView(title);
    labels.addView(subtitle);
    header.addView(labels, new LayoutParams(0, -2, 1));
    Icon close = new Icon(c, "close", TEXT);
    close.setContentDescription("Close lyrics");
    close.setOnClickListener(v -> playback.close());
    header.addView(close, new LayoutParams(dp(48), dp(48)));
    addView(header);
    status = text("Loading lyrics…", 14, MUTED, false);
    status.setPadding(dp(24), dp(8), dp(24), dp(12));
    addView(status);
    scroll = new ScrollView(c);
    scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setOnTouchListener(
        (v, event) -> {
          if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
              || event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
            manualUntil = android.os.SystemClock.uptimeMillis() + 4000;
            lastScrolled = -1;
          }
          return false;
        });
    linesView = new LinearLayout(c);
    linesView.setOrientation(VERTICAL);
    linesView.setPadding(dp(24), dp(32), dp(24), dp(140));
    scroll.addView(linesView);
    addView(scroll, new LayoutParams(-1, 0, 1));
    TextView attribution =
        text(
            "Lyrics from LRCLIB · Song title, artist and duration are sent to find a match",
            11,
            MUTED,
            false);
    attribution.setPadding(dp(24), dp(8), dp(24), dp(16));
    addView(attribution);
    main.post(tick);
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView t = new TextView(getContext());
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  void stop() {
    closed = true;
    generation++;
    main.removeCallbacks(tick);
  }

  private void refresh() {
    Track track = playback.track();
    if (track == null) return;
    long duration = playback.duration();
    String next = track.url + "\n" + track.title + "\n" + track.artist;
    if (!key.equals(next)) {
      key = next;
      load(track, duration);
    } else if (duration > 0 && waitingForDuration) load(track, duration);
    int now = Lyrics.active(lines, Math.max(0, playback.position()));
    if (now != active) {
      active = now;
      for (int i = 0; i < views.size(); i++) {
        TextView line = views.get(i);
        line.animate().cancel();
        line.animate().alpha(i == now ? 1f : .48f).setDuration(220).start();
      }
    }
    if (now >= 0
        && now < views.size()
        && android.os.SystemClock.uptimeMillis() >= manualUntil
        && lastScrolled != now) {
      lastScrolled = now;
      TextView line = views.get(now);
      scroll.post(
          () -> {
            if (!closed && active == now)
              scroll.smoothScrollTo(0, Math.max(0, line.getTop() - scroll.getHeight() / 3));
          });
    }
  }

  private boolean waitingForDuration;
  private int lastScrolled = -1;

  private void load(Track track, long duration) {
    int request = ++generation;
    title.setText(track.title);
    subtitle.setText(track.artist);
    Visuals.artwork(artwork, track);
    status.setText("Loading lyrics…");
    lines = Collections.emptyList();
    views.clear();
    linesView.removeAllViews();
    active = -2;
    lastScrolled = -1;
    waitingForDuration = duration <= 0;
    if (waitingForDuration) {
      status.setText("Waiting for song details…");
      return;
    }
    io.execute(
        () -> {
          try {
            Lyrics.Result result = Lyrics.load(track, duration);
            main.post(
                () -> {
                  if (!closed && generation == request) show(result);
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (!closed && generation == request)
                    status.setText(
                        "Couldn’t load lyrics. Check your connection and reopen lyrics to try"
                            + " again.");
                });
          }
        });
  }

  private void show(Lyrics.Result result) {
    lines = result.lines;
    if (!lines.isEmpty()) {
      status.setText("Synced lyrics · Tap a line to seek");
      for (Lyrics.Line line : lines) {
        TextView view = text(line.text, 27, TEXT, true);
        view.setAlpha(.48f);
        view.setPadding(0, dp(10), 0, dp(10));
        view.setOnClickListener(
            v -> {
              playback.seek(line.time);
              manualUntil = 0;
              lastScrolled = -1;
            });
        linesView.addView(view);
        views.add(view);
      }
    } else if (result.instrumental) status.setText("This track is instrumental.");
    else if (result.plain != null && !result.plain.trim().isEmpty()) {
      status.setText("Lyrics available without timing");
      TextView plain = text(result.plain, 22, TEXT, true);
      plain.setLineSpacing(dp(10), 1f);
      linesView.addView(plain);
    } else status.setText("No lyrics found for this song.");
    active = -2;
    refresh();
  }
}
