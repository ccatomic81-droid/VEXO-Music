package space.vexo.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class LibrarySync {
  private static final int LIMIT = 1048576;
  static final String URL = "https://vjkogwihjvlonnyedflq.supabase.co/rest/v1/vexo_libraries";
  final OkHttpClient client;

  LibrarySync() {
    this(
        new OkHttpClient.Builder()
            .callTimeout(30L, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build());
  }

  LibrarySync(OkHttpClient okHttpClient) {
    this.client = okHttpClient;
  }

  static JSONObject snapshot(LibraryStore libraryStore) throws IOException {
    try {
      JSONObject jSONObject = new JSONObject();
      if (libraryStore.playlists().size() > 100) {
        throw new IOException(
            "Sync supports up to 100 playlists. Your local library stays unchanged.");
      }
      for (String str : libraryStore.playlists()) {
        jSONObject.put(str, tracks(libraryStore.tracks("playlist:" + str)));
      }
      return checked(
          new JSONObject()
              .put("format", "vexo-library-v1")
              .put("favourites", tracks(libraryStore.tracks("favourites")))
              .put("playlists", jSONObject));
    } catch (JSONException unused) {
      throw new IOException("Couldn’t read your collections.");
    }
  }

  static JSONArray tracks(List<Track> list) throws IOException {
    TreeMap treeMap = new TreeMap();
    for (Track track : list) {
      if (eligible(track)) {
        treeMap.put(track.url, track);
      }
    }
    if (treeMap.size() > 2000) {
      throw new IOException(
          "This collection exceeds the sync limit. Your local songs stay unchanged.");
    }
    JSONArray jSONArray = new JSONArray();
    Iterator it = treeMap.values().iterator();
    while (it.hasNext()) {
      jSONArray.put(((Track) it.next()).json());
    }
    return jSONArray;
  }

  static boolean eligible(Track track) {
    HttpUrl httpUrl = HttpUrl.parse(track.url);
    if (httpUrl == null || !httpUrl.isHttps()) {
      return false;
    }
    return (httpUrl.host().equals("www.youtube.com")
            || httpUrl.host().equals("youtube.com")
            || httpUrl.host().equals("music.youtube.com")
            || httpUrl.host().equals("youtu.be"))
        && httpUrl.username().isEmpty()
        && httpUrl.password().isEmpty()
        && artworkAllowed(track.artwork)
        && track.url.length() <= 2048
        && track.title.length() <= 500
        && track.artist.length() <= 500
        && track.artwork.length() <= 2048
        && track.duration >= 0
        && track.duration <= 86400;
  }

  private static boolean artworkAllowed(String str) {
    if (str.isEmpty()) {
      return true;
    }
    HttpUrl httpUrl = HttpUrl.parse(str);
    if (httpUrl == null
        || !httpUrl.isHttps()
        || !httpUrl.username().isEmpty()
        || !httpUrl.password().isEmpty()) {
      return false;
    }
    String strHost = httpUrl.host();
    return strHost.endsWith(".ytimg.com")
        || strHost.endsWith(".googleusercontent.com")
        || strHost.endsWith(".ggpht.com");
  }

  static JSONObject checked(JSONObject jSONObject) throws IOException {
    if (!jSONObject.optString("format").equals("vexo-library-v1")
        || jSONObject.optJSONArray("favourites") == null
        || jSONObject.optJSONObject("playlists") == null
        || jSONObject.toString().getBytes(StandardCharsets.UTF_8).length > LIMIT) {
      throw new IOException(
          "This cloud library cannot be synced safely. Your local library stays unchanged.");
    }
    if (jSONObject.optJSONObject("playlists").length() > 100) {
      throw new IOException("The cloud library has too many playlists.");
    }
    validateSongs(jSONObject.optJSONArray("favourites"));
    Iterator<String> itKeys = jSONObject.optJSONObject("playlists").keys();
    while (itKeys.hasNext()) {
      String next = itKeys.next();
      if (next.isBlank()
          || next.length() > 60
          || next.chars()
              .anyMatch(
                  new IntPredicate() {
                    @Override // java.util.function.IntPredicate
                    public final boolean test(int i) {
                      return Character.isISOControl(i);
                    }
                  })) {
        throw new IOException("A playlist name cannot be synced safely.");
      }
      validateSongs(jSONObject.optJSONObject("playlists").optJSONArray(next));
    }
    return jSONObject;
  }

  private static void validateSongs(JSONArray jSONArray) throws IOException {
    if (jSONArray == null || jSONArray.length() > 2000) {
      throw new IOException("A cloud collection exceeds the sync limit.");
    }
    for (int i = 0; i < jSONArray.length(); i++) {
      if (jSONArray.optJSONObject(i) == null || !eligible(Track.from(jSONArray.optJSONObject(i)))) {
        throw new IOException("A cloud song is not supported. Your local library stays unchanged.");
      }
    }
  }

  private static JSONArray union(JSONArray jSONArray, JSONArray jSONArray2) throws IOException {
    TreeMap treeMap = new TreeMap();
    JSONArray[] jSONArrayArr = {jSONArray, jSONArray2};
    for (int i = 0; i < 2; i++) {
      JSONArray jSONArray3 = jSONArrayArr[i];
      for (int i2 = 0; i2 < jSONArray3.length(); i2++) {
        Track trackFrom = Track.from(jSONArray3.optJSONObject(i2));
        Track track = (Track) treeMap.get(trackFrom.url);
        if (track == null || trackFrom.json().toString().compareTo(track.json().toString()) < 0) {
          treeMap.put(trackFrom.url, trackFrom);
        }
      }
    }
    return tracks(new ArrayList(treeMap.values()));
  }

  static JSONObject merge(JSONObject jSONObject, JSONObject jSONObject2) throws IOException {
    JSONArray jSONArray;
    JSONArray jSONArray2;
    checked(jSONObject);
    checked(jSONObject2);
    try {
      JSONObject jSONObject3 = new JSONObject();
      final TreeSet<String> treeSet = new TreeSet();
      JSONObject[] jSONObjectArr = {
        jSONObject.getJSONObject("playlists"), jSONObject2.getJSONObject("playlists")
      };
      for (int i = 0; i < 2; i++) {
        jSONObjectArr[i]
            .keys()
            .forEachRemaining(
                new Consumer() {
                  @Override // java.util.function.Consumer
                  public final void accept(Object obj) {
                    treeSet.add((String) obj);
                  }
                });
      }
      for (String str : treeSet) {
        if (jSONObject.getJSONObject("playlists").optJSONArray(str) == null) {
          jSONArray = new JSONArray();
        } else {
          jSONArray = jSONObject.getJSONObject("playlists").getJSONArray(str);
        }
        if (jSONObject2.getJSONObject("playlists").optJSONArray(str) == null) {
          jSONArray2 = new JSONArray();
        } else {
          jSONArray2 = jSONObject2.getJSONObject("playlists").getJSONArray(str);
        }
        jSONObject3.put(str, union(jSONArray, jSONArray2));
      }
      return checked(
          new JSONObject()
              .put("format", "vexo-library-v1")
              .put(
                  "favourites",
                  union(
                      jSONObject.getJSONArray("favourites"),
                      jSONObject2.getJSONArray("favourites")))
              .put("playlists", jSONObject3));
    } catch (JSONException unused) {
      throw new IOException("Couldn’t merge cloud collections.");
    }
  }

  JSONObject sync(AccountAuth.Credentials credentials, JSONObject jSONObject) throws IOException {
    JSONObject jSONObjectMerge;
    long j;
    JSONArray jSONArrayCall;
    checked(jSONObject);
    for (int i = 0; i < 3; i++) {
      JSONArray jSONArrayCall2 = call(credentials, "GET", "?select=revision,payload", null);
      try {
        if (jSONArrayCall2.length() > 1) {
          throw new IOException("Cloud account isolation failed. Sync stopped.");
        }
        if (jSONArrayCall2.length() == 1) {
          j = jSONArrayCall2.getJSONObject(0).getLong("revision");
          jSONObjectMerge =
              merge(jSONObject, jSONArrayCall2.getJSONObject(0).getJSONObject("payload"));
        } else {
          jSONObjectMerge = jSONObject;
          j = 0;
        }
        JSONObject jSONObjectPut =
            new JSONObject().put("payload", jSONObjectMerge).put("revision", 1 + j);
        if (j == 0) {
          jSONArrayCall = call(credentials, "POST", "?on_conflict=user_id", jSONObjectPut);
        } else {
          jSONArrayCall = call(credentials, "PATCH", "?revision=eq." + j, jSONObjectPut);
        }
        if (jSONArrayCall.length() == 1) {
          return checked(jSONArrayCall.getJSONObject(0).getJSONObject("payload"));
        }
      } catch (JSONException unused) {
        throw new IOException("Couldn’t read the cloud library.");
      }
    }
    throw new IOException("Another device is syncing. Wait a moment and tap Sync now again.");
  }

  private JSONArray call(
      AccountAuth.Credentials credentials, String str, String str2, JSONObject jSONObject)
      throws IOException {
    String str3;
    String str4;
    Request.Builder builderHeader =
        new Request.Builder()
            .url(URL + str2)
            .header("apikey", "sb_publishable_0ayG6KO3l8rty85oKuYurw_SN2qL9dH")
            .header("Authorization", "Bearer " + credentials.token);
    if (str.equals("POST")) {
      str3 = "resolution=ignore-duplicates,return=representation";
    } else {
      str3 = "return=representation";
    }
    Request.Builder builderHeader2 = builderHeader.header("Prefer", str3);
    if (!str.equals("GET")) {
      builderHeader2.method(
          str, RequestBody.create(jSONObject.toString(), MediaType.get("application/json")));
    }
    Response responseExecute = this.client.newCall(builderHeader2.build()).execute();
    try {
      if (!responseExecute.isSuccessful()) {
        if (responseExecute.code() == 401 || responseExecute.code() == 403) {
          str4 = "Sign in again before syncing.";
        } else {
          str4 = "Couldn’t sync your library. Check your connection and try again.";
        }
        throw new IOException(str4);
      }
      String strString = responseExecute.peekBody(1114112L).string();
      if (strString.getBytes(StandardCharsets.UTF_8).length >= 1114112) {
        throw new IOException("The cloud library exceeds the safe sync limit.");
      }
      try {
        JSONArray jSONArray = new JSONArray(strString);
        if (responseExecute != null) {
          responseExecute.close();
        }
        return jSONArray;
      } catch (JSONException unused) {
        throw new IOException("Cloud sync returned an unreadable response.");
      }
    } catch (Throwable th) {
      if (responseExecute != null) {
        try {
          responseExecute.close();
        } catch (Throwable th2) {
          th.addSuppressed(th2);
        }
      }
      throw th;
    }
  }

  static void apply(LibraryStore libraryStore, JSONObject jSONObject) throws IOException {
    checked(jSONObject);
    try {
      List<Track> listAppend =
          append(libraryStore.tracks("favourites"), jSONObject.getJSONArray("favourites"));
      JSONObject jSONObject2 = jSONObject.getJSONObject("playlists");
      LinkedHashMap linkedHashMap = new LinkedHashMap();
      Iterator<String> itKeys = jSONObject2.keys();
      while (itKeys.hasNext()) {
        String next = itKeys.next();
        linkedHashMap.put(
            next, append(libraryStore.tracks("playlist:" + next), jSONObject2.getJSONArray(next)));
      }
      libraryStore.mergeCollections(listAppend, linkedHashMap);
    } catch (JSONException unused) {
      throw new IOException("Couldn’t restore synced collections.");
    }
  }

  private static List<Track> append(List<Track> list, JSONArray jSONArray) {
    LinkedHashMap linkedHashMap = new LinkedHashMap();
    for (Track track : list) {
      linkedHashMap.put(track.url, track);
    }
    for (int i = 0; i < jSONArray.length(); i++) {
      Track trackFrom = Track.from(jSONArray.optJSONObject(i));
      linkedHashMap.putIfAbsent(trackFrom.url, trackFrom);
    }
    return new ArrayList(linkedHashMap.values());
  }
}
