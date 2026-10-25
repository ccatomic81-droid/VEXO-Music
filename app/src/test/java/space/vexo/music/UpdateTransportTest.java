package space.vexo.music;

import static org.junit.Assert.*;

import java.io.IOException;
import okhttp3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UpdateTransportTest {
  UpdateChecker checker(int code, String body, String redirect) throws Exception {
    OkHttpClient client =
        new OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(
                chain -> {
                  Response.Builder r =
                      new Response.Builder()
                          .request(chain.request())
                          .protocol(Protocol.HTTP_1_1)
                          .code(code)
                          .message("fixture")
                          .body(ResponseBody.create(body, MediaType.get("application/json")));
                  if (redirect != null) r.header("Location", redirect);
                  return r.build();
                })
            .build();
    UpdateChecker checker = new UpdateChecker(RuntimeEnvironment.getApplication(), client);
    return checker;
  }

  @Test
  public void parsesBoundedFeedAndRejectsFailedOrOversizedResponses() throws Exception {
    String json =
        new AppReleaseTest().json(9, BuildConfig.APPLICATION_ID, "https://example.com/update.apk");
    assertEquals(9, checker(200, json, null).fetch().code);
    assertThrows(IOException.class, () -> checker(404, json, null).fetch());
    assertThrows(IOException.class, () -> checker(200, " ".repeat(66000), null).fetch());
    assertThrows(IOException.class, () -> checker(200, "broken", null).fetch());
  }

  @Test
  public void blocksHttpsDowngradeAndRedirectLoops() throws Exception {
    assertThrows(
        IOException.class, () -> checker(302, "", "http://example.com/unsafe.json").fetch());
    assertThrows(
        IOException.class, () -> checker(302, "", "https://example.com/again.json").fetch());
  }
}
