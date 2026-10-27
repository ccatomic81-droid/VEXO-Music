package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ArtistIdentityTest {
  static final String KEVIN = "UCaaaaaaaaaaaaaaaaaaaaaa", ADHEESHA = "UCbbbbbbbbbbbbbbbbbbbbbb";

  Track song(String label, ArtistCredit... credits) {
    return new Track(
            "https://www.youtube.com/watch?v=abcdefghijk",
            "Colombo Rebellion",
            label,
            "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
            210)
        .withArtists(Arrays.asList(credits));
  }

  @Test
  public void separateProviderCreditsDeduplicateCollaborationsAndPreserveBandNames() {
    Context c = RuntimeEnvironment.getApplication();
    SearchHistory history = new SearchHistory(c);
    SearchArtists artists = new SearchArtists(c);
    history.add("Colombo");
    Track duet =
        song(
            "Kevin Smokio & Adheesha Beats",
            new ArtistCredit(KEVIN, "Kevin Smokio"),
            new ArtistCredit(ADHEESHA, "Adheesha Beats"));
    artists.record(
        "Colombo",
        Arrays.asList(
            duet,
            song("Kevin Smokio", new ArtistCredit(KEVIN, "Kevin Smokio")),
            song(
                "Kevin Smokio, Chamath Sangeeth",
                new ArtistCredit(KEVIN, "Kevin Smokio"),
                new ArtistCredit("UCcccccccccccccccccccccc", "Chamath Sangeeth"))));
    assertEquals(3, artists.visible().size());
    assertEquals(1, artists.visible().stream().filter(a -> a.id.equals(KEVIN)).count());
    assertTrue(
        artists.visible().stream().noneMatch(a -> a.name.contains(" & ") || a.name.contains(",")));
    assertTrue(
        "Song covers must never become portraits",
        artists.visible().stream().allMatch(a -> a.artwork.isEmpty()));
    history.add("band");
    artists.record(
        "band",
        Arrays.asList(
            song(
                "Earth, Wind & Fire",
                new ArtistCredit("UCdddddddddddddddddddddd", "Earth, Wind & Fire"))));
    assertEquals("Earth, Wind & Fire", artists.visible().get(0).name);
    assertEquals(2, Track.from(duet.json()).artists.size());
    assertEquals(ADHEESHA, Track.from(duet.mediaItem()).artists.get(1).id);
  }

  @Test
  public void differentProviderIdentitiesWithTheSameNameRemainSeparate() {
    List<SearchArtists.Artist> artists = new ArrayList<>();
    SearchArtists.merge(
        artists, new SearchArtists.Artist(KEVIN, "Same name", "", Arrays.asList("one"), 0, false));
    SearchArtists.merge(
        artists,
        new SearchArtists.Artist(ADHEESHA, "Same name", "", Arrays.asList("two"), 0, false));
    assertEquals(2, artists.size());
  }

  @Test
  public void existingAlbumArtAndCollaborationTilesAreRepairedWithoutClearingSearches()
      throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SearchArtists artists = new SearchArtists(c);
    new SearchHistory(c).add("Colombo");
    JSONArray legacy =
        new JSONArray()
            .put(
                new JSONObject()
                    .put("name", "Kevin Smokio & Adheesha Beats")
                    .put("query", "Colombo")
                    .put("artwork", "https://example.com/album.jpg"))
            .put(
                new JSONObject()
                    .put("name", "Kevin Smokio")
                    .put("query", "Colombo")
                    .put("artwork", "https://example.com/song.jpg"));
    c.getSharedPreferences("search_history", 0)
        .edit()
        .putString("artists", legacy.toString())
        .commit();
    assertEquals(1, artists.visible().size());
    assertTrue(artists.visible().get(0).artwork.isEmpty());
    SearchArtists.Artist group = artists.entries().get(0);
    assertTrue(
        artists.resolved(
            group,
            Arrays.asList(
                new SearchArtists.Artist(
                    KEVIN,
                    "Kevin Smokio",
                    "https://yt3.googleusercontent.com/kevin",
                    group.queries,
                    1,
                    false),
                new SearchArtists.Artist(
                    ADHEESHA,
                    "Adheesha Beats",
                    "https://yt3.googleusercontent.com/adheesha",
                    group.queries,
                    1,
                    false)),
            artists.revision()));
    assertEquals(2, artists.visible().size());
    assertEquals(1, new SearchHistory(c).entries().size());
    assertEquals(1, artists.visible().stream().filter(a -> a.name.equals("Kevin Smokio")).count());
  }

  @Test
  public void exactArtistIdentityRejectsUnrelatedPhotosAndKeepsBandNamesWhole() {
    SearchArtists.Artist wanted = new SearchArtists.Artist("Kevin Smokio", "", "Colombo");
    ChannelInfoItem unrelated =
        new ChannelInfoItem(0, "https://www.youtube.com/channel/" + ADHEESHA, "Adheesha Beats");
    ChannelInfoItem exact =
        new ChannelInfoItem(0, "https://www.youtube.com/channel/" + KEVIN, "Kevin Smokio");
    assertTrue(ArtistCatalogue.matches(wanted, Arrays.asList(unrelated)).isEmpty());
    List<SearchArtists.Artist> matched =
        ArtistCatalogue.matches(wanted, Arrays.asList(unrelated, exact));
    assertEquals(KEVIN, matched.get(0).id);
    assertEquals(
        "", ArtistCatalogue.channelId("https://youtube.com.evil.example/channel/" + KEVIN));
    assertEquals(
        Arrays.asList("Kevin Smokio", "Adheesha Beats"),
        ArtistCatalogue.parts("Kevin Smokio & Adheesha Beats"));
  }

  @Test
  public void removingSearchReferencesAndClearingDuringPortraitLookupCannotRestoreHistory() {
    Context c = RuntimeEnvironment.getApplication();
    SearchHistory history = new SearchHistory(c);
    SearchArtists artists = new SearchArtists(c);
    history.add("one");
    artists.record(
        "one", Arrays.asList(song("Kevin Smokio", new ArtistCredit(KEVIN, "Kevin Smokio"))));
    history.add("two");
    artists.record(
        "two", Arrays.asList(song("Kevin Smokio", new ArtistCredit(KEVIN, "Kevin Smokio"))));
    history.remove("one");
    assertEquals(1, artists.entries().size());
    assertEquals(Arrays.asList("two"), artists.entries().get(0).queries);
    SearchArtists.Artist pending = artists.entries().get(0);
    long revision = artists.revision();
    history.clear();
    history.add("two");
    artists.record(
        "two", Arrays.asList(song("Kevin Smokio", new ArtistCredit(KEVIN, "Kevin Smokio"))));
    assertFalse(
        artists.resolved(
            pending,
            Arrays.asList(pending.checked(KEVIN, "https://example.com/stale.jpg")),
            revision));
    assertTrue(artists.entries().get(0).artwork.isEmpty());
  }

  @Test
  public void portraitWorkerCachesSuccessAndHonoursClearAndDataSaver() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SearchHistory history = new SearchHistory(c);
    SearchArtists artists = new SearchArtists(c);
    history.add("Colombo");
    artists.record(
        "Colombo", Arrays.asList(song("Kevin Smokio", new ArtistCredit(KEVIN, "Kevin Smokio"))));
    SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
    int[] calls = {0}, changes = {0};
    ArtistPortraits portraits =
        new ArtistPortraits(
            c,
            new Handler(Looper.getMainLooper()),
            work,
            (artist, country) -> {
              calls[0]++;
              return Arrays.asList(
                  artist.checked(KEVIN, "https://yt3.googleusercontent.com/portrait"));
            });
    portraits.refresh("GLOBAL", () -> changes[0]++);
    portraits.refresh("GLOBAL", () -> changes[0]++);
    assertEquals(1, work.tasks.size());
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(1, changes[0]);
    assertFalse(artists.entries().get(0).artwork.isEmpty());
    portraits.refresh("GLOBAL", () -> changes[0]++);
    assertTrue(work.tasks.isEmpty());
    history.clear();
    history.add("new");
    artists.record(
        "new", Arrays.asList(song("Adheesha Beats", new ArtistCredit(ADHEESHA, "Adheesha Beats"))));
    portraits.refresh("GLOBAL", () -> changes[0]++);
    history.clear();
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(artists.entries().isEmpty());
    assertEquals(1, calls[0]);
    history.add("new");
    artists.record(
        "new", Arrays.asList(song("Kevin Smokio", new ArtistCredit(KEVIN, "Kevin Smokio"))));
    c.getSharedPreferences("settings", 0).edit().putBoolean("data_saver", true).commit();
    portraits.refresh("GLOBAL", () -> changes[0]++);
    assertTrue(work.tasks.isEmpty());
    portraits.close();
  }

  @Test
  public void songParserRetainsIndividualArtistIdsAndDoesNotSplitBandPunctuation()
      throws Exception {
    MusicSearchTest helper = new MusicSearchTest();
    JSONObject row = helper.song("abcdefghijk");
    JSONArray runs =
        new JSONArray()
            .put(run("Kevin Smokio", KEVIN))
            .put(new JSONObject().put("text", ", "))
            .put(run("Earth, Wind & Fire", ADHEESHA))
            .put(new JSONObject().put("text", " • 3:45"));
    row.getJSONArray("flexColumns").put(1, helper.column(new JSONObject().put("runs", runs)));
    Track track = MusicSearch.parse(helper.wrap(row), "song", "GLOBAL").tracks.get(0);
    assertEquals(2, track.artists.size());
    assertEquals(KEVIN, track.artists.get(0).id);
    assertEquals("Earth, Wind & Fire", track.artists.get(1).name);
  }

  JSONObject run(String name, String id) throws Exception {
    return new JSONObject()
        .put("text", name)
        .put(
            "navigationEndpoint",
            new JSONObject().put("browseEndpoint", new JSONObject().put("browseId", id)));
  }
}
