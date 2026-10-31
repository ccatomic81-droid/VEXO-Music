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
public class ArtistCataloguePagingTest {
  final String id = ArtistIdentityTest.KEVIN;
  final SearchArtists.Artist artist = new SearchArtists.Artist(id, "Kevin Smokio", "", List.of(), 1, false);
  final String playlist = "VLOLAK5uy_test_artist_catalogue";
  JSONObject shelf(String title, String target) throws Exception {
    return new JSONObject().put("musicShelfRenderer", new JSONObject()
        .put("title", new JSONObject().put("runs", new JSONArray().put(new JSONObject().put("text", title))))
        .put("bottomEndpoint", new JSONObject().put("browseEndpoint", new JSONObject().put("browseId", target))));
  }
  JSONObject row(int i, String artistId) throws Exception {
    MusicSearchTest helper = new MusicSearchTest();
    JSONObject row = helper.song(String.format(Locale.ROOT, "%011d", i));
    row.getJSONArray("flexColumns").getJSONObject(1)
        .getJSONObject("musicResponsiveListItemFlexColumnRenderer").getJSONObject("text")
        .getJSONArray("runs").getJSONObject(0).getJSONObject("navigationEndpoint")
        .getJSONObject("browseEndpoint").put("browseId", artistId);
    return new JSONObject().put("musicResponsiveListItemRenderer", row);
  }
  JSONObject page(int start, int count, String token) throws Exception {
    JSONArray rows = new JSONArray();
    for (int i = start; i < start + count; i++) rows.put(row(i, id));
    JSONObject shelf = new JSONObject().put("contents", rows);
    if (!token.isEmpty()) shelf.put("continuations", new JSONArray().put(new JSONObject()
        .put("nextContinuationData", new JSONObject().put("continuation", token))));
    return new JSONObject().put("contents", new JSONObject().put("musicPlaylistShelfRenderer", shelf));
  }

  @Test public void followsArtistSongsPointerAndKeepsCursorBeyondOneHundredSongs() throws Exception {
    List<String> requests = new ArrayList<>();
    JSONObject profile = new JSONObject().put("contents", new JSONArray()
        .put(shelf("Related artists", "VLPL_unrelated_video_list"))
        .put(shelf("Top songs", playlist)));
    ArtistSongs.Source source = (browseId, params, country, continuation) -> {
      requests.add(browseId + ":" + continuation);
      if (browseId.equals(id)) return profile;
      assertEquals(playlist, browseId);
      return continuation.isEmpty() ? page(0, 100, "page-two") : page(100, 35, "");
    };
    ArtistSongs.Page first = ArtistSongs.load(artist, "LK", null, source);
    assertEquals(100, first.songs.size()); assertTrue(first.more());
    ArtistSongs.Page second = ArtistSongs.load(artist, "LK", first, source);
    assertEquals(35, second.songs.size()); assertFalse(second.more());
    assertEquals(List.of(id + ":", playlist + ":", playlist + ":page-two"), requests);
  }

  @Test public void excludesSuggestedAndNamesakeSongsAndHandlesActionContinuations() throws Exception {
    JSONObject good = page(0, 2, "next");
    JSONArray content = good.getJSONObject("contents").getJSONObject("musicPlaylistShelfRenderer").getJSONArray("contents");
    content.put(row(9, ArtistIdentityTest.ADHEESHA));
    good.put("recommendations", new JSONObject().put("musicShelfRenderer", new JSONObject().put("contents", new JSONArray().put(row(10, id)))));
    ArtistSongs.Cursor cursor = new ArtistSongs.Cursor(playlist, "", "LK", "");
    ArtistSongs.Page first = ArtistSongs.parse(artist, cursor, good);
    assertEquals(2, first.songs.size());
    JSONObject more = new JSONObject().put("onResponseReceivedActions", new JSONArray().put(new JSONObject()
        .put("appendContinuationItemsAction", new JSONObject().put("continuationItems", new JSONArray().put(row(12, id))
            .put(new JSONObject().put("continuationItemRenderer", new JSONObject().put("continuationEndpoint",
                new JSONObject().put("continuationCommand", new JSONObject().put("continuation", "next")))))))));
    ArtistSongs.Page repeated = ArtistSongs.parse(artist, new ArtistSongs.Cursor(playlist, "", "LK", "next"), more);
    assertEquals(1, repeated.songs.size()); assertFalse("Repeated cursors must stop", repeated.more());
    assertNull(ArtistSongs.songsEndpoint(shelf("Related artists", playlist), 0));
    assertNull(ArtistSongs.songsEndpoint(shelf("Songs", "https://evil.example/"), 0));
  }

  @Test public void sparsePagesKeepGoingAndIncompletePagesAreRetryable() throws Exception {
    JSONObject profile = new JSONObject().put("contents", shelf("Songs", playlist));
    ArtistSongs.Page page = ArtistSongs.load(artist, "LK", null, (browseId, params, country, token) -> {
      if (browseId.equals(id)) return profile;
      if (token.isEmpty()) return new JSONObject().put("contents", new JSONObject().put("musicPlaylistShelfRenderer",
          new JSONObject().put("contents", new JSONArray().put(row(5, ArtistIdentityTest.ADHEESHA)))
              .put("continuations", new JSONArray().put(new JSONObject().put("nextContinuationData", new JSONObject().put("continuation", "next"))))));
      return page(1, 3, "");
    });
    assertEquals(3, page.songs.size());
    try { ArtistSongs.parse(artist, new ArtistSongs.Cursor(playlist, "", "LK", ""), new JSONObject().put("contents", new JSONArray())); fail(); }
    catch (java.io.IOException expected) {}
  }
}
