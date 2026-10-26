package space.vexo.music;

import java.io.IOException;
import okhttp3.HttpUrl;
import org.json.*;

/** A release feed contains metadata only. Downloads always need a user tap. */
final class AppRelease {
  static final String NOTES =
      "• Full-height YouTube and settings panels remove unused top and bottom space.\n"
          + "• A fixed YouTube header keeps Close reachable while scrolling long music lists.\n"
          + "• System bars, display cutouts and the keyboard get one safe-area inset.\n"
          + "• Spotify green with black, white and neutral grey replaces the violet theme.\n"
          + "• Existing music, YouTube connection, artwork caching and playlists are preserved.";
  final int code;
  final String version, summary, url;
  String sha256 = "";
  long size;
  boolean authenticated;

  AppRelease(int code, String version, String summary, String url) {
    this.code = code;
    this.version = version;
    this.summary = summary;
    this.url = url;
  }

  static String https(String raw) throws IOException {
    HttpUrl url = HttpUrl.parse(raw == null ? "" : raw);
    if (url == null || !url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty())
      throw new IOException("Use a public HTTPS update address without a password.");
    return url.toString();
  }

  static AppRelease parse(String json) throws IOException {
    try {
      JSONObject o = new JSONObject(json);
      if (!BuildConfig.APPLICATION_ID.equals(o.getString("applicationId")))
        throw new IOException("This update belongs to a different app.");
      int code = o.getInt("versionCode");
      String version = o.getString("versionName").trim();
      String summary = o.getString("summary").trim();
      String url = https(o.getString("downloadUrl"));
      if (code < 1
          || version.isEmpty()
          || version.length() > 50
          || summary.isEmpty()
          || summary.length() > 12000)
        throw new IOException("The update information is incomplete.");
      AppRelease release = new AppRelease(code, version, summary, url);
      release.authenticated = ReleaseSecurity.verify(o, BuildConfig.VERSION_CODE);
      release.sha256 = o.optString("sha256");
      release.size = o.optLong("size");
      return release;
    } catch (JSONException e) {
      throw new IOException("The update information could not be read.", e);
    }
  }

  boolean newerThan(int installedCode) {
    return code > installedCode;
  }
}
