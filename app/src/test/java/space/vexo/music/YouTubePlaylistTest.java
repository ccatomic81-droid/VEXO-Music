package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class YouTubePlaylistTest {
  @Test
  public void boundedOwnedPlaylistsUseValidatedVideoMetadataAndNoExternalOrigin() throws Exception {
    List<String> paths = new ArrayList<>();
    List<Track> songs =
        new YouTubeMusic()
            .playlistMusic(
                "memory-only-token",
                (token, url) -> {
                  assertEquals("memory-only-token", token);
                  assertEquals("www.googleapis.com", url.host());
                  assertTrue(url.isHttps());
                  paths.add(url.encodedPath());
                  try {
                    if (url.encodedPath().endsWith("/playlists")) {
                      assertEquals("true", url.queryParameter("mine"));
                      assertEquals("3", url.queryParameter("maxResults"));
                      return new JSONObject()
                          .put(
                              "items",
                              new JSONArray()
                                  .put(new JSONObject().put("id", "PLabcdefghijk"))
                                  .put(new JSONObject().put("id", "PLabcdefghijk"))
                                  .put(new JSONObject().put("id", "https://evil.test")));
                    }
                    if (url.encodedPath().endsWith("/playlistItems"))
                      return new JSONObject()
                          .put(
                              "items",
                              new JSONArray()
                                  .put(
                                      new JSONObject()
                                          .put(
                                              "contentDetails",
                                              new JSONObject().put("videoId", "abcdefghijk")))
                                  .put(
                                      new JSONObject()
                                          .put(
                                              "contentDetails",
                                              new JSONObject().put("videoId", "abcdefghijk")))
                                  .put(
                                      new JSONObject()
                                          .put(
                                              "contentDetails",
                                              new JSONObject().put("videoId", "malformed?url"))));
                    assertEquals("abcdefghijk", url.queryParameter("id"));
                    return new JSONObject()
                        .put(
                            "items",
                            new JSONArray()
                                .put(YouTubeTasteTest.item("abcdefghijk", "10", "PT3M")));
                  } catch (Exception e) {
                    throw new java.io.IOException(e);
                  }
                });
    assertEquals(3, paths.size());
    assertEquals(1, songs.size());
  }

  @Test
  public void cancelledImportDoesNotReadEvenTheFirstPlaylist() throws Exception {
    YouTubeMusic api = new YouTubeMusic();
    api.cancel();
    AtomicInteger reads = new AtomicInteger();
    assertThrows(
        java.io.IOException.class,
        () ->
            api.playlistMusic(
                "token",
                (t, u) -> {
                  reads.incrementAndGet();
                  return new JSONObject();
                }));
    assertEquals(0, reads.get());
  }

  @Test
  public void clearMusicMetadataIncludesMiscategorizedAudioButRejectsTalkAndLive()
      throws Exception {
    JSONObject music = YouTubeTasteTest.item("abcdefghijk", "24", "PT3M");
    music.getJSONObject("snippet").put("title", "Artist - Song (Official Audio)");
    JSONObject talk = YouTubeTasteTest.item("bcdefghijkl", "24", "PT3M");
    talk.getJSONObject("snippet").put("title", "Official Audio reaction interview");
    JSONObject live = YouTubeTasteTest.item("cdefghijklm", "10", "PT3M");
    live.getJSONObject("snippet").put("liveBroadcastContent", "live");
    List<Track> out =
        YouTubeMusic.parse(
            new JSONObject().put("items", new JSONArray().put(music).put(talk).put(live)));
    assertEquals(1, out.size());
    assertTrue(out.get(0).url.endsWith("abcdefghijk"));
  }
}
