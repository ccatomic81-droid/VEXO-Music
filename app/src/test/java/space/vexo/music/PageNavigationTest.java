package space.vexo.music;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PageNavigationTest {
  SearchUiTest find = new SearchUiTest();

  @Before
  public void seed() {
    new DiscoveryUiTest().seed();
  }

  Object field(MainActivity a, String name) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(a);
  }

  void go(MainActivity a, String tab) {
    find.description(
            a.findViewById(android.R.id.content),
            tab.equals("Smart") ? "Smart Experience tab" : tab + " tab")
        .performClick();
  }

  @Test
  public void returningToSearchRestoresBrowseAndResumesTypedMatches() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
      new DiscoveryUiTest().fakeSources(a, work);
      go(a, "Search");
      View page = (View) field(a, "content");
      View browse = find.text(page, "Browse all");
      assertTrue(browse.isShown());
      go(a, "Home");
      go(a, "Search");
      assertSame(page, field(a, "content"));
      assertTrue(find.text(page, "Browse all").isShown());
      EditText input = (EditText) field(a, "searchField");
      input.setText("sky");
      go(a, "Settings");
      go(a, "Search");
      assertSame(input, field(a, "searchField"));
      assertEquals("sky", input.getText().toString());
      assertTrue(find.text(page, "Suggestions").isShown());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
      work.runNext();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertTrue(find.description(page, "Play Skyline after dark").isShown());
      new DiscoveryUiTest()
          .capture(a.findViewById(android.R.id.content), 320, 640, "restored-search-violet-320");
    }
  }

  @Test
  public void revisitingPagesReusesViewsAndNavigationAndKeepsScroll() throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      View root = a.findViewById(android.R.id.content);
      new DiscoveryUiTest().capture(root, 320, 640, "smooth-pages-home-320");
      LinearLayout home = (LinearLayout) field(a, "content");
      ScrollView scroll = (ScrollView) home.getChildAt(0);
      scroll.scrollTo(0, 120);
      int position = scroll.getScrollY();
      int padding = scroll.getPaddingBottom();
      View homeTab = find.description(root, "Home tab");
      go(a, "Settings");
      LinearLayout settings = (LinearLayout) field(a, "content");
      go(a, "Home");
      assertSame(home, field(a, "content"));
      assertEquals(position, scroll.getScrollY());
      assertEquals(padding, scroll.getPaddingBottom());
      assertSame(homeTab, find.description(root, "Home tab"));
      go(a, "Settings");
      assertSame(settings, field(a, "content"));
      new DiscoveryUiTest().capture(root, 320, 640, "smooth-pages-settings-320");
    }
  }

  @Test
  public void rapidSwitchesKeepOneVisiblePageWithoutGrowingTheDeckOrChangingPlayback()
      throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      for (int i = 0; i < 3; i++)
        for (String tab : new String[] {"Search", "Library", "Settings", "Smart", "Home"})
          go(a, tab);
      FrameLayout host = (FrameLayout) field(a, "pageHost");
      int visible = 0;
      for (int i = 0; i < host.getChildCount(); i++)
        if (host.getChildAt(i).getVisibility() == View.VISIBLE) visible++;
      assertEquals(1, visible);
      assertTrue(host.getChildCount() <= 5);
      assertEquals("Home", field(a, "tab"));
      assertNull(field(a, "controller"));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220));
      assertEquals(0f, ((View) field(a, "content")).getTranslationX(), .01f);
      assertEquals(1f, ((View) field(a, "content")).getAlpha(), .01f);
    }
  }

  @Test
  public void changingSettingsInvalidatesCachedPagesAndSmartRemainsAttachedBetweenVisits()
      throws Exception {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      LinearLayout original = (LinearLayout) field(a, "content");
      go(a, "Smart");
      LinearLayout smart = (LinearLayout) field(a, "content");
      View panel = smart.getChildAt(0);
      go(a, "Library");
      go(a, "Smart");
      assertSame(smart, field(a, "content"));
      Field disposed = SmartExperiencePanel.class.getDeclaredField("disposed");
      disposed.setAccessible(true);
      assertFalse(disposed.getBoolean(panel));
      go(a, "Settings");
      View label = find.text((View) field(a, "content"), "Automatic recovery");
      assertNotNull(label);
      ((View) label.getParent().getParent()).performClick();
      go(a, "Home");
      assertNotSame(original, field(a, "content"));
      assertTrue(((FrameLayout) field(a, "pageHost")).getChildCount() <= 5);
    }
  }
}
