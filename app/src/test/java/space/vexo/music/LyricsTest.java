package space.vexo.music;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class LyricsTest {
  @Test
  public void parsesAndSortsTimestampsIncludingRepeatedLines() {
    List<Lyrics.Line> lines =
        Lyrics.parse("[00:12.4]second\n[00:01.05][00:03.250]first\n[ar:Example]\n[00:07.00]  \n");
    assertEquals(3, lines.size());
    assertEquals(1050, lines.get(0).time);
    assertEquals(3250, lines.get(1).time);
    assertEquals(12400, lines.get(2).time);
    assertEquals("first", lines.get(1).text);
  }

  @Test
  public void followsPlaybackAndSeeksAcrossBoundaries() {
    List<Lyrics.Line> lines = Lyrics.parse("[00:01.00]a\n[00:03.00]b\n[00:05.00]c");
    assertEquals(-1, Lyrics.active(lines, 999));
    assertEquals(0, Lyrics.active(lines, 1000));
    assertEquals(2, Lyrics.active(lines, 5000));
    assertEquals(0, Lyrics.active(lines, 1500));
    assertEquals(-1, Lyrics.active(Lyrics.parse(null), 10));
  }
}
