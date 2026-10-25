package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Looper;
import androidx.media3.common.*;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConnectionRecoveryTest {
  boolean ready = true;
  int prepares;
  String id = "song";

  Player player() {
    return (Player)
        Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class[] {Player.class},
            (p, m, args) -> {
              switch (m.getName()) {
                case "getPlayWhenReady":
                  return ready;
                case "getCurrentMediaItem":
                  return new MediaItem.Builder().setMediaId(id).build();
                case "getCurrentPosition":
                  return 12000L;
                case "prepare":
                  prepares++;
                  return null;
                default:
                  Class<?> t = m.getReturnType();
                  return t == boolean.class
                      ? false
                      : t == long.class ? 0L : t == int.class ? 0 : null;
              }
            });
  }

  @Test
  public void cancellationNeverRestartsPausedMusicOrChangesQueue() {
    Context c = RuntimeEnvironment.getApplication();
    ConnectionRecovery r = new ConnectionRecovery(c, player());
    r.failed(
        new PlaybackException(
            "offline", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
    ready = false;
    r.cancel();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
    assertEquals(0, prepares);
    r.close();
  }

  @Test
  public void nonNetworkProviderErrorsDoNotScheduleRetries() {
    Context c = RuntimeEnvironment.getApplication();
    ConnectionRecovery r = new ConnectionRecovery(c, player());
    r.failed(
        new PlaybackException("denied", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS));
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
    assertEquals(0, prepares);
    r.close();
  }

  @Test
  public void aDifferentSongInvalidatesRecovery() {
    Context c = RuntimeEnvironment.getApplication();
    ConnectionRecovery r = new ConnectionRecovery(c, player());
    r.failed(
        new PlaybackException(
            "offline", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
    id = "different";
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
    assertEquals(0, prepares);
    r.close();
  }
}
