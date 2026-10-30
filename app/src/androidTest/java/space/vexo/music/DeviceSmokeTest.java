package space.vexo.music;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.media3.session.MediaController;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;

/** Runs against the real activity and Media3 service on an Android emulator. */
@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

  @Before public void prepare() {
    assertTrue(new UserProfile(context).saveName("VEXO Listener"));
    UpdateChecker updates = new UpdateChecker(context);
    updates.automatic(false);
    updates.seen(BuildConfig.VERSION_CODE);
    context.getSharedPreferences("settings", 0).edit().putString("region", "LK").commit();
  }

  @Test public void navigateInBothThemesAndPlayLocalAudio() throws Exception {
    for (int theme : new int[] {1, 2}) {
      new LibraryStore(context).save("imports", Collections.emptyList());
      AppAppearance.save(context, theme);
      try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
        await(scenario, a -> controller(a) != null, "Media3 session did not connect");
        shot("home-" + theme);
        scenario.onActivity(a -> clickDescription(a.findViewById(android.R.id.content), "Library tab"));
        await(scenario, a -> findText(a.findViewById(android.R.id.content), "Choose files") != null, "Library did not load");
        scenario.onActivity(a -> assertEquals(1, countText(a.findViewById(android.R.id.content), "Import device music")));
        shot("library-" + theme);
        scenario.onActivity(a -> clickDescription(a.findViewById(android.R.id.content), "Search tab"));
        shot("search-" + theme);
        scenario.onActivity(a -> clickDescription(a.findViewById(android.R.id.content), "Settings tab"));
        scenario.onActivity(a -> {
          View root = a.findViewById(android.R.id.content);
          assertNotNull(findText(root, "Playback & discovery"));
          assertNotNull(findText(root, "App & support"));
        });
        shot("settings-" + theme);
      }
    }
    File wav = new File(context.getFilesDir(), "device-smoke.wav");
    writeSilence(wav);
    new LibraryStore(context).save("imports", Arrays.asList(new Track(wav.toURI().toString(), "Device playback check", "VEXO test fixture", "", 20)));
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      await(scenario, a -> controller(a) != null, "Media3 session did not reconnect");
      scenario.onActivity(a -> clickDescription(a.findViewById(android.R.id.content), "Library tab"));
      await(scenario, a -> findDescription(a.findViewById(android.R.id.content), "Play Device playback check") != null, "Local track missing");
      scenario.onActivity(a -> clickDescription(a.findViewById(android.R.id.content), "Play Device playback check"));
      await(scenario, a -> controller(a).isPlaying(), "Local WAV did not start");
      await(scenario, a -> controller(a).getCurrentPosition() > 500, "Playback position did not advance");
      shot("local-playing");
      scenario.onActivity(a -> {
        Dialog d = (Dialog) field(a, "playerDialog");
        assertNotNull(d);
        clickDescription(d.getWindow().getDecorView(), "Pause");
      });
      await(scenario, a -> !controller(a).isPlaying(), "Pause did not reach the service");
      shot("local-paused");
      scenario.onActivity(a -> controller(a).clearMediaItems());
    } finally {
      new LibraryStore(context).save("imports", Collections.emptyList());
      wav.delete();
    }
  }

  private interface Check { boolean ready(MainActivity a); }
  private void await(ActivityScenario<MainActivity> scenario, Check check, String message) {
    long until = SystemClock.uptimeMillis() + 15000;
    AtomicBoolean done = new AtomicBoolean();
    do {
      scenario.onActivity(a -> done.set(check.ready(a)));
      if (done.get()) return;
      SystemClock.sleep(100);
    } while (SystemClock.uptimeMillis() < until);
    fail(message);
  }
  private static Object field(MainActivity a, String name) {
    try { var f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(a); }
    catch (ReflectiveOperationException e) { throw new AssertionError(e); }
  }
  private static MediaController controller(MainActivity a) { return (MediaController) field(a, "controller"); }
  private static TextView findText(View v, String label) {
    if (v instanceof TextView && label.contentEquals(((TextView)v).getText())) return (TextView)v;
    if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) { TextView found=findText(((ViewGroup)v).getChildAt(i),label); if(found!=null)return found; }
    return null;
  }
  private static int countText(View v, String label) {
    int n=v instanceof TextView && label.contentEquals(((TextView)v).getText()) ? 1 : 0;
    if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=countText(((ViewGroup)v).getChildAt(i),label);
    return n;
  }
  private static View findDescription(View v, String label) {
    if(v.getContentDescription()!=null && label.contentEquals(v.getContentDescription()))return v;
    if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findDescription(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}
    return null;
  }
  private static void clickDescription(View root, String label) {
    View v=findDescription(root,label);assertNotNull("Missing control: "+label,v);assertTrue(v.performClick());
  }
  private void shot(String name) throws IOException {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    File dir=new File(context.getExternalFilesDir(null),"device-smoke");dir.mkdirs();
    String scale=String.valueOf(context.getResources().getConfiguration().fontScale);
    try(OutputStream out=new FileOutputStream(new File(dir,name+"-font"+scale+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    bitmap.recycle();
  }
  private static void writeSilence(File file) throws IOException {
    int size=8000*2*20;
    ByteBuffer b=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
    b.put(new byte[]{'R','I','F','F'}).putInt(36+size).put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt(size);
    try(OutputStream out=new FileOutputStream(file)){out.write(b.array());}
  }
}
