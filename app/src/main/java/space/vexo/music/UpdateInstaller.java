package space.vexo.music;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.os.Build;
import androidx.core.content.FileProvider;
import java.io.*;
import java.security.MessageDigest;
import okhttp3.*;

/**
 * Download only a signed release's exact bytes; share only the verified APK with Android's
 * installer.
 */
final class UpdateInstaller {
  static File prepare(Context context, AppRelease release) throws Exception {
    File directory = new File(context.getCacheDir(), "verified-updates");
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Update storage unavailable");
    File target = new File(directory, "VEXO-Music-" + release.code + ".apk");
    OkHttpClient client =
        OnlineMusic.HTTP
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(3, java.util.concurrent.TimeUnit.MINUTES)
            .build();
    if (!target.isFile() || !matches(target, release)) download(release, client, target);
    verifyArchive(context, target, release);
    return target;
  }

  static boolean matches(File file, AppRelease release) throws Exception {
    if (!release.authenticated || file.length() != release.size) return false;
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] b = new byte[16384];
      int n;
      while ((n = in.read(b)) != -1) digest.update(b, 0, n);
    }
    return release.sha256.equals(ReleaseSecurity.hex(digest.digest()));
  }

  static void download(AppRelease release, OkHttpClient client, File target) throws Exception {
    if (!release.authenticated
        || release.size < 1
        || release.size > ReleaseSecurity.MAX_APK
        || !release.sha256.matches("[a-f0-9]{64}"))
      throw new IOException("Update is not authenticated");
    HttpUrl url = HttpUrl.parse(release.url);
    if (url == null
        || !url.isHttps()
        || !ReleaseSecurity.HOST.equals(url.host())
        || url.port() != 443
        || !url.username().isEmpty()
        || !url.password().isEmpty()
        || url.query() != null
        || url.fragment() != null
        || !url.encodedPath().equals("/releases/VEXO-Music-" + release.version + ".apk"))
      throw new IOException("Untrusted update source");
    File temporary = File.createTempFile("download-", ".part", target.getParentFile());
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long total = 0;
      try (Response response =
          client.newCall(new Request.Builder().url(release.url).build()).execute()) {
        if (response.code() != 200
            || response.body() == null
            || !response.request().url().equals(url))
          throw new IOException("Update download was redirected or unavailable");
        long declared = response.body().contentLength();
        if (declared >= 0 && declared != release.size)
          throw new IOException("Update size differs from signed release");
        try (InputStream in = response.body().byteStream();
            FileOutputStream out = new FileOutputStream(temporary)) {
          byte[] b = new byte[16384];
          int n;
          while ((n = in.read(b)) != -1) {
            total += n;
            if (total > release.size) throw new IOException("Update exceeds signed size");
            out.write(b, 0, n);
            digest.update(b, 0, n);
          }
          out.getFD().sync();
        }
      }
      if (total != release.size || !release.sha256.equals(ReleaseSecurity.hex(digest.digest())))
        throw new IOException("Downloaded update failed integrity verification");
      if (!temporary.renameTo(target)) throw new IOException("Could not finish verified download");
    } finally {
      if (temporary.isFile()) temporary.delete();
    }
  }

  @SuppressWarnings("deprecation")
  static void verifyArchive(Context context, File file, AppRelease release) throws Exception {
    if (!matches(file, release)) throw new IOException("Verified update changed");
    int flags =
        Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
    PackageInfo info =
        context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags);
    if (info == null
        || !BuildConfig.APPLICATION_ID.equals(info.packageName)
        || (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode)
            != release.code
        || !release.version.equals(info.versionName)
        || info.applicationInfo == null
        || (info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
      throw new IOException("Update package or version does not match");
    android.content.pm.Signature[] signatures =
        Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
            ? info.signingInfo.getApkContentsSigners()
            : info.signatures;
    if (signatures == null
        || signatures.length != 1
        || !ReleaseSecurity.CERT.equals(
            ReleaseSecurity.hex(
                MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))))
      throw new IOException("Update signing identity does not match VEXO");
  }

  static void install(Context context, File file, AppRelease release) throws Exception {
    verifyArchive(context, file, release);
    android.net.Uri uri =
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".updates", file);
    Intent intent =
        new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }
}
