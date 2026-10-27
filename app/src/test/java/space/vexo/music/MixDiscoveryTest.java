package space.vexo.music;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class MixDiscoveryTest {
  Track song(String id, String title, String artist) { return new Track("https://www.youtube.com/watch?v="+id,title,artist,"",200); }
  MusicInterests interests(List<Track> liked, List<Track> history, JSONObject prefs, JSONObject learned) {
    return MusicInterests.of(liked,history,Collections.emptyList(),YouTubeTaste.Snapshot.empty(),prefs,learned,Collections.emptySet());
  }
  final MixDiscovery.Tuning defaults = new MixDiscovery.Tuning(new JSONObject());

  @Test public void moodQueriesFollowDifferentInterestsAndExplicitLanguage() throws Exception {
    MusicInterests a=interests(Arrays.asList(song("aaaaaaaaaaa","Blue","Nova")),Collections.emptyList(),new JSONObject(),new JSONObject());
    MusicInterests b=interests(Arrays.asList(song("bbbbbbbbbbb","Gold","Orion")),Collections.emptyList(),new JSONObject(),new JSONObject());
    MixCatalogue.Mix calm=MixCatalogue.find("calm","LK");
    String qa=MixDiscovery.query(calm,a,defaults,"LK",System.currentTimeMillis());
    String qb=MixDiscovery.query(calm,b,defaults,"LK",System.currentTimeMillis());
    assertTrue(qa.contains("Nova") && qa.contains("calm relaxing")); assertTrue(qb.contains("Orion")); assertNotEquals(qa,qb);
    MixDiscovery.Tuning english=new MixDiscovery.Tuning(new JSONObject().put("language","English").put("mood","acoustic chill"));
    assertTrue(MixDiscovery.query(calm,a,english,"LK",System.currentTimeMillis()).contains("English Nova acoustic chill"));
    assertTrue(MixDiscovery.query(MixCatalogue.trending(),a,defaults,"LK",System.currentTimeMillis()).contains("TikTok viral songs"));
  }

  @Test public void newToYouExcludesHeardLikedLearnedAndDuplicateUploads() throws Exception {
    long now=System.currentTimeMillis(); Track liked=song("aaaaaaaaaaa","Home","Nova"),heard=song("bbbbbbbbbbb","Sky","Orion"),older=song("ccccccccccc","Ocean","Echo");
    JSONObject learned=new JSONObject(); TastePolicy.record(learned,older,150000,false,false,now);
    MusicInterests profile=interests(Arrays.asList(liked),Arrays.asList(heard),new JSONObject(),learned);
    Track fresh=song("ddddddddddd","Sunrise","New Artist"),duplicate=song("eeeeeeeeeee","Sunrise (Official Audio)","New Artist - Topic");
    List<Track> out=MixDiscovery.select(MixCatalogue.find("discover","LK"),profile,Arrays.asList(liked,heard,older,fresh,duplicate),defaults,now,Collections.emptySet(),30);
    assertEquals(1,out.size());assertEquals(fresh.url,out.get(0).url);
  }

  @Test public void tuningFamiliarityChangesRankingAndRecentToggleDoesNotChangeNewness() throws Exception {
    Track favourite=song("aaaaaaaaaaa","Home","Nova"),related=song("bbbbbbbbbbb","Orbit","Nova"),fresh=song("ccccccccccc","Sun","Echo");
    MusicInterests profile=interests(Arrays.asList(favourite),Arrays.asList(fresh),new JSONObject(),new JSONObject());
    MixCatalogue.Mix mix=MixCatalogue.find("calm","LK");
    MixDiscovery.Tuning familiar=new MixDiscovery.Tuning(new JSONObject().put("familiarity",2).put("avoid_recent",false));
    List<Track> out=MixDiscovery.select(mix,profile,Arrays.asList(fresh,related),familiar,System.currentTimeMillis(),Collections.emptySet(),10);
    assertEquals(related.url,out.get(0).url);assertEquals(2,out.size());
    assertEquals(1,MixDiscovery.select(mix,profile,Arrays.asList(fresh,related),defaults,System.currentTimeMillis(),Collections.emptySet(),10).size());
    assertTrue(MixDiscovery.select(MixCatalogue.find("discover","LK"),profile,Arrays.asList(fresh),familiar,System.currentTimeMillis(),Collections.emptySet(),10).isEmpty());
  }

  @Test public void dailyCacheSurvivesLearningButExpiresByDayAndTuning() throws Exception {
    android.content.Context app=RuntimeEnvironment.getApplication();MixCatalogue c=new MixCatalogue(app);
    MixCatalogue.Mix daily=MixCatalogue.find("daily","LK"),calm=MixCatalogue.find("calm","LK");
    List<Track> tracks=Arrays.asList(song("aaaaaaaaaaa","Home","Nova"));
    c.save(daily,"LK",tracks);c.save(calm,"LK",tracks);long now=System.currentTimeMillis();
    DiscoveryRevision.changed(app);assertTrue(c.fresh(daily,"LK",now));assertFalse(c.fresh(calm,"LK",now));
    assertFalse(c.fresh(daily,"LK",now+86400000L));assertTrue(c.cached(daily,"US").isEmpty());
    c.tuned(daily);assertFalse(c.fresh(daily,"LK",now));
    assertFalse(app.getSharedPreferences("music_mixes",0).getAll().toString().contains("Home"));
    assertFalse(app.getSharedPreferences("private_collections_v1",0).getAll().toString().contains("Nova"));
  }

  @Test public void explicitCancellationIsNotConvertedToAnOfflineFallback() throws Exception {
    MusicInterests profile=interests(Collections.emptyList(),Collections.emptyList(),new JSONObject(),new JSONObject());
    try {
      MixDiscovery.load(MixCatalogue.trending(),profile,new PersonalDiscovery.Source(){
        public List<Track> related(Track seed){return Collections.emptyList();}
        public List<Track> search(String query)throws Exception{throw new InterruptedException();}
      },"LK",System.currentTimeMillis(),Collections.emptySet());
      fail("Cancellation must propagate");
    }catch(InterruptedException expected){}
  }

  @Test public void learnedPositiveTrackCanSeedDiscoveryWithoutAnAccountOrHistory() throws Exception {
    android.content.Context app=RuntimeEnvironment.getApplication();Track t=song("aaaaaaaaaaa","Home","Nova");
    SmartStore store=new SmartStore(app);store.listened(t,150000,false,false,System.currentTimeMillis());
    assertTrue(MusicInterests.read(app).seeds.stream().anyMatch(x->x.url.equals(t.url)));
    store.preference("learn_listening",false);assertTrue(MusicInterests.read(app).seeds.isEmpty());
    store.resetTaste();assertEquals(0,store.taste().length());
  }
  @Test public void newNegativeFeedbackImmediatelyHidesSongsFromTheDayStableCache() {
    android.content.Context app=RuntimeEnvironment.getApplication();MixCatalogue c=new MixCatalogue(app);
    Track song=song("aaaaaaaaaaa","Home","Nova");MixCatalogue.Mix daily=MixCatalogue.find("daily","LK");
    c.save(daily,"LK",Arrays.asList(song));assertEquals(1,c.cached(daily,"LK").size());
    new SmartStore(app).feedback(song,"never");assertTrue(c.cached(daily,"LK").isEmpty());
  }

}
