package space.vexo.music;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import okhttp3.*;
import org.json.JSONObject;

/** LRCLIB lookups are made only when the listener opens lyrics. */
final class Lyrics {
  static final class Line {
    final long time;
    final String text;

    Line(long time, String text) {
      this.time = time;
      this.text = text;
    }
  }

  static final class Result {
    final List<Line> lines;
    final String plain;
    final boolean instrumental;

    Result(List<Line> lines, String plain, boolean instrumental) {
      this.lines = lines;
      this.plain = plain;
      this.instrumental = instrumental;
    }
  }

  private static final Pattern TIME =
      Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
  private static final OkHttpClient HTTP =
      new OkHttpClient.Builder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(12, TimeUnit.SECONDS)
          .callTimeout(15, TimeUnit.SECONDS)
          .build();

  static List<Line> parse(String lrc) {
    List<Line> lines = new ArrayList<>();
    if (lrc == null) return lines;
    for (String raw : lrc.split("\\r?\\n")) {
      Matcher match = TIME.matcher(raw);
      List<Long> times = new ArrayList<>();
      int end = 0;
      while (match.find()) {
        String fraction = match.group(3);
        int ms = fraction == null ? 0 : Integer.parseInt((fraction + "00").substring(0, 3));
        long time =
            Long.parseLong(match.group(1)) * 60000 + Long.parseLong(match.group(2)) * 1000 + ms;
        times.add(time);
        end = match.end();
      }
      String text = raw.substring(end).trim();
      if (!text.isEmpty()) for (long time : times) lines.add(new Line(time, text));
    }
    lines.sort(Comparator.comparingLong(line -> line.time));
    return lines;
  }

  static int active(List<Line> lines, long position) {
    int lo = 0, hi = lines.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (lines.get(mid).time <= position) lo = mid + 1;
      else hi = mid;
    }
    return lo - 1;
  }

  static Result load(Track track, long durationMs) throws Exception {
    if (track.title == null
        || track.artist == null
        || track.title.trim().isEmpty()
        || track.artist.trim().isEmpty()
        || durationMs <= 0) return new Result(Collections.emptyList(), "", false);
    HttpUrl url =
        Objects.requireNonNull(HttpUrl.parse("https://lrclib.net/api/get"))
            .newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .addQueryParameter("duration", Long.toString(Math.round(durationMs / 1000.0)))
            .build();
    Request request =
        new Request.Builder()
            .url(url)
            .header(
                "User-Agent", "VEXO-Music/0.27.0 (https://github.com/ccatomic81-droid/VEXO-Music)")
            .build();
    try (Response response = HTTP.newCall(request).execute()) {
      if (response.code() == 404) return new Result(Collections.emptyList(), "", false);
      if (!response.isSuccessful() || response.body() == null)
        throw new java.io.IOException("Lyrics unavailable");
      if (response.body().contentLength() > 512000)
        throw new java.io.IOException("Lyrics response too large");
      okio.BufferedSource source = response.body().source();
      source.request(512001);
      if (source.buffer().size() > 512000)
        throw new java.io.IOException("Lyrics response too large");
      String body = source.readUtf8();
      JSONObject data = new JSONObject(body);
      return new Result(
          parse(data.isNull("syncedLyrics") ? "" : data.optString("syncedLyrics", "")),
          data.isNull("plainLyrics") ? "" : data.optString("plainLyrics", ""),
          data.optBoolean("instrumental", false));
    }
  }
}
