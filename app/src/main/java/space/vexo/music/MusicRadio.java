package space.vexo.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.HttpUrl;
import org.json.*;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;

/** Public YouTube Music radio metadata; no Google account tokens or session cookies. */
final class MusicRadio {
  static String videoId(Track seed) {
    String identity = QueuePolicy.identity(seed.url);
    String id = identity.startsWith("youtube:") ? identity.substring(8) : "";
    return id.matches("[A-Za-z0-9_-]{11}") ? id : "";
  }

  static List<Track> load(Track seed) throws Exception {
    String id = videoId(seed);
    if (id.isEmpty()) return Collections.emptyList();
    OnlineMusic.initialize();
    String version = YoutubeParsingHelper.getYoutubeMusicClientVersion();
    String country = Locale.getDefault().getCountry();
    if (!RegionPolicy.valid(country)) country = "LK";
    JSONObject client =
        new JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", version)
            .put("hl", "en")
            .put("gl", country)
            .put("platform", "DESKTOP")
            .put("utcOffsetMinutes", 0);
    JSONObject request =
        new JSONObject()
            .put("context", new JSONObject().put("client", client))
            .put("videoId", id)
            .put("playlistId", "RDAMVM" + id)
            .put("isAudioOnly", true)
            .put("enablePersistentPlaylistPanel", true);
    List<Track> songs = parse(fetch(request));
    // Like Metrolist's queue fallback, allow a plain song endpoint when RDAMVM is unavailable.
    if (songs.size() < 2) {
      request.remove("playlistId");
      List<Track> fallback = parse(fetch(request));
      if (fallback.size() > songs.size()) songs = fallback;
    }
    return songs;
  }

  private static JSONObject fetch(JSONObject body) throws Exception {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    Response response =
        NewPipe.getDownloader()
            .postWithContentTypeJson(
                "https://music.youtube.com/youtubei/v1/next?prettyPrint=false",
                YoutubeParsingHelper.getYoutubeMusicHeaders(),
                body.toString().getBytes(StandardCharsets.UTF_8));
    String text = response.responseBody();
    if (response.responseCode() != 200 || text == null || text.length() > 4 * 1024 * 1024)
      throw new IOException("YouTube Music radio is temporarily unavailable");
    return new JSONObject(text);
  }

  static List<Track> parse(JSONObject response) {
    LinkedHashMap<String, Track> out = new LinkedHashMap<>();
    walk(response, out, 0, new int[] {0});
    return new ArrayList<>(out.values());
  }

  private static void walk(Object value, Map<String, Track> out, int depth, int[] visited) {
    if (depth > 40 || visited[0]++ > 15000 || out.size() >= 60) return;
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      JSONObject row = object.optJSONObject("playlistPanelVideoRenderer");
      if (row != null) {
        Track track = track(row);
        if (track != null) out.putIfAbsent(track.url, track);
        return;
      }
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) walk(object.opt(keys.next()), out, depth + 1, visited);
    } else if (value instanceof JSONArray) {
      JSONArray values = (JSONArray) value;
      for (int i = 0; i < Math.min(500, values.length()); i++)
        walk(values.opt(i), out, depth + 1, visited);
    }
  }

  private static Track track(JSONObject row) {
    String id = row.optString("videoId");
    if (!id.matches("[A-Za-z0-9_-]{11}") || row.optBoolean("isUnplayable")) return null;
    String title = text(row.optJSONObject("title"));
    String artist = artist(row.optJSONObject("longBylineText"));
    if (artist.isEmpty()) artist = artist(row.optJSONObject("shortBylineText"));
    long duration = duration(text(row.optJSONObject("lengthText")));
    String artwork = "";
    JSONObject thumb = row.optJSONObject("thumbnail");
    JSONArray thumbnails = thumb == null ? null : thumb.optJSONArray("thumbnails");
    if (thumbnails != null)
      for (int i = Math.min(thumbnails.length(), 20) - 1; i >= 0; i--) {
        JSONObject image = thumbnails.optJSONObject(i);
        if (image == null) continue;
        HttpUrl url = HttpUrl.parse(image.optString("url"));
        if (url != null
            && url.isHttps()
            && url.username().isEmpty()
            && url.password().isEmpty()
            && (url.host().equals("i.ytimg.com")
                || url.host().equals("lh3.googleusercontent.com"))) {
          artwork = url.toString();
          break;
        }
      }
    Track song =
        new Track("https://www.youtube.com/watch?v=" + id, title, artist, artwork, duration);
    return title.length() <= 500 && artist.length() <= 250 && RadioPolicy.music(song) ? song : null;
  }

  private static String text(JSONObject text) {
    if (text == null) return "";
    if (text.has("simpleText")) return text.optString("simpleText").trim();
    StringBuilder value = new StringBuilder();
    JSONArray runs = text.optJSONArray("runs");
    if (runs != null)
      for (int i = 0; i < Math.min(20, runs.length()); i++) {
        JSONObject run = runs.optJSONObject(i);
        if (run != null) value.append(run.optString("text"));
      }
    return value.toString().trim();
  }

  private static String artist(JSONObject byline) {
    if (byline == null) return "";
    JSONArray runs = byline.optJSONArray("runs");
    List<String> names = new ArrayList<>();
    if (runs != null)
      for (int i = 0; i < Math.min(30, runs.length()); i++) {
        JSONObject run = runs.optJSONObject(i);
        JSONObject endpoint = run == null ? null : run.optJSONObject("navigationEndpoint");
        JSONObject browse = endpoint == null ? null : endpoint.optJSONObject("browseEndpoint");
        if (browse != null && browse.optString("browseId").startsWith("UC"))
          names.add(run.optString("text"));
      }
    if (!names.isEmpty()) return String.join(", ", names);
    return text(byline).split("[•·]")[0].trim();
  }

  static long duration(String text) {
    if (!text.matches("[0-9]{1,3}:[0-5][0-9](?::[0-5][0-9])?")) return 0;
    long seconds = 0;
    for (String part : text.split(":")) seconds = seconds * 60 + Integer.parseInt(part);
    return seconds;
  }
}
