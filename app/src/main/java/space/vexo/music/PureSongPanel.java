package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.TextView;

/** Intro chapter recognition and private, user-confirmed start points. */
final class PureSongPanel extends MusicSurface {
  interface Listener {
    Track current();

    long position();

    long duration();

    boolean inRoom();

    void changed();

    void transport(String action);

    default void close() {}
  }

  private final SmartStore smart;
  private final PureSong pure;
  private final Track track;
  private final Listener listener;

  PureSongPanel(Context context, SmartStore smart, Track track, Listener listener) {
    super(context);
    this.smart = smart;
    this.pure = new PureSong(smart);
    this.track = track;
    this.listener = listener;
    setBackground(Glass.backdrop());
    show();
  }

  private void action(String label, Runnable run) {
    TextView button =
        action(
            label,
            false,
            () -> {
              try {
                run.run();
              } catch (IllegalStateException | IllegalArgumentException error) {
                new AlertDialog.Builder(getContext())
                    .setMessage(error.getMessage())
                    .setPositiveButton("Close", null)
                    .show();
              }
            });
    body.addView(button);
    gap(body, 10);
  }

  private boolean current() {
    Track playing = listener.current();
    return track != null && playing != null && track.url.equals(playing.url);
  }

  private void show() {
    body.removeAllViews();
    android.widget.LinearLayout heading = row();
    heading.addView(
        text("PURESONG", 11, LIME, true), new android.widget.LinearLayout.LayoutParams(0, -2, 1));
    Icon close = new Icon(getContext(), "close", MUTED);
    close.setContentDescription("Close PureSong");
    close.setOnClickListener(v -> listener.close());
    heading.addView(close, new android.widget.LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(heading);
    gap(body, 14);
    body.addView(text("More music. Your choice.", 25, TEXT, true));
    gap(body, 12);
    body.addView(
        text(
            "Skip intros recognizes clearly labelled opening chapters on YouTube. You can also"
                + " save your own music start for a song. Unknown songs play in full. Turn it off"
                + " anytime from Home.",
            14,
            MUTED,
            false));
    gap(body, 18);
    action(
        pure.enabled() ? "Skip intros: ON" : "Skip intros: OFF",
        () -> {
          pure.enabled(!pure.enabled());
          listener.changed();
          show();
        });
    boolean prefer = smart.preferences().optBoolean("prefer_official_audio", false);
    action(
        prefer ? "Prefer official audio: ON" : "Prefer official audio: OFF",
        () -> {
          smart.preference("prefer_official_audio", !prefer);
          listener.changed();
          show();
        });
    body.addView(
        text(
            "Official-audio preference uses title hints in future recommendations. It does not"
                + " verify ownership or replace your chosen song.",
            12,
            MUTED,
            false));
    gap(body, 18);
    if (track == null) {
      body.addView(
          text("Open a song’s menu to see its intro or save a music start.", 14, TEXT, false));
      return;
    }
    body.addView(text(track.title, 18, TEXT, true));
    gap(body, 10);
    if (!PureSong.permitted(track)) {
      body.addView(
          text(
              "Intro markers support imported audio and individual YouTube songs.",
              14,
              MUTED,
              false));
      return;
    }
    body.addView(
        text(
            "Play this song, pause where you want the music to begin, then save that position."
                + " Your saved start is private and takes priority over chapter recognition.",
            14,
            MUTED,
            false));
    gap(body, 12);
    long marker =
        current()
            ? pure.start(track, listener.duration())
            : pure.start(track, track.duration * 1000);
    body.addView(
        text(
            marker > 0
                ? (smart.intros().has(QueuePolicy.identity(track.url))
                        ? "Saved start: "
                        : "Recognized chapter intro: ")
                    + marker / 1000
                    + " seconds"
                : "No intro recognized · full song",
            14,
            LIME,
            true));
    gap(body, 12);
    action(
        "Use current position as music start",
        () -> {
          if (!current())
            throw new IllegalStateException("Play this song before choosing its start.");
          pure.mark(track, listener.position(), listener.duration());
          listener.changed();
          show();
        });
    action(
        "Remove intro marker",
        () -> {
          pure.reset(track);
          listener.changed();
          show();
        });
    action(
        "Skip recognized or saved intro now",
        () -> {
          if (!current() || listener.inRoom())
            throw new IllegalStateException("Play this song outside a shared room first.");
          long start = pure.start(track, listener.duration());
          if (start <= listener.position())
            throw new IllegalStateException("No confirmed intro ahead of this position.");
          listener.transport("skip");
        });
    action(
        "Play full version",
        () -> {
          if (!current() || listener.inRoom())
            throw new IllegalStateException("Play this song outside a shared room first.");
          listener.transport("full");
        });
  }
}
