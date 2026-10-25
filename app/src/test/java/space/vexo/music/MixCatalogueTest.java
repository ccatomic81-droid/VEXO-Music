package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MixCatalogueTest {
  Track song(String id) {
    return new Track("https://www.youtube.com/watch?v=" + id, "Song " + id, "Artist", "", 200);
  }

  @Test
  public void cachedMixesStayInTheirCountryAndOnlyStoreRealSongs() {
    Context c = RuntimeEnvironment.getApplication();
    MixCatalogue catalogue = new MixCatalogue(c);
    MixCatalogue.Mix mix = MixCatalogue.find("regional", "LK");
    assertNotNull(mix);
    assertTrue(mix.query.contains("Sinhala"));
    assertFalse(MixCatalogue.find("regional", "US").query.contains("Sinhala"));
    List<Track> songs =
        MixCatalogue.songs(
            Arrays.asList(
                song("a"),
                song("a"),
                new Track("https://www.youtube.com/watch?v=long", "Full movie", "Artist", "", 7000),
                song("b")));
    assertEquals(2, songs.size());
    catalogue.save(mix, "LK", songs);
    assertEquals(2, new MixCatalogue(c).cached(mix, "LK").size());
    assertTrue(catalogue.cached(mix, "US").isEmpty());
    assertTrue(catalogue.fresh(mix, "LK", System.currentTimeMillis()));
    assertFalse(catalogue.fresh(mix, "LK", System.currentTimeMillis() + 7 * 60 * 60_000L));
  }

  @Test
  public void savingAndRenamingNeverOverwriteAnExistingUserPlaylist() {
    LibraryStore store = new LibraryStore(RuntimeEnvironment.getApplication());
    String name = store.savePlaylistCopy("Sinhala favourites", Arrays.asList(song("a"), song("b")));
    assertEquals(
        name, store.savePlaylistCopy("Sinhala favourites", Arrays.asList(song("a"), song("b"))));
    store.addToPlaylist(name, song("mine"));
    String copy = store.savePlaylistCopy("Sinhala favourites", Arrays.asList(song("a"), song("b")));
    assertNotEquals(name, copy);
    assertEquals(3, store.tracks("playlist:" + name).size());
    assertFalse(store.renamePlaylist(name, copy));
    assertFalse(store.renamePlaylist(name, "  "));
    assertTrue(store.renamePlaylist(name, "Evening favourites"));
    LibraryStore reopened = new LibraryStore(RuntimeEnvironment.getApplication());
    assertTrue(reopened.playlists().contains("Evening favourites"));
    assertFalse(reopened.playlists().contains(name));
    assertEquals(3, reopened.tracks("playlist:Evening favourites").size());
    assertTrue(reopened.tracks("playlist:" + name).isEmpty());
    assertEquals(2, reopened.tracks("playlist:" + copy).size());
  }
}
