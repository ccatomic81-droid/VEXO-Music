package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.app.*;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

/**
 * Native, scrollable smart tools. Every available action works with the existing player/library.
 */
final class SmartExperiencePanel extends MusicSurface {
  interface Listener {
    void play(List<Track> tracks, int index, long moment);

    void discover(String query, boolean newArtists);

    default void fair(List<Track> tracks) {
      play(tracks, 0, 0);
    }

    void changed();
  }

  private final SmartStore smart;
  private final LibraryStore library;
  private final Listener listener;

  SmartExperiencePanel(Context c, SmartStore smart, LibraryStore library, Listener listener) {
    super(c);
    this.smart = smart;
    this.library = library;
    this.listener = listener;
    setBackgroundColor(BG);
    menu();
  }

  private void safe(Runnable action) {
    try {
      action.run();
    } catch (IllegalStateException e) {
      new AlertDialog.Builder(getContext())
          .setMessage(e.getMessage())
          .setPositiveButton("Close", null)
          .show();
    }
  }

  private void screen(String title) {
    body.removeAllViews();
    scrollTo(0, 0);
    if (!title.equals("Your music. Your way."))
      body.addView(action("‹ Smart experience", false, this::menu));
    else {
      TextView brand = text("VEXO MUSIC  /  SMART EXPERIENCE", 11, LIME, true);
      brand.setLetterSpacing(.08f);
      body.addView(brand);
    }
    gap(body, 18);
    body.addView(text(title, 26, TEXT, true));
    gap(body, 12);
  }

  private void note(String value) {
    body.addView(text(value, 14, MUTED, false));
    gap(body, 14);
  }

  private void button(String label, Runnable action) {
    TextView control = action(label, false, () -> safe(action));
    control.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    control.setBackground(round(PANEL, dp(16)));
    body.addView(control);
    gap(body, 8);
  }

  void menu() {
    screen("Your music. Your way.");
    note("Private tools on this phone. Choose how your music feels.");
    tool("VEXO MindRadio", "Set the mood. Shape what plays next.", this::mind);
    tool(
        "Recommendation controls & anti-repeat",
        "A better balance of familiar and fresh.",
        this::preferences);
    tool("Find a forgotten song", "Follow a clue back to a past listen.", this::find);
    tool("Song memories", "Keep a note. Return to a favourite moment.", this::memories);
    tool("Playlist Time Machine", "Recover songs and revisit earlier versions.", this::revisions);
    tool("FairPlay · on this phone", "Everyone gets a turn in the queue.", this::group);
    tool("Discovery adventure", "Take a new artist or regional trail.", this::adventure);
    tool("Customize Home", "Keep the sections that matter to you.", this::home);
    tool("Privacy & listening history", "Pause recording or clear your history.", this::privacy);
  }

  private void tool(String title, String detail, Runnable open) {
    LinearLayout card = column();
    card.setPadding(dp(16), dp(16), dp(16), dp(16));
    card.setBackground(round(PANEL, dp(18)));
    card.setMinimumHeight(dp(80));
    LinearLayout heading = row();
    TextView label = text(title, 16, TEXT, true);
    heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    TextView arrow = text("›", 24, LIME, false);
    arrow.setPadding(dp(12), 0, 0, 0);
    heading.addView(arrow);
    card.addView(heading);
    gap(card, 7);
    card.addView(text(detail, 12, MUTED, false));
    card.setContentDescription(title + ". " + detail);
    card.setOnClickListener(v -> safe(open));
    // Keep the label independently accessible to keyboard and touch navigation.
    label.setOnClickListener(v -> safe(open));
    body.addView(card, new LinearLayout.LayoutParams(-1, -2));
    gap(body, 10);
  }

  private EditText input(String hint, String value) {
    EditText e = new EditText(getContext());
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setTextSize(16);
    e.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(1000)});
    e.setHint(hint);
    e.setText(value);
    e.setSingleLine(false);
    e.setPadding(dp(12), dp(12), dp(12), dp(12));
    body.addView(e, new LinearLayout.LayoutParams(-1, -2));
    gap(body, 10);
    return e;
  }

  private void choice(
      String label, String[] values, String current, java.util.function.Consumer<String> save) {
    TextView caption = text(label, 14, TEXT, true);
    body.addView(caption);
    Spinner spinner = new Spinner(getContext());
    String[] display =
        Arrays.stream(values).map(value -> value.isEmpty() ? "Any" : value).toArray(String[]::new);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, display);
    spinner.setAdapter(adapter);
    int selected = Arrays.asList(values).indexOf(current);
    spinner.setSelection(Math.max(0, selected));
    body.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));
    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> p) {}

          public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            safe(() -> save.accept(values[pos]));
          }
        });
    gap(body, 10);
  }

  private void mind() {
    screen("VEXO MindRadio");
    note(
        "Describe what you want: calm Sinhala music, Tamil dance, or an artist. Uses search clues"
            + " and your controls on this device. Cloud AI conversation is not connected yet.");
    EditText command = input("What would you like to hear?", "");
    button(
        "Build my radio",
        () -> {
          smart.preference("radio_command", command.getText().toString().trim());
          listener.discover(
              SmartPolicy.query(smart.preferences(), command.getText().toString()), false);
        });
    button("Adjust mood, energy & artists", this::preferences);
  }

  private void preferences() {
    screen("Tune your recommendations");
    org.json.JSONObject p = smart.preferences();
    note(
        "Mood, energy, genre and language guide searches. Ranking uses listening history and"
            + " explicit feedback. Audio analysis and popularity filtering need a catalogue that"
            + " supplies those fields.");
    EditText genres = input("Genres · e.g. pop, acoustic", p.optString("genres"));
    EditText artists = input("Preferred artists", p.optString("artists"));
    choice(
        "Language",
        new String[] {"", "Sinhala", "Tamil", "English"},
        p.optString("language"),
        v -> smart.preference("language", v));
    choice(
        "Mood",
        new String[] {"", "calm", "happy", "sad", "focus", "romantic"},
        p.optString("mood"),
        v -> smart.preference("mood", v));
    choice(
        "Energy",
        new String[] {"", "acoustic mellow", "upbeat", "dance energetic"},
        p.optString("energy"),
        v -> smart.preference("energy", v));
    choice(
        "Discovery · 0 familiar / 100 new artists",
        new String[] {"0", "25", "50", "75", "100"},
        "" + p.optInt("discovery", 50),
        v -> smart.preference("discovery", Integer.parseInt(v)));
    choice(
        "Avoid played songs for (hours)",
        new String[] {"0", "1", "6", "24", "72", "168", "720"},
        "" + p.optInt("repeat_hours", 24),
        v -> smart.preference("repeat_hours", Integer.parseInt(v)));
    button(
        "Save artist & genre preferences",
        () -> {
          smart.preference(
              "genres",
              genres
                  .getText()
                  .toString()
                  .trim()
                  .substring(0, Math.min(100, genres.getText().toString().trim().length())));
          smart.preference(
              "artists",
              artists
                  .getText()
                  .toString()
                  .trim()
                  .substring(0, Math.min(200, artists.getText().toString().trim().length())));
          listener.changed();
          Toast.makeText(
                  getContext(),
                  "Preferences saved · future radio refills use these",
                  Toast.LENGTH_SHORT)
              .show();
        });
    button(
        "Forgotten favourites",
        () ->
            songs(
                "Forgotten favourites · not played in 30 days",
                SmartPolicy.forgotten(
                    library.tracks("favourites"), smart.events(), System.currentTimeMillis())));
  }

  private long from = 0, to = Long.MAX_VALUE;

  private void date(String title, java.util.function.LongConsumer consumer, boolean end) {
    Calendar c = Calendar.getInstance();
    DatePickerDialog d =
        new DatePickerDialog(
            getContext(),
            (v, y, m, day) -> {
              Calendar selected = Calendar.getInstance();
              selected.set(y, m, day, end ? 23 : 0, end ? 59 : 0, end ? 59 : 0);
              selected.set(Calendar.MILLISECOND, end ? 999 : 0);
              consumer.accept(selected.getTimeInMillis());
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH));
    d.setTitle(title);
    d.show();
  }

  private void find() {
    from = 0;
    to = Long.MAX_VALUE;
    screen("Find a forgotten song");
    note(
        "Search titles and artists from recorded plays, with spelling tolerance. Descriptive clues"
            + " work when they appear in metadata. Lyric matching needs a licensed lyrics service."
            + " Plays before this upgrade have no known dates.");
    EditText clue = input("Song, artist, language or words you remember", "");
    TextView range = text("All dates", 14, LIME, false);
    body.addView(range);
    button(
        "From date",
        () ->
            date(
                "From date",
                v -> {
                  from = v;
                  range.setText("From " + DateFormat.getDateInstance().format(new Date(v)));
                },
                false));
    button(
        "Until date",
        () ->
            date(
                "Until date",
                v -> {
                  to = v;
                  range.setText("Until " + DateFormat.getDateInstance().format(new Date(v)));
                },
                true));
    button(
        "Any date",
        () -> {
          from = 0;
          to = Long.MAX_VALUE;
          range.setText("All dates");
        });
    button(
        "Search my listening history",
        () -> {
          if (from > to) {
            note("Choose an end date after the start date.");
            return;
          }
          List<Track> found = SmartPolicy.find(smart.events(), clue.getText().toString(), from, to);
          if (from == 0 && to == Long.MAX_VALUE) {
            JSONArray legacy = new JSONArray();
            for (Track t : library.tracks("history"))
              try {
                legacy.put(new JSONObject().put("track", t.json()).put("at", 0));
              } catch (JSONException ignored) {
              }
            LinkedHashMap<String, Track> unique = new LinkedHashMap<>();
            for (Track t : found) unique.put(t.url, t);
            for (Track t : SmartPolicy.find(legacy, clue.getText().toString(), 0, Long.MAX_VALUE))
              unique.putIfAbsent(t.url, t);
            found = new ArrayList<>(unique.values());
          }
          songs("History matches", found);
        });
  }

  private void songs(String title, List<Track> songs) {
    screen(title);
    note(
        songs.isEmpty()
            ? "No matches yet. Try fewer clues or listen to more music."
            : songs.size() + " songs · tap to play; hold for feedback or a memory");
    for (int i = 0; i < Math.min(100, songs.size()); i++) {
      int index = i;
      Track t = songs.get(i);
      TextView row = action(t.title + "\n" + t.artist, false, () -> listener.play(songs, index, 0));
      row.setOnLongClickListener(
          v -> {
            track(t, 0);
            return true;
          });
      body.addView(row);
      gap(body, 8);
    }
  }

  void track(Track t, long moment) {
    screen(t.title);
    note(t.artist);
    button("Save a song memory", () -> addMemory(t, moment));
    String[]
        labels =
            {
              "More like this",
              "Less of this song",
              "Take a 7-day break",
              "Never recommend this song",
              "Reset song feedback"
            },
        values = {"more", "less", "break", "never", "reset"};
    for (int i = 0; i < labels.length; i++) {
      String value = values[i];
      button(
          labels[i],
          () -> {
            smart.feedback(t, value);
            listener.changed();
            Toast.makeText(
                    getContext(), "Feedback saved for future recommendations", Toast.LENGTH_SHORT)
                .show();
          });
    }
  }

  private void addMemory(Track t, long moment) {
    screen("Save this moment");
    note(t.title + " · " + t.artist);
    EditText note = input("Your private note", "");
    EditText seconds = input("Favourite moment in seconds", Long.toString(moment / 1000));
    seconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    button(
        "Save memory",
        () -> {
          long at;
          try {
            at = Math.multiplyExact(Long.parseLong(seconds.getText().toString()), 1000L);
          } catch (Exception e) {
            Toast.makeText(getContext(), "Enter a valid moment in seconds", Toast.LENGTH_SHORT)
                .show();
            return;
          }
          if (at < 0 || at > (t.duration > 0 ? t.duration * 1000 : 86400000L)) {
            Toast.makeText(getContext(), "Choose a moment within this song", Toast.LENGTH_SHORT)
                .show();
            return;
          }
          smart.memory(t, note.getText().toString(), at, System.currentTimeMillis());
          memories();
        });
  }

  private void memories() {
    screen("Your memory timeline");
    JSONArray a = smart.memories();
    note(
        a.length() == 0
            ? "Save a memory from any song’s menu."
            : "Only on this phone · hold a memory to delete it.");
    for (int i = 0; i < a.length(); i++) {
      JSONObject m = a.optJSONObject(i);
      if (m == null) continue;
      Track t = Track.from(m.optJSONObject("track"));
      TextView row =
          action(
              DateFormat.getDateTimeInstance().format(new Date(m.optLong("at")))
                  + "\n"
                  + t.title
                  + " · "
                  + m.optLong("moment") / 1000
                  + "s\n"
                  + m.optString("note"),
              false,
              () -> listener.play(Collections.singletonList(t), 0, m.optLong("moment")));
      row.setOnLongClickListener(
          v -> {
            new AlertDialog.Builder(getContext())
                .setMessage("Delete this memory?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Delete",
                    (d, w) ->
                        safe(
                            () -> {
                              smart.removeMemory(m.optString("id"));
                              memories();
                            }))
                .show();
            return true;
          });
      body.addView(row);
      gap(body, 10);
    }
  }

  private void revisions() {
    screen("Playlist Time Machine");
    JSONArray a = smart.revisions();
    note(
        "Snapshots start with this upgrade. Keeps the latest 20 per playlist, up to 100 total."
            + " Restore a deleted track or an entire version. Existing playlists are kept unless"
            + " you explicitly replace them.");
    for (int i = 0; i < a.length(); i++) {
      JSONObject r = a.optJSONObject(i);
      if (r == null) continue;
      button(
          r.optString("name")
              + " · "
              + DateFormat.getDateTimeInstance().format(new Date(r.optLong("at")))
              + "\n"
              + r.optString("reason")
              + " · "
              + r.optJSONArray("tracks").length()
              + " songs",
          () -> revision(r));
    }
  }

  private void revision(JSONObject r) {
    screen(r.optString("name"));
    List<Track> tracks = SmartStore.tracks(r.optJSONArray("tracks"));
    button(
        "Restore as a separate playlist",
        () -> {
          String name = library.savePlaylistCopy(r.optString("name") + " recovered", tracks);
          listener.changed();
          note("Recovered as " + name);
        });
    button(
        "Restore this version in place",
        () ->
            new AlertDialog.Builder(getContext())
                .setMessage(
                    "Replace the current playlist named “"
                        + r.optString("name")
                        + "”? Its current state will become an undo snapshot.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Restore",
                    (d, w) ->
                        safe(
                            () -> {
                              String name = r.optString("name");
                              library.createPlaylist(name);
                              library.save("playlist:" + name, tracks);
                              listener.changed();
                              revisions();
                            }))
                .show());
    for (Track t : tracks)
      button(
          "Recover track: " + t.title,
          () -> {
            List<String> names = library.playlists();
            new AlertDialog.Builder(getContext())
                .setTitle("Recover into playlist")
                .setItems(
                    names.toArray(new String[0]),
                    (d, w) ->
                        safe(
                            () -> {
                              library.addToPlaylist(names.get(w), t);
                              listener.changed();
                              note("Track recovered");
                            }))
                .setNegativeButton("Close", null)
                .show();
          });
  }

  private void adventure() {
    screen("Discovery adventure");
    note(
        "Explore artists you have not played on this phone. Results are filtered against your"
            + " history and feedback. Hidden gems is an exploration search, not a verified"
            + " popularity measurement.");
    button(
        "New artist trail",
        () -> listener.discover(SmartPolicy.query(smart.preferences(), "new artists"), true));
    button(
        "Hidden gems",
        () ->
            listener.discover(
                SmartPolicy.query(smart.preferences(), "independent emerging artists"), true));
    button(
        "Explore Sri Lanka",
        () -> listener.discover("Sri Lankan Sinhala Tamil independent music", true));
    button("Explore South India", () -> listener.discover("Tamil independent music", true));
    button(
        "Forgotten favourites",
        () ->
            songs(
                "Forgotten favourites",
                SmartPolicy.forgotten(
                    library.tracks("favourites"), smart.events(), System.currentTimeMillis())));
  }

  static final List<String> MODULES =
      Arrays.asList("Shortcuts", "Mood mixes", "Playlists", "Recently played", "Regional music");

  static List<String> order(JSONObject p) {
    List<String> out = new ArrayList<>();
    JSONArray a = p.optJSONArray("home_order");
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        String id = a.optString(i);
        if (MODULES.contains(id) && !out.contains(id)) out.add(id);
      }
    for (String id : MODULES) if (!out.contains(id)) out.add(id);
    return out;
  }

  private void home() {
    screen("Make Home yours");
    JSONObject p = smart.preferences();
    button(
        p.optBoolean("focus") ? "Turn off distraction-free mode" : "Enable distraction-free mode",
        () -> {
          smart.preference("focus", !p.optBoolean("focus"));
          listener.changed();
          home();
        });
    note(
        "Distraction-free mode keeps shortcuts and pinned playlists. Tap a module to hide or show"
            + " it; use Move up to rearrange.");
    List<String> order = order(p);
    for (int i = 0; i < order.size(); i++) {
      String id = order.get(i);
      button(
          (p.optBoolean("hide:" + id) ? "○ Hidden · " : "● Shown · ") + id,
          () -> {
            smart.preference("hide:" + id, !p.optBoolean("hide:" + id));
            listener.changed();
            home();
          });
      if (i > 0) {
        int at = i;
        button(
            "↑ Move " + id + " up",
            () -> {
              Collections.swap(order, at, at - 1);
              smart.preference("home_order", new JSONArray(order));
              listener.changed();
              home();
            });
      }
    }
    button(
        "Choose pinned playlists",
        () -> {
          List<String> names = library.playlists();
          boolean[] selected = new boolean[names.size()];
          JSONArray saved = p.optJSONArray("pins");
          for (int i = 0; i < names.size(); i++)
            if (saved != null)
              for (int j = 0; j < saved.length(); j++)
                if (names.get(i).equals(saved.optString(j))) selected[i] = true;
          new AlertDialog.Builder(getContext())
              .setTitle("Pin favourite playlists")
              .setMultiChoiceItems(
                  names.toArray(new String[0]), selected, (d, w, checked) -> selected[w] = checked)
              .setNegativeButton("Cancel", null)
              .setPositiveButton(
                  "Save",
                  (d, w) ->
                      safe(
                          () -> {
                            JSONArray pins = new JSONArray();
                            for (int i = 0; i < names.size(); i++)
                              if (selected[i]) pins.put(names.get(i));
                            smart.preference("pins", pins);
                            listener.changed();
                            home();
                          }))
              .show();
        });
  }

  private void privacy() {
    screen("Your private music data");
    JSONObject p = smart.preferences();
    note(
        "Timestamped plays, notes, preferences, feedback and playlist revisions stay encrypted on"
            + " this device with an Android Keystore key. No cloud account or sync. New history"
            + " keeps up to 1,000 plays, memories 200. Clearing app data or uninstalling removes"
            + " these records. Existing saved music is retained.");
    button(
        p.optBoolean("pause_history") ? "Resume listening history" : "Pause listening history",
        () -> {
          smart.preference("pause_history", !p.optBoolean("pause_history"));
          privacy();
        });
    button(
        "Clear listening history",
        () ->
            new AlertDialog.Builder(getContext())
                .setMessage(
                    "Clear timestamped plays and the older recently played list? Memories and"
                        + " playlists stay.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Clear",
                    (d, w) ->
                        safe(
                            () -> {
                              smart.clearHistory();
                              library.save("history", Collections.emptyList());
                              listener.changed();
                              privacy();
                            }))
                .show());
  }

  private void group() {
    screen("FairPlay on this phone");
    note(
        "Pass this phone around to collect requests. Each participant gets one song per round."
            + " Multi-device sessions need an authenticated realtime service and are not"
            + " connected.");
    EditText person = input("Participant name", "");
    List<Track> available = library.tracks("favourites");
    available.addAll(library.tracks("history"));
    button(
        "Add a request from saved music",
        () -> {
          String name = person.getText().toString().trim();
          if (name.isEmpty() || name.length() > 40) {
            note("Enter a name of 1–40 characters.");
            return;
          }
          new AlertDialog.Builder(getContext())
              .setTitle("Choose a song")
              .setItems(
                  available.stream().map(t -> t.title).toArray(String[]::new),
                  (d, w) ->
                      safe(
                          () -> {
                            FairPlayPolicy.add(smart, name, available.get(w));
                            group();
                          }))
              .setNegativeButton("Close", null)
              .show();
        });
    List<FairPlayPolicy.Request> requests = FairPlayPolicy.requests(smart);
    List<Track> queue = FairPlayPolicy.rotate(requests);
    button(
        "Play fair rotation (" + queue.size() + " songs)",
        () -> {
          if (!queue.isEmpty()) listener.fair(queue);
        });
    for (FairPlayPolicy.Request r : requests)
      button(
          r.person + " · " + r.track.title + "  ×",
          () -> {
            FairPlayPolicy.remove(smart, r.id);
            group();
          });
    button(
        "Clear this session",
        () -> {
          smart.preference("group_requests", new JSONArray());
          group();
        });
  }
}
