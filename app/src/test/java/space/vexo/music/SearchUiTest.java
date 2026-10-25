package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SearchUiTest {
  @Before
  public void isolate() {
    Application app = RuntimeEnvironment.getApplication();
    new UpdateChecker(app).seen(BuildConfig.VERSION_CODE);
    app.getSharedPreferences("settings", 0).edit().putString("region", "LK").commit();
    new RegionalFeed(
            "LK", "Regional music", false, System.currentTimeMillis(), Collections.emptyList())
        .persist(app);
    Shadows.shadowOf(app)
        .declareComponentUnbindable(new android.content.ComponentName(app, PlaybackService.class));
    new SearchHistory(app).add("Sinhala music");
  }

  View description(View v, String text) {
    if (text.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        View found = description(((ViewGroup) v).getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }

  TextView text(View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return (TextView) v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        TextView found = text(((ViewGroup) v).getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }

  void capture(View root, String name, int height) throws Exception {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 320, height);
    Bitmap b = Bitmap.createBitmap(320, height, Bitmap.Config.ARGB_8888);
    root.draw(new Canvas(b));
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }

  @Test
  public void editingFillingRemovingAndBackKeepNavigationAccessible() throws Exception {
    try (ActivityController<MainActivity> a =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = a.get();
      Field field = MainActivity.class.getDeclaredField("suggestions");
      field.setAccessible(true);
      ((SearchSuggestions) field.get(activity)).close();
      SearchSuggestionsTest.Work work = new SearchSuggestionsTest.Work();
      field.set(
          activity,
          new SearchSuggestions(
              new Handler(Looper.getMainLooper()),
              work,
              (q, c) -> Arrays.asList("Sinhala new songs", "Sinhala music")));
      View root = activity.findViewById(android.R.id.content);
      description(root, "Search tab").performClick();
      assertNotNull(text(root, "Recent searches"));
      EditText input = (EditText) description(root, "Search songs or artists");
      input.setText("Sinhala");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
      work.runNext();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNotNull(text(root, "Sinhala new songs"));
      description(root, "Use suggestion Sinhala new songs").performClick();
      assertEquals("Sinhala new songs", input.getText().toString());
      input.setText("");
      description(root, "Remove recent search Sinhala music").performClick();
      assertTrue(new SearchHistory(activity).entries().isEmpty());
      input.setText("Sinhala");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
      if (!work.tasks.isEmpty()) work.runNext();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      capture(root, "search-suggestions-small", 568);
      View nav = description(root, "Settings tab");
      int[] xy = new int[2];
      nav.getLocationOnScreen(xy);
      assertTrue(xy[1] + nav.getHeight() <= 568);
      activity.getOnBackPressedDispatcher().onBackPressed();
      assertEquals(
          View.GONE, ((View) text(root, "Suggestions").getParent().getParent()).getVisibility());
      description(root, "Settings tab").performClick();
      ((View) text(root, "What’s new").getParent().getParent()).performClick();
      AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      assertEquals(
          "What’s new in " + BuildConfig.VERSION_NAME,
          org.robolectric.Shadows.shadowOf(dialog).getTitle().toString());
      dialog.dismiss();
      ((View) text(root, "Check for updates").getParent().getParent()).performClick();
      dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertEquals(
          "Update source not connected",
          org.robolectric.Shadows.shadowOf(dialog).getTitle().toString());
      dialog.dismiss();
    }
  }
}
