package space.vexo.music;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class OfficialMusicTest {
  static final String JANANA = "UCz2nbDqzWpCdA5JKuRh_hUw";
  static final String KOLAMA = "31HnVoGRXvw";
  final SearchArtists.Artist artist = new SearchArtists.Artist(JANANA, "JANANA", "", List.of(), 1, false);
  final String songs = "VLOLAK5uy_songs_catalogue", videos = "VLOLAK5uy_official_catalogue";

  JSONObject row(String id, String title, String type, String artistId, String pageType) throws Exception {
    MusicSearchTest helper = new MusicSearchTest();
    JSONObject r = helper.song(id).put("musicVideoType", type);
    r.getJSONArray("flexColumns").put(0, helper.column(helper.line(title)));
    JSONObject browse = new JSONObject().put("browseId", artistId).put("browseEndpointContextSupportedConfigs",
        new JSONObject().put("browseEndpointContextMusicConfig", new JSONObject().put("pageType", pageType)));
    JSONArray runs = new JSONArray().put(new JSONObject().put("text", "JANANA").put("navigationEndpoint",
        new JSONObject().put("browseEndpoint", browse)))
        .put(new JSONObject().put("text", " • ")).put(new JSONObject().put("text", "4:17"));
    r.getJSONArray("flexColumns").put(1, helper.column(new JSONObject().put("runs", runs)));
    return r;
  }
  JSONObject kolama() throws Exception { return row(KOLAMA, "Kolama කෝලම - Janana (Official Lyrics Video)", "MUSIC_VIDEO_TYPE_OMV", JANANA, "MUSIC_PAGE_TYPE_ARTIST"); }
  JSONObject audio() throws Exception { return row("abcdefghijk", "Bo Mal", "MUSIC_VIDEO_TYPE_ATV", JANANA, "MUSIC_PAGE_TYPE_ARTIST"); }
  JSONObject playlist(String token, JSONObject... tracks) throws Exception {
    JSONArray rows = new JSONArray();
    for (JSONObject track : tracks) rows.put(new JSONObject().put("musicResponsiveListItemRenderer", track));
    JSONObject shelf = new JSONObject().put("contents", rows);
    if (!token.isEmpty()) shelf.put("continuations", new JSONArray().put(new JSONObject().put("nextContinuationData",
        new JSONObject().put("continuation", token))));
    return new JSONObject().put("contents", new JSONObject().put("musicPlaylistShelfRenderer", shelf));
  }
  JSONObject profile(boolean audio) throws Exception {
    ArtistCataloguePagingTest helper = new ArtistCataloguePagingTest();
    JSONArray content = new JSONArray();
    if (audio) content.put(helper.shelf("Top songs", songs));
    JSONObject title = new JSONObject().put("runs", new JSONArray().put(new JSONObject().put("text", "Videos")
        .put("navigationEndpoint", new JSONObject().put("browseEndpoint", new JSONObject().put("browseId", videos)))));
    content.put(new JSONObject().put("musicCarouselShelfRenderer", new JSONObject().put("header",
        new JSONObject().put("musicCarouselShelfBasicHeaderRenderer", new JSONObject().put("title", title)))));
    return new JSONObject().put("contents", content);
  }

  @Test public void officialKolamaIsAcceptedWithoutOpeningUpUnverifiedUploads() throws Exception {
    MusicSearchTest helper = new MusicSearchTest();
    JSONObject valid = kolama();
    assertTrue(MusicSearch.parse(helper.wrap(valid), "Janana Kolama", "LK").tracks.isEmpty());
    JSONObject ugc = row("bbbbbbbbbbb", "Kolama Official", "MUSIC_VIDEO_TYPE_UGC", JANANA, "MUSIC_PAGE_TYPE_ARTIST");
    JSONObject channel = row("ccccccccccc", "Kolama Official", "MUSIC_VIDEO_TYPE_OMV", JANANA, "MUSIC_PAGE_TYPE_USER_CHANNEL");
    JSONObject unknown = row("ddddddddddd", "Kolama Official", "", JANANA, "MUSIC_PAGE_TYPE_ARTIST");
    var parsed = MusicSearch.parse(helper.wrap(ugc, channel, valid, unknown), "Janana Kolama", "LK", true);
    assertEquals(1, parsed.tracks.size());
    Track song = parsed.tracks.get(0);
    assertTrue(song.url.endsWith(KOLAMA)); assertEquals(257, song.duration);
    assertEquals(JANANA, song.artists.get(0).id);
  }

  @Test public void explicitSearchRanksTitleAndArtistTogetherAndKeepsBothCursors() throws Exception {
    Track unrelated = new Track("https://www.youtube.com/watch?v=eeeeeeeeeee", "Janana Tappy", "Another artist", "", 200);
    Track match = MusicSearch.track(kolama(), true);
    var combined = MusicSearch.combine(new MusicSearch.Result(List.of(unrelated), "Janana Kolama", "LK", "audio-next"),
        new MusicSearch.Result(List.of(match), "Janana Kolama", "LK", "official-next"), "Janana Kolama", "LK");
    assertEquals(match, combined.tracks.get(0)); assertTrue(combined.more());
    assertEquals("official-next", combined.officialContinuation);
    var onlyOfficial = MusicSearch.combine(null, new MusicSearch.Result(List.of(match), "Kolama", "LK", "next"), "Kolama", "LK");
    assertNotNull(new OnlineMusic.Results(onlyOfficial).next);
    Track audio = MusicSearch.track(audio());
    Track duplicate = MusicSearch.track(row("fffffffffff", "Bo Mal (Official Music Video)", "MUSIC_VIDEO_TYPE_OMV", JANANA, "MUSIC_PAGE_TYPE_ARTIST"), true);
    assertEquals(List.of(audio), MusicSearch.combine(new MusicSearch.Result(List.of(audio), "Bo Mal", "LK", ""),
        new MusicSearch.Result(List.of(duplicate), "Bo Mal", "LK", ""), "Bo Mal", "LK").tracks);
  }

  @Test public void artistLoadsOfficialKolamaOnFirstPageAndPaginatesBothSources() throws Exception {
    List<String> requests = new ArrayList<>();
    ArtistSongs.Source source = (id, params, country, token) -> {
      requests.add(id + ":" + token);
      if (id.equals(JANANA)) return profile(true);
      if (id.equals(songs)) return token.isEmpty() ? playlist("audio-next", audio())
          : playlist("", row("ggggggggggg", "Another audio", "MUSIC_VIDEO_TYPE_ATV", JANANA, "MUSIC_PAGE_TYPE_ARTIST"));
      return token.isEmpty() ? playlist("video-next", kolama())
          : playlist("", row("hhhhhhhhhhh", "Another official release", "MUSIC_VIDEO_TYPE_OMV", JANANA, "MUSIC_PAGE_TYPE_ARTIST"));
    };
    ArtistSongs.Page first = ArtistSongs.load(artist, "LK", null, source);
    assertEquals(2, first.songs.size()); assertTrue(first.songs.stream().anyMatch(t -> t.url.endsWith(KOLAMA)));
    assertTrue(first.more());
    ArtistSongs.Page second = ArtistSongs.load(artist, "LK", first, source);
    assertEquals(1, second.songs.size()); assertTrue(second.more()); assertTrue(second.cursor.official);
    ArtistSongs.Page third = ArtistSongs.load(artist, "LK", second, source);
    assertEquals(1, third.songs.size()); assertFalse(third.more());
    assertEquals(List.of(JANANA+":",songs+":",videos+":",songs+":audio-next",videos+":video-next"), requests);
  }

  @Test public void officialOnlyArtistWorksAndFailedSecondaryCatalogueStaysRetryable() throws Exception {
    ArtistSongs.Page only = ArtistSongs.load(artist, "LK", null, (id, params, country, token) -> id.equals(JANANA) ? profile(false) : playlist("", kolama()));
    assertTrue(only.songs.get(0).url.endsWith(KOLAMA)); assertFalse(only.more());
    int[] tries = {0};
    ArtistSongs.Source source = (id, params, country, token) -> {
      if (id.equals(JANANA)) return profile(true);
      if (id.equals(songs)) return playlist("", audio());
      if (tries[0]++ == 0) throw new java.io.IOException("temporary");
      return playlist("", kolama());
    };
    ArtistSongs.Page partial = ArtistSongs.load(artist, "LK", null, source);
    assertEquals(1, partial.songs.size()); assertTrue(partial.more()); assertTrue(partial.cursor.official);
    assertTrue(ArtistSongs.load(artist, "LK", partial, source).songs.get(0).url.endsWith(KOLAMA));
  }
}
