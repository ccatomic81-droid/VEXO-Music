package space.vexo.music;

import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DiscoveryMixUiTest {
  Object field(Object owner,String key)throws Exception{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}
  @Test public void discoveryHomeAndMixControlsFitSmallPhonesAndKeepSavedData()throws Exception{
    DiscoveryUiTest helper=new DiscoveryUiTest();helper.seed();Application app=RuntimeEnvironment.getApplication();
    MixCatalogue catalogue=new MixCatalogue(app);
    for(MixCatalogue.Mix mix:MixCatalogue.all("LK"))catalogue.save(mix,"LK",Arrays.asList(helper.saved,helper.remote));
    try(var a=Robolectric.buildActivity(MainActivity.class).setup()){
      MainActivity activity=a.get();((Handler)field(activity,"main")).removeCallbacksAndMessages(null);
      MusicHomePanel home=(MusicHomePanel)field(activity,"homePanel");
      home.personal(MusicInterests.read(app),Arrays.asList(helper.remote),false,false,()->{},()->{},(songs,index)->{},()->{});
      View root=activity.findViewById(android.R.id.content);
      for(int width:new int[]{320,393})helper.capture(root,width,800,"discovery-mixes-home-"+width);
      assertNotNull(helper.text(root,"Daily Mix"));assertNotNull(helper.text(root,"New to you"));
      assertNotNull(helper.text(root,"TikTok-inspired · full songs"));
      home.scrollTo(0,home.body.getHeight());helper.capture(root,320,800,"discovery-trending-320");
      assertNotNull(helper.find(root,"Open Sinhala favourites"));
      helper.find(root,"Open Daily Mix").performClick();
      helper.capture(root,320,800,"discovery-daily-320");
      assertNotNull(helper.text(root,"Tune My Mix"));assertNotNull(helper.text(root,"Save to your playlists"));
      helper.text(root,"Tune My Mix").performClick();
      AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertTrue(dialog.isShowing());assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
      View decor=dialog.getWindow().getDecorView();
      decor.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));
      decor.layout(0,0,320,decor.getMeasuredHeight());
      android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(320,decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
      decor.draw(new android.graphics.Canvas(image));File dir=new File("build/ui-checks");dir.mkdirs();
      try(var out=new FileOutputStream(new File(dir,"tune-my-mix-320.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
      assertTrue(decor.getHeight()<=720);
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertFalse(dialog.isShowing());assertEquals(1,new LibraryStore(app).tracks("favourites").size());
    }
  }
  @Test public void savingTuningIsPerMixAndDoesNotStartAPlaybackSession()throws Exception{
    try (var activity = Robolectric.buildActivity(android.app.Activity.class).setup()) {
    Configuration config = new Configuration(activity.get().getResources().getConfiguration()); config.fontScale = 1.6f;
    Context app=new android.view.ContextThemeWrapper(activity.get().createConfigurationContext(config),R.style.AppTheme);
    MixCatalogue.Mix daily=MixCatalogue.find("daily","LK");final boolean[] changed={false};
    AlertDialog dialog=MixTuner.show(app,daily,Runnable::run,()->changed[0]=true);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
    View decor=dialog.getWindow().getDecorView();
    decor.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.AT_MOST));
    decor.layout(0,0,320,decor.getMeasuredHeight());
    android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(320,decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
    decor.draw(new android.graphics.Canvas(image));File dir=new File("build/ui-checks");dir.mkdirs();
    try(var out=new FileOutputStream(new File(dir,"tune-my-mix-large-320.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    assertTrue(decor.getHeight()<=700);
    for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
      View button = dialog.getButton(which); int[] at = new int[2], origin = new int[2];
      button.getLocationOnScreen(at); decor.getLocationOnScreen(origin);
      assertTrue("Action must fit horizontally", at[0] - origin[0] >= 0 && at[0] - origin[0] + button.getWidth() <= decor.getWidth());
      assertTrue("Action must be fully visible vertically", at[1] - origin[1] >= 0 && at[1] - origin[1] + button.getHeight() <= decor.getHeight());
    }
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertTrue(changed[0]);assertNull(MixSession.active(app));
    assertNotNull(new SmartStore(app).preferences().optJSONObject("mix_tune:daily"));
    assertNull(new SmartStore(app).preferences().optJSONObject("mix_tune:calm"));
    MixSession.set(app,"daily","LK");assertEquals("daily",MixSession.active(app).id);
    MixSession.clear(app);assertNull(MixSession.active(app));
    }
  }
}
