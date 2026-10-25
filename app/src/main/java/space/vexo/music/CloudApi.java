package space.vexo.music;

import android.content.Context;
import java.io.*;
import okhttp3.*;
import org.json.*;

/**
 * Only explicit music commands and room actions leave the device. Never uploads memories/history.
 */
final class CloudApi {
  static final String BASE = "https://vexo-music-cloud.kavindukavishka785.workers.dev";
  private final SmartStore store;
  private final OkHttpClient client;

  CloudApi(Context c) {
    store = new SmartStore(c);
    client =
        OnlineMusic.HTTP
            .newBuilder()
            .callTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .build();
  }

  private JSONObject call(String path, JSONObject body, boolean retry) throws Exception {
    String token = store.preferences().optString("cloud_device_token");
    if (!path.equals("/v1/device") && token.isEmpty()) {
      JSONObject registration = call("/v1/device", new JSONObject(), false);
      token = registration.getString("token");
      store.preference("cloud_device_token", token);
    }
    Request.Builder builder =
        new Request.Builder()
            .url(BASE + path)
            .post(
                RequestBody.create(
                    body.toString(), MediaType.parse("application/json; charset=utf-8")));
    if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
    try (Response r = client.newCall(builder.build()).execute()) {
      if (r.code() == 401 && retry) {
        store.preference("cloud_device_token", "");
        return call(path, body, false);
      }
      if (r.body() == null) throw new IOException("Cloud service returned no response");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (InputStream in = r.body().byteStream()) {
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) != -1) {
          if (out.size() + n > 32768) throw new IOException("Cloud response too large");
          out.write(b, 0, n);
        }
      }
      JSONObject result =
          new JSONObject(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
      if (!r.isSuccessful() || result.has("error"))
        throw new IOException(result.optString("error", "Cloud service unavailable. Try again."));
      return result;
    }
  }

  JSONObject plan(String command) throws Exception {
    JSONObject p = store.preferences(), prefs = new JSONObject();
    for (String key : new String[] {"genres", "language", "mood", "energy", "artists"})
      prefs.put(key, p.optString(key));
    return call(
        "/v1/mindradio",
        new JSONObject()
            .put("command", command)
            .put("preferences", prefs)
            .put("context", p.optString("cloud_context")),
        true);
  }

  JSONObject create(String name) throws Exception {
    return call("/v1/rooms/create", new JSONObject().put("name", name), true);
  }

  JSONObject join(String code, String name) throws Exception {
    String[] parts = code.trim().replace("VEXO-", "").split("[.\\s]+", -1);
    if (parts.length != 2) throw new IOException("Paste the full invite code from the host");
    return call(
        "/v1/rooms/join",
        new JSONObject().put("room", parts[0]).put("invite", parts[1]).put("name", name),
        true);
  }
}
