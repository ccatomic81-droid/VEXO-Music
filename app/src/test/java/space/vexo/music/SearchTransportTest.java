package space.vexo.music;

import static org.junit.Assert.*;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.junit.Test;

public class SearchTransportTest {
  @Test
  public void cancellingAnOldSearchCancelsItsCallAndReleasesItsWorker() throws Exception {
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    OkHttpClient client =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  started.countDown();
                  try {
                    release.await(3, TimeUnit.SECONDS);
                  } catch (InterruptedException error) {
                    throw new IOException(error);
                  }
                  return new Response.Builder()
                      .request(chain.request())
                      .protocol(Protocol.HTTP_1_1)
                      .code(200)
                      .message("fixture")
                      .body(ResponseBody.create("{}", MediaType.get("application/json")))
                      .build();
                })
            .build();
    Call call = client.newCall(new Request.Builder().url("https://music.example/search").build());
    Thread worker =
        new Thread(
            () -> {
              try (Response ignored = OnlineMusic.executeInterruptibly(call)) {
              } catch (Throwable error) {
                failure.set(error);
              }
            });
    try {
      worker.start();
      assertTrue(started.await(3, TimeUnit.SECONDS));
      worker.interrupt();
      worker.join(1000);
      assertFalse("Cancelled search must release the worker immediately", worker.isAlive());
      assertTrue(call.isCanceled());
      assertTrue(failure.get() instanceof InterruptedIOException);
    } finally {
      release.countDown();
      worker.join(1000);
    }
  }
}
