package space.vexo.music;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class MixQueueTest {
  Track song(String id,String title,String artist){return new Track("https://www.youtube.com/watch?v="+id,title,artist,"",200);}
  @Test public void refillsKeepManualQueueAndRejectAlternateUploadsWhileSpreadingArtists(){
    Track current=song("a","Home","Nova"),manual=song("b","Sky","Echo");
    List<Track> queue=new ArrayList<>(Arrays.asList(current,manual));
    List<Track> out=MixQueue.select(Arrays.asList(song("z","Home (Official Audio)","Nova - Topic"),song("c","Night","Echo"),song("d","Ocean","Orion"),manual),queue,Collections.emptyList(),10);
    assertEquals(Arrays.asList(current,manual),queue);assertEquals(2,out.size());assertEquals("Ocean",out.get(0).title);
  }
  @Test public void capacityIsRespectedAndRefillsCanContinueAfterOldQueueHistoryIsPruned(){
    List<Track> full=new ArrayList<>();for(int i=0;i<100;i++)full.add(song("q"+i,"Song "+i,"Artist"+i));
    List<Track> candidates=Arrays.asList(song("next","Next","Orion"));
    assertTrue(MixQueue.select(candidates,full,Collections.emptyList(),10).isEmpty());
    full.subList(0,60).clear();assertEquals(1,MixQueue.select(candidates,full,Collections.emptyList(),10).size());
  }
}
