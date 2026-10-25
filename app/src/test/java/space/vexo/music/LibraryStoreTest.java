package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LibraryStoreTest {
  Context context;
  LibraryStore library;
  Track track =
      new Track(
          "https://www.youtube.com/watch?v=test",
          "සිංහල song 🎧",
          "Artist",
          "https://example.com/cover.jpg",
          213);

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
    context.getSharedPreferences("library", 0).edit().clear().commit();
    library = new LibraryStore(context);
  }

  @Test
  public void favouritesPersistAndToggleWithoutDuplicates() {
    library.toggle("favourites", track);
    assertTrue(new LibraryStore(context).contains("favourites", track));
    assertEquals("සිංහල song 🎧", library.tracks("favourites").get(0).title);
    library.toggle("favourites", track);
    assertTrue(library.tracks("favourites").isEmpty());
  }

  @Test
  public void playlistsDeduplicateAndDeleteOnlyTheirOwnCollection() {
    library.createPlaylist("Chill");
    library.createPlaylist("Chill");
    library.createPlaylist("Focus");
    library.addToPlaylist("Chill", track);
    library.addToPlaylist("Chill", track);
    library.addToPlaylist("Focus", track);
    library.toggle("favourites", track);
    assertEquals(2, library.playlists().size());
    assertEquals(1, library.tracks("playlist:Chill").size());
    library.deletePlaylist("Chill");
    assertEquals(1, library.playlists().size());
    assertTrue(library.tracks("playlist:Chill").isEmpty());
    assertEquals(1, library.tracks("playlist:Focus").size());
    assertTrue(library.contains("favourites", track));
  }

  @Test
  public void historyIsBoundedAndReplayingMovesToFront() {
    for (int i = 0; i < 130; i++)
      library.recent(new Track("content://audio/" + i, "Song " + i, "Artist", "", 100));
    assertEquals(100, library.tracks("history").size());
    library.recent(new Track("content://audio/50", "Song 50", "Artist", "", 100));
    assertEquals(100, library.tracks("history").size());
    assertEquals("content://audio/50", library.tracks("history").get(0).url);
  }

  @Test
  public void malformedStoredDataDoesNotCrashLibrary() {
    context
        .getSharedPreferences("library", 0)
        .edit()
        .putString("favourites", "broken json")
        .commit();
    assertTrue(library.tracks("favourites").isEmpty());
  }
}
