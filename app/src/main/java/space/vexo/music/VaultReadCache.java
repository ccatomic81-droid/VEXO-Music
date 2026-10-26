package space.vexo.music;

import android.content.SharedPreferences;
import java.util.*;

/** Process-memory-only validated envelopes. The current ciphertext is always the cache key. */
final class VaultReadCache {
  private static final Map<SharedPreferences, LinkedHashMap<String, Entry>> caches =
      new WeakHashMap<>();
  private static final int MAX_ENTRIES = 12, MAX_CHARS = 256 * 1024;

  private static final class Entry {
    final String seal, raw;

    Entry(String seal, String raw) {
      this.seal = seal;
      this.raw = raw;
    }
  }

  static synchronized String get(SharedPreferences prefs, String bucket, String seal) {
    Map<String, Entry> entries = caches.get(prefs);
    Entry e = entries == null ? null : entries.get(bucket);
    return e != null && e.seal.equals(seal) ? e.raw : null;
  }

  static synchronized void put(SharedPreferences prefs, String bucket, String seal, String raw) {
    LinkedHashMap<String, Entry> entries =
        caches.computeIfAbsent(prefs, k -> new LinkedHashMap<>());
    entries.remove(bucket);
    if (raw.length() > MAX_CHARS) return;
    entries.put(bucket, new Entry(seal, raw));
    while (entries.size() > MAX_ENTRIES) entries.remove(entries.keySet().iterator().next());
  }
}
