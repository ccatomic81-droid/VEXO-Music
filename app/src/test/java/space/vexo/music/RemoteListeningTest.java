package space.vexo.music;

import static org.junit.Assert.*;

import androidx.media3.common.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RemoteListeningTest {
  MediaItem current;
  long position;
  boolean ready;
  int sets, seeks;

  Player player() {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class[] {Player.class},
            (p, m, a) -> {
              switch (m.getName()) {
                case "getCurrentMediaItem":
                  return current;
                case "getCurrentPosition":
                  return position;
                case "setMediaItem":
                  current = (MediaItem) a[0];
                  position = a.length > 1 ? (long) a[1] : 0;
                  sets++;
                  return null;
                case "seekTo":
                  position = (long) a[0];
                  seeks++;
                  return null;
                case "setPlayWhenReady":
                  ready = (boolean) a[0];
                  return null;
                case "getPlayWhenReady":
                  return ready;
                case "pause":
                  ready = false;
                  return null;
                default:
                  Class<?> t = m.getReturnType();
                  if (t == boolean.class) return false;
                  if (t == long.class) return 0L;
                  if (t == int.class) return 0;
                  return null;
              }
            });
  }

  JSONObject frame(long pos, boolean playing) throws Exception {
    return new JSONObject()
        .put(
            "track",
            new Track("https://www.youtube.com/watch?v=fixture1234", "Song", "Artist", "", 180)
                .json())
        .put("position", pos)
        .put("playing", playing)
        .put("at", System.currentTimeMillis());
  }

  @Test
  public void guestSyncsTrackAndPositionAndRespectsFollowToggle() throws Exception {
    RemoteListening remote =
        new RemoteListening(RuntimeEnvironment.getApplication(), player(), () -> {});
    remote.apply(frame(45000, false), false);
    assertEquals(45000, position);
    assertFalse(ready);
    remote.apply(frame(80000, false), false);
    assertEquals(80000, position);
    assertEquals(1, seeks);
    remote.follow(false);
    remote.apply(frame(10000, true), false);
    assertEquals(80000, position);
    assertFalse(ready);
    remote.close();
  }

  @Test
  public void explicitFairAdvanceRestartsEvenTheSameTrack() throws Exception {
    RemoteListening remote =
        new RemoteListening(RuntimeEnvironment.getApplication(), player(), () -> {});
    remote.apply(frame(50000, false), false);
    remote.apply(frame(0, true), true);
    assertEquals(2, sets);
    assertTrue(ready);
    assertTrue(position < 1000);
    remote.close();
  }
}
