package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArtistsPageTest {
  final String first = ArtistIdentityTest.KEVIN, other = ArtistIdentityTest.ADHEESHA;
  SearchArtists.Artist artist(String id, String name) {
    return new SearchArtists.Artist(id, name, "", Collections.emptyList(), 1, false);
  }
  Track song(String id, String title, ArtistCredit... credits) {
    return new Track("https://www.youtube.com/watch?v=" + id, title, "Artist name", "", 210)
        .withArtists(Arrays.asList(credits));
  }

  @After public void palette() { Visuals.appearance(false); }

  @Test public void songsRequireExactArtistIdAndIncludeCreditedCollaborations() {
    SearchArtists.Artist a = artist(first, "Earth, Wind & Fire");
    Track solo = song("abcdefghijk", "Solo", new ArtistCredit(first, a.name));
    Track duet = song("bcdefghijkl", "Duet", new ArtistCredit(other, "Guest"), new ArtistCredit(first, a.name));
    Track namesake = song("cdefghijklm", a.name + " tribute", new ArtistCredit(other, a.name));
    Track unverified = song("defghijklmn", "Song mentioning " + a.name);
    assertEquals(Arrays.asList(solo, duet), ArtistSongs.only(a, Arrays.asList(solo, namesake, duet, solo, unverified)));
    assertFalse(ArtistSongs.credited(artist("", a.name), solo));
    assertFalse(ArtistSongs.credited(a, namesake));
  }

  @Test public void artistSearchIsSeparateFromSongsAndKeepsNamesakesDistinct() {
    var a = new ChannelInfoItem(0, "https://www.youtube.com/channel/" + first, "Same name");
    var b = new ChannelInfoItem(0, "https://www.youtube.com/channel/" + other, "Same name");
    var unsafe = new ChannelInfoItem(0, "https://youtube.com.evil.example/channel/" + first, "Wrong");
    var results = ArtistCatalogue.artists(Arrays.asList(a, b, a, unsafe));
    assertEquals(2, results.size());
    assertEquals(first, results.get(0).id);
    assertEquals(other, results.get(1).id);
  }

  @Test public void followingSurvivesHistoryClearAndRoundTripsIdentity() {
    Context c = RuntimeEnvironment.getApplication();
    SavedArtists saved = new SavedArtists(c);
    SearchArtists.Artist a = artist(first, "Earth, Wind & Fire");
    assertFalse(saved.follow(artist("", "Unresolved")));
    assertTrue(saved.follow(a));
    assertTrue(saved.follow(a));
    assertTrue(saved.follow(artist(other, a.name)));
    assertEquals(2, saved.entries().size());
    new SearchHistory(c).clear();
    assertEquals(2, new SavedArtists(c).entries().size());
    assertEquals(first, SavedArtists.decode(SavedArtists.encode(a)).id);
    saved.unfollow(a);
    assertFalse(saved.contains(a));
    assertEquals(other, saved.entries().get(0).id);
  }

  @Test public void directoryAndArtistPagesFitBothThemesAndOpenOnlySelectedSongs() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SearchArtists.Artist a = artist(first, "Earth, Wind & Fire");
    Track solo = song("abcdefghijk", "A familiar song", new ArtistCredit(first, a.name));
    Track duet = song("bcdefghijkl", "A collaboration with a longer song title", new ArtistCredit(first, a.name));
    new SavedArtists(c).follow(a);
    var helper = new DiscoveryUiTest();
    var capture = new ArtistAppearanceTest();
    List<String> actions = new ArrayList<>();
    for (boolean light : new boolean[] {false, true}) {
      Visuals.appearance(light);
      for (int width : new int[] {320, 420}) {
        ArtistsPanel directory = new ArtistsPanel(c, "", Collections.emptyList(), false, false, "",
            new ArtistsPanel.Listener() {
              public void search(String q) { actions.add("search:" + q); }
              public void open(SearchArtists.Artist artist) { actions.add(artist.id); }
            });
        assertTrue(helper.find(directory, "Open artist " + a.name).performClick());
        capture.capture(directory, width, 800, "artists-" + light + "-" + width);
        ArtistPanel profile = new ArtistPanel(c, a, Arrays.asList(solo, duet), true, false, true, "",
            new ArtistPanel.Listener() {
              public void back() { actions.add("back"); }
              public void follow() { actions.add("follow"); }
              public void play(int index) { actions.add("play:" + index); }
              public void shuffle() { actions.add("shuffle"); }
              public void options(Track t) {}
              public void more() { actions.add("more"); }
              public void retry() {}
            });
        assertNotNull(helper.text(profile, a.name));
        assertTrue(helper.find(profile, "Play " + duet.title + " by " + duet.artist).performClick());
        assertTrue(helper.text(profile, "Load more songs").performClick());
        capture.capture(profile, width, 800, "artist-profile-" + light + "-" + width);
      }
    }
    assertTrue(actions.contains(first));
    assertEquals(4, Collections.frequency(actions, "play:1"));
    assertEquals(4, Collections.frequency(actions, "more"));
  }

  @Test public void homeOpensArtistProfileBackReturnsAndLateResponseCannotReopenIt() throws Exception {
    DiscoveryUiTest helper = new DiscoveryUiTest();
    helper.seed();
    Context c = RuntimeEnvironment.getApplication();
    SearchArtists.Artist wanted = artist(first, "Kevin Smokio");
    new SavedArtists(c).follow(wanted);
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), returned = new CountDownLatch(1);
    try (var activity = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = activity.get();
      Field source = MainActivity.class.getDeclaredField("artistSource");
      source.setAccessible(true);
      source.set(a, new MainActivity.ArtistSource() {
        public List<SearchArtists.Artist> search(String query, String country) { throw new AssertionError("Song search must not open an artist"); }
        public ArtistSongs.Page songs(SearchArtists.Artist artist, String country, ArtistSongs.Page previous) {
          assertEquals(first, artist.id);
          entered.countDown();
          try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
          returned.countDown();
          return new ArtistSongs.Page(artist, Collections.emptyList(), null);
        }
      });
      View root = a.findViewById(android.R.id.content);
      assertTrue(helper.find(root, "Find songs by " + wanted.name).performClick());
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertNotNull(helper.find(root, "Back from artist"));
      assertNull(new ArtistAppearanceTest().findType(root, ArtistsPanel.class));
      a.getOnBackPressedDispatcher().onBackPressed();
      assertNull(helper.find(root, "Back from artist"));
      release.countDown();
      assertTrue(returned.await(2, TimeUnit.SECONDS));
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNull(helper.find(root, "Back from artist"));
      assertNotNull(new ArtistAppearanceTest().findType(root, MusicHomePanel.class));
      helper.find(root, "Open Artists page").performClick();
      assertNotNull(new ArtistAppearanceTest().findType(root, ArtistsPanel.class));
      assertNotNull(helper.find(root, "Open artist " + wanted.name));
    } finally { release.countDown(); }
  }
}
