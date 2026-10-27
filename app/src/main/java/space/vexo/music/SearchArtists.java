package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Bounded, on-device artist identities. Song artwork is never an artist portrait. */
final class SearchArtists {
  static final int LIMIT = 12;
  private final SharedPreferences prefs;

  static final class Artist {
    final String id, name, artwork, query;
    final List<String> queries;
    final long checkedAt;
    final boolean legacy;

    Artist(String name, String ignoredSongArtwork, String query) {
      this("", name, "", Collections.singletonList(query), 0, true);
    }

    Artist(
        String id,
        String name,
        String artwork,
        List<String> queries,
        long checkedAt,
        boolean legacy) {
      this.id = id != null && id.matches("UC[A-Za-z0-9_-]{22}") ? id : "";
      this.name = SearchArtists.name(name);
      this.artwork = ArtworkQuality.normalizeSource(artwork);
      this.queries = Collections.unmodifiableList(new ArrayList<>(queries));
      this.query = queries.isEmpty() ? "" : queries.get(0);
      this.checkedAt = checkedAt;
      this.legacy = legacy;
    }

    String key() {
      return id.isEmpty() ? RadioPolicy.normalize(name) : id;
    }

    Artist checked(String id, String portrait) {
      return new Artist(
          id.isEmpty() ? this.id : id,
          name,
          portrait.isEmpty() ? artwork : portrait,
          queries,
          System.currentTimeMillis(),
          false);
    }

    Artist individual(String name) {
      return new Artist("", name, "", queries, 0, false);
    }

    Artist withQueries(List<String> terms) {
      return new Artist(id, name, artwork, terms, checkedAt, legacy);
    }

    boolean due(long now) {
      return checkedAt <= 0
          || now < checkedAt
          || now - checkedAt > (artwork.isEmpty() ? 6 * 3600000L : 7 * 86400000L);
    }
  }

  SearchArtists(Context c) {
    prefs = c.getSharedPreferences("search_history", 0);
  }

  long revision() {
    return prefs.getLong("artist_revision", 0);
  }

  List<Artist> entries() {
    return read(prefs);
  }

  List<Artist> visible() {
    List<Artist> out = entries();
    // Combined legacy labels are pending resolution, rather than duplicate artist circles.
    out.removeIf(a -> a.legacy && ArtistCatalogue.parts(a.name).size() > 1);
    return out;
  }

  private static List<Artist> read(SharedPreferences prefs) {
    List<Artist> out = new ArrayList<>();
    try {
      JSONArray array = new JSONArray(prefs.getString("artists", "[]"));
      for (int i = 0; i < Math.min(LIMIT, array.length()); i++) {
        JSONObject o = array.optJSONObject(i);
        if (o == null) continue;
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        JSONArray refs = o.optJSONArray("queries");
        if (refs != null)
          for (int j = 0; j < Math.min(20, refs.length()); j++)
            if (!SearchHistory.clean(refs.optString(j)).isEmpty())
              terms.add(SearchHistory.clean(refs.optString(j)));
        if (terms.isEmpty() && !o.optString("query").isEmpty())
          terms.add(SearchHistory.clean(o.optString("query")));
        Artist a =
            new Artist(
                o.optString("id"),
                o.optString("name"),
                o.optString("artworkKind").equals("portrait") ? o.optString("artwork") : "",
                new ArrayList<>(terms),
                o.optLong("checkedAt"),
                o.optBoolean("legacy", true));
        if (!a.name.isEmpty() && !a.queries.isEmpty()) merge(out, a);
      }
    } catch (JSONException ignored) {
    }
    return out;
  }

  void record(String query, List<Track> songs) {
    query = SearchHistory.clean(query);
    if (query.isEmpty() || query.contains("://") || missing(query)) return;
    List<Artist> next = new ArrayList<>();
    for (Track song : songs.subList(0, Math.min(6, songs.size()))) {
      if (!RadioPolicy.music(song)) continue;
      List<ArtistCredit> credits = song.artists;
      if (credits.isEmpty()) {
        Artist a = new Artist(name(song.artist), "", query);
        if (!a.name.isEmpty()) merge(next, a);
      } else
        for (ArtistCredit credit : credits) {
          if (!credit.name.isEmpty())
            merge(
                next,
                new Artist(credit.id, credit.name, "", Collections.singletonList(query), 0, false));
          if (next.size() == 3) break;
        }
      if (next.size() >= 3) break;
    }
    if (next.isEmpty()) return;
    for (Artist old : entries()) merge(next, old);
    write(prefs, next);
  }

  void retryPortraits() {
    List<Artist> next = new ArrayList<>();
    for (Artist a : entries())
      next.add(new Artist(a.id, a.name, a.artwork, a.queries, 0, a.legacy));
    prefs.edit().putLong("artist_revision", revision() + 1).apply();
    write(prefs, next);
  }

  boolean resolved(Artist requested, List<Artist> resolved, long revision) {
    if (revision() != revision) return false;
    List<Artist> old = entries(), next = new ArrayList<>();
    boolean found = false;
    for (Artist a : old) {
      if (a.key().equals(requested.key())) {
        found = true;
        for (Artist result : resolved) merge(next, result.withQueries(a.queries));
      } else merge(next, a);
    }
    if (found) write(prefs, next);
    return found;
  }

  static void merge(List<Artist> out, Artist incoming) {
    for (int i = 0; i < out.size(); i++) {
      Artist old = out.get(i);
      if ((!old.id.isEmpty() && old.id.equals(incoming.id))
          || RadioPolicy.normalize(old.name).equals(RadioPolicy.normalize(incoming.name))) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(old.queries);
        terms.addAll(incoming.queries);
        List<String> refs = new ArrayList<>(terms);
        refs = refs.subList(0, Math.min(20, refs.size()));
        Artist preferred =
            incoming.checkedAt > old.checkedAt || old.id.isEmpty() && !incoming.id.isEmpty()
                ? incoming
                : old;
        out.set(i, preferred.withQueries(refs));
        return;
      }
    }
    if (out.size() < LIMIT) out.add(incoming);
  }

  static String name(String value) {
    String clean =
        SearchHistory.clean(value)
            .replaceFirst("(?i)(?:\\s*[-–]\\s*topic|\\s+official(?:\\s+channel)?|\\s*vevo)$", "")
            .trim();
    String key = RadioPolicy.normalize(clean);
    return key.isEmpty()
            || key.equals("unknown")
            || key.equals("unknown artist")
            || key.equals("various artists")
            || key.equals("youtube")
        ? ""
        : clean;
  }

  static void remove(SharedPreferences prefs, String term) {
    List<Artist> out = new ArrayList<>();
    for (Artist a : read(prefs)) {
      List<String> refs = new ArrayList<>(a.queries);
      refs.removeIf(q -> q.equalsIgnoreCase(term));
      if (!refs.isEmpty()) out.add(a.withQueries(refs));
    }
    prefs.edit().putLong("artist_revision", prefs.getLong("artist_revision", 0) + 1).apply();
    write(prefs, out);
  }

  private static void write(SharedPreferences prefs, List<Artist> artists) {
    JSONArray next = new JSONArray();
    for (Artist a : artists.subList(0, Math.min(LIMIT, artists.size())))
      try {
        next.put(
            new JSONObject()
                .put("id", a.id)
                .put("name", a.name)
                .put("artwork", a.artwork)
                .put("artworkKind", "portrait")
                .put("query", a.query)
                .put("queries", new JSONArray(a.queries))
                .put("checkedAt", a.checkedAt)
                .put("legacy", a.legacy));
      } catch (JSONException ignored) {
      }
    prefs.edit().putString("artists", next.toString()).apply();
  }

  private boolean missing(String term) {
    try {
      JSONArray terms = new JSONArray(prefs.getString("terms", "[]"));
      for (int i = 0; i < Math.min(20, terms.length()); i++)
        if (term.equalsIgnoreCase(terms.optString(i))) return false;
    } catch (JSONException ignored) {
    }
    return true;
  }
}
