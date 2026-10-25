package space.vexo.music;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Device-only encrypted data. Never replace an unreadable vault or fall back to plaintext. */
final class SmartStore {
  interface Codec {
    String encode(String raw) throws Exception;

    String decode(String sealed) throws Exception;
  }

  private static final Object LOCK = new Object();
  private final android.content.SharedPreferences prefs;
  private final Codec codec;

  SmartStore(Context context) {
    this(context, new DeviceCodec());
  }

  SmartStore(Context context, Codec codec) {
    prefs = context.getSharedPreferences("smart_private_v1", Context.MODE_PRIVATE);
    this.codec = codec;
  }

  private static final class DeviceCodec implements Codec {
    private SecretKey key() throws Exception {
      KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
      ks.load(null);
      String alias = "vexo.smart.v1";
      if (!ks.containsAlias(alias)) {
        KeyGenerator generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(
            new KeyGenParameterSpec.Builder(
                    alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        generator.generateKey();
      }
      return (SecretKey) ks.getKey(alias, null);
    }

    public String encode(String raw) throws Exception {
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key());
      c.updateAAD("vexo.smart.v1".getBytes(StandardCharsets.UTF_8));
      return Base64.encodeToString(c.getIV(), Base64.NO_WRAP)
          + "."
          + Base64.encodeToString(c.doFinal(raw.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    public String decode(String sealed) throws Exception {
      String[] parts = sealed.split("\\.", -1);
      if (parts.length != 2) throw new java.security.GeneralSecurityException("Invalid vault");
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
      c.updateAAD("vexo.smart.v1".getBytes(StandardCharsets.UTF_8));
      return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
  }

  private JSONObject read(String bucket) {
    try {
      String sealed = prefs.getString("vault:" + bucket, "");
      if (sealed.isEmpty()) return new JSONObject();
      JSONObject envelope = new JSONObject(codec.decode(sealed));
      if (!bucket.equals(envelope.getString("bucket")))
        throw new java.security.GeneralSecurityException("Wrong vault bucket");
      return envelope.getJSONObject("data");
    } catch (Exception e) {
      throw new IllegalStateException(
          "Private music data is unavailable. Existing data was kept. Restart the app and try"
              + " again.",
          e);
    }
  }

  private void write(String bucket, JSONObject data) {
    try {
      if (!prefs
          .edit()
          .putString(
              "vault:" + bucket,
              codec.encode(new JSONObject().put("bucket", bucket).put("data", data).toString()))
          .commit()) throw new java.io.IOException("Storage full");
    } catch (Exception e) {
      throw new IllegalStateException(
          "Could not save private music data. Free storage and try again.", e);
    }
  }

  JSONObject preferences() {
    synchronized (LOCK) {
      JSONObject p = read("preferences").optJSONObject("preferences");
      return p == null ? new JSONObject() : p;
    }
  }

  void preference(String key, Object value) {
    synchronized (LOCK) {
      try {
        JSONObject d = read("preferences"), p = d.optJSONObject("preferences");
        if (p == null) p = new JSONObject();
        p.put(key, value);
        d.put("preferences", p);
        write("preferences", d);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  JSONArray events() {
    synchronized (LOCK) {
      JSONArray a = read("events").optJSONArray("events");
      return a == null ? new JSONArray() : a;
    }
  }

  void played(Track t, long when) {
    synchronized (LOCK) {
      try {
        JSONObject d = read("events");
        JSONObject settings = preferences();
        if (settings.optBoolean("pause_history", false)
            || when <= settings.optLong("history_cleared_at", -1)) return;
        JSONArray a = d.optJSONArray("events"), next = new JSONArray();
        next.put(new JSONObject().put("track", t.json()).put("at", when));
        if (a != null) for (int i = 0; i < Math.min(999, a.length()); i++) next.put(a.get(i));
        d.put("events", next);
        write("events", d);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  void clearHistory() {
    synchronized (LOCK) {
      preference("history_cleared_at", System.currentTimeMillis());
      JSONObject d = read("events");
      d.remove("events");
      write("events", d);
    }
  }

  JSONArray memories() {
    synchronized (LOCK) {
      JSONArray a = read("memories").optJSONArray("memories");
      return a == null ? new JSONArray() : a;
    }
  }

  void memory(Track t, String note, long moment, long when) {
    synchronized (LOCK) {
      try {
        JSONObject d = read("memories");
        JSONArray a = d.optJSONArray("memories"), next = new JSONArray();
        next.put(
            new JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("track", t.json())
                .put("note", note.trim().substring(0, Math.min(1000, note.trim().length())))
                .put("moment", Math.max(0, moment))
                .put("at", when));
        if (a != null) for (int i = 0; i < Math.min(199, a.length()); i++) next.put(a.get(i));
        d.put("memories", next);
        write("memories", d);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  void removeMemory(String id) {
    synchronized (LOCK) {
      try {
        JSONObject d = read("memories");
        JSONArray a = memories(), next = new JSONArray();
        for (int i = 0; i < a.length(); i++)
          if (!a.getJSONObject(i).optString("id").equals(id)) next.put(a.get(i));
        d.put("memories", next);
        write("memories", d);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  void feedback(Track track, String value) {
    synchronized (LOCK) {
      try {
        JSONObject d = read("feedback"), f = d.optJSONObject("feedback");
        if (f == null) f = new JSONObject();
        String key = RadioPolicy.songKey(track);
        f.remove(key);
        if (!value.equals("reset"))
          f.put(
              key,
              new JSONObject()
                  .put("value", value)
                  .put("track", track.json())
                  .put("at", System.currentTimeMillis()));
        while (f.length() > 500) f.remove(f.keys().next());
        d.put("feedback", f);
        write("feedback", d);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  JSONObject feedback() {
    synchronized (LOCK) {
      JSONObject a = read("feedback").optJSONObject("feedback");
      return a == null ? new JSONObject() : a;
    }
  }

  void snapshot(String name, List<Track> songs, String reason) {
    synchronized (LOCK) {
      try {
        JSONObject d = read("revisions");
        JSONArray old = d.optJSONArray("revisions"),
            next = new JSONArray(),
            tracks = new JSONArray();
        for (Track t : songs) tracks.put(t.json());
        next.put(
            new JSONObject()
                .put("name", name)
                .put("reason", reason)
                .put("at", System.currentTimeMillis())
                .put("tracks", tracks));
        int perName = 1;
        if (old != null)
          for (int i = 0; i < old.length() && next.length() < 100; i++) {
            JSONObject r = old.getJSONObject(i);
            if (r.optString("name").equals(name) && ++perName > 20) continue;
            next.put(r);
          }
        d.put("revisions", next);
        write("revisions", d);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
    }
  }

  JSONArray revisions() {
    synchronized (LOCK) {
      JSONArray a = read("revisions").optJSONArray("revisions");
      return a == null ? new JSONArray() : a;
    }
  }

  static List<Track> tracks(JSONArray array) {
    List<Track> songs = new ArrayList<>();
    if (array != null)
      for (int i = 0; i < array.length(); i++) {
        JSONObject e = array.optJSONObject(i);
        if (e != null) songs.add(Track.from(e));
      }
    return songs;
  }
}
