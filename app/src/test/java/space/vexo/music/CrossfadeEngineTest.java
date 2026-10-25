package space.vexo.music;

import static org.junit.Assert.*;

import org.junit.Test;

public class CrossfadeEngineTest {
  static class Deck implements CrossfadeEngine.Deck {
    String id = "A";
    long pos = 1500, duration = 20000;
    float speed = 1, gain = 1;
    boolean playing = true, ready = true, failed, eligible = true, next = true, closed, pauseEnd;
    boolean startImmediately = true;
    int plays;

    public String stamp() {
      return id;
    }

    public String item() {
      return id;
    }

    public long position() {
      return pos;
    }

    public long duration() {
      return duration;
    }

    public float speed() {
      return speed;
    }

    public boolean playing() {
      return playing;
    }

    public boolean ready() {
      return ready;
    }

    public boolean failed() {
      return failed;
    }

    public boolean eligible() {
      return eligible;
    }

    public boolean hasNext() {
      return next;
    }

    public void play() {
      plays++;
      if (startImmediately) playing = true;
    }

    public void volume(float value) {
      gain = value;
    }

    public void pauseAtEnd(boolean value) {
      pauseEnd = value;
    }

    public void release() {
      closed = true;
      playing = false;
    }
  }

  static class Host implements CrossfadeEngine.Host {
    Deck current = new Deck(), next = new Deck();
    int prepares, promotions;

    Host() {
      next.id = "B";
      next.pos = 0;
      next.playing = false;
    }

    public Deck current() {
      return current;
    }

    public Deck prepareNext() {
      prepares++;
      return next;
    }

    public void promote(CrossfadeEngine.Deck deck) {
      current = (Deck) deck;
      promotions++;
    }
  }

  @Test
  public void overlapsTwoAdvancingDecksAndReleasesOnlyTheOldDeck() {
    Host h = new Host();
    Deck old = h.current;
    CrossfadeEngine e = new CrossfadeEngine(h, () -> 4);
    e.tick();
    assertEquals(1, h.prepares);
    assertFalse(e.blending());
    assertEquals(0, h.next.gain, 0);
    old.pos = 16000;
    e.tick();
    assertTrue(e.blending());
    assertEquals(1, h.promotions);
    assertTrue(old.pauseEnd);
    assertEquals(1, old.gain, 0);
    assertEquals(0, h.current.gain, 0);
    h.current.pos = 2000;
    old.pos = 18000;
    e.tick();
    assertEquals(Math.sqrt(.5), old.gain, .00001);
    assertEquals(Math.sqrt(.5), h.current.gain, .00001);
    h.current.pos = 4000;
    old.pos = 20000;
    e.tick();
    assertFalse(e.blending());
    assertTrue(old.closed);
    assertFalse(h.current.closed);
    assertEquals(1, h.current.gain, 0);
  }

  @Test
  public void unbufferedOrFailedNextNeverFadesTheCurrentTrackOrLoopsRequests() {
    Host h = new Host();
    h.next.ready = false;
    CrossfadeEngine e = new CrossfadeEngine(h, () -> 4);
    h.current.pos = 18000;
    e.tick();
    assertEquals(1, h.current.gain, 0);
    assertEquals(0, h.promotions);
    h.next.failed = true;
    e.tick();
    e.tick();
    assertTrue(h.next.closed);
    assertEquals(1, h.prepares);
    assertEquals(1, h.current.gain, 0);
  }

  @Test
  public void warmingNextCannotDelayTheNormalQueueTransition() {
    Host h = new Host();
    Deck old = h.current;
    h.next.startImmediately = false;
    old.pos = 17000;
    CrossfadeEngine e = new CrossfadeEngine(h, () -> 4);
    e.tick();
    assertEquals(1, h.next.plays);
    assertEquals(0, h.promotions);
    assertFalse(old.pauseEnd);
    old.id = "C";
    old.next = false;
    e.tick();
    assertTrue(h.next.closed);
    assertFalse(old.closed);
    assertEquals(1, old.gain, 0);
  }

  @Test
  public void pauseSeekOrClearCancelsBothStandbyAndOutgoingAudio() {
    Host h = new Host();
    Deck old = h.current;
    CrossfadeEngine e = new CrossfadeEngine(h, () -> 4);
    old.pos = 16000;
    e.tick();
    h.current.pos = 1000;
    e.tick();
    e.interrupt();
    assertTrue(old.closed);
    assertFalse(h.current.closed);
    assertEquals(1, h.current.gain, 0);
    assertFalse(e.blending());
    Host before = new Host();
    CrossfadeEngine pending = new CrossfadeEngine(before, () -> 4);
    pending.tick();
    pending.interrupt();
    assertTrue(before.next.closed);
    assertEquals(1, before.current.gain, 0);
  }

  @Test
  public void shortSongsSpeedAndDuckGainUseTheSameMusicalOverlap() {
    Host h = new Host();
    Deck old = h.current;
    old.duration = 9000;
    old.pos = 7000;
    old.speed = 2;
    h.next.duration = 6000;
    h.next.speed = 2;
    CrossfadeEngine e = new CrossfadeEngine(h, () -> 12);
    e.tick();
    assertTrue(e.blending());
    h.current.pos = 1000;
    old.pos = 8000;
    e.master(.2f);
    e.tick();
    assertEquals(.2 * Math.sqrt(.5), old.gain, .00001);
    assertEquals(.2 * Math.sqrt(.5), h.current.gain, .00001);
    h.current.pos = 2000;
    e.tick();
    assertEquals(.2, h.current.gain, .00001);
    assertTrue(old.closed);
  }

  @Test
  public void futureQueueEditsAndTurningOffDoNotCutAnActiveBlend() {
    Host h = new Host();
    Deck old = h.current;
    int[] seconds = {4};
    CrossfadeEngine e = new CrossfadeEngine(h, () -> seconds[0]);
    old.pos = 16000;
    e.tick();
    e.queueChanged();
    seconds[0] = 0;
    e.tick();
    assertTrue(e.blending());
    h.current.pos = 4000;
    e.tick();
    assertTrue(old.closed);
    assertFalse(e.blending());
    int count = h.prepares;
    e.tick();
    assertEquals(count, h.prepares);
  }

  @Test
  public void manualTrackChangeOrIncomingBufferingCannotLeaveAHiddenTail() {
    for (boolean changed : new boolean[] {false, true}) {
      Host h = new Host();
      Deck old = h.current;
      CrossfadeEngine e = new CrossfadeEngine(h, () -> 4);
      old.pos = 16000;
      e.tick();
      if (changed) h.current.id = "C";
      else h.current.playing = false;
      e.tick();
      assertTrue(old.closed);
      assertEquals(1, h.current.gain, 0);
    }
  }

  @Test
  public void offLiveUnknownDurationAndNoSuccessorAvoidPreparingAnything() {
    for (int type = 0; type < 4; type++) {
      Host h = new Host();
      final int seconds = type == 0 ? 0 : 4;
      if (type == 1) h.current.eligible = false;
      if (type == 2) h.current.next = false;
      if (type == 3) h.current.playing = false;
      new CrossfadeEngine(h, () -> seconds).tick();
      assertEquals(0, h.prepares);
    }
    assertEquals(0, CrossfadeEngine.clampSeconds(-8));
    assertEquals(12, CrossfadeEngine.clampSeconds(99));
  }

  @Test
  public void equalPowerCurveIsContinuousAndMixesBothPcmSignals() {
    double previousOut = 1, previousIn = 0;
    for (int i = 0; i <= 1000; i++) {
      double p = i / 1000.0, out = CrossfadeEngine.outgoing(p), in = CrossfadeEngine.incoming(p);
      assertEquals(1, out * out + in * in, .000001);
      assertTrue(out <= previousOut + .000001);
      assertTrue(in >= previousIn - .000001);
      previousOut = out;
      previousIn = in;
      double sample =
          .3 * Math.sin(2 * Math.PI * 220 * p) * out + .3 * Math.sin(2 * Math.PI * 330 * p) * in;
      assertTrue(Math.abs(sample) <= .6);
    }
    assertEquals(0, CrossfadeEngine.incoming(0), 0);
    assertEquals(1, CrossfadeEngine.incoming(1), 0);
    assertEquals(1, CrossfadeEngine.outgoing(0), 0);
    assertEquals(0, CrossfadeEngine.outgoing(1), .000001);
  }
}
