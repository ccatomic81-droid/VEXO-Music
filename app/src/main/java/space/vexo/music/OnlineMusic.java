package space.vexo.music;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public final class OnlineMusic {
  public static final OkHttpClient HTTP =
      new OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .callTimeout(45, TimeUnit.SECONDS)
          .build();
  private static final OkHttpClient SUGGESTIONS_HTTP =
      HTTP.newBuilder()
          .connectTimeout(4, TimeUnit.SECONDS)
          .readTimeout(4, TimeUnit.SECONDS)
          .callTimeout(6, TimeUnit.SECONDS)
          .build();
  private static boolean initialized;
  private static final ThreadLocal<Boolean> PREVIEW = new ThreadLocal<>();
  private static final OkHttpClient PREVIEW_HTTP =
      HTTP.newBuilder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(7, TimeUnit.SECONDS)
          .callTimeout(10, TimeUnit.SECONDS)
          .build();
  private static final Map<String, Cached> cache = new LinkedHashMap<>();
  private static final Map<String, List<Track>> relatedCache = new LinkedHashMap<>();

  public static List<Track> related(Track seed) throws Exception {
    initialize();
    synchronized (OnlineMusic.class) {
      List<Track> hit = relatedCache.get(seed.url);
      if (hit != null && !hit.isEmpty()) return new ArrayList<>(hit);
    }
    List<Track> songs = new ArrayList<>();
    try {
      songs = MusicRadio.load(seed);
    } catch (Exception unavailable) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
    if (songs.size() < 3) {
      try {
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, seed.url);
        LinkedHashMap<String, Track> merged = new LinkedHashMap<>();
        for (Track song : songs) merged.put(QueuePolicy.identity(song.url), song);
        for (Track song : convert(info.getRelatedItems()))
          merged.putIfAbsent(QueuePolicy.identity(song.url), song);
        songs = new ArrayList<>(merged.values());
      } catch (Exception unavailable) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (songs.isEmpty()) throw unavailable;
      }
    }
    if (songs.isEmpty()) songs = search(seed.artist + " music").tracks;
    rememberRelated(seed.url, songs);
    return songs;
  }

  static List<Track> relatedPreview(Track seed) throws Exception {
    PREVIEW.set(true);
    try {
      return related(seed);
    } finally {
      PREVIEW.remove();
    }
  }

  private static synchronized void rememberRelated(String url, List<Track> songs) {
    if (relatedCache.size() >= 30) relatedCache.remove(relatedCache.keySet().iterator().next());
    relatedCache.put(url, new ArrayList<>(songs));
  }

  public static synchronized void initialize() {
    if (initialized) return;
    NewPipe.init(
        new Downloader() {
          @Override
          public Response execute(Request request) throws IOException, ReCaptchaException {
            okhttp3.Request.Builder b =
                new okhttp3.Request.Builder()
                    .url(request.url())
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile"
                            + " Safari/537.36");
            for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
              b.removeHeader(h.getKey());
              for (String value : h.getValue()) b.addHeader(h.getKey(), value);
            }
            byte[] data = request.dataToSend();
            RequestBody body = data == null ? null : RequestBody.create(data, null);
            if (body == null
                && (request.httpMethod().equals("POST") || request.httpMethod().equals("PUT")))
              body = RequestBody.create(new byte[0], null);
            b.method(request.httpMethod(), body);
            okhttp3.Request call = b.build();
            OkHttpClient client =
                call.url().host().contains("suggestqueries")
                    ? SUGGESTIONS_HTTP
                    : Boolean.TRUE.equals(PREVIEW.get()) ? PREVIEW_HTTP : HTTP;
            try (okhttp3.Response r = client.newCall(call).execute()) {
              if (r.code() == 429)
                throw new ReCaptchaException(
                    "YouTube is temporarily limiting requests. Try again later.", request.url());
              return new Response(
                  r.code(),
                  r.message(),
                  r.headers().toMultimap(),
                  r.body() == null ? "" : r.body().string(),
                  r.request().url().toString());
            }
          }
        });
    initialized = true;
  }

  public static final class Results {
    final List<Track> tracks;
    final Page next;
    final SearchQueryHandler query;
    final MusicSearch.Result music;

    Results(List<Track> t, Page p, SearchQueryHandler q) {
      tracks = t;
      next = p;
      query = q;
      music = null;
    }

    Results(MusicSearch.Result page) {
      tracks = page.tracks;
      next =
          page.continuation.isEmpty()
              ? null
              : new Page("https://music.youtube.com/youtubei/v1/search");
      query = null;
      music = page;
    }
  }

  static List<Track> convert(List<? extends InfoItem> items) {
    List<Track> tracks = new ArrayList<>();
    for (InfoItem i : items)
      if (i instanceof StreamInfoItem) {
        StreamInfoItem t = (StreamInfoItem) i;
        if (t.getDuration() == 0) continue;
        String art = ArtworkQuality.best(t.getThumbnails());
        tracks.add(new Track(t.getUrl(), t.getName(), t.getUploaderName(), art, t.getDuration()));
      }
    return tracks;
  }

  public static Results search(String text) throws Exception {
    String country = Locale.getDefault().getCountry();
    return search(text, RegionPolicy.valid(country) ? country : "LK");
  }

  public static Results search(String text, String country) throws Exception {
    initialize();
    PREVIEW.set(true);
    try {
      return new Results(MusicSearch.load(text, country));
    } catch (Exception unavailable) {
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      return legacySearch(text, country);
    } finally {
      PREVIEW.remove();
    }
  }

  private static Results legacySearch(String text, String country) throws Exception {
    SearchQueryHandler q =
        ServiceList.YouTube.getSearchQHFactory()
            .fromQuery(text, Collections.singletonList("music_songs"), "");
    org.schabi.newpipe.extractor.search.SearchExtractor extractor =
        ServiceList.YouTube.getSearchExtractor(q);
    extractor.forceContentCountry(
        new org.schabi.newpipe.extractor.localization.ContentCountry(country));
    extractor.fetchPage();
    ListExtractor.InfoItemsPage<InfoItem> page = extractor.getInitialPage();
    return new Results(convert(page.getItems()), page.getNextPage(), q);
  }

  public static List<Track> preview(String text, String country) throws Exception {
    PREVIEW.set(true);
    try {
      return regionalSearch(text, country);
    } finally {
      PREVIEW.remove();
    }
  }

  public static List<Track> regionalSearch(String text, String country) throws Exception {
    return search(text, country).tracks;
  }

  public static Results more(Results previous) throws Exception {
    if (previous.music != null) {
      PREVIEW.set(true);
      try {
        return new Results(MusicSearch.more(previous.music));
      } finally {
        PREVIEW.remove();
      }
    }
    ListExtractor.InfoItemsPage<InfoItem> p =
        SearchInfo.getMoreItems(ServiceList.YouTube, previous.query, previous.next);
    return new Results(convert(p.getItems()), p.getNextPage(), previous.query);
  }

  private static final class Cached {
    final String uri;
    final long at;

    Cached(String u) {
      uri = u;
      at = android.os.SystemClock.elapsedRealtime();
    }
  }

  public static synchronized void clearCache() {
    cache.clear();
  }

  public static synchronized void resetTransientCaches() {
    cache.clear();
    relatedCache.clear();
    IntroRecognition.clear();
  }

  public static String resolve(String url, boolean dataSaver) throws IOException {
    initialize();
    String key = url + dataSaver;
    synchronized (OnlineMusic.class) {
      Cached c = cache.get(key);
      if (c != null && android.os.SystemClock.elapsedRealtime() - c.at < 600_000) return c.uri;
    }
    try {
      StreamInfo i = StreamInfo.getInfo(ServiceList.YouTube, url);
      IntroRecognition.remember(url, i.getDuration() * 1000, i.getStreamSegments());
      rememberRelated(url, convert(i.getRelatedItems()));
      List<AudioStream> audio = new ArrayList<>();
      for (AudioStream s : i.getAudioStreams())
        if (s.isUrl() && s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP) audio.add(s);
      audio.sort(Comparator.comparingInt(AudioStream::getAverageBitrate));
      if (audio.isEmpty())
        throw new IOException(
            "No playable audio stream. Open this track in YouTube or try a different song.");
      String uri = audio.get(dataSaver ? 0 : audio.size() - 1).getContent();
      synchronized (OnlineMusic.class) {
        if (cache.size() > 100) cache.clear();
        cache.put(key, new Cached(uri));
      }
      return uri;
    } catch (Exception e) {
      throw new IOException(
          "YouTube audio could not be loaded. The track may be restricted, or YouTube may require"
              + " an extractor update. Try another song or open YouTube.",
          e);
    }
  }
}
