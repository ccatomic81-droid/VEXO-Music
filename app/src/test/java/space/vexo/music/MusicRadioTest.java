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
public class MusicRadioTest {
  JSONObject label(String value) throws Exception {
    return new JSONObject().put("runs", new JSONArray().put(new JSONObject().put("text", value)));
  }

  JSONObject row(String id) throws Exception {
    JSONObject artist =
        new JSONObject()
            .put("text", "Sinhala Artist")
            .put(
                "navigationEndpoint",
                new JSONObject()
                    .put("browseEndpoint", new JSONObject().put("browseId", "UCartist")));
    return new JSONObject()
        .put("videoId", id)
        .put("title", label("A new song"))
        .put(
            "longBylineText",
            new JSONObject()
                .put(
                    "runs",
                    new JSONArray()
                        .put(artist)
                        .put(new JSONObject().put("text", " • "))
                        .put(new JSONObject().put("text", "Album name"))))
        .put("lengthText", label("3:45"))
        .put(
            "thumbnail",
            new JSONObject()
                .put(
                    "thumbnails",
                    new JSONArray()
                        .put(
                            new JSONObject()
                                .put("url", "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"))
                        .put(new JSONObject().put("url", "https://untrusted.example/art"))));
  }

  @Test
  public void musicRadioParsesNestedQueuesAndFiltersUnsafeMalformedAndDuplicateEntries()
      throws Exception {
    JSONObject response =
        new JSONObject()
            .put(
                "contents",
                new JSONObject()
                    .put(
                        "musicQueueRenderer",
                        new JSONArray()
                            .put(
                                new JSONObject()
                                    .put("playlistPanelVideoRenderer", row("abcdefghijk")))
                            .put(
                                new JSONObject()
                                    .put("playlistPanelVideoRenderer", row("abcdefghijk")))
                            .put(new JSONObject().put("playlistPanelVideoRenderer", row("bad/id")))
                            .put(
                                new JSONObject()
                                    .put(
                                        "playlistPanelVideoRenderer",
                                        row("bcdefghijkl").put("isUnplayable", true)))));
    List<Track> songs = MusicRadio.parse(response);
    assertEquals(1, songs.size());
    assertEquals("Sinhala Artist", songs.get(0).artist);
    assertEquals(225, songs.get(0).duration);
    assertTrue(songs.get(0).artwork.startsWith("https://i.ytimg.com/"));
    assertTrue(MusicRadio.parse(new JSONObject()).isEmpty());
    assertEquals(3723, MusicRadio.duration("1:02:03"));
    assertEquals(0, MusicRadio.duration("Live"));
    assertEquals(
        "abcdefghijk",
        MusicRadio.videoId(
            new Track("https://music.youtube.com/watch?v=abcdefghijk", "Song", "Artist", "", 200)));
    assertEquals(
        "",
        MusicRadio.videoId(
            new Track("https://example.com/watch?v=abcdefghijk", "Song", "Artist", "", 200)));
  }

  @Test
  public void liveYouTubeMusicRadioReturnsActualSongs() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    Track seed =
        new Track("https://www.youtube.com/watch?v=2Vv-BfVoq4g", "Perfect", "Ed Sheeran", "", 263);
    List<Track> songs = MusicRadio.load(seed);
    assertTrue(
        "Expected fresh YouTube Music radio entries",
        songs.stream().anyMatch(t -> !t.url.equals(seed.url)));
    System.out.println("YouTube Music radio songs: " + songs.size());
  }
}
