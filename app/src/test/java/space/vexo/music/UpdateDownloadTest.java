package space.vexo.music;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.View;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import okhttp3.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h568dp-mdpi")
public class UpdateDownloadTest {
  AppRelease release(byte[] bytes) throws Exception {
    AppRelease r =
        new AppRelease(
            37,
            "0.25.1",
            "Update",
            "https://" + ReleaseSecurity.HOST + "/releases/VEXO-Music-0.25.1.apk");
    r.authenticated = true;
    r.size = bytes.length;
    r.sha256 = ReleaseSecurity.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    return r;
  }

  OkHttpClient source(byte[] bytes) {
    return new OkHttpClient.Builder()
        .addInterceptor(
            chain ->
                new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("fixture")
                    .body(
                        ResponseBody.create(
                            bytes, MediaType.get("application/vnd.android.package-archive")))
                    .build())
        .build();
  }

  @Test
  public void realBytesDriveProgressBeforeIntegrityVerification() throws Exception {
    byte[] bytes = new byte[70_000];
    new Random(7).nextBytes(bytes);
    AppRelease r = release(bytes);
    List<Long> values = new ArrayList<>();
    List<Boolean> phases = new ArrayList<>();
    UpdateInstaller.Transfer transfer =
        new UpdateInstaller.Transfer(
            (done, total, verifying) -> {
              assertEquals(bytes.length, total);
              assertTrue(done >= 0 && done <= total);
              values.add(done);
              phases.add(verifying);
            });
    File target = new File(Files.createTempDirectory("vexo-update").toFile(), "app.apk");
    UpdateInstaller.download(r, source(bytes), target, transfer);
    assertEquals(Long.valueOf(0), values.get(0));
    assertFalse(phases.get(0));
    assertEquals(Long.valueOf(bytes.length), values.get(values.size() - 1));
    assertTrue(phases.get(phases.size() - 1));
    assertArrayEquals(bytes, Files.readAllBytes(target.toPath()));
    assertTrue(UpdateInstaller.matches(target, r));
    assertEquals(1, target.getParentFile().list().length);
  }

  @Test
  public void cancellingTransferRemovesPartialAndPreservesExistingFile() throws Exception {
    byte[] bytes = new byte[70_000];
    AppRelease r = release(bytes);
    File target = new File(Files.createTempDirectory("vexo-cancel").toFile(), "app.apk");
    byte[] original = {1, 2, 3};
    Files.write(target.toPath(), original);
    UpdateInstaller.Transfer[] transfer = new UpdateInstaller.Transfer[1];
    transfer[0] =
        new UpdateInstaller.Transfer(
            (done, total, verifying) -> {
              if (done > 0) transfer[0].cancel();
            });
    assertThrows(
        IOException.class, () -> UpdateInstaller.download(r, source(bytes), target, transfer[0]));
    assertArrayEquals(original, Files.readAllBytes(target.toPath()));
    assertEquals(1, target.getParentFile().list().length);
    List<Long> retry = new ArrayList<>();
    UpdateInstaller.download(
        r,
        source(bytes),
        target,
        new UpdateInstaller.Transfer((done, total, verifying) -> retry.add(done)));
    assertTrue(UpdateInstaller.matches(target, r));
    assertEquals(Long.valueOf(0), retry.get(0));
  }

  @Test
  public void failedVerificationNeverLeavesAnInstallableDownload() throws Exception {
    byte[] bytes = new byte[32_000];
    AppRelease r = release(bytes);
    r.sha256 = "a".repeat(64);
    File target = new File(Files.createTempDirectory("vexo-corrupt").toFile(), "app.apk");
    assertThrows(
        IOException.class,
        () ->
            UpdateInstaller.download(r, source(bytes), target, new UpdateInstaller.Transfer(null)));
    assertFalse(target.exists());
    assertEquals(0, target.getParentFile().list().length);
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void downloadAndVerificationAreDistinctAndCancelStaysReachable() throws Exception {
    boolean[] cancelled = {false};
    UpdateProgressPanel panel =
        new UpdateProgressPanel(
            RuntimeEnvironment.getApplication(), "0.25.1", () -> cancelled[0] = true);
    panel.progress(5 * 1048576L, 10 * 1048576L, false);
    assertEquals("50% downloaded", panel.percent.getText().toString());
    assertEquals("5.0 MB of 10.0 MB", panel.amount.getText().toString());
    capture(panel, "update-download-320");
    panel.progress(10 * 1048576L, 10 * 1048576L, true);
    assertEquals("Verifying update…", panel.status.getText().toString());
    assertFalse(panel.percent.getText().toString().contains("ready"));
    capture(panel, "update-verifying-320");
    View cancel = panel.cancel;
    assertTrue(cancel.getBottom() <= 568);
    cancel.performClick();
    assertTrue(cancelled[0]);
  }

  void capture(UpdateProgressPanel panel, String name) throws Exception {
    panel.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(568, View.MeasureSpec.EXACTLY));
    panel.layout(0, 0, 320, 568);
    Bitmap bitmap = Bitmap.createBitmap(320, 568, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Visuals.BG);
    panel.draw(canvas);
    File directory = new File("build/ui-checks");
    directory.mkdirs();
    try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
    }
    bitmap.recycle();
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void largeTextKeepsCancelVisibleWhileDetailsScroll() throws Exception {
    android.content.Context app = RuntimeEnvironment.getApplication();
    android.content.res.Configuration config =
        new android.content.res.Configuration(app.getResources().getConfiguration());
    config.fontScale = 1.6f;
    UpdateProgressPanel panel =
        new UpdateProgressPanel(app.createConfigurationContext(config), "0.25.1", () -> {});
    panel.progress(5 * 1048576L, 10 * 1048576L, false);
    capture(panel, "update-download-large-320");
    assertTrue(panel.cancel.getBottom() <= 568);
    assertTrue(panel.cancel.getTop() >= 0);
  }
}
