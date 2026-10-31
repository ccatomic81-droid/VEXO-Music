package space.vexo.music;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** The selected artist's own Songs catalogue, never general search or related-artist shelves. */
final class ArtistSongs {
  static final class Cursor {
    final String id, params, country, continuation;
    Cursor(String id, String params, String country, String continuation) {
      this.id = id; this.params = params; this.country = country; this.continuation = continuation;
    }
  }

  static final class Page {
    final SearchArtists.Artist artist;
    final List<Track> songs;
    final Cursor cursor;
    Page(SearchArtists.Artist artist, List<Track> songs, Cursor cursor) {
      this.artist = artist; this.songs = songs; this.cursor = cursor;
    }
    boolean more() { return cursor != null && !cursor.continuation.isEmpty(); }
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
    Cursor cursor;
    if (previous == null) {
      JSONObject profile = source.browse(artist.id, "", country, "");
      JSONObject songs = songsEndpoint(profile, 0);
      if (songs == null) throw new IOException("This artist’s song catalogue is unavailable");
      cursor = new Cursor(songs.optString("browseId"), songs.optString("params"), country, "");
    } else {
      if (!artist.key().equals(previous.artist.key())) throw new IOException("Artist changed");
      if (!previous.more()) return new Page(artist, Collections.emptyList(), null);
      cursor = previous.cursor;
    }
    JSONObject data = source.browse(cursor.id, cursor.params, cursor.country, cursor.continuation);
    Page page = parse(artist, cursor, data);
    // Keep following sparse pages without substituting songs by another artist.
    for (int i = 0; i < 2 && page.songs.isEmpty() && page.more(); i++) {
      Cursor next = page.cursor;
      page = parse(artist, next, source.browse(next.id, next.params, next.country, next.continuation));
    }
    return page;
  }

  /** Only the artist's Songs shelf may select a playlist; Albums/Related/Videos cannot. */
  static JSONObject songsEndpoint(Object value, int depth) {
    if (depth > 30) return null;
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      JSONObject shelf = object.optJSONObject("musicShelfRenderer");
      if (shelf != null) {
        JSONObject title = shelf.optJSONObject("title");
        JSONArray runs = title == null ? null : title.optJSONArray("runs");
        StringBuilder label = new StringBuilder();
        if (runs != null) for (int i = 0; i < Math.min(20, runs.length()); i++) {
          JSONObject run = runs.optJSONObject(i);
          if (run != null) label.append(run.optString("text"));
        }
        if (label.toString().equalsIgnoreCase("Songs") || label.toString().equalsIgnoreCase("Top songs")) {
          JSONObject endpoint = shelf.optJSONObject("bottomEndpoint");
          JSONObject browse = endpoint == null ? null : endpoint.optJSONObject("browseEndpoint");
          if (browse == null && runs != null && runs.length() > 0) {
            JSONObject first = runs.optJSONObject(0);
            endpoint = first == null ? null : first.optJSONObject("navigationEndpoint");
            browse = endpoint == null ? null : endpoint.optJSONObject("browseEndpoint");
          }
          if (browse != null && browse.optString("browseId").matches("VL[A-Za-z0-9_-]{10,150}"))
            return browse;
        }
        return null;
      }
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        JSONObject hit = songsEndpoint(object.opt(keys.next()), depth + 1);
        if (hit != null) return hit;
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < Math.min(500, array.length()); i++) {
        JSONObject hit = songsEndpoint(array.opt(i), depth + 1);
        if (hit != null) return hit;
      }
    }
    return null;
  }

  static Page parse(SearchArtists.Artist artist, Cursor requested, JSONObject data) throws IOException {
    LinkedHashMap<String, Track> out = new LinkedHashMap<>();
    String[] continuation = {""};
    boolean[] shelf = {false};
    collect(data, out, continuation, shelf, 0, new int[]{0});
    if (!shelf[0]) throw new IOException("Artist songs returned an incomplete page");
    String next = continuation[0].equals(requested.continuation) ? "" : continuation[0];
    return new Page(artist, only(artist, new ArrayList<>(out.values())),
        new Cursor(requested.id, requested.params, requested.country, next));
  }

  private static void collect(Object value, Map<String, Track> out, String[] next,
      boolean[] found, int depth, int[] visited) {
    if (depth > 35 || visited[0]++ > 30000) return;
    if (value instanceof JSONObject) {
      JSONObject o = (JSONObject) value;
      for (String key : new String[]{"musicPlaylistShelfRenderer", "musicPlaylistShelfContinuation", "appendContinuationItemsAction"}) {
        JSONObject shelf = o.optJSONObject(key);
        if (shelf != null) {
          found[0] = true;
          rows(shelf, out, next, 0, new int[]{0});
          return;
        }
      }
      // Do not traverse suggestion shelves or recommendation carousels.
      if (o.has("musicShelfRenderer") || o.has("musicCarouselShelfRenderer")) return;
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) collect(o.opt(keys.next()), out, next, found, depth + 1, visited);
    } else if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value;
      for (int i = 0; i < Math.min(500, a.length()); i++) collect(a.opt(i), out, next, found, depth + 1, visited);
    }
  }

  private static void rows(Object value, Map<String, Track> out, String[] next, int depth, int[] visited) {
    if (depth > 25 || visited[0]++ > 30000) return;
    if (value instanceof JSONObject) {
      JSONObject o = (JSONObject) value;
      JSONObject row = o.optJSONObject("musicResponsiveListItemRenderer");
      if (row != null) {
        Track t = MusicSearch.track(row);
        if (t != null && out.size() < 500) out.putIfAbsent(t.url, t);
        return;
      }
      JSONObject token = o.optJSONObject("nextContinuationData");
      if (token == null) token = o.optJSONObject("continuationCommand");
      if (token != null && token.optString("continuation").length() <= 8192)
        next[0] = token.optString("continuation");
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) rows(o.opt(keys.next()), out, next, depth + 1, visited);
    } else if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value;
      for (int i = 0; i < Math.min(501, a.length()); i++) rows(a.opt(i), out, next, depth + 1, visited);
    }
  }
}
