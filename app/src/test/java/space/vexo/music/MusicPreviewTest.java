package space.vexo.music;

import static org.junit.Assert.*;

import android.os.*;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MusicPreviewTest {
  Track song(String id, String title, String by) {
    return new Track("https://www.youtube.com/watch?v=" + id, title, by, "", 210);
  }

  @Test
  public void cachedMatchesAppearFirstAndDuplicateRemoteSongsAreRemoved() {
    Track a = song("a", "Sky lights", "Nova"),
        b = song("b", "The sky", "Nova"),
        c = song("c", "A new song", "Sky band");
    List<Track> result = MusicPreview.combine("sky", Arrays.asList(b, a), Arrays.asList(a, c), 6);
    assertEquals(Arrays.asList(a, b, c), result);
    assertEquals(2, MusicPreview.combine("sky", Arrays.asList(a, b), Arrays.asList(c), 2).size());
    assertTrue(
        MusicPreview.combine("nothing", Arrays.asList(a), Collections.emptyList(), 6).isEmpty());
  }

  @Test
  public void latestTypingWinsEvenWhenAnOlderRequestHasFinished() {
    SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
    List<String> delivered = new ArrayList<>();
    MusicPreview preview =
        new MusicPreview(
            new Handler(Looper.getMainLooper()),
            work,
            (q, c) -> Arrays.asList(song(q, q, "Artist")));
    MusicPreview.Listener listener = (q, s, fail) -> delivered.add(q);
    preview.request("sky", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(399));
    assertTrue(work.tasks.isEmpty());
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
    work.runNext();
    preview.request("stars", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(delivered.isEmpty());
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertEquals(Arrays.asList("stars"), delivered);
    preview.close();
    assertTrue(work.stopped);
  }

  @Test
  public void cacheKeysIncludeRegionAndFailuresAllowLaterRetry() {
    SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
    List<Boolean> failures = new ArrayList<>();
    MusicPreview preview =
        new MusicPreview(
            new Handler(Looper.getMainLooper()),
            work,
            (q, c) -> {
              if (c.equals("US")) throw new java.io.IOException();
              return Arrays.asList(song("1", "song", "artist"));
            });
    MusicPreview.Listener listener = (q, s, f) -> failures.add(f);
    preview.request("artist", "LK", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    preview.request("artist", "LK", listener);
    assertTrue(work.tasks.isEmpty());
    assertEquals(2, failures.size());
    preview.request("artist", "US", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    work.runNext();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(failures.get(2));
    preview.request("artist", "US", listener);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    assertEquals(1, work.tasks.size());
    preview.close();
  }
}
