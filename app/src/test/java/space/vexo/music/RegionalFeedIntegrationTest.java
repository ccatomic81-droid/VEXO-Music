package space.vexo.music;

import static org.junit.Assert.*;

import org.junit.*;

public class RegionalFeedIntegrationTest {
  @Test
  public void regionalFeedUsesRealSourcesAndLabelsSearchFallback() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("VEXO_LIVE_TEST")));
    RegionalFeed sriLanka = RegionalFeed.fetch("LK");
    assertFalse(sriLanka.tracks.isEmpty());
    assertEquals("LK", sriLanka.country);
    if (!sriLanka.ranked) assertTrue(sriLanka.source.contains("not a ranked chart"));
    System.out.println(
        "LK feed: "
            + sriLanka.source
            + "; "
            + sriLanka.tracks.size()
            + " songs; first: "
            + sriLanka.tracks.get(0).title);
    RegionalFeed australia = RegionalFeed.fetch("AU");
    assertFalse(australia.tracks.isEmpty());
    assertEquals("AU", australia.country);
    System.out.println(
        "AU feed: "
            + australia.source
            + "; "
            + australia.tracks.size()
            + " songs; first: "
            + australia.tracks.get(0).title);
  }
}
