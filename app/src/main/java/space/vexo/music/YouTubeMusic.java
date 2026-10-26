package space.vexo.music;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Google read-only metadata import. Bearer credentials are sent only to this fixed API origin. */
final class YouTubeMusic {
  static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
  private static final OkHttpClient HTTP = new OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
      .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
  private volatile Call active;
  private volatile boolean cancelled;
  interface PageFetcher { JSONObject get(String token, String page) throws IOException; }
  private final PageFetcher fetcher;

  YouTubeMusic() { fetcher = this::page; }
  YouTubeMusic(PageFetcher fetcher) { this.fetcher = fetcher; }

  void cancel() {
    cancelled = true;
    Call call = active;
    if (call != null) call.cancel();
  }

  List<Track> likes(String token) throws IOException {
    if (token == null || token.isEmpty() || token.length() > 16384)
      throw new IOException("Google did not grant YouTube read access. Please try connecting again.");
    LinkedHashMap<String, Track> songs = new LinkedHashMap<>();
    String next = "";
    Set<String> pages = new HashSet<>();
    for (int page = 0; page < 4 && songs.size() < 100; page++) {
      if (cancelled) throw new IOException("Cancelled");
      JSONObject data = fetcher.get(token, next);
      for (Track song : parse(data)) {
        songs.put(song.url, song);
        if (songs.size() >= 100) break;
      }
      next = data.optString("nextPageToken");
      if (next.isEmpty() || next.length() > 1024 || !pages.add(next)) break;
    }
    return new ArrayList<>(songs.values());
  }

  private JSONObject page(String token, String next) throws IOException {
      HttpUrl.Builder url = Objects.requireNonNull(HttpUrl.parse("https://www.googleapis.com/youtube/v3/videos"))
          .newBuilder().addQueryParameter("part", "snippet,contentDetails")
          .addQueryParameter("myRating", "like").addQueryParameter("maxResults", "50")
          .addQueryParameter("fields", "nextPageToken,items(id,snippet(title,channelTitle,categoryId,liveBroadcastContent,thumbnails),contentDetails(duration))");
      if (!next.isEmpty()) url.addQueryParameter("pageToken", next);
      Request request = new Request.Builder().url(url.build())
          .header("Authorization", "Bearer " + token).build();
      active = HTTP.newCall(request);
      if (cancelled) active.cancel();
      try (Response response = active.execute()) {
        if (!response.isSuccessful()) throw new IOException(error(response.code()));
        ResponseBody body = response.body();
        if (body == null || body.contentLength() > 1024 * 1024)
          throw new IOException("YouTube returned an incomplete response. Try again later.");
        // Bound unknown-length responses too; never copy arbitrary API error bodies into the UI.
        okio.Buffer buffer = new okio.Buffer();
        long read;
        while ((read = body.source().read(buffer, 8192)) != -1) {
          if (buffer.size() > 1024 * 1024) throw new IOException("YouTube response is too large.");
        }
        return new JSONObject(buffer.readUtf8());
      } catch (JSONException invalid) {
        throw new IOException("Couldn’t read your liked music. Please try again.");
      } finally {
        active = null;
      }
  }

  static String error(int code) {
    if (code == 401) return "Your Google permission expired. Connect YouTube again.";
    if (code == 403) return "YouTube access isn’t available yet. The app’s Google API or permission setup may need attention.";
    if (code == 429) return "YouTube is busy. Please try again later.";
    return "Couldn’t reach YouTube. Check your connection and try again.";
  }

  static List<Track> parse(JSONObject data) {
    LinkedHashMap<String, Track> out = new LinkedHashMap<>();
    JSONArray items = data.optJSONArray("items");
    if (items == null) return new ArrayList<>();
    for (int i = 0; i < Math.min(50, items.length()); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      JSONObject snippet = item.optJSONObject("snippet"), details = item.optJSONObject("contentDetails");
      String id = item.optString("id");
      if (!id.matches("[A-Za-z0-9_-]{11}") || snippet == null || details == null
          || !"10".equals(snippet.optString("categoryId"))
          || !"none".equals(snippet.optString("liveBroadcastContent", "none"))) continue;
      long duration;
      try { duration = Duration.parse(details.optString("duration")).getSeconds(); }
      catch (Exception invalid) { continue; }
      String title = snippet.optString("title").trim(), artist = snippet.optString("channelTitle").trim();
      if (title.length() > 500 || artist.length() > 250) continue;
      String artwork = "";
      JSONObject thumbs = snippet.optJSONObject("thumbnails");
      if (thumbs != null) for (String size : new String[] {"maxres", "standard", "high", "medium", "default"}) {
        JSONObject thumb = thumbs.optJSONObject(size);
        if (thumb == null) continue;
        HttpUrl image = HttpUrl.parse(thumb.optString("url"));
        if (image != null && image.isHttps() && image.host().equals("i.ytimg.com")
            && image.username().isEmpty() && image.password().isEmpty()) {
          artwork = image.toString();
          break;
        }
      }
      Track song = new Track("https://www.youtube.com/watch?v=" + id, title, artist, artwork, duration);
      if (valid(song)) out.put(song.url, song);
    }
    return new ArrayList<>(out.values());
  }

  static boolean valid(Track track) {
    return track != null && track.url.matches("https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}")
        && track.title.length() <= 500 && track.artist.length() <= 250 && RadioPolicy.music(track);
  }

  interface Finder { List<Track> related(Track seed) throws Exception; }

  static List<Track> picks(List<Track> likes, Finder finder) {
    LinkedHashMap<String, Track> pool = new LinkedHashMap<>();
    Set<String> seeds = new HashSet<>();
    for (Track seed : likes) {
      if (Thread.currentThread().isInterrupted() || seeds.size() >= 3) break;
      if (!seeds.add(RadioPolicy.artist(seed))) continue;
      try {
        for (Track candidate : finder.related(seed))
          if (valid(candidate)) pool.putIfAbsent(candidate.url, candidate);
      } catch (Exception unavailable) {
        // A valid liked-music import can still succeed while music discovery is unavailable.
      }
    }
    if (likes.isEmpty()) return Collections.emptyList();
    return RadioPolicy.select(likes.get(0), likes.get(0), new ArrayList<>(pool.values()),
        likes, Collections.emptyList(), likes, Collections.emptySet(), RadioPolicy.Mode.BALANCED, 10);
  }
}
