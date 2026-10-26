package space.vexo.music;

import java.util.*;
import org.schabi.newpipe.extractor.stream.StreamSegment;

/** Recognizes only an explicitly labelled opening chapter. Unknown timing is never guessed. */
final class IntroRecognition {
  private static final Map<String, long[]> chapters = new LinkedHashMap<>();

  static long detect(List<StreamSegment> segments, long duration) {
    if (segments == null || segments.size() < 2 || duration < 45000) return 0;
    StreamSegment first = segments.get(0), second = segments.get(1);
    if (first == null || second == null || first.getStartTimeSeconds() != 0) return 0;
    String title = RadioPolicy.normalize(first.getTitle());
    if (!title.equals("intro") && !title.equals("introduction") && !title.equals("opening credits"))
      return 0;
    long start = second.getStartTimeSeconds() * 1000L;
    return start >= 1000 && start <= 90000 && start < duration - 30000 ? start : 0;
  }

  static synchronized void remember(String url, long duration, List<StreamSegment> segments) {
    String key = QueuePolicy.identity(url);
    long start = detect(segments, duration);
    chapters.remove(key);
    if (start > 0) chapters.put(key, new long[] {start, duration});
    while (chapters.size() > 100) chapters.remove(chapters.keySet().iterator().next());
  }

  static synchronized long start(Track track, long duration) {
    long[] marker = chapters.get(QueuePolicy.identity(track.url));
    return marker != null && Math.abs(marker[1] - duration) <= 1500 ? marker[0] : 0;
  }

  static synchronized void clear() {
    chapters.clear();
  }
}
