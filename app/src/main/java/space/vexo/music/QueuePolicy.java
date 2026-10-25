package space.vexo.music;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Queue rules shared by playback and the queue screen. */
public final class QueuePolicy {
  public static final int MAX_ITEMS = 100;

  public static String identity(String url) {
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      if ("youtu.be".equalsIgnoreCase(host))
        return "youtube:" + uri.getPath().substring(1).split("/")[0];
      if (host != null
          && (host.equalsIgnoreCase("youtube.com")
              || host.toLowerCase(Locale.ROOT).endsWith(".youtube.com"))) {
        String query = uri.getRawQuery();
        if (query != null)
          for (String part : query.split("&"))
            if (part.startsWith("v="))
              return "youtube:"
                  + URLDecoder.decode(part.substring(2), StandardCharsets.UTF_8.name());
        String[] path = uri.getPath().split("/");
        if (path.length > 2 && (path[1].equals("shorts") || path[1].equals("embed")))
          return "youtube:" + path[2];
      }
    } catch (Exception ignored) {
    }
    return url;
  }

  public static List<Track> fresh(
      List<Track> candidates, List<Track> queue, List<Track> recent, int limit) {
    Set<String> seen = new HashSet<>();
    for (Track t : queue) seen.add(identity(t.url));
    for (Track t : recent) seen.add(identity(t.url));
    List<Track> result = new ArrayList<>();
    int room = Math.max(0, Math.min(limit, MAX_ITEMS - queue.size()));
    for (Track t : candidates) {
      if (result.size() >= room) break;
      if (t.online()
          && !t.title.trim().isEmpty()
          && (t.duration >= 30)
          && t.duration <= 1800
          && seen.add(identity(t.url))) result.add(t);
    }
    return result;
  }

  public static int playNextTarget(int from, int current) {
    return from <= current ? current : current + 1;
  }

  public static List<Integer> order(
      int count, int start, java.util.function.IntUnaryOperator next) {
    List<Integer> indices = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    int at = start;
    while (at >= 0 && at < count && seen.add(at)) {
      indices.add(at);
      at = next.applyAsInt(at);
    }
    return indices;
  }

  public static int[] appendOrder(List<Integer> existing, int before, int added) {
    List<Integer> order = new ArrayList<>(existing);
    for (int i = 0; i < before; i++) if (!order.contains(i)) order.add(i);
    for (int i = 0; i < added; i++) order.add(before + i);
    return order.stream().mapToInt(Integer::intValue).toArray();
  }

  public static List<Integer> oldPlayed(List<Integer> order, int current, int keep) {
    int at = order.indexOf(current);
    if (at <= keep) return Collections.emptyList();
    List<Integer> remove = new ArrayList<>(order.subList(0, at - keep));
    remove.sort(Collections.reverseOrder());
    return remove;
  }
}
