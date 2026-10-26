package space.vexo.music;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONObject;

public final class Track {
  public final String url, title, artist, artwork;
  public final long duration;
  public final boolean recommended;

  public Track(String url, String title, String artist, String artwork, long duration) {
    this(url, title, artist, artwork, duration, false);
  }

  private Track(String url, String title, String artist, String artwork, long duration,
      boolean recommended) {
    this.recommended = recommended;
    this.url = url;
    this.title = title;
    this.artist = artist;
    this.artwork = artwork;
    this.duration = duration;
  }

  public boolean online() {
    return url.startsWith("https://");
  }

  @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
  public MediaItem mediaItem() {
    return mediaItem(false);
  }

  @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
  MediaItem mediaItem(boolean recommended) {
    MediaMetadata.Builder meta = new MediaMetadata.Builder().setTitle(title).setArtist(artist);
    android.os.Bundle extras = new android.os.Bundle();
    extras.putLong("duration", duration);
    extras.putBoolean("vexo_recommended", recommended);
    meta.setExtras(extras);
    if (!artwork.isEmpty()) meta.setArtworkUri(Uri.parse(artwork));
    return new MediaItem.Builder()
        .setMediaId(url)
        .setUri(url)
        .setCustomCacheKey(url)
        .setMediaMetadata(meta.build())
        .build();
  }

  public JSONObject json() {
    JSONObject o = new JSONObject();
    try {
      o.put("url", url);
      o.put("title", title);
      o.put("artist", artist);
      o.put("artwork", artwork);
      o.put("duration", duration);
      o.put("recommended", recommended);
    } catch (Exception ignored) {
    }
    return o;
  }

  public static Track from(JSONObject o) {
    return new Track(
        o.optString("url"),
        o.optString("title"),
        o.optString("artist"),
        o.optString("artwork"),
        o.optLong("duration"), o.optBoolean("recommended"));
  }

  public static Track from(MediaItem m) {
    return new Track(
        m.mediaId,
        String.valueOf(m.mediaMetadata.title),
        String.valueOf(m.mediaMetadata.artist),
        m.mediaMetadata.artworkUri == null ? "" : m.mediaMetadata.artworkUri.toString(),
        m.mediaMetadata.extras == null ? 0 : m.mediaMetadata.extras.getLong("duration", 0),
        m.mediaMetadata.extras != null && m.mediaMetadata.extras.getBoolean("vexo_recommended"));
  }
}
