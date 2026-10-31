package space.vexo.music;

import java.io.IOException;
import java.util.*;

/** Only provider-credited songs enter an artist page, including credited collaborations. */
final class ArtistSongs {
  static final class Page {
    final SearchArtists.Artist artist;
    final List<Track> songs;
    final MusicSearch.Result cursor;

    Page(SearchArtists.Artist artist, List<Track> songs, MusicSearch.Result cursor) {
      this.artist = artist;
      this.songs = songs;
      this.cursor = cursor;
    }

    boolean more() { return cursor != null && !cursor.continuation.isEmpty(); }
  }

  static boolean credited(SearchArtists.Artist artist, Track song) {
    if (artist.id.isEmpty()) return false;
    for (ArtistCredit credit : song.artists)
      if (artist.id.equals(credit.id)) return true;
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
    MusicSearch.Result page = previous == null
        ? MusicSearch.load(artist.name, country) : MusicSearch.more(previous.cursor);
    List<Track> songs = only(artist, page.tracks);
    // A sparse page may contain namesakes. Keep a bounded cursor, never loosen identity matching.
    for (int i = 0; i < 2 && songs.isEmpty() && !page.continuation.isEmpty(); i++) {
      page = MusicSearch.more(page);
      songs = only(artist, page.tracks);
    }
    return new Page(artist, songs, page);
  }
}
