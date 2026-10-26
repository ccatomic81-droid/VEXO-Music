package space.vexo.music;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.view.View;
import java.io.IOException;
import java.util.*;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RecoveredAccountsAndSyncTest {
  private final SearchUiTest ui = new SearchUiTest();

  @Before
  public void isolate() {
    ui.isolate();
  }

  AccountAuth auth(List<Request> requests, int status, String body) {
    return new AccountAuth(
        RuntimeEnvironment.getApplication(),
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  requests.add(chain.request());
                  return new Response.Builder()
                      .request(chain.request())
                      .protocol(Protocol.HTTP_1_1)
                      .code(status)
                      .message("Fixture")
                      .body(ResponseBody.create(body, MediaType.get("application/json")))
                      .build();
                })
            .build());
  }

  static String session(String access) {
    return "{\"access_token\":\""
        + access
        + "\",\"refresh_token\":\"fixture-refresh\",\"expires_in\":3600,\"user\":{\"id\":\"00000000-0000-4000-8000-000000000001\",\"email\":\"listener@example.com\"}}";
  }

  @Test
  public void accountTokensStayEncryptedAndSignOutPreservesCollections() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    LibraryStore store = new LibraryStore(c);
    store.createPlaylist("Keep my songs");
    store.addToPlaylist(
        "Keep my songs", new Track("content://fixture/audio", "Private song", "Artist", "", 180));
    List<Request> requests = new ArrayList<>();
    AccountAuth account = auth(requests, 200, session("fixture-access"));
    account.submit("signin", " listener@example.com ", "password-fixture");
    assertTrue(account.signedIn());
    String disk =
        c.getSharedPreferences("private_collections_v1", 0).getString("account_session_v1", "");
    assertFalse(disk.contains("fixture-access"));
    assertFalse(disk.contains("password-fixture"));
    account.signOut();
    assertFalse(account.signedIn());
    assertEquals(1, store.tracks("playlist:Keep my songs").size());
  }

  @Test
  public void googleExchangeIsBoundedAndIncompleteSessionsNeverReplaceValidCredentials()
      throws Exception {
    List<Request> requests = new ArrayList<>();
    AccountAuth account = auth(requests, 200, session("saved-access"));
    String token = "fixture-".repeat(20);
    account.google(token);
    assertTrue(account.signedIn());
    assertEquals(
        "https://vjkogwihjvlonnyedflq.supabase.co/functions/v1/firebase-signin",
        requests.get(0).url().toString());
    assertNull(requests.get(0).header("Authorization"));
    for (String body :
        Arrays.asList(
            session(""),
            session("new").replace("fixture-refresh", ""),
            session("new").replace("}", " ".repeat(100001) + "}"))) {
      assertThrows(IOException.class, () -> auth(requests, 200, body).google(token));
      assertEquals(
          "saved-access",
          new JSONObject(
                  new PrivateStore(RuntimeEnvironment.getApplication(), "account_session_v1")
                      .getString("session", "{}"))
              .getString("access_token"));
    }
    assertThrows(IOException.class, () -> account.google("short"));
  }

  @Test
  public void syncMergesBothDevicesWithoutUploadingPhoneAudioOrDeletingLocalSongs()
      throws Exception {
    LibraryStore store = new LibraryStore(RuntimeEnvironment.getApplication());
    Track local = new Track("content://fixture/audio", "Phone song", "Artist", "", 90);
    Track first =
        new Track("https://www.youtube.com/watch?v=fixture-one", "First", "Artist", "", 180);
    Track second =
        new Track("https://www.youtube.com/watch?v=fixture-two", "Second", "Other", "", 190);
    store.save("favourites", Arrays.asList(local, first));
    store.createPlaylist("My mix");
    store.addToPlaylist("My mix", first);
    JSONObject snapshot = LibrarySync.snapshot(store);
    assertEquals(1, snapshot.getJSONArray("favourites").length());
    JSONObject remote =
        new JSONObject()
            .put("format", "vexo-library-v1")
            .put("favourites", new JSONArray().put(second.json()))
            .put("playlists", new JSONObject().put("My mix", new JSONArray().put(second.json())));
    LibrarySync.apply(store, LibrarySync.merge(snapshot, remote));
    assertEquals(3, store.tracks("favourites").size());
    assertEquals(2, store.tracks("playlist:My mix").size());
    assertTrue(store.tracks("favourites").stream().anyMatch(t -> t.url.startsWith("content:")));
    LibrarySync.apply(store, LibrarySync.merge(LibrarySync.snapshot(store), remote));
    assertEquals(3, store.tracks("favourites").size());
  }

  @Test
  public void unsafeRemoteMetadataFailsBeforeAnyLocalImport() throws Exception {
    LibraryStore store = new LibraryStore(RuntimeEnvironment.getApplication());
    store.createPlaylist("Keep me");
    JSONObject invalid =
        new JSONObject()
            .put("format", "vexo-library-v1")
            .put(
                "favourites",
                new JSONArray()
                    .put(new Track("https://evil.example/audio", "Fake", "Artist", "", 9).json()))
            .put("playlists", new JSONObject());
    assertThrows(IOException.class, () -> LibrarySync.apply(store, invalid));
    assertTrue(store.playlists().contains("Keep me"));
  }

  @Test
  public void pushRequiresOptInChannelAndNewVersionAndDeduplicatesSuccessfulDisplay() {
    Context c = RuntimeEnvironment.getApplication();
    NotificationManager manager = c.getSystemService(NotificationManager.class);
    var controller = Robolectric.buildService(VexoMessagingService.class).create();
    try {
      VexoMessagingService service = controller.get();
      com.google.firebase.messaging.RemoteMessage newer =
          push("new", BuildConfig.VERSION_CODE + 1, "stable");
      service.onMessageReceived(newer);
      assertEquals(0, Shadows.shadowOf(manager).size());
      c.getSharedPreferences("settings", 0).edit().putBoolean("push_release_alerts", true).commit();
      service.onMessageReceived(push("old", BuildConfig.VERSION_CODE, "stable"));
      service.onMessageReceived(push("other", BuildConfig.VERSION_CODE + 1, "testing"));
      assertEquals(0, Shadows.shadowOf(manager).size());
      service.onMessageReceived(newer);
      assertEquals(1, Shadows.shadowOf(manager).size());
      manager.cancelAll();
      service.onMessageReceived(push("same-version", BuildConfig.VERSION_CODE + 1, "stable"));
      assertEquals(0, Shadows.shadowOf(manager).size());
    } finally {
      controller.destroy();
    }
  }

  private com.google.firebase.messaging.RemoteMessage push(String id, int code, String channel) {
    return new com.google.firebase.messaging.RemoteMessage.Builder("fixture")
        .setMessageId(id)
        .addData("versionCode", Integer.toString(code))
        .addData("channel", channel)
        .build();
  }

  @Test
  public void recoveredHomeAccountAndNotificationSheetsFitAndKeepSmartNavigation()
      throws Exception {
    try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = controller.get();
      View root = a.findViewById(android.R.id.content);
      ui.capture(root, "recovered-home-320", 640);
      assertNotNull(ui.text(root, "Curated for your day"));
      ui.description(root, "Settings tab").performClick();
      ((View) ui.text(root, "VEXO account").getParent().getParent()).performClick();
      Dialog dialog = ShadowDialog.getLatestDialog();
      View account = dialog.getWindow().getDecorView();
      assertNotNull(ui.text(account, "Continue with Google"));
      assertNotNull(ui.text(account, "Continue as guest"));
      ui.capture(account, "recovered-account-320", 600);
      dialog.dismiss();
      ((View) ui.text(root, "Notifications").getParent().getParent()).performClick();
      dialog = ShadowDialog.getLatestDialog();
      assertNotNull(ui.text(dialog.getWindow().getDecorView(), "Send a test notification"));
      dialog.dismiss();
      ui.description(root, "Smart Experience tab").performClick();
      assertNotNull(ui.text(root, "PureSong & skip intros"));
    }
  }

  @Test
  public void miniPlayerArtworkKeepsRoundedCornersAfterBackgroundReplacement() {
    RoundedArtwork view = new RoundedArtwork(RuntimeEnvironment.getApplication());
    view.layout(0, 0, 44, 44);
    view.setBackground(new android.graphics.drawable.ColorDrawable(android.graphics.Color.RED));
    android.graphics.Bitmap bitmap =
        android.graphics.Bitmap.createBitmap(44, 44, android.graphics.Bitmap.Config.ARGB_8888);
    view.draw(new android.graphics.Canvas(bitmap));
    assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)));
    assertEquals(android.graphics.Color.RED, bitmap.getPixel(22, 22));
  }
}
