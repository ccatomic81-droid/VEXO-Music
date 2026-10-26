package space.vexo.music;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/**
 * Encrypted legacy collections. Existing plaintext is removed only after a verified durable write.
 */
final class PrivateStore {
  private static final Object LOCK = new Object();
  private final SharedPreferences legacy, vault;
  private final SmartStore.Codec codec;
  private final String name;
  private String cachedSeal;
  private JSONObject cached;

  PrivateStore(Context context, String name) {
    this(
        context.getSharedPreferences(name, 0),
        context.getSharedPreferences("private_collections_v1", 0),
        name,
        new SmartStore.DeviceCodec());
  }

  PrivateStore(
      SharedPreferences legacy, SharedPreferences vault, String name, SmartStore.Codec codec) {
    this.legacy = legacy;
    this.vault = vault;
    this.name = name;
    this.codec = codec;
  }

  private JSONObject unseal(String sealed) throws Exception {
    JSONObject envelope = new JSONObject(codec.decode(sealed));
    if (!name.equals(envelope.getString("collection")))
      throw new SecurityException("Wrong collection");
    return envelope.getJSONObject("data");
  }

  private void write(JSONObject value) throws Exception {
    String sealed =
        codec.encode(new JSONObject().put("collection", name).put("data", value).toString());
    JSONObject check = unseal(sealed);
    if (!check.toString().equals(value.toString()))
      throw new SecurityException("Migration verification failed");
    if (!vault.edit().putString(name, sealed).commit())
      throw new java.io.IOException("Storage unavailable");
    // Read the actual committed value before deleting the old collection.
    cached = unseal(vault.getString(name, ""));
    cachedSeal = sealed;
    if (!legacy.getAll().isEmpty()) legacy.edit().clear().commit();
  }

  private JSONObject read() {
    String sealed = vault.getString(name, "");
    if (!sealed.isEmpty()) {
      try {
        if (!sealed.equals(cachedSeal)) {
          cached = unseal(sealed);
          cachedSeal = sealed;
        }
        if (!legacy.getAll().isEmpty()) legacy.edit().clear().commit();
        return cached;
      } catch (Exception e) {
        throw new IllegalStateException(
            "Private library data is unavailable. Existing data was kept.", e);
      }
    }
    JSONObject original = new JSONObject();
    try {
      for (Map.Entry<String, ?> e : legacy.getAll().entrySet()) {
        Object v = e.getValue();
        if (v instanceof String || v instanceof Boolean || v instanceof Number)
          original.put(e.getKey(), v);
        else throw new IllegalStateException("Unsupported legacy data. Existing data was kept.");
      }
      try {
        write(original);
      } catch (Exception unavailable) {
        // Read-only legacy recovery: no new plaintext writes, no deletion on failed migration.
      }
      return original;
    } catch (JSONException e) {
      throw new IllegalStateException("Could not read existing library", e);
    }
  }

  String getString(String key, String fallback) {
    synchronized (LOCK) {
      return read().optString(key, fallback);
    }
  }

  long getLong(String key, long fallback) {
    synchronized (LOCK) {
      return read().optLong(key, fallback);
    }
  }

  boolean contains(String key) {
    synchronized (LOCK) {
      return read().has(key);
    }
  }

  Editor edit() {
    return new Editor();
  }

  final class Editor {
    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean clear;

    Editor putString(String key, String value) {
      values.put(key, value);
      return this;
    }

    Editor putLong(String key, long value) {
      values.put(key, value);
      return this;
    }

    Editor remove(String key) {
      values.put(key, null);
      return this;
    }

    Editor clear() {
      clear = true;
      return this;
    }

    boolean commit() {
      synchronized (LOCK) {
        try {
          JSONObject data = clear ? new JSONObject() : new JSONObject(read().toString());
          for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getValue() == null) data.remove(e.getKey());
            else data.put(e.getKey(), e.getValue());
          }
          write(data);
          return true;
        } catch (Exception e) {
          return false;
        }
      }
    }

    void apply() {
      if (!commit())
        throw new IllegalStateException("Could not save private data. Existing data was kept.");
    }
  }
}
