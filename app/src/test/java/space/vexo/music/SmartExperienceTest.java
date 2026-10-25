package space.vexo.music;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class SmartExperienceTest {
  Context context;SmartStore smart;LibraryStore library;
  Track a=new Track("https://www.youtube.com/watch?v=a","සිංහල Adaraya","Artist A","",180);
  Track b=new Track("https://www.youtube.com/watch?v=b","தமிழ் பாடல்","Artist B","",180);
  Track c=new Track("https://www.youtube.com/watch?v=c","Forgotten dream","Artist C","",180);
  @Before public void setup(){context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("smart_private_v1",0).edit().clear().commit();context.getSharedPreferences("library",0).edit().clear().commit();smart=new SmartStore(context);library=new LibraryStore(context);}
  @Test public void memoriesHistoryAndPreferencesAreEncryptedAndPersist() throws Exception {
    smart.memory(a,"private-secret-note",12000,100);smart.played(a,100);smart.preference("genres","private-genre");
    String raw=context.getSharedPreferences("smart_private_v1",0).getString("vault","");
    assertFalse(raw.contains("private"));assertFalse(raw.contains(a.title));assertEquals(1,new SmartStore(context).events().length());assertEquals("private-secret-note",new SmartStore(context).memories().getJSONObject(0).getString("note"));
  }
  @Test public void tamperedVaultNeverOverwritesExistingData(){smart.preference("mood","calm");String raw=context.getSharedPreferences("smart_private_v1",0).getString("vault","");String bad=raw.substring(0,raw.length()-5)+"AAAAA";context.getSharedPreferences("smart_private_v1",0).edit().putString("vault",bad).commit();try{smart.preference("mood","happy");fail();}catch(IllegalStateException expected){}assertEquals(bad,context.getSharedPreferences("smart_private_v1",0).getString("vault",""));}
  @Test public void pausedHistoryDoesNotRecordAndClearingDoesNotDeleteMemories(){smart.memory(a,"kept",0,10);smart.preference("pause_history",true);smart.played(a,20);assertEquals(0,smart.events().length());smart.preference("pause_history",false);smart.played(a,20);smart.clearHistory();assertEquals(0,smart.events().length());assertEquals(1,smart.memories().length());}
  @Test public void historySearchSupportsDatesScriptsSpellingAndAliases(){smart.played(a,100);smart.played(b,200);smart.played(c,300);assertEquals(1,SmartPolicy.find(smart.events(),"sinhala",0,150).size());assertEquals(1,SmartPolicy.find(smart.events(),"பாடல்",150,250).size());assertEquals(c.url,SmartPolicy.find(smart.events(),"forgoten",0,400).get(0).url);assertTrue(SmartPolicy.find(smart.events(),"dream",0,250).isEmpty());}
  @Test public void antiRepeatExpiresAndNeverFeedbackDoesNot(){long now=System.currentTimeMillis();smart.played(a,now);smart.played(b,now-25*3600000L);smart.feedback(c,"never");List<Track> picks=SmartPolicy.rank(Arrays.asList(a,b,c),smart,Collections.emptyList(),false);assertEquals(1,picks.size());assertEquals(b.url,picks.get(0).url);smart.preference("repeat_hours",0);assertEquals(2,SmartPolicy.rank(Arrays.asList(a,b,c),smart,Collections.emptyList(),false).size());}
  @Test public void feedbackReordersFutureRecommendations(){smart.preference("repeat_hours",0);smart.feedback(c,"more");assertEquals(c.url,SmartPolicy.rank(Arrays.asList(a,c),smart,Collections.emptyList(),false).get(0).url);smart.feedback(c,"never");assertEquals(1,SmartPolicy.rank(Arrays.asList(a,c),smart,Collections.emptyList(),false).size());smart.feedback(c,"reset");assertEquals(2,SmartPolicy.rank(Arrays.asList(a,c),smart,Collections.emptyList(),false).size());}
  @Test public void playlistDeletionAndEditsRemainRecoverable() throws Exception {library.createPlaylist("Keep");library.addToPlaylist("Keep",a);library.addToPlaylist("Keep",b);library.deletePlaylist("Keep");assertTrue(library.playlists().isEmpty());JSONObject deleted=smart.revisions().getJSONObject(0);assertEquals(2,deleted.getJSONArray("tracks").length());String recovered=library.savePlaylistCopy("Keep recovered",SmartStore.tracks(deleted.getJSONArray("tracks")));assertEquals(2,library.tracks("playlist:"+recovered).size());assertTrue(library.tracks("favourites").isEmpty());}
  @Test public void failedVaultBlocksDestructivePlaylistEdit(){library.createPlaylist("Keep");library.addToPlaylist("Keep",a);context.getSharedPreferences("smart_private_v1",0).edit().putString("vault","broken").commit();try{library.deletePlaylist("Keep");fail();}catch(IllegalStateException expected){}assertTrue(library.playlists().contains("Keep"));assertEquals(1,library.tracks("playlist:Keep").size());}
  @Test public void renameCheckpointPreservesOldNameAndSongs() throws Exception {library.createPlaylist("Before");library.addToPlaylist("Before",a);assertTrue(library.renamePlaylist("Before","After"));assertEquals("Before",smart.revisions().getJSONObject(0).getString("name"));assertEquals(1,library.tracks("playlist:After").size());}
  @Test public void groupRotationIsFairAndDeduplicatesSharedRequests(){FairPlayPolicy.add(smart,"Ann",a);FairPlayPolicy.add(smart,"Ann",c);FairPlayPolicy.add(smart,"Bob",b);FairPlayPolicy.add(smart,"Bob",a);List<Track> out=FairPlayPolicy.rotate(FairPlayPolicy.requests(smart));assertEquals(Arrays.asList(a,b,c),out);}
  @Test public void homeOrderRepairsUnknownAndDuplicateModules() throws Exception {JSONObject p=new JSONObject().put("home_order",new JSONArray(Arrays.asList("Regional music","unknown","Regional music")));List<String> order=SmartExperiencePanel.order(p);assertEquals(5,order.size());assertEquals("Regional music",order.get(0));}
  @Test public void shuffleKeepsLocalAudioAndHonoursRestrictions(){Track local=new Track("content://media/1","My audio","Local artist","",180);smart.played(a,System.currentTimeMillis());List<Track> shuffled=SmartPolicy.shuffle(Arrays.asList(a,b,local),smart);assertEquals(2,shuffled.size());assertTrue(shuffled.contains(local));assertFalse(shuffled.contains(a));}
}
