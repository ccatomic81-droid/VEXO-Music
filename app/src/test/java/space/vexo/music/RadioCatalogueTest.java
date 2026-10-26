package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class RadioCatalogueTest {
  @Test
  public void oneDistinctInterestSeedBroadensTheStationWithIndependentFailureFallback()
      throws Exception {
    List<String> related = new ArrayList<>();
    Track interest = RadioPolicyTest.song("interest", "Taste seed", "Loved");
    RadioCatalogue catalogue =
        new RadioCatalogue(
            new RadioCatalogue.Source() {
              public List<Track> related(Track seed) {
                related.add(seed.artist);
                return Arrays.asList(
                    RadioPolicyTest.song(seed.artist, "Next " + seed.artist, seed.artist));
              }

              public List<Track> search(String query) {
                return Collections.emptyList();
              }
            });
    List<Track> result =
        catalogue.load(
            RadioPolicyTest.SEED,
            RadioPolicyTest.SEED,
            Arrays.asList(
                RadioPolicyTest.SEED, interest, RadioPolicyTest.song("unused", "Unused", "Other")));
    assertEquals(Arrays.asList("Anchor", "Loved"), related);
    assertEquals(2, result.size());
  }

  @Test
  public void keepsOriginalSeedAndFallsBackWhenRelatedRequestsFail() throws Exception {
    List<String> requests = new ArrayList<>();
    RadioCatalogue catalogue =
        new RadioCatalogue(
            new RadioCatalogue.Source() {
              public List<Track> related(Track t) throws Exception {
                requests.add(t.title);
                throw new java.io.IOException("offline");
              }

              public List<Track> search(String q) {
                requests.add(q);
                return Arrays.asList(RadioPolicyTest.song("new", "Next", "Other"));
              }
            });
    List<Track> result =
        catalogue.load(
            RadioPolicyTest.SEED, RadioPolicyTest.song("current", "Current song", "Different"));
    assertEquals(Arrays.asList("Seed song", "Current song", "Anchor songs"), requests);
    assertEquals(1, result.size());
  }

  @Test
  public void sparseMusicAndCanonicalDuplicatesAreFilteredBeforeRanking() throws Exception {
    RadioCatalogue catalogue =
        new RadioCatalogue(
            new RadioCatalogue.Source() {
              public List<Track> related(Track t) {
                return Arrays.asList(
                    new Track("https://youtu.be/talk", "Podcast", "Talk", "", 200),
                    RadioPolicyTest.song("same", "Tune", "A"));
              }

              public List<Track> search(String q) {
                return Arrays.asList(
                    new Track("https://youtu.be/same", "Tune", "A", "", 200),
                    RadioPolicyTest.song("second", "Another", "B"));
              }
            });
    assertEquals(2, catalogue.load(RadioPolicyTest.SEED, RadioPolicyTest.SEED).size());
  }

  @Test
  public void totalFailureIsSurfacedAndCancellationDoesNotStartMoreRequests() throws Exception {
    RadioCatalogue catalogue =
        new RadioCatalogue(
            new RadioCatalogue.Source() {
              public List<Track> related(Track t) throws Exception {
                throw new java.io.IOException("unavailable");
              }

              public List<Track> search(String q) throws Exception {
                throw new java.io.IOException("unavailable");
              }
            });
    try {
      catalogue.load(RadioPolicyTest.SEED, RadioPolicyTest.SEED);
      fail();
    } catch (java.io.IOException expected) {
    }
    Thread.currentThread().interrupt();
    try {
      catalogue.load(RadioPolicyTest.SEED, RadioPolicyTest.SEED);
      fail();
    } catch (InterruptedException expected) {
    } finally {
      Thread.interrupted();
    }
  }
}
