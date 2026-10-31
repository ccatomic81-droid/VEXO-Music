package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Artist discovery and following have their own screen and search input. */
final class ArtistsPanel extends MusicSurface {
  interface Listener {
    void search(String query);
    void open(SearchArtists.Artist artist);
    default void edit(String query) {}
  }

  final EditText search;

  ArtistsPanel(Context c, String query, List<SearchArtists.Artist> results,
      boolean searched, boolean loading, String error, Listener listener) {
    super(c);
    setBackgroundColor(BG);
    body.addView(text("YOUR MUSIC", 11, LIME, true));
    gap(body, 8);
    body.addView(text("Artists", 32, TEXT, true));
    gap(body, 8);
    body.addView(text("Find your favourites. Go straight to their songs.", 14, MUTED, false));
    gap(body, 20);
    LinearLayout input = row();
    input.setBackground(round(CONTROL, dp(16)));
    search = new EditText(c);
    search.setSingleLine(true);
    search.setTextSize(16);
    search.setTextColor(TEXT);
    search.setHintTextColor(MUTED);
    search.setHint("Search artists");
    search.setContentDescription("Search artists");
    search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    search.setPadding(dp(14), dp(14), dp(4), dp(14));
    search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    search.setText(query);
    search.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        listener.edit(s.toString());
      }
      public void afterTextChanged(android.text.Editable s) {}
    });
    search.setOnEditorActionListener((v, action, event) -> {
      if (action == EditorInfo.IME_ACTION_SEARCH || event != null
          && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
        listener.search(search.getText().toString());
        return true;
      }
      return false;
    });
    input.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
    Icon go = new Icon(c, "search", LIME);
    go.setContentDescription("Find artists");
    go.setOnClickListener(v -> listener.search(search.getText().toString()));
    input.addView(go, new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(input);
    gap(body, 22);
    if (loading) body.addView(text("Finding artists…", 14, MUTED, false));
    if (!error.isEmpty()) {
      body.addView(text(error, 14, MUTED, false));
      gap(body, 12);
      body.addView(action("Try again", false, () -> listener.search(query)));
    }
    if (searched) {
      if (!loading && error.isEmpty() && results.isEmpty())
        body.addView(text("No artists found. Try another name.", 14, MUTED, false));
      if (!results.isEmpty()) section("Artists found", null, () -> {});
      for (SearchArtists.Artist artist : results) artistRow(artist, listener);
      gap(body, 16);
      body.addView(action("Your artists", false, () -> listener.search("")));
    } else {
      SavedArtists saved = new SavedArtists(c);
      List<SearchArtists.Artist> followed = saved.entries();
      section("Following", null, () -> {});
      if (followed.isEmpty())
        body.addView(text("Search for an artist, then tap Follow on their page.", 14, MUTED, false));
      for (SearchArtists.Artist artist : followed) artistRow(artist, listener);
      List<SearchArtists.Artist> recent = new SearchArtists(c).visible();
      recent.removeIf(saved::contains);
      if (!recent.isEmpty()) {
        gap(body, 20);
        section("Recently found", null, () -> {});
        for (SearchArtists.Artist artist : recent) artistRow(artist, listener);
      }
    }
  }

  private void artistRow(SearchArtists.Artist artist, Listener listener) {
    LinearLayout item = row();
    item.setPadding(0, dp(10), 0, dp(10));
    ImageView photo = portrait(getContext(), artist);
    item.addView(photo, new LinearLayout.LayoutParams(dp(56), dp(56)));
    LinearLayout label = column();
    label.setPadding(dp(14), 0, dp(8), 0);
    TextView name = text(artist.name, 17, TEXT, true);
    name.setMaxLines(2);
    name.setEllipsize(TextUtils.TruncateAt.END);
    label.addView(name);
    gap(label, 5);
    label.addView(text(new SavedArtists(getContext()).contains(artist) ? "Following" : "Artist",
        12, MUTED, false));
    item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    Icon arrow = new Icon(getContext(), "chevron", MUTED);
    arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    item.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));
    item.setContentDescription("Open artist " + artist.name);
    item.setFocusable(true);
    item.setOnClickListener(v -> listener.open(artist));
    body.addView(item);
  }

  static ImageView portrait(Context c, SearchArtists.Artist artist) {
    ImageView photo = new ArtistStoriesPanel.CircleImage(c);
    android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
    circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    circle.setColor(PANEL);
    photo.setBackground(circle);
    photo.setClipToOutline(true);
    photo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    ArtworkLoader.load(photo, artist.artwork, new ArtistStoriesPanel.Initials(artist.name));
    return photo;
  }
}
