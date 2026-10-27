package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** Live Up Next screen. Playback commands remain owned by the activity/session. */
public final class QueuePanel extends LinearLayout {
  public interface Actions {
    void close();

    void play(int index, String expectedId);

    void options(int index, Track track);

    void clearUpcoming();

    void clearAll();

    void autoplay(boolean enabled);

    default void retry() {}
  }

  private final TextView summary, status, retry;
  private final Switch toggle;
  private final Songs adapter = new Songs();
  private final Actions actions;
  private List<Track> songs = new ArrayList<>();
  private int current = -1;
  private boolean playing;
  private boolean updatingToggle;

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(getContext());
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    return t;
  }

  private LinearLayout row() {
    LinearLayout r = new LinearLayout(getContext());
    r.setGravity(Gravity.CENTER_VERTICAL);
    return r;
  }

  private Visuals.Icon icon(String name, Runnable click) {
    Visuals.Icon v = new Visuals.Icon(getContext(), name, TEXT);
    v.setLayoutParams(new LayoutParams(dp(48), dp(48)));
    v.setOnClickListener(w -> click.run());
    return v;
  }

  private TextView action(String label, Runnable click) {
    TextView v = text(label, 13, LIME);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(48));
    v.setPadding(dp(10), dp(12), dp(10), dp(12));
    v.setBackground(round(PANEL, dp(16)));
    v.setOnClickListener(w -> click.run());
    return v;
  }

  public QueuePanel(Context c, boolean autoplay, Actions actions) {
    super(c);
    this.actions = actions;
    setOrientation(VERTICAL);
    setBackgroundColor(BG);
    setPadding(dp(18), dp(6), dp(18), dp(12));
    LinearLayout top = row();
    TextView title = text("Up next", 27, TEXT);
    title.setTypeface(null, Typeface.BOLD);
    top.addView(title, new LayoutParams(0, -2, 1));
    top.addView(icon("close", actions::close));
    addView(top);
    summary = text("", 13, MUTED);
    summary.setPadding(0, dp(6), 0, dp(16));
    addView(summary);
    LinearLayout buttons = row();
    TextView keep = action("Clear upcoming", actions::clearUpcoming);
    TextView clear = action("Clear queue", actions::clearAll);
    LayoutParams left = new LayoutParams(0, -1, 1);
    left.setMargins(0, 0, dp(8), 0);
    buttons.addView(keep, left);
    buttons.addView(clear, new LayoutParams(0, -1, 1));
    addView(buttons);
    LinearLayout automatic = row();
    automatic.setPadding(dp(2), dp(12), 0, dp(4));
    LinearLayout labels = new LinearLayout(c);
    labels.setOrientation(VERTICAL);
    labels.addView(text("Autoplay", 15, TEXT));
    TextView detail = text("Continue with related songs", 12, MUTED);
    detail.setPadding(0, dp(5), 0, dp(5));
    labels.addView(detail);
    automatic.addView(labels, new LayoutParams(0, -2, 1));
    toggle = new Switch(c);
    toggle.setContentDescription("Autoplay related songs");
    toggle.setMinHeight(dp(48));
    toggle.setChecked(autoplay);
    toggle.setOnCheckedChangeListener(
        (v, value) -> {
          if (!updatingToggle) actions.autoplay(value);
        });
    automatic.addView(toggle, new LayoutParams(dp(58), -2));
    addView(automatic);
    retry = action("Retry recommendations", actions::retry);
    retry.setVisibility(GONE);
    addView(retry, new LayoutParams(-1, -2));
    status = text("", 12, MUTED);
    status.setPadding(0, dp(4), 0, dp(12));
    addView(status);
    RecyclerView list = new RecyclerView(c);
    list.setLayoutManager(new LinearLayoutManager(c));
    list.setAdapter(adapter);
    list.setClipToPadding(false);
    addView(list, new LayoutParams(-1, 0, 1));
  }

  public void refresh(List<Track> tracks, int index, boolean playing, String message) {
    songs = new ArrayList<>(tracks);
    current = index;
    this.playing = playing;
    summary.setText(
        songs.isEmpty()
            ? "Your queue is empty"
            : (playing ? "Now playing" : "Ready to play")
                + " · "
                + Math.max(0, songs.size() - 1)
                + " songs up next");
    status(message);
    adapter.notifyDataSetChanged();
  }

  public void autoplayState(boolean enabled) {
    updatingToggle = true;
    toggle.setChecked(enabled);
    updatingToggle = false;
  }

  public void status(String message) {
    status.setText(message);
    retry.setVisibility(message != null && (message.contains("Couldn’t load related songs")
        || message.contains("No new related songs")) ? VISIBLE : GONE);
  }

  private final class Songs extends RecyclerView.Adapter<Holder> {
    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int type) {
      LinearLayout r = row();
      r.setPadding(dp(8), dp(10), 0, dp(10));
      r.setMinimumHeight(dp(76));
      r.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      ImageView image = new ImageView(getContext());
      r.addView(image, new LayoutParams(dp(44), dp(44)));
      LinearLayout copy = new LinearLayout(getContext());
      copy.setOrientation(VERTICAL);
      copy.setPadding(dp(10), 0, dp(4), 0);
      TextView title = text("", 14, TEXT), artist = text("", 12, MUTED);
      title.setMaxLines(2);
      title.setEllipsize(TextUtils.TruncateAt.END);
      artist.setSingleLine();
      artist.setEllipsize(TextUtils.TruncateAt.END);
      artist.setPadding(0, dp(5), 0, 0);
      copy.addView(title);
      copy.addView(artist);
      r.addView(copy, new LayoutParams(0, -2, 1));
      Visuals.Icon play = icon("play", () -> {}), more = icon("more", () -> {});
      r.addView(play);
      r.addView(more);
      return new Holder(r, image, title, artist, play, more);
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
      Track t = songs.get(position);
      h.title.setText(t.title);
      h.artist.setText(
          (position == current ? (playing ? "Playing now · " : "Selected · ")
              : t.recommended ? "Recommended · " : "In your queue · ") + t.artist);
      artwork(h.image, t);
      h.itemView.setBackground(position == current ? round(PANEL, dp(14)) : null);
      h.play.set(position == current && playing ? "pause" : "play", LIME);
      Runnable start =
          () -> {
            int index = h.getBindingAdapterPosition();
            if (index >= 0 && index < songs.size()) actions.play(index, songs.get(index).url);
          };
      h.play.setOnClickListener(v -> start.run());
      h.play.setContentDescription((position == current && playing ? "Pause " : "Play ") + t.title);
      h.itemView.setOnClickListener(v -> start.run());
      h.more.setContentDescription("Queue options for " + t.title);
      h.more.setOnClickListener(
          v -> {
            int index = h.getBindingAdapterPosition();
            if (index >= 0 && index < songs.size()) actions.options(index, songs.get(index));
          });
    }

    @Override
    public int getItemCount() {
      return songs.size();
    }
  }

  private static final class Holder extends RecyclerView.ViewHolder {
    final ImageView image;
    final TextView title, artist;
    final Visuals.Icon play, more;

    Holder(
        View r,
        ImageView image,
        TextView title,
        TextView artist,
        Visuals.Icon play,
        Visuals.Icon more) {
      super(r);
      this.image = image;
      this.title = title;
      this.artist = artist;
      this.play = play;
      this.more = more;
    }
  }
}
