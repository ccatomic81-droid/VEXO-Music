package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class QueuePolicyTest {
  @Test
  public void shuffleOrderTraversalAndPruningKeepCurrentAndFutureSongs() {
    int[] next = {3, -1, 0, 1};
    assertEquals(Arrays.asList(2, 0, 3, 1), QueuePolicy.order(4, 2, i -> next[i]));
    assertEquals(Arrays.asList(3, 1), QueuePolicy.order(4, 3, i -> next[i]));
    assertArrayEquals(
        new int[] {2, 0, 3, 1, 4, 5}, QueuePolicy.appendOrder(Arrays.asList(2, 0, 3, 1), 4, 2));
    assertEquals(Arrays.asList(2, 0), QueuePolicy.oldPlayed(Arrays.asList(2, 0, 3, 1), 1, 1));
    assertEquals(Arrays.asList(0, 1, 2), QueuePolicy.order(3, 0, i -> (i + 1) % 3));
  }

  @Test
  public void duplicateYouTubeFormsUseTheSameSongIdentity() {
    assertEquals(
        QueuePolicy.identity("https://youtu.be/abc?t=5"),
        QueuePolicy.identity("https://music.youtube.com/watch?v=abc&list=radio"));
    assertEquals("youtube:abc", QueuePolicy.identity("https://www.youtube.com/shorts/abc"));
    assertFalse(QueuePolicy.identity("https://youtube.com.evil/watch?v=abc").equals("youtube:abc"));
  }

  @Test
  public void playNextAccountsForRemovingAnEarlierItem() {
    assertEquals(2, QueuePolicy.playNextTarget(0, 2));
    assertEquals(3, QueuePolicy.playNextTarget(4, 2));
    assertEquals(2, QueuePolicy.playNextTarget(2, 2));
  }

  @Test
  public void radioHonoursCapacityAndRejectsVeryShortOrLongVideos() {
    List<Track> queue = new ArrayList<>();
    for (int i = 0; i < 99; i++) queue.add(AutoplayManagerTest.song("old" + i));
    List<Track> candidates =
        Arrays.asList(
            new Track("https://youtu.be/short", "Short", "A", "", 10),
            new Track("https://youtu.be/long", "Long", "A", "", 7200),
            AutoplayManagerTest.song("fresh1"),
            AutoplayManagerTest.song("fresh2"));
    List<Track> fresh = QueuePolicy.fresh(candidates, queue, Collections.emptyList(), 10);
    assertEquals(1, fresh.size());
    assertEquals("fresh1", fresh.get(0).title);
  }
}
