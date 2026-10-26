package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MusicSearchTest {
  JSONObject line(String text) throws Exception {
    return new JSONObject().put("runs", new JSONArray().put(new JSONObject().put("text", text)));
  }

  JSONObject column(JSONObject text) throws Exception {
    return new JSONObject()
        .put("musicResponsiveListItemFlexColumnRenderer", new JSONObject().put("text", text));
  }

  JSONObject song(String id) throws Exception {
    JSONObject byline = line("Artist");
    byline
        .getJSONArray("runs")
        .getJSONObject(0)
        .put(
            "navigationEndpoint",
            new JSONObject().put("browseEndpoint", new JSONObject().put("browseId", "UCArtist")));
    byline
        .getJSONArray("runs")
        .put(new JSONObject().put("text", " • "))
        .put(new JSONObject().put("text", "3:45"));
    return new JSONObject()
        .put("playlistItemData", new JSONObject().put("videoId", id))
        .put("flexColumns", new JSONArray().put(column(line("සිංහල Song"))).put(column(byline)))
        .put(
            "thumbnail",
            new JSONObject()
                .put(
                    "musicThumbnailRenderer",
                    new JSONObject()
                        .put(
                            "thumbnail",
                            new JSONObject()
                                .put(
                                    "thumbnails",
                                    new JSONArray()
                                        .put(
                                            new JSONObject()
                                                .put(
                                                    "url",
                                                    "https://lh3.googleusercontent.com/small")
                                                .put("width", 60))
                                        .put(
                                            new JSONObject()
                                                .put(
                                                    "url",
                                                    "https://lh3.googleusercontent.com/large")
                                                .put("width", 544))))));
  }

  JSONObject wrap(JSONObject... rows) throws Exception {
    JSONArray values = new JSONArray();
    for (JSONObject row : rows)
      values.put(new JSONObject().put("musicResponsiveListItemRenderer", row));
    return new JSONObject().put("contents", values);
  }

  @Test
  public void explicitVideoAndPodcastTypesAreExcludedFromSongSearch() throws Exception {
    JSONObject audio = song("abcdefghijk").put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV");
    JSONObject video = song("12345678901").put("musicVideoType", "MUSIC_VIDEO_TYPE_UGC");
    JSONObject officialVideo = song("12345678902").put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV");
    List<Track> tracks = MusicSearch.parse(wrap(audio, video, officialVideo), "song", "LK").tracks;
    assertEquals(1, tracks.size());
    assertTrue(tracks.get(0).url.endsWith("abcdefghijk"));
  }

  @Test
  public void songsCarryArtistDurationBestCoverAndDeduplicateByVideo() throws Exception {
    MusicSearch.Result result =
        MusicSearch.parse(wrap(song("abcdefghijk"), song("abcdefghijk")), "සිංහල", "LK");
    assertEquals(1, result.tracks.size());
    Track t = result.tracks.get(0);
    assertEquals("සිංහල Song", t.title);
    assertEquals("Artist", t.artist);
    assertEquals(225, t.duration);
    assertEquals("https://lh3.googleusercontent.com/large", t.artwork);
    assertEquals("LK", result.country);
  }

  @Test
  public void overlayEndpointsAndFixedDurationColumnsRemainPlayable() throws Exception {
    JSONObject row = song("abcdefghijk");
    row.remove("playlistItemData");
    row.put(
        "overlay",
        new JSONObject()
            .put(
                "playNavigationEndpoint",
                new JSONObject()
                    .put("watchEndpoint", new JSONObject().put("videoId", "12345678901"))));
    row.getJSONArray("flexColumns").put(1, column(line("Singer")));
    row.put(
        "fixedColumns",
        new JSONArray()
            .put(
                new JSONObject()
                    .put(
                        "musicResponsiveListItemFixedColumnRenderer",
                        new JSONObject().put("text", line("4:12")))));
    Track t = MusicSearch.parse(wrap(row), "song", "LK").tracks.get(0);
    assertEquals("https://www.youtube.com/watch?v=12345678901", t.url);
    assertEquals(252, t.duration);
  }

  @Test
  public void continuationsPreserveMusicProviderCursor() throws Exception {
    JSONObject page =
        new JSONObject()
            .put(
                "continuationContents",
                new JSONObject()
                    .put("musicShelfContinuation", wrap(song("abcdefghijk")))
                    .put(
                        "continuations",
                        new JSONArray()
                            .put(
                                new JSONObject()
                                    .put(
                                        "nextContinuationData",
                                        new JSONObject().put("continuation", "cursor_2")))));
    MusicSearch.Result result = MusicSearch.parse(page, "song", "IN");
    assertEquals("cursor_2", result.continuation);
    OnlineMusic.Results publicPage = new OnlineMusic.Results(result);
    assertTrue(org.schabi.newpipe.extractor.Page.isValid(publicPage.next));
    assertSame(result, publicPage.music);
    assertNull(publicPage.query);
  }

  @Test
  public void podcastsGreyedRowsAndInvalidVideosDoNotBecomeMusic() throws Exception {
    JSONObject podcast =
        song("abcdefghijk")
            .put(
                "navigationEndpoint",
                new JSONObject()
                    .put(
                        "watchEndpoint",
                        new JSONObject()
                            .put("musicVideoType", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE")));
    JSONObject blocked =
        song("12345678901")
            .put("musicItemRendererDisplayPolicy", "MUSIC_ITEM_RENDERER_DISPLAY_POLICY_GREY_OUT");
    assertTrue(
        MusicSearch.parse(wrap(podcast, blocked, song("bad-id")), "song", "LK").tracks.isEmpty());
    assertTrue(MusicSearch.parse(new JSONObject(), "", "LK").continuation.isEmpty());
  }
}
