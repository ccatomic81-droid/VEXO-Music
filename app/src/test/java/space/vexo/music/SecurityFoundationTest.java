package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SecurityFoundationTest {
  @Test
  public void legacyMigrationPreservesCollectionAndRemovesVerifiedPlaintext() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    SharedPreferences old = c.getSharedPreferences("legacy_fixture", 0),
        vault = c.getSharedPreferences("secure_fixture", 0);
    old.edit()
        .putString("playlist:Private name", "[\"private song\"]")
        .putLong("photo_version", 123)
        .commit();
    PrivateStore store = new PrivateStore(old, vault, "library", new SmartStore.DeviceCodec());
    assertEquals("[\"private song\"]", store.getString("playlist:Private name", ""));
    assertTrue(old.getAll().isEmpty());
    assertFalse(vault.getString("library", "").contains("private song"));
    PrivateStore reload = new PrivateStore(old, vault, "library", new SmartStore.DeviceCodec());
    assertEquals(123, reload.getLong("photo_version", 0));
    reload.edit().putString("favourites", "kept").apply();
    assertEquals("kept", store.getString("favourites", ""));
    vault.edit().putString("library", "damaged").commit();
    old.edit().putString("favourites", "stale plaintext").commit();
    assertThrows(IllegalStateException.class, () -> reload.getString("favourites", ""));
    assertEquals("stale plaintext", old.getString("favourites", ""));
  }

  @Test
  public void migrationFailureKeepsOldDataReadableButRejectsPlaintextWrites() {
    Context c = RuntimeEnvironment.getApplication();
    SharedPreferences old = c.getSharedPreferences("old_failure", 0),
        vault = c.getSharedPreferences("vault_failure", 0);
    old.edit().putString("name", "Existing listener").commit();
    SmartStore.Codec unavailable =
        new SmartStore.Codec() {
          public String encode(String v) throws Exception {
            throw new IOException();
          }

          public String decode(String v) throws Exception {
            throw new IOException();
          }
        };
    PrivateStore store = new PrivateStore(old, vault, "profile", unavailable);
    assertEquals("Existing listener", store.getString("name", ""));
    assertFalse(store.edit().putString("name", "Replacement").commit());
    assertEquals("Existing listener", old.getString("name", ""));
    assertTrue(vault.getAll().isEmpty());
  }

  JSONObject metadata() throws Exception {
    return new JSONObject()
        .put("applicationId", BuildConfig.APPLICATION_ID)
        .put("versionCode", BuildConfig.VERSION_CODE + 1)
        .put("versionName", "0.14.0")
        .put("summary", "Verified update")
        .put("downloadUrl", "https://" + ReleaseSecurity.HOST + "/releases/VEXO-Music-0.14.0.apk")
        .put("size", 64)
        .put("sha256", "a".repeat(64))
        .put("certificateSha256", ReleaseSecurity.CERT)
        .put("sourceCommit", "b".repeat(40));
  }

  JSONObject signed(JSONObject m, KeyPair key) throws Exception {
    JSONObject payload = new JSONObject(m.toString()).put("format", "vexo-release-v1");
    byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
    Signature sig = Signature.getInstance("SHA256withRSA");
    sig.initSign(key.getPrivate());
    sig.update(bytes);
    return new JSONObject(m.toString())
        .put("signedPayload", Base64.getEncoder().encodeToString(bytes))
        .put("releaseSignature", Base64.getEncoder().encodeToString(sig.sign()));
  }

  @Test
  public void signedUpdatesRejectTamperingWrongKeysUntrustedUrlsAndUnsignedNewVersions()
      throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair key = generator.generateKeyPair();
    JSONObject m = signed(metadata(), key);
    assertTrue(ReleaseSecurity.verify(m, BuildConfig.VERSION_CODE, key.getPublic()));
    for (String field : ReleaseSecurity.FIELDS) {
      JSONObject altered = new JSONObject(m.toString());
      altered.put(field, field.equals("summary") ? "Altered release" : "changed");
      assertThrows(
          IOException.class,
          () -> ReleaseSecurity.verify(altered, BuildConfig.VERSION_CODE, key.getPublic()));
    }
    KeyPair other = generator.generateKeyPair();
    assertThrows(
        IOException.class,
        () -> ReleaseSecurity.verify(m, BuildConfig.VERSION_CODE, other.getPublic()));
    assertThrows(
        IOException.class,
        () -> ReleaseSecurity.verify(metadata(), BuildConfig.VERSION_CODE, key.getPublic()));
    for (String url :
        new String[] {
          "http://" + ReleaseSecurity.HOST + "/releases/VEXO-Music-0.14.0.apk",
          "https://evil.test/app.apk",
          "https://" + ReleaseSecurity.HOST + "/releases/other.apk",
          "https://" + ReleaseSecurity.HOST + "/releases/VEXO-Music-0.14.0.apk?next=evil"
        }) {
      JSONObject unsafe = signed(metadata().put("downloadUrl", url), key);
      assertThrows(
          IOException.class,
          () -> ReleaseSecurity.verify(unsafe, BuildConfig.VERSION_CODE, key.getPublic()));
    }
    assertFalse(
        ReleaseSecurity.verify(
            new JSONObject().put("versionCode", 14), BuildConfig.VERSION_CODE, key.getPublic()));
    assertThrows(IOException.class, () -> AppRelease.parse(metadata().toString()));
  }

  @Test
  public void externalControllersKeepTransportButCannotInjectMedia() {
    androidx.media3.common.Player.Commands commands = ControllerAccess.playbackCommands();
    assertTrue(commands.contains(androidx.media3.common.Player.COMMAND_PLAY_PAUSE));
    assertTrue(commands.contains(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT));
    assertFalse(commands.contains(androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS));
    assertFalse(commands.contains(androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM));
    assertFalse(ControllerAccess.app(2000, 1000));
    assertTrue(ControllerAccess.app(1000, 1000));
  }

  @Test
  public void downloadRejectsCorruptionAndDeletesIncompleteFiles() throws Exception {
    byte[] bytes = "verified bytes".getBytes(StandardCharsets.UTF_8);
    AppRelease r =
        new AppRelease(
            16,
            "0.14.0",
            "Update",
            "https://" + ReleaseSecurity.HOST + "/releases/VEXO-Music-0.14.0.apk");
    r.authenticated = true;
    r.size = bytes.length;
    r.sha256 = ReleaseSecurity.hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    File directory = new File(RuntimeEnvironment.getApplication().getCacheDir(), "download-test");
    directory.mkdirs();
    File target = new File(directory, "candidate.apk");
    OkHttpClient good =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain ->
                    new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("ok")
                        .body(ResponseBody.create(bytes, MediaType.get("application/octet-stream")))
                        .build())
            .build();
    UpdateInstaller.download(r, good, target);
    assertTrue(UpdateInstaller.matches(target, r));
    r.sha256 = "0".repeat(64);
    assertThrows(IOException.class, () -> UpdateInstaller.download(r, good, target));
    assertFalse(UpdateInstaller.matches(target, r));
    assertEquals(
        1, directory.listFiles().length); // Only the old completed file remains; no .part file.
    OkHttpClient redirect =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain ->
                    new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("redirect")
                        .body(ResponseBody.create("", MediaType.get("text/plain")))
                        .header("Location", "https://evil.test/app.apk")
                        .build())
            .build();
    assertThrows(IOException.class, () -> UpdateInstaller.download(r, redirect, target));
    r.authenticated = false;
    assertThrows(IOException.class, () -> UpdateInstaller.download(r, good, target));
  }
}
