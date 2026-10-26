package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MusicDiscoveryLiveTest {
  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void realMusicArtworkDownloadsAndDecodesWithPublicFallback() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    List<Track> songs = OnlineMusic.preview("Ed Sheeran", "LK");
    assertFalse(songs.isEmpty());
    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build();
    int decoded = 0;
    for (Track track : songs.subList(0, Math.min(3, songs.size()))) {
      for (String url : ArtworkQuality.candidates(track.artwork, track.url, 512)) {
        try (okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().url(url).build()).execute()) {
          if (!response.isSuccessful() || response.body() == null
              || !response.header("Content-Type", "").startsWith("image/")) continue;
          android.graphics.Bitmap image = ArtworkQuality.decode(response.body().byteStream());
          if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            decoded++;
            image.recycle();
            break;
          }
        } catch (java.io.IOException unavailable) { /* Try the bounded public fallback. */ }
      }
    }
    assertTrue("Expected real cover bytes, not just artwork URLs", decoded >= 2);
    System.out.println("Live artwork decoded: " + decoded);
  }

  @Test
  public void directMusicCatalogueProvidesSongsAndProviderContinuation() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    MusicSearch.Result result = MusicSearch.load("Ed Sheeran", "LK");
    assertFalse("Expected public YouTube Music songs", result.tracks.isEmpty());
    assertTrue(result.tracks.stream().allMatch(t -> t.duration > 0 && !t.artist.isEmpty()));
    int more = result.continuation.isEmpty() ? 0 : MusicSearch.more(result).tracks.size();
    System.out.println(
        "Direct music catalogue: " + result.tracks.size() + "; continuation=" + more);
  }

  @Test
  public void playableMatchesAndRegionalMixLoadRealMetadata() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    List<Track> songs = OnlineMusic.preview("Ed Sheeran", "LK");
    assertFalse("Expected real search matches", songs.isEmpty());
    assertTrue(
        songs.stream()
            .allMatch(t -> t.online() && !t.title.trim().isEmpty() && !t.artist.trim().isEmpty()));
    assertTrue("Expected real cover artwork", songs.stream().anyMatch(t -> !t.artwork.isEmpty()));
    MixCatalogue.Mix mix = MixCatalogue.find("regional", "LK");
    List<Track> picks = MixCatalogue.songs(OnlineMusic.regionalSearch(mix.query, "LK"));
    assertFalse("Expected songs in the regional mix", picks.isEmpty());
    System.out.println("Live matches: " + songs.size() + "; regional mix songs: " + picks.size());
  }

  @Test
  public void scopedCategoryReturnsRealMusicAndSupportsTheProviderCursor() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    String query = MusicCategory.find("chill").query("", "Sinhala");
    OnlineMusic.Results first = OnlineMusic.search(query);
    assertFalse("Expected scoped category results", first.tracks.isEmpty());
    assertTrue(first.tracks.stream().allMatch(t -> t.online() && !t.title.trim().isEmpty()));
    int more = 0;
    if (org.schabi.newpipe.extractor.Page.isValid(first.next)) {
      OnlineMusic.Results next = OnlineMusic.more(first);
      more = next.tracks.size();
      assertTrue(next.tracks.stream().allMatch(t -> t.online() && !t.title.trim().isEmpty()));
    }
    System.out.println(
        "Live category: " + query + "; first=" + first.tracks.size() + "; cursor songs=" + more);
  }
}
