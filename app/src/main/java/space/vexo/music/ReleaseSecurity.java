package space.vexo.music;

import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import okhttp3.HttpUrl;
import org.json.*;

/** Authenticate future releases with the original app signing key, independently of the host. */
final class ReleaseSecurity {
  static final String CERT = "0b31c50904567389b723e23ad6043b2d2fc6635c30dfdb3ed5ef3f9ba9727ef3";
  static final String HOST = "vexo-music-updates.kavindukavishka785.workers.dev";
  static final String PUBLIC_KEY =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqX7/c7897t7vrkC6OoZFOsx0kkrHTQ2AXQzrAp6mHDl2VH2jMimZdQ/NGKCpE6GhXbdyLl2aRVIxvboFYpuTb/CSOo27z5wG1NB77c1oUiNsMFbTUYZ0yZD4MBCHjJOP87Ab9reMel171abK7D1xnQtjqCghfUMNiU/5fkzl6jxWmM4TBx8DfYnREeZk1wVcnD50V52A5QJhWx65tW1vfcqhOEB5/G7qbbsAcUIL/RGi50dTebOjEDlvcfKGlY2ij2OPKMwtggfZqaNizRgSokQIRiR0sFxS8A+Ehh0cOoLaFOSR5bSL4SlKoUWmpFgX1x8+RSaHUn58nijL45Dr3QIDAQAB";
  static final long MAX_APK = 100L * 1024 * 1024;
  static final String[] FIELDS = {
    "applicationId",
    "versionCode",
    "versionName",
    "summary",
    "downloadUrl",
    "size",
    "sha256",
    "certificateSha256",
    "sourceCommit"
  };

  static PublicKey key() throws Exception {
    return KeyFactory.getInstance("RSA")
        .generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.NO_WRAP)));
  }

  static void address(JSONObject o) throws Exception {
    HttpUrl u = HttpUrl.parse(o.getString("downloadUrl"));
    String version = o.getString("versionName");
    if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-dev)?")
        || u == null
        || !u.isHttps()
        || !HOST.equals(u.host())
        || u.port() != 443
        || !u.username().isEmpty()
        || !u.password().isEmpty()
        || u.query() != null
        || u.fragment() != null
        || !u.encodedPath().equals("/releases/VEXO-Music-" + version + ".apk"))
      throw new IOException("Untrusted update download address");
    if (!o.getString("sha256").matches("[a-f0-9]{64}")
        || !CERT.equals(o.getString("certificateSha256"))
        || !o.getString("sourceCommit").matches("[a-f0-9]{40}")
        || o.getLong("size") < 1
        || o.getLong("size") > MAX_APK)
      throw new IOException("Incomplete verified update metadata");
  }

  static boolean verify(JSONObject o, int installed) throws IOException {
    try {
      return verify(o, installed, key());
    } catch (Exception e) {
      if (e instanceof IOException) throw (IOException) e;
      throw new IOException("Could not verify update identity", e);
    }
  }

  static boolean verify(JSONObject o, int installed, PublicKey key) throws IOException {
    try {
      boolean signed = o.has("signedPayload") || o.has("releaseSignature");
      if (!signed && o.getInt("versionCode") <= installed)
        return false; // Legacy current/older feed is never installable.
      if (!signed) throw new IOException("This newer update has no trusted release signature");
      address(o);
      String raw = o.getString("signedPayload"), sig = o.getString("releaseSignature");
      if (raw.length() > 50000 || sig.length() > 1500)
        throw new IOException("Update signature payload too large");
      byte[] bytes = Base64.decode(raw, Base64.NO_WRAP);
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key);
      verifier.update(bytes);
      if (!verifier.verify(Base64.decode(sig, Base64.NO_WRAP)))
        throw new IOException("Update signature does not match VEXO");
      JSONObject payload = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
      if (!"vexo-release-v1".equals(payload.getString("format")))
        throw new IOException("Unknown release signature format");
      for (String field : FIELDS)
        if (!String.valueOf(o.get(field)).equals(String.valueOf(payload.get(field))))
          throw new IOException("Update metadata was changed after signing");
      return true;
    } catch (Exception e) {
      if (e instanceof IOException) throw (IOException) e;
      throw new IOException("Could not authenticate this update", e);
    }
  }

  static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }
}
