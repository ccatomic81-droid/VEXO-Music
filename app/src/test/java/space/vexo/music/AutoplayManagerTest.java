package space.vexo.music;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AutoplayManagerTest {
  static Track song(String id) {
    return new Track("https://www.youtube.com/watch?v=" + id, id, "Artist", "", 180);
  }

  static class Queue implements AutoplayManager.Queue {
    List<Track> songs = new ArrayList<>(Arrays.asList(song("first"), song("second")));
    int at = 1, resumed = -1;
    boolean play = true, ended = false, repeat = false;

    public List<Track> tracks() {
      return new ArrayList<>(songs);
    }

    public int index() {
      return at;
    }

    public boolean repeatOff() {
      return !repeat;
    }

    public boolean wantsPlay() {
      return play;
    }

    public boolean ended() {
      return ended;
    }

    public void append(List<Track> added) {
      songs.addAll(added);
    }

    public void resumeAt(int index) {
      resumed = index;
      at = index;
      ended = false;
    }
  }

  @Test
  public void appendsUniqueSongsAndContinuesWhenQueueEnds() {
    Queue q = new Queue();
    q.ended = true;
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> Arrays.asList(song("first"), song("new"), song("new"), song("recent")),
            tasks::add,
            Runnable::run,
            () -> true,
            () -> Arrays.asList(song("recent")),
            s -> {});
    radio.check();
    radio.check();
    assertEquals(1, tasks.size());
    tasks.remove().run();
    assertEquals(3, q.songs.size());
    assertEquals("new", q.songs.get(2).title);
    assertEquals(2, q.resumed);
  }

  @Test
  public void aPauseDuringFetchNeverRestartsPlayback() {
    Queue q = new Queue();
    q.ended = true;
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> Arrays.asList(song("new")),
            tasks::add,
            Runnable::run,
            () -> true,
            Collections::emptyList,
            s -> {});
    radio.check();
    q.play = false;
    tasks.remove().run();
    assertEquals(3, q.songs.size());
    assertEquals(-1, q.resumed);
  }

  @Test
  public void clearOrAutoplayOffRejectsInFlightResults() {
    Queue q = new Queue();
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    boolean[] on = {true};
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> Arrays.asList(song("new")),
            tasks::add,
            Runnable::run,
            () -> on[0],
            Collections::emptyList,
            s -> {});
    radio.check();
    radio.suspend();
    tasks.remove().run();
    assertEquals(2, q.songs.size());
    radio.check();
    assertTrue(tasks.isEmpty());
    radio.newQueue();
    radio.check();
    on[0] = false;
    radio.settingsChanged();
    tasks.remove().run();
    assertEquals(2, q.songs.size());
  }

  @Test
  public void staleSongResultIsDiscardedAndNewSeedIsRequested() {
    Queue q = new Queue();
    q.at = 0;
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    List<String> seeds = new ArrayList<>();
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> {
              seeds.add(seed.title);
              return Arrays.asList(song("after-" + seed.title));
            },
            tasks::add,
            Runnable::run,
            () -> true,
            Collections::emptyList,
            s -> {});
    radio.check();
    q.at = 1;
    tasks.remove().run();
    assertEquals(2, q.songs.size());
    assertEquals(1, tasks.size());
    tasks.remove().run();
    assertEquals(Arrays.asList("first", "second"), seeds);
    assertEquals("after-second", q.songs.get(2).title);
  }

  @Test
  public void failedFetchDoesNotLoopAndRetryIsExplicit() {
    Queue q = new Queue();
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    List<String> status = new ArrayList<>();
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> {
              throw new java.io.IOException("offline");
            },
            tasks::add,
            Runnable::run,
            () -> true,
            Collections::emptyList,
            status::add);
    radio.check();
    tasks.remove().run();
    radio.check();
    assertTrue(tasks.isEmpty());
    assertTrue(status.get(status.size() - 1).contains("Couldn’t"));
    radio.settingsChanged();
    assertEquals(1, tasks.size());
  }

  @Test
  public void localRepeatAndPausedQueuesDoNotStartRadio() {
    Queue q = new Queue();
    q.songs = Arrays.asList(new Track("content://media/1", "Local", "Artist", "", 180));
    q.at = 0;
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> Collections.emptyList(),
            tasks::add,
            Runnable::run,
            () -> true,
            Collections::emptyList,
            s -> {});
    radio.check();
    assertTrue(tasks.isEmpty());
    q.songs = Arrays.asList(song("online"));
    q.repeat = true;
    radio.check();
    assertTrue(tasks.isEmpty());
    q.repeat = false;
    q.play = false;
    radio.check();
    assertTrue(tasks.isEmpty());
  }

  @Test
  public void retuningDuringFetchRejectsOldResultsAndUsesNewRanking() {
    Queue q = new Queue();
    ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    boolean[] discover = {false};
    AutoplayManager radio =
        new AutoplayManager(
            q,
            seed -> Arrays.asList(song("familiar"), song("discovery")),
            tasks::add,
            Runnable::run,
            () -> true,
            Collections::emptyList,
            value -> {},
            (seed, candidates, queue, history) ->
                Collections.singletonList(candidates.get(discover[0] ? 1 : 0)));
    radio.check();
    discover[0] = true;
    radio.settingsChanged();
    assertEquals(2, tasks.size());
    tasks.remove().run();
    assertEquals(2, q.songs.size());
    tasks.remove().run();
    assertEquals("discovery", q.songs.get(2).title);
  }
  @Test public void workerReceivesTheQueueSnapshotForItsRequest() {
    Queue q = new Queue(); ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    List<Track> captured = new ArrayList<>();
    AutoplayManager manager = new AutoplayManager(q, new AutoplayManager.Loader() {
      public List<Track> load(Track seed) { throw new AssertionError("Snapshot overload required"); }
      public List<Track> load(Track seed, List<Track> snapshot) {
        captured.addAll(snapshot);
        try { snapshot.clear(); fail("Snapshot must be immutable"); } catch (UnsupportedOperationException expected) { }
        return Arrays.asList(song("new"));
      }
    }, tasks::add, Runnable::run, () -> true, Collections::emptyList, text -> {});
    manager.check(); q.songs.add(song("manual-after-request")); tasks.remove().run();
    assertEquals(2, captured.size()); assertEquals(4,q.songs.size());
    assertEquals("manual-after-request",q.songs.get(2).title);
  }

}
