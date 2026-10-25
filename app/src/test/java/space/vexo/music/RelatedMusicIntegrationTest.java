package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

public class RelatedMusicIntegrationTest {
  @Test
  public void loadsRealAutoplayCandidatesWithoutDownloadingAudio() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_LIVE_TEST")));
    OnlineMusic.Results results = OnlineMusic.search("Sinhala popular songs");
    assertFalse(results.tracks.isEmpty());
    Track seed =
        results.tracks.stream()
            .filter(RadioPolicy::music)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No song seeds"));
    List<Track> candidates =
        new RadioCatalogue(
                new RadioCatalogue.Source() {
                  public List<Track> related(Track t) throws Exception {
                    return OnlineMusic.related(t);
                  }

                  public List<Track> search(String q) throws Exception {
                    return OnlineMusic.search(q).tracks;
                  }
                })
            .load(seed, seed);
    List<Track> songs =
        RadioPolicy.select(
            seed,
            seed,
            candidates,
            Arrays.asList(seed),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            RadioPolicy.Mode.BALANCED,
            10);
    assertFalse("Expected fresh related music", songs.isEmpty());
    System.out.println(
        "Loaded "
            + songs.size()
            + " fresh autoplay candidates for "
            + seed.title
            + "; first: "
            + songs.get(0).title);
  }
}
