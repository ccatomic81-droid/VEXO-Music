package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.media3.datasource.*;
import androidx.test.core.app.ApplicationProvider;
import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class OfflineLibraryTest {
  Context context;
  private final Track song =
      new Track("https://www.youtube.com/watch?v=offline-fixture", "Test song", "Artist", "", 3);

  @Before
  public void before() {
    context = ApplicationProvider.getApplicationContext();
    context.getSharedPreferences("library", 0).edit().clear().commit();
    context.getSharedPreferences("settings", 0).edit().clear().commit();
  }

  @Test
  public void completedAudioPersistsAndReplaysWithoutAnyNetwork() throws Exception {
    byte[] audio = new byte[32_000];
    for (int i = 0; i < audio.length; i++) audio[i] = (byte) (i % 251);
    File directory = Files.createTempDirectory("vexo-offline").toFile();
    OfflineLibrary first =
        new OfflineLibrary(context, () -> new ByteArrayDataSource(audio), directory, 100_000);
    first.request(song);
    first.awaitIdle();
    assertTrue(first.status(), first.complete(song.url));
    assertEquals(1, first.saved().size());
    assertEquals(audio.length, first.bytes());
    first.close();
    OfflineLibrary reopened =
        new OfflineLibrary(
            context,
            () -> {
              throw new AssertionError("Offline playback must not resolve a network URL");
            },
            directory,
            100_000);
    try {
      assertEquals(1, reopened.saved().size());
      DataSource source = reopened.offlineOnlyFactory().createDataSource();
      source.open(new DataSpec.Builder().setUri(song.url).setKey(song.url).build());
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] chunk = new byte[4096];
      int count;
      while ((count = source.read(chunk, 0, chunk.length)) != -1) result.write(chunk, 0, count);
      source.close();
      assertArrayEquals(audio, result.toByteArray());
      reopened.remove(song.url);
      assertTrue(reopened.saved().isEmpty());
      assertEquals(0, reopened.bytes());
    } finally {
      reopened.close();
    }
  }

  @Test
  public void overBudgetAndFailedDownloadsNeverAppearAsSaved() throws Exception {
    File directory = Files.createTempDirectory("vexo-budget").toFile();
    OfflineLibrary library =
        new OfflineLibrary(
            context, () -> new ByteArrayDataSource(new byte[32_000]), directory, 1000);
    try {
      library.request(song);
      library.awaitIdle();
      assertFalse(library.complete(song.url));
      assertTrue(library.saved().isEmpty());
      assertEquals(0, library.bytes());
      assertTrue(library.status(), library.status().contains("limit"));
    } finally {
      library.close();
    }
  }

  @Test
  public void autoSaveOffDoesNotFetchAudio() throws Exception {
    context.getSharedPreferences("settings", 0).edit().putBoolean("auto_save", false).commit();
    OfflineLibrary library =
        new OfflineLibrary(
            context,
            () -> {
              throw new AssertionError("Disabled auto-save fetched audio");
            },
            Files.createTempDirectory("vexo-off").toFile(),
            100_000);
    try {
      library.request(song);
      assertTrue(library.saved().isEmpty());
      assertEquals(0, library.bytes());
    } finally {
      library.close();
    }
  }

  @Test
  public void fullStorageKeepsExistingSongsAndClearMakesRoom() throws Exception {
    OfflineLibrary library = new OfflineLibrary(context,
        () -> new ByteArrayDataSource(new byte[32_000]),
        Files.createTempDirectory("vexo-full").toFile(), 40_000);
    Track second = new Track("https://example.com/second.mp3", "Second", "Artist", "", 3);
    try {
      library.request(song);
      library.awaitIdle();
      library.request(song);
      assertEquals(1, library.saved().size());
      library.request(second);
      library.awaitIdle();
      assertTrue(library.complete(song.url));
      assertFalse(library.complete(second.url));
      assertEquals(32_000, library.bytes());
      assertEquals(1, library.saved().size());
      library.clear();
      library.awaitIdle();
      assertEquals(0, library.bytes());
      assertTrue(library.saved().isEmpty());
      library.request(second);
      library.awaitIdle();
      assertTrue(library.status(), library.complete(second.url));
    } finally {
      library.close();
    }
  }

  @Test
  public void failedSaveCanRetryAndToggleCanResumeSaving() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean(true);
    OfflineLibrary library = new OfflineLibrary(context,
        () -> new DataSource() {
          final ByteArrayDataSource data = new ByteArrayDataSource(new byte[32_000]);
          boolean readOnce;
          @Override public void addTransferListener(TransferListener listener) { data.addTransferListener(listener); }
          @Override public long open(DataSpec spec) throws IOException { return data.open(spec); }
          @Override public android.net.Uri getUri() { return data.getUri(); }
          @Override public void close() throws IOException { data.close(); }
          @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (fail.get() && readOnce) throw new IOException("Connection lost");
            readOnce = true;
            return data.read(buffer, offset, Math.min(length, 4096));
          }
        }, Files.createTempDirectory("vexo-retry").toFile(), 100_000);
    try {
      library.request(song);
      library.awaitIdle();
      assertEquals(0, library.bytes());
      assertTrue(library.saved().isEmpty());
      context.getSharedPreferences("settings", 0).edit().putBoolean("auto_save", false).commit();
      fail.set(false);
      library.request(song);
      assertTrue(library.saved().isEmpty());
      context.getSharedPreferences("settings", 0).edit().putBoolean("auto_save", true).commit();
      library.request(song);
      library.awaitIdle();
      assertTrue(library.status(), library.complete(song.url));
    } finally {
      library.close();
    }
  }
}
