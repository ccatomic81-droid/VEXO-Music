package space.vexo.music;

import org.json.*;

/** Confirmed track markers take priority over an explicitly labelled online intro chapter. */
final class PureSong {
  private final SmartStore smart;

  PureSong(SmartStore smart) {
    this.smart = smart;
  }

  boolean enabled() {
    return smart.preferences().optBoolean("skip_intros", false);
  }

  void enabled(boolean value) {
    smart.preference("skip_intros", value);
  }

  static boolean permitted(Track t) {
    return t != null && (t.url.startsWith("content://") || !MusicRadio.videoId(t).isEmpty());
  }

  long start(Track t, long duration) {
    if (!permitted(t)) return 0;
    JSONObject m = smart.intros().optJSONObject(QueuePolicy.identity(t.url));
    if (m == null) return IntroRecognition.start(t, duration);
    if (Math.abs(m.optLong("duration") - duration) > 1500) return 0;
    long value = m.optLong("start");
    return value >= 1000 && value < duration - 5000 ? value : 0;
  }

  long crossfadeStart(Track track, long duration, boolean sharedRoom) {
    return !sharedRoom && enabled() ? start(track, duration) : 0;
  }

  void mark(Track t, long start, long duration) {
    if (!permitted(t) || start < 1000 || start >= duration - 5000)
      throw new IllegalArgumentException(
          "Choose a start at least one second in, with more than five seconds of music remaining.");
    smart.intro(t, start, duration);
  }

  void reset(Track t) {
    if (t != null) smart.intro(t, 0, 0);
  }

  static int audioBonus(Track t, JSONObject preferences) {
    return preferences.optBoolean("prefer_official_audio", false)
            && RadioPolicy.normalize(t.title).contains("official audio")
        ? 5
        : 0;
  }
}
