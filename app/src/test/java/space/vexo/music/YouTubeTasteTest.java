package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Application;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class YouTubeTasteTest {
  static Track song(String id, String title, String artist) {
    return new Track("https://www.youtube.com/watch?v=" + id, title, artist, "", 200);
  }

  static JSONObject item(String id, String category, String duration) throws Exception {
    return new JSONObject()
        .put("id", id)
        .put(
            "snippet",
            new JSONObject()
                .put("title", "A music song")
                .put("channelTitle", "Artist")
                .put("categoryId", category)
                .put("liveBroadcastContent", "none")
                .put(
                    "thumbnails",
                    new JSONObject()
                        .put(
                            "maxres",
                            new JSONObject().put("url", "https://untrusted.example/image"))
                        .put(
                            "high",
                            new JSONObject()
                                .put("url", "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"))))
        .put("contentDetails", new JSONObject().put("duration", duration));
  }

  @Test
  public void importsOnlyFiniteMusicAndValidIdsWithTrustedArtwork() throws Exception {
    JSONObject live = item("bcdefghijkl", "10", "PT3M");
    live.getJSONObject("snippet").put("liveBroadcastContent", "live");
    JSONObject response =
        new JSONObject()
            .put(
                "items",
                new JSONArray()
                    .put(item("abcdefghijk", "10", "PT3M25S"))
                    .put(item("abcdefghijk", "10", "PT3M25S"))
                    .put(item("cdefghijklm", "20", "PT3M"))
                    .put(item("defghijklmn", "10", "P1D"))
                    .put(item("efghijklmno", "10", "broken"))
                    .put(item("invalid?url!", "10", "PT3M"))
                    .put(live)
                    .put(JSONObject.NULL));
    List<Track> songs = YouTubeMusic.parse(response);
    assertEquals(1, songs.size());
    assertEquals(205, songs.get(0).duration);
    assertTrue(songs.get(0).artwork.startsWith("https://i.ytimg.com/"));
    assertTrue(YouTubeMusic.parse(new JSONObject()).isEmpty());
  }

  @Test
  public void paginationIsBoundedAndRepeatedPageStopsAndCancellationDoesNotRead() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    YouTubeMusic endless =
        new YouTubeMusic(
            (token, page) -> {
              assertEquals("only-in-memory", token);
              int n = calls.incrementAndGet();
              try {
                return new JSONObject()
                    .put("items", new JSONArray())
                    .put("nextPageToken", "page" + n);
              } catch (JSONException e) {
                throw new IOException(e);
              }
            });
    assertTrue(endless.likes("only-in-memory").isEmpty());
    assertEquals(4, calls.get());
    calls.set(0);
    YouTubeMusic repeated =
        new YouTubeMusic(
            (token, page) -> {
              calls.incrementAndGet();
              try {
                return new JSONObject().put("nextPageToken", "same");
              } catch (JSONException e) {
                throw new IOException(e);
              }
            });
    repeated.likes("only-in-memory");
    assertEquals(2, calls.get());
    repeated.cancel();
    assertThrows(IOException.class, () -> repeated.likes("only-in-memory"));
    assertEquals(2, calls.get());
    assertThrows(IOException.class, () -> repeated.likes(""));
  }

  @Test
  public void recommendationsExcludeLikedDuplicatesAndStayUsableWhenOneSourceFails() {
    Track first = song("abcdefghijk", "Original", "Artist One");
    Track second = song("bcdefghijkl", "Another", "Artist Two");
    Track pick = song("cdefghijklm", "Fresh music", "Artist Three");
    List<Track> picks =
        YouTubeMusic.picks(
            Arrays.asList(first, second),
            seed -> {
              if (seed == second) throw new IOException("Offline");
              return Arrays.asList(
                  first, pick, pick, new Track("file:///private.mp3", "Local", "Artist", "", 200));
            });
    assertEquals(1, picks.size());
    assertEquals(pick.url, picks.get(0).url);
    assertTrue(
        YouTubeMusic.picks(
                Collections.emptyList(),
                seed -> {
                  fail();
                  return null;
                })
            .isEmpty());
  }

  @Test
  public void optionalDataExpiresAndDisconnectLeavesExistingLibraryIntact() throws Exception {
    Application app = RuntimeEnvironment.getApplication();
    YouTubeTaste taste = new YouTubeTaste(app);
    assertFalse(taste.read().connected);
    Track song = song("abcdefghijk", "Private liked song", "Artist");
    LibraryStore library = new LibraryStore(app);
    library.save("favourites", Collections.singletonList(song));
    long now = System.currentTimeMillis();
    taste.save(Collections.singletonList(song), Collections.singletonList(song), now);
    assertEquals(song.url, new YouTubeTaste(app).read(now).likes.get(0).url);
    assertTrue(app.getSharedPreferences("youtube_taste", 0).getAll().isEmpty());
    assertFalse(
        app.getSharedPreferences("private_collections_v1", 0)
            .getString("youtube_taste", "")
            .contains("Private liked song"));
    assertFalse(taste.read(now + YouTubeTaste.MAX_AGE).connected);
    assertEquals(1, library.tracks("favourites").size());
    taste.save(Collections.singletonList(song), Collections.emptyList(), now);
    taste.clear();
    assertFalse(new YouTubeTaste(app).read(now).connected);
    assertEquals(1, library.tracks("favourites").size());
    assertTrue(new PrivateStore(app, "youtube_taste").getString("snapshot", "").isEmpty());
  }
}
