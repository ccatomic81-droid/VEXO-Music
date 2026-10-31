package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** One persistent, recycled catalogue. Loading and Follow never replace the page or its artwork. */
final class ArtistPanel extends RecyclerView {
  interface Listener {
    void back(); void follow(); void play(int index); void shuffle();
    void options(Track song); void more(); void retry();
  }

  private final MusicSurface ui;
  private final Listener listener;
  private final Catalogue adapter = new Catalogue();
  private final List<Track> songs = new ArrayList<>();
  private final Map<String, Long> ids = new HashMap<>();
  private SearchArtists.Artist artist;
  private boolean following, loading, more;
  private String error = "";
  private long nextId;
  private boolean morePosted;

  ArtistPanel(Context c, SearchArtists.Artist artist, List<Track> songs, boolean following,
      boolean loading, boolean more, String error, Listener listener) {
    super(c);
    this.listener = listener;
    ui = new MusicSurface(c);
    setBackgroundColor(BG);
    setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(24));
    setClipToPadding(false);
    setVerticalScrollBarEnabled(false);
    setLayoutManager(new LinearLayoutManager(c));
    setItemAnimator(null);
    adapter.setHasStableIds(true);
    setAdapter(adapter);
    update(artist, songs, following, loading, more, error);
    addOnScrollListener(new OnScrollListener() {
      @Override public void onScrolled(RecyclerView view, int dx, int dy) {
        if (dy > 0 && !ArtistPanel.this.loading && ArtistPanel.this.more
            && ArtistPanel.this.error.isEmpty()
            && !morePosted && ((LinearLayoutManager) getLayoutManager()).findLastVisibleItemPosition()
                >= adapter.getItemCount() - 6) {
          morePosted = true;
          post(() -> {
            morePosted = false;
            if (isAttachedToWindow() && !ArtistPanel.this.loading && ArtistPanel.this.more
                && ArtistPanel.this.error.isEmpty()) listener.more();
          });
        }
      }
    });
  }

  void update(SearchArtists.Artist artist, List<Track> next, boolean following,
      boolean loading, boolean more, String error) {
    List<Track> before = new ArrayList<>(songs);
    boolean headerChanged = this.artist == null || !this.artist.name.equals(artist.name)
        || !this.artist.artwork.equals(artist.artwork) || !this.artist.id.equals(artist.id)
        || this.following != following || before.size() != next.size() || this.more != more;
    boolean footerChanged = this.loading != loading || this.more != more
        || !this.error.equals(error) || before.isEmpty() != next.isEmpty();
    this.artist = artist; this.following = following; this.loading = loading;
    this.more = more; this.error = error;
    DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      public int getOldListSize() { return before.size(); }
      public int getNewListSize() { return next.size(); }
      public boolean areItemsTheSame(int old, int at) { return before.get(old).url.equals(next.get(at).url); }
      public boolean areContentsTheSame(int old, int at) {
        Track a = before.get(old), b = next.get(at);
        return a.title.equals(b.title) && a.artist.equals(b.artist) && a.artwork.equals(b.artwork);
      }
    });
    songs.clear(); songs.addAll(next);
    for (Track song : songs) if (!ids.containsKey(song.url)) ids.put(song.url, nextId++);
    changes.dispatchUpdatesTo(new ListUpdateCallback() {
      public void onInserted(int position, int count) { adapter.notifyItemRangeInserted(position + 1, count); }
      public void onRemoved(int position, int count) { adapter.notifyItemRangeRemoved(position + 1, count); }
      public void onMoved(int from, int to) { adapter.notifyItemMoved(from + 1, to + 1); }
      public void onChanged(int position, int count, Object payload) { adapter.notifyItemRangeChanged(position + 1, count, payload); }
    });
    if (headerChanged) adapter.notifyItemChanged(0, "state");
    if (footerChanged) adapter.notifyItemChanged(songs.size() + 1, "state");
  }

  private final class Catalogue extends Adapter<ViewHolder> {
    public int getItemCount() { return songs.size() + 2; }
    public int getItemViewType(int at) { return at == 0 ? 0 : at == songs.size() + 1 ? 2 : 1; }
    public long getItemId(int at) {
      return at == 0 ? Long.MIN_VALUE : at == songs.size() + 1 ? Long.MIN_VALUE + 1 : ids.get(songs.get(at - 1).url);
    }
    public ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
      return type == 0 ? new Header() : type == 2 ? new Footer() : new Song();
    }
    public void onBindViewHolder(ViewHolder holder, int at) {
      if (holder instanceof Header) ((Header) holder).bind();
      else if (holder instanceof Footer) ((Footer) holder).bind();
      else ((Song) holder).bind(songs.get(at - 1));
    }
    public void onBindViewHolder(ViewHolder holder, int at, List<Object> payloads) {
      onBindViewHolder(holder, at);
    }
  }

  private LinearLayout container() {
    LinearLayout v = ui.column();
    v.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
    return v;
  }

  private final class Header extends ViewHolder {
    final TextView name, follow, play, count;
    final ImageView photo;
    final Icon shuffle;
    String portrait = "";
    Header() {
      super(container());
      LinearLayout body = (LinearLayout) itemView;
      LinearLayout top = ui.row();
      Icon back = new Icon(getContext(), "back", TEXT);
      back.setContentDescription("Back from artist");
      back.setOnClickListener(v -> listener.back());
      top.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
      TextView category = ui.text("ARTIST", 11, LIME, true);
      category.setLetterSpacing(.13f); top.addView(category); body.addView(top); ui.gap(body, 12);
      LinearLayout hero = ui.column();
      hero.setPadding(ui.dp(18), ui.dp(24), ui.dp(18), ui.dp(24));
      hero.setBackground(round(SELECTED, ui.dp(24)));
      photo = ArtistsPanel.portrait(getContext(), artist);
      portrait = artist.name + "\n" + artist.artwork;
      LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ui.dp(152), ui.dp(152));
      pp.gravity = Gravity.CENTER_HORIZONTAL; hero.addView(photo, pp); ui.gap(hero, 22);
      name = ui.text("", 30, TEXT, true); name.setGravity(Gravity.CENTER); hero.addView(name); ui.gap(hero, 8);
      TextView sub = ui.text("Artist song catalogue", 13, MUTED, false);
      sub.setGravity(Gravity.CENTER); hero.addView(sub); body.addView(hero); ui.gap(body, 18);
      LinearLayout controls = ui.row();
      follow = ui.action("Follow", false, listener::follow);
      controls.addView(follow, new LinearLayout.LayoutParams(0, -2, 1));
      play = ui.action("Play all", true, () -> listener.play(0));
      LinearLayout.LayoutParams playp = new LinearLayout.LayoutParams(0, -2, 1); playp.leftMargin = ui.dp(10);
      controls.addView(play, playp);
      shuffle = new Icon(getContext(), "shuffle", TEXT);
      shuffle.setContentDescription("Shuffle artist songs"); shuffle.setOnClickListener(v -> listener.shuffle());
      controls.addView(shuffle, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
      body.addView(controls); ui.gap(body, 24);
      LinearLayout heading = ui.row();
      heading.addView(ui.text("Songs", 22, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
      count = ui.text("", 12, MUTED, false); heading.addView(count); body.addView(heading); ui.gap(body, 10);
    }
    void bind() {
      set(name, artist.name); set(follow, following ? "Following" : "Follow");
      follow.setEnabled(!artist.id.isEmpty()); follow.setAlpha(artist.id.isEmpty() ? .4f : 1);
      play.setEnabled(!songs.isEmpty()); play.setAlpha(songs.isEmpty() ? .4f : 1);
      shuffle.setEnabled(!songs.isEmpty()); shuffle.setAlpha(songs.isEmpty() ? .4f : 1);
      set(count, songs.isEmpty() ? "" : songs.size() + (more ? " loaded" : " songs"));
      String key = artist.name + "\n" + artist.artwork;
      if (!key.equals(portrait)) {
        portrait = key;
        ArtworkLoader.load(photo, artist.artwork, new ArtistStoriesPanel.Initials(artist.name));
      }
    }
  }

  private final class Song extends ViewHolder {
    final ImageView art;
    final TextView title, by;
    Track track;
    String artworkKey = "";
    Song() {
      super(ui.row());
      LinearLayout row = (LinearLayout) itemView;
      row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      row.setPadding(0, ui.dp(10), 0, ui.dp(10));
      art = new RoundedArtwork(getContext()); art.setBackground(round(PANEL, ui.dp(6))); art.setClipToOutline(true);
      art.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(art, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
      LinearLayout labels = ui.column(); labels.setPadding(ui.dp(12), 0, ui.dp(4), 0);
      title = ui.text("", 15, TEXT, true); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
      by = ui.text("", 12, MUTED, false); by.setMaxLines(2); by.setEllipsize(TextUtils.TruncateAt.END);
      labels.addView(title); ui.gap(labels, 5); labels.addView(by);
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      Icon options = new Icon(getContext(), "more", MUTED);
      options.setOnClickListener(v -> { if (track != null) listener.options(track); });
      row.addView(options, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
      row.setFocusable(true);
      row.setOnClickListener(v -> {
        int at = getBindingAdapterPosition(); if (at > 0 && at <= songs.size()) listener.play(at - 1);
      });
    }
    void bind(Track t) {
      track = t; set(title, t.title); set(by, t.artist);
      itemView.setContentDescription("Play " + t.title + " by " + t.artist);
      ((LinearLayout) itemView).getChildAt(2).setContentDescription("Options for " + t.title);
      String key = t.url + "\n" + t.artwork;
      if (!key.equals(artworkKey)) {
        artworkKey = key; Visuals.artwork(art, t);
      }
    }
  }

  private final class Footer extends ViewHolder {
    final TextView status, retry, load;
    Footer() {
      super(container());
      LinearLayout body = (LinearLayout) itemView;
      body.setPadding(0, ui.dp(14), 0, ui.dp(16));
      status = ui.text("", 14, MUTED, false); status.setPadding(0, 0, 0, ui.dp(12)); body.addView(status);
      retry = ui.action("Try again", false, listener::retry); body.addView(retry);
      load = ui.action("Load more songs", false, listener::more); body.addView(load);
    }
    void bind() {
      String value = !error.isEmpty() ? error : loading
          ? songs.isEmpty() ? "Loading artist catalogue…" : "Loading more songs…"
          : songs.isEmpty() ? "No available songs in this artist’s catalogue."
          : more ? "More songs load as you scroll." : "All available catalogue songs loaded.";
      set(status, value);
      retry.setVisibility(error.isEmpty() ? GONE : VISIBLE);
      load.setVisibility(more && !loading && error.isEmpty() ? VISIBLE : GONE);
    }
  }

  private static void set(TextView view, String text) {
    if (!text.contentEquals(view.getText())) view.setText(text);
  }
}
