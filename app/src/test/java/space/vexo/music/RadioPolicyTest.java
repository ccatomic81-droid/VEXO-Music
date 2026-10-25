package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class RadioPolicyTest {
  static Track song(String id, String title, String artist) {
    return new Track("https://www.youtube.com/watch?v=" + id, title, artist, "", 210);
  }

  static final Track SEED = song("seed", "Seed song", "Anchor");

  private List<Track> select(
      List<Track> pool,
      List<Track> history,
      List<Track> liked,
      Set<String> hidden,
      RadioPolicy.Mode mode) {
    return RadioPolicy.select(
        SEED, SEED, pool, Arrays.asList(SEED), history, liked, hidden, mode, 10);
  }

  @Test
  public void familiarityAndDiscoveryProduceDifferentFirstChoices() {
    Track favourite = song("fav", "Known favourite", "Loved Artist"),
        fresh = song("new", "New song", "New Artist");
    List<Track> pool = Arrays.asList(favourite, fresh),
        liked = Arrays.asList(song("old", "Old song", "Loved Artist"));
    assertEquals(
        favourite.url,
        select(
                pool,
                Collections.emptyList(),
                liked,
                Collections.emptySet(),
                RadioPolicy.Mode.FAMILIAR)
            .get(0)
            .url);
    assertEquals(
        fresh.url,
        select(
                pool,
                Collections.emptyList(),
                liked,
                Collections.emptySet(),
                RadioPolicy.Mode.DISCOVER)
            .get(0)
            .url);
  }

  @Test
  public void duplicateUploadsAndUnsafeCandidatesNeverBecomeRecommendations() {
    Track first = song("one", "Artist - Moon (Official Music Video)", "Artist - Topic");
    Track duplicate = song("two", "Moon Lyrics", "Artist");
    List<Track> pool =
        Arrays.asList(
            first,
            duplicate,
            song("seed", "Seed song", "Anchor"),
            song("blocked", "Hidden", "Other"),
            song("talk", "Podcast interview", "Talk"),
            new Track("https://youtu.be/long", "Long set", "DJ", "", 3600),
            new Track("content://media/1", "Local", "Phone", "", 200),
            song("live", "Moon Live", "Artist"));
    List<Track> out =
        select(
            pool,
            Collections.emptyList(),
            Collections.emptyList(),
            new HashSet<>(Arrays.asList("youtube:blocked")),
            RadioPolicy.Mode.BALANCED);
    assertEquals(2, out.size());
    assertEquals(first.url, out.get(0).url);
    assertEquals("Moon Live", out.get(1).title);
    assertEquals("artist", RadioPolicy.artist(first));
  }

  @Test
  public void artistVarietyAvoidsConsecutivePicksWhenAlternativesExist() {
    List<Track> pool =
        Arrays.asList(
            song("a", "One", "A"),
            song("b", "Two", "A"),
            song("c", "Three", "B"),
            song("d", "Four", "C"));
    List<Track> out =
        select(
            pool,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            RadioPolicy.Mode.BALANCED);
    for (int i = 1; i < out.size(); i++) assertNotEquals(out.get(i - 1).artist, out.get(i).artist);
  }

  @Test
  public void recentAndDiscoverExclusionsDifferWithoutReplayingTheQueue() {
    List<Track> history = new ArrayList<>();
    for (int i = 0; i < 25; i++) history.add(song("h" + i, "Old " + i, "Past"));
    List<Track> pool = Arrays.asList(history.get(0), history.get(24), SEED);
    assertEquals(
        1,
        select(
                pool,
                history,
                Collections.emptyList(),
                Collections.emptySet(),
                RadioPolicy.Mode.FAMILIAR)
            .size());
    assertTrue(
        select(
                pool,
                history,
                Collections.emptyList(),
                Collections.emptySet(),
                RadioPolicy.Mode.DISCOVER)
            .isEmpty());
    List<Track> full = new ArrayList<>();
    for (int i = 0; i < 100; i++) full.add(song("q" + i, "Queued " + i, "Q"));
    assertTrue(
        RadioPolicy.select(
                SEED,
                SEED,
                Arrays.asList(song("new", "New", "N")),
                full,
                history,
                Collections.emptyList(),
                Collections.emptySet(),
                RadioPolicy.Mode.BALANCED,
                10)
            .isEmpty());
  }

  @Test
  public void unicodeAndVersionNamesSurviveNormalization() {
    Track sinhala = song("s", "මගේ ගීතය (Official Audio)", "ගායකයා - Topic");
    assertTrue(RadioPolicy.songKey(sinhala).contains("මගේ"));
    assertNotEquals(
        RadioPolicy.songKey(song("a", "Moon", "Artist")),
        RadioPolicy.songKey(song("b", "Moon Remix", "Artist")));
    assertEquals(RadioPolicy.Mode.BALANCED, RadioPolicy.Mode.read("corrupt"));
  }

  @Test
  public void hiddenSongFeedbackAlsoExcludesAlternateUploads() {
    Track hidden = song("hidden", "Moon (Official Audio)", "Artist - Topic");
    Track alternate = song("alternate", "Moon Lyrics", "Artist");
    Set<String> exclusions =
        new HashSet<>(
            Arrays.asList(QueuePolicy.identity(hidden.url), "song:" + RadioPolicy.songKey(hidden)));
    assertTrue(
        select(
                Arrays.asList(alternate),
                Collections.emptyList(),
                Collections.emptyList(),
                exclusions,
                RadioPolicy.Mode.BALANCED)
            .isEmpty());
  }
}
