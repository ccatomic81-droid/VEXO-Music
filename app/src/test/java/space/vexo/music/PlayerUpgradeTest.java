package space.vexo.music;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerUpgradeTest {
  private Track song(String id, String artist) {
    return new Track("https://www.youtube.com/watch?v=" + id, id, artist, "", 180);
  }

  @Test public void filtersChangeCandidateMembershipWithoutChangingTheirInput() {
    Track known = song("known", "Nova"), sameArtist = song("new from Nova", "Nova"), fresh = song("fresh", "Orion");
    List<Track> input = Arrays.asList(fresh, known, sameArtist);
    assertEquals(Arrays.asList(known, sameArtist), QueueMixFilter.select("Familiar", input, Arrays.asList(known), Collections.emptyList()));
    assertEquals(Arrays.asList(fresh, sameArtist), QueueMixFilter.select("Discover", input, Arrays.asList(known), Collections.emptyList()));
    assertEquals(3, input.size());
    assertTrue(QueueMixFilter.query("Popular", known).contains("popular hit"));
    assertTrue(QueueMixFilter.query("Deep cuts", known).contains("album tracks b sides"));
    assertEquals("All", QueueMixFilter.valid("invalid"));
  }

  @Test public void completedDragsKeepCurrentPositionPauseAndShuffleAndBecomeManual() {
    Context c = RuntimeEnvironment.getApplication();
    for (boolean shuffled : new boolean[] {false, true}) {
      CrossfadePlayer player = new CrossfadePlayer(c, new DefaultDataSource.Factory(c));
      try {
        Track current = song("current", "A"), a = song("a", "B"), b = song("b", "C"), d = song("d", "D");
        player.setMediaItems(Arrays.asList(current.mediaItem(), a.mediaItem(), b.mediaItem(true), d.mediaItem()), 0, 42000);
        player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(new int[] {0, 3, 1, 2}, 1));
        player.setShuffleModeEnabled(shuffled); player.pause();
        java.util.function.Consumer<int[]> order = values -> player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(values, 1));
        assertFalse(ManualQueue.move(player, 0, 1, current.url, a.url, order));
        assertFalse(ManualQueue.move(player, 2, 1, "stale", a.url, order));
        assertTrue(ManualQueue.move(player, 2, 1, b.url, a.url, order));
        assertEquals(current.url, player.getCurrentMediaItem().mediaId);
        assertEquals(42000, player.getCurrentPosition());
        assertFalse(player.getPlayWhenReady()); assertEquals(shuffled, player.getShuffleModeEnabled());
        List<String> upcoming = new SessionQueueTest().upcoming(player);
        assertTrue(upcoming.indexOf(b.url) < upcoming.indexOf(a.url));
        for (int i = 0; i < player.getMediaItemCount(); i++)
          if (player.getMediaItemAt(i).mediaId.equals(b.url)) assertFalse(Track.from(player.getMediaItemAt(i)).recommended);
        SongRadio.pruneRecommendations(player);
        assertEquals(4, player.getMediaItemCount());
      } finally { player.release(); }
    }
  }

  @Test public void mixedHomeGradientHasDifferentColoursAndFadesIntoBothThemeBackgrounds() {
    for (boolean light : new boolean[] {false, true}) {
      HomeGradient gradient = new HomeGradient(light, 1);
      gradient.setBounds(0, 0, 360, 800);
      Bitmap image = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888);
      gradient.draw(new Canvas(image));
      int left = image.getPixel(15, 30), right = image.getPixel(340, 30), teal = image.getPixel(50, 250);
      assertNotEquals(left, right); assertNotEquals(right, teal);
      assertEquals(light ? 0xfff7f9f7 : 0xff121212, image.getPixel(180, 799));
      image.recycle();
    }
  }

  @Test public void homeQuickPicksAndMoodShortcutsRenderAndQueueSheetFitsNarrowPhones() throws Exception {
    DiscoveryUiTest helper = new DiscoveryUiTest(); helper.seed();
    Context context = RuntimeEnvironment.getApplication();
    try (var a = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity owner = a.get();
      ((Handler) HomePolishTest.field(owner, "main")).removeCallbacksAndMessages(null);
      MusicHomePanel home = (MusicHomePanel) HomePolishTest.field(owner, "homePanel");
      home.personal(MusicInterests.read(context), Arrays.asList(helper.saved, helper.remote), false, false,
          () -> {}, () -> {}, (songs, at) -> {}, () -> {});
      assertNotNull(helper.text(home, "Quick picks")); assertNotNull(helper.text(home, "Play all"));
      for (String label : new String[] {"Energize", "Relax", "Feel good", "Party", "Focus"})
        assertNotNull(helper.text(home, label));
      home.playback(helper.remote.url, true);
      helper.capture(owner.findViewById(android.R.id.content), 360, 800, "mixed-gradient-home-360");
      helper.text(home, "Relax").performClick();
      assertEquals("calm", HomePolishTest.field(owner, "activeMix"));
    }
    List<String> actions = new ArrayList<>();
    QueuePanel panel = new QueuePanel(context, true, new QueuePanel.Actions() {
      public void close() {} public void play(int at, String id) {} public void options(int at, Track track) {}
      public void clearUpcoming() {} public void clearAll() {} public void autoplay(boolean value) {}
      public void filter(String value) { actions.add(value); } public void save() { actions.add("save"); }
    });
    panel.refresh(Arrays.asList(helper.saved, helper.remote), 0, true, "Autoplay on");
    QueueSheet sheet = new QueueSheet(context, panel, () -> actions.add("close"));
    sheet.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
    sheet.layout(0, 0, 320, 720);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    helper.capture(sheet, 320, 720, "smart-queue-half-320");
    assertTrue(panel.getHeight() < 720); assertTrue(panel.getHeight() >= 340);
    assertEquals(View.GONE, helper.text(panel, "Clear upcoming").getParent().getParent() instanceof LinearLayout
        ? ((View) helper.text(panel, "Clear upcoming").getParent()).getVisibility() : View.VISIBLE);
    panel.handle.performClick();
    helper.capture(sheet, 320, 720, "smart-queue-full-320");
    assertEquals(720, panel.getHeight());
    helper.text(panel, "Discover").performClick(); helper.text(panel, "Save mix").performClick();
    assertEquals(Arrays.asList("Discover", "save"), actions);
  }
}
