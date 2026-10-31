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
  public void jananaKolamaAppearsInExplicitSearchAndArtistCatalogueAsOfficialMusic() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    String id = "UCz2nbDqzWpCdA5JKuRh_hUw", video = "31HnVoGRXvw";
    for (String country : Arrays.asList("LK", RegionPolicy.GLOBAL)) {
      OnlineMusic.Results results = OnlineMusic.userSearch("Janana Kolama", country);
      Track found = results.tracks.stream().filter(t -> t.url.endsWith(video)).findFirst().orElseThrow();
      assertEquals("JANANA", found.artist.toUpperCase(Locale.ROOT));
      assertEquals(257, found.duration);
      assertTrue("Exact title/artist result should rank first", results.tracks.get(0).url.endsWith(video));
      SearchArtists.Artist artist = new SearchArtists.Artist(id, "JANANA", "", Collections.emptyList(), 1, false);
      ArtistSongs.Page page = ArtistSongs.load(artist, country, null);
      assertTrue("Kolama must appear without another Load more tap", page.songs.stream().anyMatch(t -> t.url.endsWith(video)));
      assertTrue(page.songs.stream().allMatch(t -> ArtistSongs.credited(artist, t)));
      System.out.println("Verified JANANA Kolama in " + country + ": search first; catalogue=" + page.songs.size() + "; duration=" + found.duration);
    }
  }

  @Test
  public void artistDirectoryLoadsOnlyTheSelectedArtistsCreditedSongs() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    for (String name : Arrays.asList("Kevin Smokio", "Billie Eilish")) {
      List<SearchArtists.Artist> results = OnlineMusic.searchArtists(name, RegionPolicy.GLOBAL);
      SearchArtists.Artist artist = results.stream().filter(a -> a.name.equalsIgnoreCase(name))
          .findFirst().orElseThrow();
      ArtistSongs.Page page = ArtistSongs.load(artist, RegionPolicy.GLOBAL, null);
      assertTrue("Expected the artist catalogue beyond a 20-song search page for " + name, page.songs.size() >= 40);
      assertTrue(page.songs.stream().allMatch(t -> ArtistSongs.credited(artist, t)));
      if (page.more()) {
        ArtistSongs.Page next = ArtistSongs.load(artist, RegionPolicy.GLOBAL, page);
        assertTrue(next.songs.stream().allMatch(t -> ArtistSongs.credited(artist, t)));
        assertFalse("Continuation must return additional catalogue songs", next.songs.isEmpty());
      }
      System.out.println("Verified artist songs: " + name + " " + artist.id + " count=" + page.songs.size());
    }
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void artistPortraitsMatchKevinSmokioAndBillieEilishAndDecodeRealPhotos() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    okhttp3.OkHttpClient client =
        new okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    for (String name : Arrays.asList("Kevin Smokio", "Billie Eilish")) {
      SearchArtists.Artist wanted = new SearchArtists.Artist(name, "", "fixture");
      List<SearchArtists.Artist> results = ArtistCatalogue.resolve(wanted, RegionPolicy.GLOBAL);
      assertEquals(1, results.size());
      SearchArtists.Artist actual = results.get(0);
      assertFalse("Expected canonical artist id for " + name, actual.id.isEmpty());
      assertFalse("Expected artist portrait for " + name, actual.artwork.isEmpty());
      try (okhttp3.Response response =
          client.newCall(new okhttp3.Request.Builder().url(actual.artwork).build()).execute()) {
        assertTrue(response.isSuccessful());
        android.graphics.Bitmap image = ArtworkQuality.decode(response.body().byteStream());
        assertNotNull("Expected real artist photo bytes for " + name, image);
        image.recycle();
      }
      System.out.println("Verified artist portrait: " + name + " " + actual.id);
    }
  }

  @Test
  public void globalFeedReturnsActualMusicWithoutACountryCode() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    RegionalFeed feed = RegionalFeed.fetch(RegionPolicy.GLOBAL);
    assertEquals(RegionPolicy.GLOBAL, feed.country);
    assertFalse(feed.ranked);
    assertTrue("Expected worldwide music", feed.tracks.size() >= 5);
    assertTrue(feed.tracks.stream().allMatch(RadioPolicy::music));
    System.out.println("Global music feed: " + feed.tracks.size() + " songs");
  }

  @Test
  public void trendingAndPersonalizedMixesReturnActualSongsWithoutAccountLogin() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    Track seed =
        OnlineMusic.preview("Ed Sheeran Perfect", "LK").stream()
            .filter(RadioPolicy::music)
            .findFirst()
            .orElseThrow();
    MusicInterests interests =
        MusicInterests.of(
            Arrays.asList(seed),
            Collections.emptyList(),
            Collections.emptyList(),
            YouTubeTaste.Snapshot.empty(),
            new org.json.JSONObject().put("language", "English"),
            new org.json.JSONObject(),
            Collections.emptySet());
    PersonalDiscovery.Source source =
        new PersonalDiscovery.Source() {
          public List<Track> related(Track t) throws Exception {
            return OnlineMusic.relatedPreview(t);
          }

          public List<Track> search(String q) throws Exception {
            return OnlineMusic.preview(q, "LK");
          }

          public List<Track> trending(String q) throws Exception {
            return TrendingMusic.load(q, "LK");
          }
        };
    for (String id : new String[] {"daily", "calm", "trending", "discover"}) {
      List<Track> songs =
          MixDiscovery.load(
              MixCatalogue.find(id, "LK"),
              interests,
              source,
              "LK",
              System.currentTimeMillis(),
              Collections.emptySet());
      assertTrue("Expected playable mix: " + id, songs.size() >= 2);
      assertTrue(songs.stream().allMatch(RadioPolicy::music));
      if (id.equals("discover"))
        assertTrue(
            songs.stream()
                .noneMatch(t -> RadioPolicy.songKey(t).equals(RadioPolicy.songKey(seed))));
      System.out.println("Live " + id + ": " + songs.size() + " real tracks");
    }
  }

  @Test
  public void selectedSongRadioProducesDistinctRelatedQueueWithoutSearchResults() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    Track seed =
        OnlineMusic.preview("Ed Sheeran Perfect", "LK").stream()
            .filter(RadioPolicy::music)
            .findFirst()
            .orElseThrow();
    List<Track> related = OnlineMusic.relatedPreview(seed);
    assertTrue("Expected an actual song radio", related.size() >= 10);
    List<Track> candidates =
        new RadioCatalogue(
                new RadioCatalogue.Source() {
                  public List<Track> related(Track track) {
                    return related;
                  }

                  public List<Track> search(String query) {
                    throw new AssertionError("Radio must use the song endpoint");
                  }
                })
            .load(seed, seed);
    List<Track> selected =
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
    assertEquals(10, selected.size());
    assertTrue(
        selected.stream()
            .noneMatch(
                song -> QueuePolicy.identity(song.url).equals(QueuePolicy.identity(seed.url))));
    Set<String> artists = new HashSet<>();
    for (Track song : selected) artists.add(RadioPolicy.artist(song));
    assertTrue("Radio should include related artists", artists.size() > 1);
    System.out.println(
        "Selected-song radio: "
            + selected.size()
            + " tracks across "
            + artists.size()
            + " artists");
  }

  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void realMusicArtworkDownloadsAndDecodesWithPublicFallback() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_MUSIC_LIVE_TEST")));
    List<Track> songs = OnlineMusic.preview("Ed Sheeran", "LK");
    assertFalse(songs.isEmpty());
    okhttp3.OkHttpClient client =
        new okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    int decoded = 0;
    for (Track track : songs.subList(0, Math.min(3, songs.size()))) {
      for (String url : ArtworkQuality.candidates(track.artwork, track.url, 512)) {
        try (okhttp3.Response response =
            client.newCall(new okhttp3.Request.Builder().url(url).build()).execute()) {
          if (!response.isSuccessful()
              || response.body() == null
              || !response.header("Content-Type", "").startsWith("image/")) continue;
          android.graphics.Bitmap image = ArtworkQuality.decode(response.body().byteStream());
          if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            decoded++;
            image.recycle();
            break;
          }
        } catch (java.io.IOException unavailable) {
          /* Try the bounded public fallback. */
        }
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
