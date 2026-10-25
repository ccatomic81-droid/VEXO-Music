package space.vexo.music;

import static org.junit.Assert.*;

import org.junit.Assume;
import org.junit.Test;

/** Opt-in, real network check. Excluded by assumption during normal offline CI. */
public class OnlinePlaybackIntegrationTest {
  @Test
  public void canSearchResolveAndReadAudioFromYouTube() throws Exception {
    Assume.assumeTrue(
        "Set VEXO_LIVE_TEST=1 for real YouTube network verification",
        "1".equals(System.getenv("VEXO_LIVE_TEST")));
    OnlineMusic.Results results = OnlineMusic.search("creative commons instrumental music");
    assertFalse("YouTube search should return songs", results.tracks.isEmpty());
    String uri = OnlineMusic.resolve(results.tracks.get(0).url, true);
    assertTrue("Audio must use HTTPS", uri.startsWith("https://"));
    try (okhttp3.Response response =
        OnlineMusic.HTTP
            .newCall(new okhttp3.Request.Builder().url(uri).header("Range", "bytes=0-4095").build())
            .execute()) {
      assertTrue("Audio response status " + response.code(), response.isSuccessful());
      assertNotNull(response.body());
      String mime = response.header("Content-Type", "");
      if (mime.startsWith("text/")) {
        System.out.println(
            "Non-audio response from "
                + java.net.URI.create(uri).getHost()
                + ": "
                + response.peekBody(512).string());
      }
      assertTrue(
          "Expected audio content, got " + mime,
          mime.startsWith("audio/")
              || mime.startsWith("video/webm")
              || mime.startsWith("application/octet-stream"));
      byte[] sample = new byte[4096];
      java.io.InputStream input = response.body().byteStream();
      int bytes = 0, next;
      while (bytes < sample.length && (next = input.read(sample, bytes, sample.length - bytes)) > 0)
        bytes += next;
      assertTrue("Audio response must contain more than a header", bytes >= 1024);
      boolean webm =
          (sample[0] & 255) == 0x1a
              && (sample[1] & 255) == 0x45
              && (sample[2] & 255) == 0xdf
              && (sample[3] & 255) == 0xa3;
      boolean mp4 = sample[4] == 'f' && sample[5] == 't' && sample[6] == 'y' && sample[7] == 'p';
      boolean ogg = sample[0] == 'O' && sample[1] == 'g' && sample[2] == 'g' && sample[3] == 'S';
      assertTrue("Expected an audio container, not a web error page", webm || mp4 || ogg);
      System.out.println(
          "Verified YouTube search, stream resolution and "
              + bytes
              + " audio bytes ("
              + mime
              + ") from: "
              + results.tracks.get(0).title);
    }
  }
}
