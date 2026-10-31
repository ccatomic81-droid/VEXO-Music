package space.vexo.music;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** The selected artist’s music catalogues, including official recordings without an audio-only release. */
final class ArtistSongs {
  static final class Cursor {
    final String id, params, country, continuation;
    final boolean official;
    final Cursor pending;
    Cursor(String id, String params, String country, String continuation) {
      this(id, params, country, continuation, false, null);
    }
    Cursor(String id, String params, String country, String continuation, boolean official, Cursor pending) {
      this.id = id; this.params = params; this.country = country; this.continuation = continuation;
      this.official = official; this.pending = pending;
    }
  }

  static final class Page {
    final SearchArtists.Artist artist;
    final List<Track> songs;
    final Cursor cursor;
    final Set<String> seen;
    Page(SearchArtists.Artist artist, List<Track> songs, Cursor cursor) {
      this(artist, songs, cursor, new HashSet<>());
    }
    Page(SearchArtists.Artist artist, List<Track> songs, Cursor cursor, Set<String> earlier) {
      this.artist = artist; this.songs = songs; this.cursor = cursor;
      seen = new HashSet<>(earlier);
      for (Track song : songs) seen.add(song.url);
    }
    boolean more() { return cursor != null; }
  }

  interface Source {
    JSONObject browse(String id, String params, String country, String continuation) throws Exception;
  }

  static boolean credited(SearchArtists.Artist artist, Track song) {
    if (artist.id.isEmpty()) return false;
    for (ArtistCredit credit : song.artists) if (artist.id.equals(credit.id)) return true;
    return false;
  }

  static List<Track> only(SearchArtists.Artist artist, List<Track> candidates) {
    LinkedHashMap<String, Track> songs = new LinkedHashMap<>();
    for (Track song : candidates)
      if (credited(artist, song)) songs.putIfAbsent(song.url, song);
    return new ArrayList<>(songs.values());
  }

  static Page load(SearchArtists.Artist requested, String country, Page previous) throws Exception {
    SearchArtists.Artist artist = requested;
    if (artist.id.isEmpty()) {
      List<SearchArtists.Artist> resolved = ArtistCatalogue.resolve(artist, country);
      if (resolved.size() != 1 || resolved.get(0).id.isEmpty())
        throw new IOException("Find this artist in Artists search to choose their official profile.");
      artist = resolved.get(0);
    }
    return load(artist, country, previous, MusicSearch::browse);
  }

  static Page load(SearchArtists.Artist artist, String country, Page previous, Source source)
      throws Exception {
    if (artist.id.isEmpty()) throw new IOException("An artist identity is required");
    Page page;
    if (previous == null) {
      JSONObject profile = source.browse(artist.id, "", country, "");
      JSONObject songs = songsEndpoint(profile, 0), videos = endpoint(profile, "Videos", 0);
      if (songs == null && videos == null) throw new IOException("This artist’s music catalogue is unavailable");
      List<Track> tracks = new ArrayList<>();
      Cursor next = null;
      Exception failure = null;
      boolean loaded = false;
      for (int at = 0; at < 2; at++) {
        JSONObject target = at == 0 ? songs : videos;
        if (target == null) continue;
        Cursor cursor = new Cursor(target.optString("browseId"), target.optString("params"), country, "", at == 1, null);
        try {
          Page found = read(artist, cursor, source);
          loaded = true; tracks.addAll(found.songs); next = chain(next, found.cursor);
        } catch (Exception e) {
          if (Thread.currentThread().isInterrupted()) throw e;
          failure = e; next = chain(next, cursor);
        }
      }
      if (!loaded) throw failure;
      page = new Page(artist, unique(tracks, Collections.emptySet()), next);
    } else {
      if (!artist.key().equals(previous.artist.key())) throw new IOException("Artist changed");
      if (!previous.more()) return new Page(artist, Collections.emptyList(), null, previous.seen);
      page = read(artist, previous.cursor, source);
    }
    for (int i = 0; i < 2 && page.songs.isEmpty() && page.more(); i++) page = read(artist, page.cursor, source);
    Set<String> seen = previous == null ? Collections.emptySet() : previous.seen;
    return new Page(artist, unique(page.songs, seen), page.cursor, seen);
  }

  private static Page read(SearchArtists.Artist artist, Cursor cursor, Source source) throws Exception {
    return parse(artist, cursor, source.browse(cursor.id, cursor.params, cursor.country, cursor.continuation));
  }

  static Cursor chain(Cursor first, Cursor next) {
    return first == null ? next : new Cursor(first.id, first.params, first.country,
        first.continuation, first.official, chain(first.pending, next));
  }

  private static List<Track> unique(List<Track> songs, Set<String> earlier) {
    Set<String> seen = new HashSet<>(earlier);
    List<Track> out = new ArrayList<>();
    for (Track song : songs) if (seen.add(song.url)) out.add(song);
    return out;
  }

  static JSONObject songsEndpoint(Object value, int depth) { return endpoint(value, "Songs", depth); }

  /** Only labelled artist-owned music shelves select sources; related artists and arbitrary playlists cannot. */
  static JSONObject endpoint(Object value, String kind, int depth) {
    if (depth > 30) return null;
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      JSONObject shelf = object.optJSONObject("musicShelfRenderer");
      JSONObject title = shelf == null ? null : shelf.optJSONObject("title");
      if (shelf == null) {
        shelf = object.optJSONObject("musicCarouselShelfRenderer");
        JSONObject header = shelf == null ? null : shelf.optJSONObject("header");
        JSONObject basic = header == null ? null : header.optJSONObject("musicCarouselShelfBasicHeaderRenderer");
        title = basic == null ? null : basic.optJSONObject("title");
      }
      if (shelf != null) {
        JSONArray runs = title == null ? null : title.optJSONArray("runs");
        StringBuilder label = new StringBuilder();
        if (runs != null) for (int i = 0; i < Math.min(20, runs.length()); i++) {
          JSONObject run = runs.optJSONObject(i);
          if (run != null) label.append(run.optString("text"));
        }
        if (label.toString().equalsIgnoreCase(kind)
            || kind.equals("Songs") && label.toString().equalsIgnoreCase("Top songs")) {
          JSONObject link = shelf.optJSONObject("bottomEndpoint");
          JSONObject browse = link == null ? null : link.optJSONObject("browseEndpoint");
          if (browse == null && runs != null && runs.length() > 0) {
            JSONObject first = runs.optJSONObject(0);
            link = first == null ? null : first.optJSONObject("navigationEndpoint");
            browse = link == null ? null : link.optJSONObject("browseEndpoint");
          }
          if (browse != null && browse.optString("browseId").matches("VL[A-Za-z0-9_-]{10,150}")) return browse;
        }
        return null;
      }
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        JSONObject hit = endpoint(object.opt(keys.next()), kind, depth + 1);
        if (hit != null) return hit;
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < Math.min(500, array.length()); i++) {
        JSONObject hit = endpoint(array.opt(i), kind, depth + 1);
        if (hit != null) return hit;
      }
    }
    return null;
  }

  static Page parse(SearchArtists.Artist artist, Cursor requested, JSONObject data) throws IOException {
    LinkedHashMap<String, Track> out = new LinkedHashMap<>();
    String[] continuation = {""};
    boolean[] shelf = {false};
    collect(data, out, continuation, shelf, 0, new int[]{0}, requested.official);
    if (!shelf[0]) throw new IOException("Artist songs returned an incomplete page");
    String next = continuation[0].equals(requested.continuation) ? "" : continuation[0];
    Cursor cursor = next.isEmpty() ? requested.pending : new Cursor(requested.id,
        requested.params, requested.country, next, requested.official, requested.pending);
    return new Page(artist, only(artist, new ArrayList<>(out.values())), cursor);
  }

  private static void collect(Object value, Map<String, Track> out, String[] next,
      boolean[] found, int depth, int[] visited, boolean official) {
    if (depth > 35 || visited[0]++ > 30000) return;
    if (value instanceof JSONObject) {
      JSONObject o = (JSONObject) value;
      for (String key : new String[]{"musicPlaylistShelfRenderer", "musicPlaylistShelfContinuation", "appendContinuationItemsAction"}) {
        JSONObject shelf = o.optJSONObject(key);
        if (shelf != null) {
          found[0] = true;
          rows(shelf, out, next, 0, new int[]{0}, official);
          return;
        }
      }
      // Do not traverse suggestion shelves or recommendation carousels.
      if (o.has("musicShelfRenderer") || o.has("musicCarouselShelfRenderer")) return;
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) collect(o.opt(keys.next()), out, next, found, depth + 1, visited, official);
    } else if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value;
      for (int i = 0; i < Math.min(500, a.length()); i++) collect(a.opt(i), out, next, found, depth + 1, visited, official);
    }
  }

  private static void rows(Object value, Map<String, Track> out, String[] next, int depth, int[] visited, boolean official) {
    if (depth > 25 || visited[0]++ > 30000) return;
    if (value instanceof JSONObject) {
      JSONObject o = (JSONObject) value;
      JSONObject row = o.optJSONObject("musicResponsiveListItemRenderer");
      if (row != null) {
        Track t = MusicSearch.track(row, official);
        if (t != null && out.size() < 500) out.putIfAbsent(t.url, t);
        return;
      }
      JSONObject token = o.optJSONObject("nextContinuationData");
      if (token == null) token = o.optJSONObject("continuationCommand");
      if (token != null && token.optString("continuation").length() <= 8192)
        next[0] = token.optString("continuation");
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) rows(o.opt(keys.next()), out, next, depth + 1, visited, official);
    } else if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value;
      for (int i = 0; i < Math.min(501, a.length()); i++) rows(a.opt(i), out, next, depth + 1, visited, official);
    }
  }
}
