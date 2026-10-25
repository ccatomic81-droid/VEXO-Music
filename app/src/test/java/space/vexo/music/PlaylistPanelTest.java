package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlaylistPanelTest {
  @Test
  public void playShuffleAndSongMenusTargetTheDisplayedCollection() {
    Context c = RuntimeEnvironment.getApplication();
    List<String> actions = new ArrayList<>();
    Track a = new Track("content://audio/1", "First song", "Artist", "", 200),
        b = new Track("content://audio/2", "Second song", "Artist", "", 190);
    PlaylistPanel.Listener listener =
        new PlaylistPanel.Listener() {
          public void back() {
            actions.add("back");
          }

          public void play(int i) {
            actions.add("play:" + i);
          }

          public void shuffle() {
            actions.add("shuffle");
          }

          public void options(Track t) {
            actions.add("options:" + t.url);
          }

          public void add() {
            actions.add("add");
          }

          public void edit() {
            actions.add("edit");
          }

          public void save() {
            actions.add("save");
          }

          public void retry() {
            actions.add("retry");
          }
        };
    PlaylistPanel panel =
        new PlaylistPanel(
            c,
            "Evening",
            "Your songs",
            Arrays.asList(a, b),
            false,
            true,
            false,
            "",
            0xff665599,
            listener);
    SearchUiTest finder = new SearchUiTest();
    finder.text(panel, "Play all").performClick();
    finder.text(panel, "Shuffle").performClick();
    finder.description(panel, "Play Second song").performClick();
    finder.description(panel, "Options for First song").performClick();
    finder.text(panel, "Add songs").performClick();
    finder.text(panel, "Edit playlist").performClick();
    assertEquals(
        Arrays.asList("play:0", "shuffle", "play:1", "options:content://audio/1", "add", "edit"),
        actions);
    PlaylistPanel empty =
        new PlaylistPanel(
            c,
            "Empty",
            "Your songs",
            Collections.emptyList(),
            false,
            true,
            false,
            "",
            0xff665599,
            listener);
    assertFalse(finder.text(empty, "Play all").isEnabled());
    assertFalse(finder.text(empty, "Shuffle").isEnabled());
  }
}
