package space.vexo.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.HttpUrl;
import org.json.*;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;

/** Public, music-filtered WEB_REMIX catalogue. No account cookies or private session APIs. */
final class MusicSearch {
  static final String SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D";
  static final String VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D";

  static final class Result {
    final List<Track> tracks;
    final String query, country, continuation;

    Result(List<Track> tracks, String query, String country, String continuation) {
      this.tracks = tracks;
      this.query = query;
      this.country = country;
      this.continuation = continuation;
    }
  }

  static Result load(String query, String country) throws Exception {
    // Keep the catalogue on Songs even when there are no matches.
    return parse(fetch(query, country, SONGS, ""), query, country);
  }

  static Result more(Result previous) throws Exception {
    if (previous.continuation.isEmpty())
      return new Result(Collections.emptyList(), previous.query, previous.country, "");
    Result page =
        parse(
            fetch("", previous.country, "", previous.continuation),
            previous.query,
            previous.country);
    if (page.continuation.equals(previous.continuation))
      return new Result(page.tracks, page.query, page.country, "");
    return page;
  }

  private static JSONObject fetch(String query, String country, String filter, String token)
      throws Exception {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    OnlineMusic.initialize();
    JSONObject client = client(country, YoutubeParsingHelper.getYoutubeMusicClientVersion());
    JSONObject body = new JSONObject().put("context", new JSONObject().put("client", client));
    if (token.isEmpty()) body.put("query", query).put("params", filter);
    String url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false";
    if (!token.isEmpty()) url += "&continuation=" + java.net.URLEncoder.encode(token, "UTF-8");
    // Mobile responses omit artist browse endpoints, even with platform=DESKTOP.
    // Request the desktop metadata shape so artist identity survives search and pagination.
    Map<String, List<String>> headers = new HashMap<>(YoutubeParsingHelper.getYoutubeMusicHeaders());
    headers.put("User-Agent", Collections.singletonList(
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/131.0.0.0 Safari/537.36"));
    Response response =
        NewPipe.getDownloader()
            .postWithContentTypeJson(
                url,
                headers,
                body.toString().getBytes(StandardCharsets.UTF_8));
    String text = response.responseBody();
    if (response.responseCode() != 200 || text == null || text.length() > 4 * 1024 * 1024)
      throw new IOException("YouTube Music search is temporarily unavailable");
    JSONObject data = new JSONObject(text);
    if (!data.has("contents")
        && !data.has("continuationContents")
        && !data.has("onResponseReceivedCommands"))
      throw new IOException("YouTube Music search returned an incomplete page");
    return data;
  }

  static JSONObject client(String country, String version) throws JSONException {
    JSONObject client =
        new JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", version)
            .put("hl", "en")
            .put("platform", "DESKTOP")
            .put("utcOffsetMinutes", 0);
    String providerCountry = RegionPolicy.providerCountry(country);
    if (!providerCountry.isEmpty()) client.put("gl", providerCountry);
    return client;
  }

  static Result parse(JSONObject data, String query, String country) {
    LinkedHashMap<String, Track> songs = new LinkedHashMap<>();
    String[] next = {""};
    walk(data, songs, next, 0, new int[] {0});
    return new Result(
        new ArrayList<>(songs.values()), query, country, songs.isEmpty() ? "" : next[0]);
  }

  private static void walk(
      Object value, Map<String, Track> out, String[] next, int depth, int[] visited) {
    if (depth > 40 || visited[0]++ > 20000 || out.size() >= 100) return;
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < Math.min(500, array.length()); i++)
        walk(array.opt(i), out, next, depth + 1, visited);
    } else if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      JSONObject row = object.optJSONObject("musicResponsiveListItemRenderer");
      if (row != null) {
        Track song = track(row);
        if (song != null) out.putIfAbsent(song.url, song);
        return;
      }
      if (next[0].isEmpty()) {
        JSONObject cursor = object.optJSONObject("nextContinuationData");
        if (cursor == null) cursor = object.optJSONObject("continuationCommand");
        if (cursor != null && cursor.optString("continuation").length() <= 8192)
          next[0] = cursor.optString("continuation");
      }
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) walk(object.opt(keys.next()), out, next, depth + 1, visited);
    }
  }

  private static Track track(JSONObject row) {
    if (row.optString("musicItemRendererDisplayPolicy").contains("GREY_OUT")
        || row.optBoolean("isUnplayable")
        || podcast(row)) return null;
    String type = find(row, "musicVideoType", 0);
    if (!type.isEmpty() && !type.equals("MUSIC_VIDEO_TYPE_ATV")) return null;
    JSONArray columns = row.optJSONArray("flexColumns");
    if (columns == null || columns.length() == 0) return null;
    JSONObject titleText = column(columns.optJSONObject(0));
    String title = text(titleText);
    JSONObject item = row.optJSONObject("playlistItemData");
    String id = item == null ? "" : item.optString("videoId");
    if (id.isEmpty()) id = video(row.optJSONObject("navigationEndpoint"));
    if (id.isEmpty()) id = video(titleText);
    if (id.isEmpty()) id = video(row.optJSONObject("overlay"));
    if (!id.matches("[A-Za-z0-9_-]{11}") || title.isEmpty() || title.length() > 500) return null;
    LinkedHashSet<String> artists = new LinkedHashSet<>();
    List<ArtistCredit> credits = new ArrayList<>();
    long duration = 0;
    String fallback = "";
    for (int i = 1; i < Math.min(columns.length(), 8); i++) {
      JSONObject line = column(columns.optJSONObject(i));
      if (fallback.isEmpty()) fallback = text(line).split("[•·]")[0].trim();
      duration = Math.max(duration, duration(line));
      JSONArray runs = line == null ? null : line.optJSONArray("runs");
      if (runs == null) continue;
      for (int j = 0; j < Math.min(40, runs.length()); j++) {
        JSONObject run = runs.optJSONObject(j);
        JSONObject endpoint = run == null ? null : run.optJSONObject("navigationEndpoint");
        JSONObject browse = endpoint == null ? null : endpoint.optJSONObject("browseEndpoint");
        if (browse != null && browse.optString("browseId").startsWith("UC")) {
          String artistName = run.optString("text").trim();
          if (artists.add(artistName))
            credits.add(new ArtistCredit(browse.optString("browseId"), artistName));
        }
      }
    }
    JSONArray fixed = row.optJSONArray("fixedColumns");
    if (fixed != null)
      for (int i = 0; i < Math.min(5, fixed.length()); i++) {
        JSONObject col = fixed.optJSONObject(i);
        JSONObject renderer =
            col == null ? null : col.optJSONObject("musicResponsiveListItemFixedColumnRenderer");
        duration =
            Math.max(duration, duration(renderer == null ? null : renderer.optJSONObject("text")));
      }
    String artist = artists.isEmpty() ? fallback : String.join(", ", artists);
    if (artist.isEmpty() || artist.length() > 500 || duration < 1 || duration > 21600) return null;
    return new Track(
            "https://www.youtube.com/watch?v=" + id,
            title,
            artist,
            thumbnail(row.optJSONObject("thumbnail"), 0),
            duration)
        .withArtists(credits);
  }

  private static JSONObject column(JSONObject col) {
    JSONObject renderer =
        col == null ? null : col.optJSONObject("musicResponsiveListItemFlexColumnRenderer");
    return renderer == null ? null : renderer.optJSONObject("text");
  }

  private static String text(JSONObject text) {
    if (text == null) return "";
    if (text.has("simpleText")) return text.optString("simpleText").trim();
    StringBuilder result = new StringBuilder();
    JSONArray runs = text.optJSONArray("runs");
    if (runs != null)
      for (int i = 0; i < Math.min(40, runs.length()); i++) {
        JSONObject run = runs.optJSONObject(i);
        if (run != null) result.append(run.optString("text"));
      }
    return result.toString().trim();
  }

  private static long duration(JSONObject line) {
    long seconds = MusicRadio.duration(text(line));
    JSONArray runs = line == null ? null : line.optJSONArray("runs");
    if (runs != null)
      for (int i = 0; i < Math.min(40, runs.length()); i++) {
        JSONObject run = runs.optJSONObject(i);
        if (run != null) seconds = Math.max(seconds, MusicRadio.duration(run.optString("text")));
      }
    return seconds;
  }

  private static String video(Object value) {
    return find(value, "videoId", 0);
  }

  private static String find(Object value, String key, int depth) {
    if (depth > 12) return "";
    if (value instanceof JSONObject) {
      JSONObject o = (JSONObject) value;
      if (o.has(key)) return o.optString(key);
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) {
        String hit = find(o.opt(keys.next()), key, depth + 1);
        if (!hit.isEmpty()) return hit;
      }
    } else if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value;
      for (int i = 0; i < Math.min(40, a.length()); i++) {
        String hit = find(a.opt(i), key, depth + 1);
        if (!hit.isEmpty()) return hit;
      }
    }
    return "";
  }

  private static boolean podcast(JSONObject row) {
    return find(row, "musicVideoType", 0).contains("PODCAST")
        || find(row, "pageType", 0).contains("PODCAST");
  }

  private static String thumbnail(JSONObject object, int depth) {
    if (object == null || depth > 8) return "";
    JSONArray thumbs = object.optJSONArray("thumbnails");
    String best = "";
    int width = -1;
    if (thumbs != null)
      for (int i = 0; i < Math.min(30, thumbs.length()); i++) {
        JSONObject image = thumbs.optJSONObject(i);
        HttpUrl url = image == null ? null : HttpUrl.parse(image.optString("url"));
        if (url != null
            && url.isHttps()
            && url.username().isEmpty()
            && url.password().isEmpty()
            && image.optInt("width", 0) >= width) {
          best = url.toString();
          width = image.optInt("width", 0);
        }
      }
    if (!best.isEmpty()) return best;
    Iterator<String> keys = object.keys();
    while (keys.hasNext()) {
      String hit = thumbnail(object.optJSONObject(keys.next()), depth + 1);
      if (!hit.isEmpty()) return hit;
    }
    return "";
  }
}
