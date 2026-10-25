package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Application;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ProfileUiTest {
  final SearchUiTest finder = new SearchUiTest();

  @Before
  public void isolate() {
    Application app = RuntimeEnvironment.getApplication();
    new UpdateChecker(app).automatic(false);
    Shadows.shadowOf(app)
        .declareComponentUnbindable(new android.content.ComponentName(app, PlaybackService.class));
    new UpdateChecker(app).seen(BuildConfig.VERSION_CODE);
    app.getSharedPreferences("settings", 0).edit().putString("region", "LK").commit();
    new RegionalFeed(
            "LK",
            "Regional music",
            false,
            System.currentTimeMillis(),
            java.util.Collections.emptyList())
        .persist(app);
    MixCatalogue cache = new MixCatalogue(app);
    for (MixCatalogue.Mix mix : MixCatalogue.mixes("LK"))
      cache.save(
          mix,
          "LK",
          java.util.Arrays.asList(
              new Track("https://www.youtube.com/watch?v=fixture", "Song", "Artist", "", 200)));
  }

  @Test
  public void firstOpenCollectsNameThenShowsProfileGreetingAndNoUrlSetting() throws Exception {
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = a.get();
      View root = activity.findViewById(android.R.id.content);
      assertNotNull(finder.text(root, "Make yourself at home."));
      finder.text(root, "Let’s listen").performClick();
      assertFalse(new UserProfile(activity).complete());
      EditText name = (EditText) finder.description(root, "Your name");
      name.setText("Kavindu");
      new DiscoveryUiTest().capture(root, 320, 640, "profile-first-open");
      finder.text(root, "Let’s listen").performClick();
      assertTrue(new UserProfile(activity).complete());
      assertNotNull(finder.text(root, new UserProfile(activity).greeting() + ", Kavindu"));
      new DiscoveryUiTest().capture(root, 320, 640, "home-personal-greeting");
      finder.description(root, "Edit your profile").performClick();
      assertNotNull(finder.text(root, "Your profile"));
      ((EditText) finder.description(root, "Your name")).setText("Kavi");
      finder.text(root, "Save profile").performClick();
      assertEquals("Kavi", new UserProfile(activity).name());
      finder.description(root, "Settings tab").performClick();
      assertNotNull(finder.text(root, "Your profile"));
      assertNotNull(finder.text(root, "Check for updates"));
      assertNull(finder.text(root, "Update source"));
      assertTrue(new UpdateChecker(activity).source().contains("vexo-music-updates"));
    }
  }

  @Test
  public void unfinishedNameSurvivesActivityRecreation() {
    Bundle state = new Bundle();
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      View root = a.get().findViewById(android.R.id.content);
      ((EditText) finder.description(root, "Your name")).setText("Listener");
      a.saveInstanceState(state);
    }
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible()) {
      View root = a.get().findViewById(android.R.id.content);
      assertEquals(
          "Listener", ((EditText) finder.description(root, "Your name")).getText().toString());
      assertFalse(new UserProfile(a.get()).complete());
    }
  }
}
