package space.vexo.music;

import java.util.function.IntSupplier;

/** A two-deck transition, driven by actual playback position rather than wall-clock timers. */
public final class CrossfadeEngine {
  public interface Deck {
    String stamp();

    String item();

    long position();

    long duration();

    float speed();

    boolean playing();

    boolean ready();

    boolean failed();

    boolean eligible();

    boolean hasNext();

    void play();

    void volume(float gain);

    void pauseAtEnd(boolean enabled);

    void release();
  }

  public interface Host {
    Deck current();

    Deck prepareNext();

    void promote(Deck incoming);
  }

  private final Host host;
  private final IntSupplier seconds;
  private Deck standby, tail, incoming;
  private String planned = "", attempted = "", incomingItem = "";
  private boolean warming, closed;
  private double blendMs, startPosition, blendSpeed;
  private float master = 1;

  public CrossfadeEngine(Host host, IntSupplier seconds) {
    this.host = host;
    this.seconds = seconds;
  }

  public static int clampSeconds(int value) {
    return Math.max(0, Math.min(12, value));
  }

  public static float outgoing(double progress) {
    return (float) Math.cos(Math.max(0, Math.min(1, progress)) * Math.PI / 2);
  }

  public static float incoming(double progress) {
    return (float) Math.sin(Math.max(0, Math.min(1, progress)) * Math.PI / 2);
  }

  public boolean blending() {
    return tail != null;
  }

  public boolean preparing() {
    return standby != null;
  }

  public void master(float gain) {
    master = Math.max(0, Math.min(1, gain));
    apply();
  }

  public void queueChanged() {
    dropStandby();
    attempted = "";
  }

  public void interrupt() {
    dropStandby();
    finish();
    attempted = "";
    host.current().pauseAtEnd(false);
    host.current().volume(master);
  }

  public void close() {
    interrupt();
    closed = true;
  }

  private void dropStandby() {
    if (standby != null) standby.release();
    standby = null;
    planned = "";
    warming = false;
  }

  private void finish() {
    if (tail != null) tail.release();
    tail = null;
    incoming = null;
    host.current().volume(master);
  }

  private double progress() {
    return incoming == null ? 1 : (incoming.position() - startPosition) / blendSpeed / blendMs;
  }

  private void apply() {
    if (tail == null) {
      host.current().volume(master);
      return;
    }
    double p = progress();
    tail.volume(master * outgoing(p));
    incoming.volume(master * incoming(p));
  }

  public void tick() {
    if (closed) return;
    Deck current = host.current();
    if (tail != null) {
      if (current != incoming
          || !current.item().equals(incomingItem)
          || !current.playing()
          || current.failed()
          || !tail.playing()
          || progress() >= 1) {
        finish();
        return;
      }
      apply();
      return;
    }
    int duration = clampSeconds(seconds.getAsInt());
    if (duration == 0
        || current.failed()
        || !current.playing()
        || !current.eligible()
        || !current.hasNext()) {
      dropStandby();
      return;
    }
    double speed = Math.max(.1, current.speed());
    double remaining = (current.duration() - current.position()) / speed;
    if (remaining <= 0) {
      dropStandby();
      return;
    }
    String stamp = current.stamp();
    if (standby != null && !planned.equals(stamp)) dropStandby();
    if (standby == null) {
      if (remaining > duration * 1000 + 15000 || attempted.equals(stamp)) return;
      attempted = stamp;
      standby = host.prepareNext();
      planned = stamp;
      if (standby == null) return;
      standby.volume(0);
    }
    if (standby.failed()) {
      dropStandby();
      return;
    }
    if (!standby.ready() || !standby.eligible()) return;
    // Never consume more than a third of a short song, at the selected playback speed.
    double fade =
        Math.min(
            duration * 1000,
            Math.min(
                current.duration() / speed / 3,
                standby.duration() / Math.max(.1, standby.speed()) / 3));
    if (fade < 500 || remaining < 150) {
      dropStandby();
      return;
    }
    if (remaining > fade) return;
    if (!warming) {
      standby.play();
      warming = true;
    }
    if (!standby.playing())
      return; // Keep the outgoing track at full gain until audio is advancing.
    tail = current;
    incoming = standby;
    standby = null;
    warming = false;
    tail.pauseAtEnd(true);
    blendMs = Math.min(fade, remaining);
    startPosition = incoming.position();
    blendSpeed = Math.max(.1, incoming.speed());
    incomingItem = incoming.item();
    host.promote(incoming);
    apply();
  }
}
