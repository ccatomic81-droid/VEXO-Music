package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

public class MusicDiscoveryLiveTest {
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
}
