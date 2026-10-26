package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Application;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MusicInterestsTest {
  Track song(String id, String artist) {
    return YouTubeTasteTest.song(id, "Song " + id, artist);
  }

  MusicInterests profile(
      YouTubeTaste.Snapshot yt,
      List<Track> liked,
      List<Track> history,
      JSONObject prefs,
      Set<String> hidden) {
    return MusicInterests.of(
        liked, history, Collections.emptyList(), yt, prefs, new JSONObject(), hidden);
  }

  @Test
  public void connectedMusicChangesRankingAndDisconnectRemovesOnlyImportedSignals() {
    Track imported = song("abcdefghijk", "Loved Artist"),
        fresh = song("bcdefghijkl", "Other Artist"),
        related = song("cdefghijklm", "Loved Artist - Topic");
    YouTubeTaste.Snapshot yt =
        new YouTubeTaste.Snapshot(true, 1, Arrays.asList(imported), Collections.emptyList());
    MusicInterests connected =
        profile(
            yt,
            Collections.emptyList(),
            Collections.emptyList(),
            new JSONObject(),
            Collections.emptySet());
    assertEquals(related.url, connected.rank(Arrays.asList(fresh, related), false).get(0).url);
    MusicInterests disconnected =
        profile(
            YouTubeTaste.Snapshot.empty(),
            Collections.emptyList(),
            Collections.emptyList(),
            new JSONObject(),
            Collections.emptySet());
    assertEquals(fresh.url, disconnected.rank(Arrays.asList(fresh, related), false).get(0).url);
    assertTrue(disconnected.imported.isEmpty());
  }

  @Test
  public void playlistsProvideSeedsWhenLikesAreEmptyAndOptInIsRequired() throws Exception {
    Track playlist = song("abcdefghijk", "Playlist Artist");
    YouTubeTaste.Snapshot yt =
        new YouTubeTaste.Snapshot(
            true,
            1,
            Collections.emptyList(),
            Collections.emptyList(),
            Arrays.asList(playlist),
            true);
    assertEquals(
        playlist.url,
        profile(
                yt,
                Collections.emptyList(),
                Collections.emptyList(),
                new JSONObject(),
                Collections.emptySet())
            .seeds
            .get(0)
            .url);
    YouTubeTaste.Snapshot declined =
        new YouTubeTaste.Snapshot(
            true,
            1,
            Collections.emptyList(),
            Collections.emptyList(),
            Arrays.asList(playlist),
            false);
    assertTrue(declined.sources().isEmpty());
    Application app = RuntimeEnvironment.getApplication();
    YouTubeTaste store = new YouTubeTaste(app);
    store.save(
        Collections.emptyList(),
        Collections.emptyList(),
        Arrays.asList(playlist),
        true,
        System.currentTimeMillis());
    assertEquals(1, store.read().playlistSongs.size());
    assertFalse(new PrivateStore(app, "youtube_taste").getString("snapshot", "").isEmpty());
    assertFalse(
        app.getSharedPreferences("private_collections_v1", 0)
            .getAll()
            .toString()
            .contains("Playlist Artist"));
    store.clear();
    assertTrue(MusicInterests.read(app).imported.isEmpty());
  }

  @Test
  public void feedbackAndRecentExclusionsBeatImportedLikesWithoutLosingRecentSeeds()
      throws Exception {
    Application app = RuntimeEnvironment.getApplication();
    Track like = song("abcdefghijk", "Loved"), recent = song("bcdefghijkl", "Recent");
    new YouTubeTaste(app)
        .save(Arrays.asList(like), Collections.emptyList(), System.currentTimeMillis());
    new LibraryStore(app).recent(recent);
    SmartStore smart = new SmartStore(app);
    smart.played(recent, System.currentTimeMillis());
    smart.feedback(like, "less");
    MusicInterests interests = MusicInterests.read(app);
    assertTrue(interests.seeds.stream().anyMatch(t -> t.url.equals(recent.url)));
    assertFalse(interests.seeds.stream().anyMatch(t -> t.url.equals(like.url)));
    assertFalse(
        interests.rank(Arrays.asList(recent), false).stream()
            .anyMatch(t -> t.url.equals(recent.url)));
    new RadioStore(app).hide(like);
    assertTrue(MusicInterests.read(app).rank(Arrays.asList(like), false).isEmpty());
  }

  @Test
  public void pausedLearningDoesNotUseHistoryAsATasteSeed() throws Exception {
    Track recent = song("abcdefghijk", "Recent");
    MusicInterests p =
        profile(
            YouTubeTaste.Snapshot.empty(),
            Collections.emptyList(),
            Arrays.asList(recent),
            new JSONObject().put("learn_listening", false),
            Collections.emptySet());
    assertTrue(p.seeds.isEmpty());
    assertEquals(0, p.bonus(recent));
  }

  @Test
  public void discoveryUsesBoundedDiverseSeedsAndFiltersHiddenAndDuplicateUploads()
      throws Exception {
    Track one = song("abcdefghijk", "One"),
        two = song("bcdefghijkl", "Two"),
        three = song("cdefghijklm", "Three"),
        pick = song("defghijklmn", "New"),
        blocked = song("efghijklmno", "Hidden");
    Set<String> hidden = new HashSet<>(Arrays.asList(QueuePolicy.identity(blocked.url)));
    MusicInterests p =
        profile(
            YouTubeTaste.Snapshot.empty(),
            Arrays.asList(one, two, three),
            Collections.emptyList(),
            new JSONObject(),
            hidden);
    List<String> requests = new ArrayList<>();
    List<Track> out =
        PersonalDiscovery.load(
            p,
            new PersonalDiscovery.Source() {
              public List<Track> related(Track seed) {
                requests.add(seed.artist);
                return Arrays.asList(pick, pick, blocked);
              }

              public List<Track> search(String q) {
                fail("No broad search without explicit interests");
                return Collections.emptyList();
              }
            });
    assertEquals(2, requests.size());
    assertEquals(1, out.size());
    assertEquals(pick.url, out.get(0).url);
    Thread.currentThread().interrupt();
    try {
      PersonalDiscovery.load(
          p,
          new PersonalDiscovery.Source() {
            public List<Track> related(Track t) {
              fail();
              return null;
            }

            public List<Track> search(String q) {
              fail();
              return null;
            }
          });
      fail();
    } catch (InterruptedException expected) {
    } finally {
      Thread.interrupted();
    }
  }
}
