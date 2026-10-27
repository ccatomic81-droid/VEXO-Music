package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BrandRegionDialogTest {
  @Test
  public void scannersAndLauncherResolveTheSameCurrentPackageMark() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    ApplicationInfo app =
        c.getPackageManager().getApplicationInfo(c.getPackageName(), PackageManager.GET_META_DATA);
    assertEquals(R.mipmap.vexo_launcher_v2, app.icon);
    assertEquals(R.drawable.vexo_logo_v2, app.logo);
    ActivityInfo activity =
        c.getPackageManager().getActivityInfo(new ComponentName(c, MainActivity.class), 0);
    assertEquals(app.icon, activity.icon);
    assertEquals(app.logo, activity.logo);
    assertEquals(
        R.drawable.vexo_notification_v2,
        app.metaData.getInt("com.google.firebase.messaging.default_notification_icon"));
    android.view.ContextThemeWrapper themed =
        new android.view.ContextThemeWrapper(c, R.style.AppTheme);
    android.util.TypedValue splash = new android.util.TypedValue();
    assertTrue(
        themed
            .getTheme()
            .resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, splash, true));
    assertEquals(R.drawable.vexo_foreground_v2, splash.resourceId);
    android.graphics.drawable.Drawable mark = c.getDrawable(app.icon);
    Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
    mark.setBounds(0, 0, 256, 256);
    mark.draw(new Canvas(bitmap));
    save(bitmap, "current-package-icon");
  }

  @Test
  public void globalRequestsOmitCountryAndKeepTheirOwnSavedFeed() throws Exception {
    assertFalse(MusicSearch.client(RegionPolicy.GLOBAL, "fixture").has("gl"));
    assertEquals("LK", MusicSearch.client("LK", "fixture").getString("gl"));
    Context c = RuntimeEnvironment.getApplication();
    Track local = new Track("https://example.com/lk", "Local", "Artist", "", 180);
    Track worldwide = new Track("https://example.com/global", "Worldwide", "Artist", "", 180);
    new RegionalFeed("LK", "Country picks", false, 1, Arrays.asList(local)).persist(c);
    new RegionalFeed(RegionPolicy.GLOBAL, "Worldwide picks", false, 2, Arrays.asList(worldwide))
        .persist(c);
    assertEquals(worldwide.url, RegionalFeed.cached(c, RegionPolicy.GLOBAL).tracks.get(0).url);
    assertEquals(local.url, RegionalFeed.cached(c, "LK").tracks.get(0).url);
    assertEquals("Global favourites", MixCatalogue.find("regional", RegionPolicy.GLOBAL).name);
  }

  @Test
  public void regionPickerPersistsGlobalInsteadOfSelectingACountry() throws Exception {
    DiscoveryUiTest helper = new DiscoveryUiTest();
    helper.seed();
    Context c = RuntimeEnvironment.getApplication();
    new RegionalFeed(
            RegionPolicy.GLOBAL,
            "Worldwide picks",
            false,
            System.currentTimeMillis(),
            Arrays.asList(helper.remote))
        .persist(c);
    try (var activity = Robolectric.buildActivity(MainActivity.class).setup()) {
      java.lang.reflect.Method choose = MainActivity.class.getDeclaredMethod("chooseRegion");
      choose.setAccessible(true);
      choose.invoke(activity.get());
      AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertEquals(
          "Global · music from around the world", dialog.getListView().getAdapter().getItem(1));
      capture(dialog, "vexo-region-picker-320");
      dialog.getListView().performItemClick(null, 1, 1);
      assertEquals(RegionPolicy.GLOBAL, RegionPolicy.current(c));
    }
  }

  @Test
  public void brandedDialogRetainsConfirmAndCancelSemantics() throws Exception {
    try (var activity = Robolectric.buildActivity(Activity.class).setup()) {
      boolean[] confirmed = {false};
      AlertDialog dialog =
          new VexoDialogs.Builder(activity.get())
              .setTitle("Clear offline audio?")
              .setMessage("Your phone’s original audio, favourites and playlists will stay.")
              .setNegativeButton("Keep music", null)
              .setPositiveButton("Clear", (d, w) -> confirmed[0] = true)
              .show();
      capture(dialog, "vexo-confirmation-320");
      assertNotNull(text(dialog.getWindow().getDecorView(), "VEXO MUSIC"));
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      assertFalse(confirmed[0]);
      assertFalse(dialog.isShowing());
      dialog =
          new VexoDialogs.Builder(activity.get())
              .setTitle("Ready to update")
              .setMessage("The update passed its checks.")
              .setPositiveButton("Install update", (d, w) -> confirmed[0] = true)
              .show();
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      assertTrue(confirmed[0]);
    }
  }

  @Test
  public void largeTextActionsFitAndNoticesAppearAboveTheActiveDialog() throws Exception {
    try (var activity = Robolectric.buildActivity(Activity.class).setup()) {
      Configuration config = new Configuration(activity.get().getResources().getConfiguration());
      config.fontScale = 1.6f;
      ContextThemeWrapper c = new ContextThemeWrapper(activity.get(), R.style.AppTheme);
      c.applyOverrideConfiguration(config);
      AlertDialog dialog =
          new VexoDialogs.Builder(c)
              .setTitle("Update ready")
              .setMessage(
                  "Your downloaded update has been checked. Your music and settings stay safe.")
              .setNegativeButton("Later", null)
              .setPositiveButton("Install update", null)
              .show();
      capture(dialog, "vexo-confirmation-large-320");
      View decor = dialog.getWindow().getDecorView();
      for (int which : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
        View button = dialog.getButton(which);
        int[] at = new int[2], origin = new int[2];
        button.getLocationOnScreen(at);
        decor.getLocationOnScreen(origin);
        assertTrue(
            at[0] - origin[0] >= 0 && at[0] - origin[0] + button.getWidth() <= decor.getWidth());
        assertTrue(
            at[1] - origin[1] >= 0 && at[1] - origin[1] + button.getHeight() <= decor.getHeight());
      }
      VexoNotice.show(activity.get(), "First notice");
      VexoNotice.show(activity.get(), "Latest notice");
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNull(text(dialog.getWindow().getDecorView(), "First notice"));
      assertNotNull(text(dialog.getWindow().getDecorView(), "Latest notice"));
      dialog.dismiss();
    }
  }

  TextView text(View root, String value) {
    if (root instanceof TextView && value.contentEquals(((TextView) root).getText()))
      return (TextView) root;
    if (root instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
        TextView found = text(((ViewGroup) root).getChildAt(i), value);
        if (found != null) return found;
      }
    return null;
  }

  void capture(AlertDialog dialog, String name) throws Exception {
    View view = dialog.getWindow().getDecorView();
    view.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(680, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, 320, view.getMeasuredHeight());
    Bitmap bitmap = Bitmap.createBitmap(320, view.getHeight(), Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    save(bitmap, name);
  }

  void save(Bitmap bitmap, String name) throws Exception {
    File dir = new File("build/ui-checks");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }
}
