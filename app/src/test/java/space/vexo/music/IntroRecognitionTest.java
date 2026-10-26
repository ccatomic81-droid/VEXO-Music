package space.vexo.music;

import static org.junit.Assert.*;

import androidx.media3.common.Player;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.extractor.stream.StreamSegment;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class IntroRecognitionTest {
  Track track = new Track("https://www.youtube.com/watch?v=abcdefghijk", "Song", "Singer", "", 180);

  @After
  public void clear() {
    IntroRecognition.clear();
  }

  @Test
  public void onlyExplicitShortOpeningChaptersAreRecognized() {
    assertEquals(
        18000,
        IntroRecognition.detect(
            Arrays.asList(new StreamSegment("Intro", 0), new StreamSegment("Song", 18)), 180000));
    for (String title : Arrays.asList("Verse 1", "Introvert", "Song", "Advertisement"))
      assertEquals(
          0,
          IntroRecognition.detect(
              Arrays.asList(new StreamSegment(title, 0), new StreamSegment("Song", 18)), 180000));
    assertEquals(
        0,
        IntroRecognition.detect(
            Arrays.asList(new StreamSegment("Intro", 0), new StreamSegment("Song", 95)), 180000));
    assertEquals(
        0,
        IntroRecognition.detect(Collections.singletonList(new StreamSegment("Intro", 0)), 180000));
  }

  @Test
  public void userMarkerWinsAndChangedDurationDoesNotReuseOldTiming() {
    IntroRecognition.remember(
        track.url,
        180000,
        Arrays.asList(new StreamSegment("Intro", 0), new StreamSegment("Song", 18)));
    PureSong pure = new PureSong(new SmartStore(RuntimeEnvironment.getApplication()));
    assertEquals(18000, pure.start(track, 180000));
    assertEquals(0, pure.start(track, 200000));
    pure.mark(track, 12000, 180000);
    assertEquals(12000, pure.start(track, 180000));
    pure.reset(track);
    assertEquals(18000, pure.start(track, 180000));
  }

  @Test
  public void pausedStartStaysArmedUntilPlaybackAndFullVersionDoesNotReskip() {
    PureSong pure = new PureSong(new SmartStore(RuntimeEnvironment.getApplication()));
    pure.enabled(true);
    pure.mark(track, 12000, 180000);
    ListeningExperienceTest.Fake f = new ListeningExperienceTest.Fake();
    f.item = track.mediaItem();
    f.playing = false;
    IntroSkipper skip = new IntroSkipper(f.player, pure, () -> false);
    skip.onMediaItemTransition(f.item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO);
    skip.onEvents(f.player, null);
    assertEquals(0, f.seeks);
    f.playing = true;
    skip.onEvents(f.player, null);
    assertEquals(12000, f.position);
    skip.fullVersion();
    skip.onEvents(f.player, null);
    assertEquals(0, f.position);
    skip.close();
  }
}
