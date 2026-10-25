package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SearchSuggestionsTest {
  static class Work extends AbstractExecutorService {
    final List<Runnable> tasks = new ArrayList<>();
    boolean stopped;

    public void execute(Runnable r) {
      tasks.add(r);
    }

    public void shutdown() {
      stopped = true;
    }

    public List<Runnable> shutdownNow() {
      stopped = true;
      return Collections.emptyList();
    }

    public boolean isShutdown() {
      return stopped;
    }

    public boolean isTerminated() {
      return stopped;
    }

    public boolean awaitTermination(long t, TimeUnit u) {
      return stopped;
    }

    void runNext() {
      tasks.remove(0).run();
    }
  }

  @Test
  public void historyIsBoundedPrivateDeduplicatedAndRemovable() {
    Context c = RuntimeEnvironment.getApplication();
    SearchHistory history = new SearchHistory(c);
    for (int i = 0; i < 25; i++) history.add("song " + i);
    assertEquals(20, new SearchHistory(c).entries().size());
    history.add(" SONG 8 ");
    assertEquals("SONG 8", history.entries().get(0));
    assertEquals(1, history.entries().stream().filter(s -> s.equalsIgnoreCase("song 8")).count());
    history.add("https://youtu.be/fixture");
    assertEquals("SONG 8", history.entries().get(0));
    history.remove("song 8");
    assertFalse(history.entries().contains("SONG 8"));
    history.clear();
    assertTrue(history.entries().isEmpty());
  }

  @Test
  public void debounceOnlyFetchesLatestAndOlderDeliveryCannotOverwriteIt() {
    Work work = new Work();
    List<String> fetched = new ArrayList<>(), delivered = new ArrayList<>();
    SearchSuggestions suggestions =
        new SearchSuggestions(
            new Handler(Looper.getMainLooper()),
            work,
            (query, region) -> {
              fetched.add(query + region);
              return Arrays.asList(query + " song");
            });
    SearchSuggestions.Listener listener = (q, values, failed) -> delivered.add(q);
    suggestions.request("si", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    suggestions.request("sinhala", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(299));
    assertTrue(work.tasks.isEmpty());
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
    work.runNext(); // Fetch finishes and queues delivery, then the user changes the query.
    suggestions.request("tamil", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(delivered.isEmpty());
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(Arrays.asList("tamil"), delivered);
    assertEquals(Arrays.asList("sinhalaLK", "tamilLK"), fetched);
    suggestions.close();
    assertTrue(work.stopped);
  }

  @Test
  public void cacheIsRegionSpecificAndErrorsOrLinksAreHandled() {
    Work work = new Work();
    List<String> outcomes = new ArrayList<>();
    SearchSuggestions suggestions =
        new SearchSuggestions(
            new Handler(Looper.getMainLooper()),
            work,
            (q, r) -> {
              if (r.equals("US")) throw new java.io.IOException();
              return Arrays.asList(q);
            });
    SearchSuggestions.Listener listener =
        (q, values, failed) -> outcomes.add(q + failed + values.size());
    suggestions.request("artist", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    suggestions.request("artist", "LK", listener);
    assertTrue(work.tasks.isEmpty());
    assertEquals(2, outcomes.size());
    suggestions.request("artist", "US", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals("artisttrue0", outcomes.get(2));
    suggestions.request("https://youtu.be/example", "LK", listener);
    suggestions.request("a", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    assertTrue(work.tasks.isEmpty());
    suggestions.close();
  }
}
