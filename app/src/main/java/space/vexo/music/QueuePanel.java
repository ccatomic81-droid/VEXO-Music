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
    default void save() {}
    default void filter(String value) {}
    default void move(int from, int to, String expected, String target) {}
    default void remove(int index, String expected) {}
    default void changed() {}
  }

  private final TextView summary, status, retry, clearUpcoming, clearQueue;
  private final Switch toggle;
  private final Songs adapter = new Songs();
  private final Actions actions;
  private List<Track> songs = new ArrayList<>();
  private int current = -1;
  private boolean playing;
  private boolean updatingToggle;
  final View handle;
  private final LinearLayout buttons, automatic, currentRow;
  private boolean expanded = true;
  private final ImageView currentArt;
  private final TextView currentTitle, currentArtist;
  private final Visuals.Icon currentPlay;
  private final Map<String, TextView> filters = new LinkedHashMap<>();
  private boolean editing;
  private int dragFrom = -1, dragTo = -1;
  private String dragId = "", dragTarget = "";
  private List<Track> dragSnapshot;
  private final RecyclerView list;

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
    FrameLayout grip = new FrameLayout(c);
    View bar = new View(c);
    bar.setBackground(round(MUTED, dp(3)));
    grip.addView(bar, new FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER));
    grip.setContentDescription("Expand or collapse queue. Swipe up for full queue, down to return to player");
    grip.setFocusable(true);
    addView(grip, new LayoutParams(-1, dp(32)));
    handle = grip;
    LinearLayout top = row();
    TextView title = text("Up next", 22, TEXT);
    title.setTypeface(null, Typeface.BOLD);
    top.addView(title, new LayoutParams(0, -2, 1));
    top.addView(action("Save mix", actions::save));
    Visuals.Icon options = icon("more", this::showOptions);
    options.setContentDescription("Queue options");
    top.addView(options);
    top.addView(icon("close", actions::close));
    addView(top);
    currentRow = row();
    currentRow.setPadding(0, dp(10), 0, dp(6));
    currentArt = new RoundedArtwork(c);
    currentRow.addView(currentArt, new LayoutParams(dp(44), dp(44)));
    LinearLayout currentCopy = new LinearLayout(c);
    currentCopy.setOrientation(VERTICAL);
    currentCopy.setPadding(dp(12), 0, dp(8), 0);
    currentTitle = text("", 14, TEXT);
    currentTitle.setSingleLine(); currentTitle.setEllipsize(TextUtils.TruncateAt.END);
    currentArtist = text("", 12, MUTED);
    currentArtist.setSingleLine(); currentArtist.setEllipsize(TextUtils.TruncateAt.END);
    currentCopy.addView(currentTitle); currentCopy.addView(currentArtist);
    currentRow.addView(currentCopy, new LayoutParams(0, -2, 1));
    currentPlay = icon("play", () -> {
      if (current >= 0 && current < songs.size()) actions.play(current, songs.get(current).url);
    });
    currentRow.addView(currentPlay);
    addView(currentRow);
    summary = text("", 13, MUTED);
    summary.setPadding(0, dp(6), 0, dp(16));
    addView(summary);
    HorizontalScrollView chips = new HorizontalScrollView(c);
    chips.setHorizontalScrollBarEnabled(false);
    LinearLayout chipRow = row();
    for (String label : QueueMixFilter.LABELS) {
      TextView chip = action(label, () -> actions.filter(label));
      chip.setContentDescription(label.equals("Deep cuts")
          ? "Deep cuts: search for album tracks and B-sides" : label + " upcoming recommendations");
      LayoutParams cp = new LayoutParams(-2, dp(48));
      cp.rightMargin = dp(8);
      chipRow.addView(chip, cp); filters.put(label, chip);
    }
    chips.addView(chipRow);
    addView(chips, new LayoutParams(-1, -2));
    filterState(QueueMixFilter.read(c));
    buttons = row();
    clearUpcoming = action("Clear upcoming", actions::clearUpcoming);
    clearQueue = action("Clear queue", actions::clearAll);
    LayoutParams left = new LayoutParams(0, -1, 1);
    left.setMargins(0, 0, dp(8), 0);
    buttons.addView(clearUpcoming, left);
    buttons.addView(clearQueue, new LayoutParams(0, -1, 1));
    addView(buttons);
    automatic = row();
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
    toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(TEXT));
    toggle.setTrackTintList(new android.content.res.ColorStateList(
        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
        new int[] {LIME, CONTROL}));
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
    list = new RecyclerView(c);
    list.setLayoutManager(new LinearLayoutManager(c));
    list.setAdapter(adapter);
    list.setClipToPadding(false);
    addView(list, new LayoutParams(-1, 0, 1));
    ItemTouchHelper touch = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
      @Override public boolean isLongPressDragEnabled() { return true; }
      @Override public int getMovementFlags(RecyclerView view, RecyclerView.ViewHolder holder) {
        int at = holder.getBindingAdapterPosition();
        return at <= current ? 0 : super.getMovementFlags(view, holder);
      }
      @Override public boolean onMove(RecyclerView view, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
        int from = source.getBindingAdapterPosition(), to = target.getBindingAdapterPosition();
        if (from <= current || to <= current || from >= songs.size() || to >= songs.size()) return false;
        if (dragFrom < 0) { dragFrom = from; dragId = songs.get(from).url; dragSnapshot = new ArrayList<>(songs); }
        dragTo = to; dragTarget = dragSnapshot.get(to).url;
        Track moved = songs.remove(from); songs.add(to, moved);
        adapter.notifyItemMoved(from, to);
        return true;
      }
      @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
        if (state != ItemTouchHelper.ACTION_STATE_IDLE) editing = true;
        super.onSelectedChanged(holder, state);
      }
      @Override public void clearView(RecyclerView view, RecyclerView.ViewHolder holder) {
        super.clearView(view, holder);
        editing = false;
        if (dragFrom >= 0 && dragTo >= 0 && dragFrom != dragTo)
          actions.move(dragFrom, dragTo, dragId, dragTarget);
        else post(actions::changed);
        dragFrom = dragTo = -1;
      }
      @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
        int index = holder.getBindingAdapterPosition();
        editing = false;
        if (index > current && index < songs.size()) actions.remove(index, songs.get(index).url);
        adapter.notifyDataSetChanged();
      }
    });
    touch.attachToRecyclerView(list);
  }

  void expanded(boolean value) {
    expanded = value;
    requestLayout();
  }

  @Override protected void onMeasure(int width, int height) {
    int available = MeasureSpec.getMode(height) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(height);
    boolean showOptions = expanded && available >= dp(580);
    buttons.setVisibility(showOptions ? VISIBLE : GONE);
    automatic.setVisibility(showOptions ? VISIBLE : GONE);
    currentRow.setVisibility(available >= dp(420) ? VISIBLE : GONE);
    summary.setVisibility(available >= dp(400) ? VISIBLE : GONE);
    super.onMeasure(width, height);
  }

  boolean editing() { return editing; }

  private void showOptions() {
    new VexoDialogs.Builder(getContext()).setTitle("Queue options")
        .setItems(new String[] {toggle.isChecked() ? "Turn autoplay off" : "Turn autoplay on", "Clear upcoming", "Clear queue"},
            (dialog, which) -> {
              if (which == 0) actions.autoplay(!toggle.isChecked());
              else if (which == 1) actions.clearUpcoming();
              else actions.clearAll();
            }).setNegativeButton("Close", null).show();
  }

  void filterState(String selected) {
    for (Map.Entry<String, TextView> entry : filters.entrySet()) {
      boolean active = entry.getKey().equals(selected);
      entry.getValue().setSelected(active);
      entry.getValue().setTextColor(active ? BG : TEXT);
      entry.getValue().setBackground(round(active ? TEXT : CONTROL, dp(16)));
    }
  }

  public void refresh(List<Track> tracks, int index, boolean playing, String message) {
    if (editing) return;
    songs = new ArrayList<>(tracks);
    current = index;
    this.playing = playing;
    filterState(QueueMixFilter.read(getContext()));
    if (index >= 0 && index < songs.size()) {
      Track currentSong = songs.get(index);
      currentTitle.setText(currentSong.title); currentArtist.setText(currentSong.artist);
      artwork(currentArt, currentSong);
      currentPlay.set(playing ? "pause" : "play", TEXT);
    } else { currentTitle.setText("Choose a song to begin"); currentArtist.setText(""); }
    int upcoming = Math.max(0, songs.size() - Math.max(0, index + 1));
    summary.setText(
        songs.isEmpty()
            ? "Your queue is empty"
            : (playing ? "Now playing" : "Ready to play")
                + " · "
                + upcoming
                + (upcoming == 1 ? " song up next" : " songs up next"));
    clearUpcoming.setEnabled(upcoming > 0);
    clearUpcoming.setAlpha(upcoming > 0 ? 1 : .4f);
    clearQueue.setEnabled(!songs.isEmpty());
    clearQueue.setAlpha(songs.isEmpty() ? .4f : 1);
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
      FrameLayout cover = new FrameLayout(getContext());
      ImageView image = new RoundedArtwork(getContext());
      cover.addView(image, new FrameLayout.LayoutParams(-1, -1));
      PlayingIndicator indicator = new PlayingIndicator(getContext());
      FrameLayout.LayoutParams marker = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.BOTTOM | Gravity.END);
      marker.setMargins(0, 0, dp(3), dp(3));
      cover.addView(indicator, marker);
      r.addView(cover, new LayoutParams(dp(44), dp(44)));
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
      Visuals.Icon play = icon("drag", () -> {}), more = icon("more", () -> {});
      r.addView(play);
      r.addView(more);
      return new Holder(r, image, title, artist, play, more, indicator);
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
      h.indicator.setVisibility(position == current ? VISIBLE : GONE);
      h.indicator.playing(playing);
      h.play.set(position == current ? (playing ? "pause" : "play") : "drag", position == current ? LIME : MUTED);
      Runnable start =
          () -> {
            int index = h.getBindingAdapterPosition();
            if (index >= 0 && index < songs.size()) actions.play(index, songs.get(index).url);
          };
      h.play.setOnClickListener(v -> { if (h.getBindingAdapterPosition() == current) start.run(); });
      h.play.setContentDescription(position == current ? (playing ? "Pause " : "Play ") + t.title : "Hold and drag to reorder " + t.title + ". More options also includes Move up and Move down");
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
    final PlayingIndicator indicator;

    Holder(
        View r,
        ImageView image,
        TextView title,
        TextView artist,
        Visuals.Icon play,
        Visuals.Icon more, PlayingIndicator indicator) {
      super(r);
      this.image = image;
      this.title = title;
      this.artist = artist;
      this.play = play;
      this.more = more;
      this.indicator = indicator;
    }
  }
}
