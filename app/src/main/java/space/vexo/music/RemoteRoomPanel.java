package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.*;
import android.os.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

final class RemoteRoomPanel extends MusicSurface {
  interface Actions {
    void connect();

    void action(JSONObject value);

    void follow(boolean value);

    void leave();
  }

  private final SmartStore store;
  private final LibraryStore library;
  private final Actions actions;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final TextView status;
  private final LinearLayout live;
  private boolean attached = true;
  private String last = "";

  RemoteRoomPanel(Context c, LibraryStore library, Actions actions) {
    super(c);
    this.library = library;
    this.actions = actions;
    store = new SmartStore(c);
    setBackground(Glass.backdrop());
    body.addView(text("LISTEN TOGETHER", 11, LIME, true));
    gap(body, 12);
    body.addView(text("One room. Every device.", 28, TEXT, true));
    gap(body, 12);
    body.addView(
        text(
            "Share an invite to listen together. Your name and queued song details are shared with"
                + " this room for up to 2 hours. Each phone streams its own audio. Up to 8"
                + " listeners.",
            14,
            MUTED,
            false));
    gap(body, 16);
    status = text("", 14, LIME, true);
    body.addView(status);
    gap(body, 12);
    live = column();
    body.addView(live);
    refresh();
    main.postDelayed(poll, 1000);
  }

  private TextView button(String label, Runnable r) {
    TextView b = action(label, false, r);
    Glass.apply(b, 18, false);
    body.addView(b);
    gap(body, 10);
    return b;
  }

  private EditText input(String hint) {
    EditText e = new EditText(getContext());
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setHint(hint);
    e.setTextSize(15);
    e.setPadding(dp(12), dp(12), dp(12), dp(12));
    body.addView(e, new LinearLayout.LayoutParams(-1, -2));
    gap(body, 10);
    return e;
  }

  private interface Job {
    JSONObject run() throws Exception;
  }

  private void work(Job job) {
    status.setText("Connecting…");
    io.execute(
        () -> {
          try {
            JSONObject result = job.run();
            main.post(
                () -> {
                  if (!attached) return;
                  try {
                    store.preference("room_session", result);
                    store.preference("room_follow", true);
                    actions.connect();
                    last = "";
                    refresh();
                  } catch (Exception e) {
                    status.setText("Couldn’t save private room data");
                  }
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  if (attached)
                    status.setText(
                        e.getMessage() == null ? "Couldn’t connect. Try again." : e.getMessage());
                });
          }
        });
  }

  private final Runnable poll =
      new Runnable() {
        public void run() {
          if (!attached) return;
          refresh();
          main.postDelayed(this, 1000);
        }
      };

  private void refresh() {
    try {
      JSONObject p = store.preferences(),
          session = p.optJSONObject("room_session"),
          state = p.optJSONObject("room_public");
      String key =
          (session == null ? "none" : session.optString("room"))
              + "|"
              + (state == null ? "" : state.toString());
      String connection =
          getContext().getSharedPreferences("playback_state", 0).getString("room_status", "");
      if (!connection.isEmpty()) status.setText(connection);
      if (key.equals(last)) return;
      last = key;
      while (body.getChildCount() > body.indexOfChild(live) + 1)
        body.removeViewAt(body.getChildCount() - 1);
      live.removeAllViews();
      if (session == null) {
        EditText name = input("Your name");
        name.setText(new UserProfile(getContext()).name());
        button(
            "Create a listening room",
            () -> work(() -> new CloudApi(getContext()).create(name.getText().toString())));
        EditText invite = input("Paste the host’s full invite code");
        button(
            "Join a room",
            () ->
                work(
                    () ->
                        new CloudApi(getContext())
                            .join(invite.getText().toString(), name.getText().toString())));
        return;
      }
      boolean host = session.optBoolean("host");
      live.addView(text(host ? "You’re hosting" : "You joined the room", 20, TEXT, true));
      gap(live, 10);
      if (host && session.has("invite")) {
        String code = "VEXO-" + session.optString("room") + "." + session.optString("invite");
        TextView invitation = text(code, 12, MUTED, false);
        invitation.setTextIsSelectable(true);
        live.addView(invitation);
        button(
            "Copy invite code",
            () -> {
              ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("VEXO room invite", code));
              VexoNotice.show(getContext(), "Invite copied");
            });
      }
      button("Reconnect to this room", actions::connect);
      if (!host)
        button(
            p.optBoolean("room_follow", true) ? "Pause following the host" : "Follow host playback",
            () -> {
              boolean follow = !store.preferences().optBoolean("room_follow", true);
              actions.follow(follow);
              last = "";
              refresh();
            });
      if (host) button("Play next · fair rotation", () -> send("next", null));
      button("Add a song request", this::add);
      if (state != null) {
        JSONArray members = state.optJSONArray("members"), queue = state.optJSONArray("queue");
        live.addView(
            text(
                (members == null ? 0 : members.length()) + " listeners · fair song rotation",
                14,
                LIME,
                true));
        gap(live, 12);
        if (queue != null)
          for (int i = 0; i < queue.length(); i++) {
            JSONObject q = queue.optJSONObject(i);
            if (q == null) continue;
            Track t = Track.from(q.optJSONObject("track"));
            TextView row = text(q.optString("name") + " · " + t.title, 14, TEXT, false);
            row.setPadding(0, dp(8), 0, dp(8));
            live.addView(row);
            if (host || session.optString("member").equals(q.optString("member"))) {
              TextView remove =
                  action("Remove request", false, () -> send("remove", q.optString("id")));
              live.addView(remove);
            }
          }
      }
      button(
          host ? "End room for everyone" : "Leave room",
          () -> {
            actions.leave();
            last = "";
            main.postDelayed(this::refresh, 250);
          });
      if (host && session.has("invite"))
        button(
            "Share invite",
            () -> {
              String code = "VEXO-" + session.optString("room") + "." + session.optString("invite");
              Intent share =
                  new Intent(Intent.ACTION_SEND)
                      .setType("text/plain")
                      .putExtra(
                          Intent.EXTRA_TEXT,
                          "Join my VEXO Music listening room. Invite code: " + code);
              getContext().startActivity(Intent.createChooser(share, "Share room invite"));
            });
    } catch (Exception e) {
      status.setText("Private room data unavailable. Try reopening this screen.");
    }
  }

  private void send(String type, String id) {
    try {
      JSONObject value = new JSONObject().put("type", type);
      if (id != null) value.put("id", id);
      actions.action(value);
    } catch (JSONException ignored) {
    }
  }

  private void add() {
    List<Track> songs = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String key : new String[] {"favourites", "history"})
      for (Track t : library.tracks(key)) if (RadioPolicy.music(t) && seen.add(t.url)) songs.add(t);
    new VexoDialogs.Builder(getContext())
        .setTitle("Request a song · 3 per listener")
        .setItems(
            songs.stream().map(t -> t.title + " · " + t.artist).toArray(String[]::new),
            (d, i) -> {
              try {
                actions.action(
                    new JSONObject().put("type", "add").put("track", songs.get(i).json()));
              } catch (JSONException ignored) {
              }
            })
        .setNegativeButton("Close", null)
        .show();
  }

  @Override
  protected void onDetachedFromWindow() {
    attached = false;
    main.removeCallbacksAndMessages(null);
    io.shutdownNow();
    super.onDetachedFromWindow();
  }
}
