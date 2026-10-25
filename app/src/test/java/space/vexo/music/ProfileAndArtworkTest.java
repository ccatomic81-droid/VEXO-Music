package space.vexo.music;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ProfileAndArtworkTest {
  @Test
  public void namesPersistAndGreetingBoundariesUseTheLocalHour() {
    UserProfile profile = new UserProfile(RuntimeEnvironment.getApplication());
    assertFalse(profile.complete());
    assertFalse(profile.saveName("   "));
    assertTrue(profile.saveName("  Kavindu   Kavishka  "));
    assertEquals("Kavindu Kavishka", new UserProfile(RuntimeEnvironment.getApplication()).name());
    assertFalse(profile.saveName("a".repeat(41)));
    assertEquals("Kavindu Kavishka", profile.name());
    assertTrue(profile.saveName("ක්‍රිෂාන්"));
    assertEquals("ක්‍රිෂාන්", profile.name());
    assertEquals("Good night", UserProfile.greeting(3));
    assertEquals("Good morning", UserProfile.greeting(4));
    assertEquals("Good morning", UserProfile.greeting(5));
    assertEquals("Good morning", UserProfile.greeting(11));
    assertEquals("Good afternoon", UserProfile.greeting(12));
    assertEquals("Good evening", UserProfile.greeting(17));
    assertEquals("Good night", UserProfile.greeting(21));
  }

  File photo(Context c) throws Exception {
    Bitmap image = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888);
    image.eraseColor(Color.rgb(52, 112, 212));
    File input = new File(c.getCacheDir(), "selected-photo.png");
    try (FileOutputStream out = new FileOutputStream(input)) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
    return input;
  }

  @Test
  public void selectedPhotoIsCopiedCroppedAndSurvivesOriginalRemoval() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    UserProfile profile = new UserProfile(c);
    File selected = photo(c);
    profile.importPhoto(Uri.fromFile(selected));
    assertTrue(selected.delete());
    Bitmap saved = new UserProfile(c).avatar();
    assertNotNull(saved);
    assertEquals(512, saved.getWidth());
    assertEquals(512, saved.getHeight());
    saved.recycle();
    File invalid = new File(c.getCacheDir(), "bad.png");
    try (FileOutputStream out = new FileOutputStream(invalid)) {
      out.write("not an image".getBytes());
    }
    assertThrows(Exception.class, () -> profile.importPhoto(Uri.fromFile(invalid)));
    assertTrue(profile.photo().isFile());
    profile.removePhoto();
    assertFalse(profile.photo().exists());
  }

  @Test
  @Config(sdk = 26)
  public void photoImportWorksOnAndroid8() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    UserProfile profile = new UserProfile(c);
    profile.importPhoto(Uri.fromFile(photo(c)));
    Bitmap image = profile.avatar();
    assertNotNull(image);
    assertEquals(512, image.getWidth());
    image.recycle();
  }

  @Test
  public void artworkKeepsSmallImagesSharpAndBoundsLargeOnes() throws Exception {
    for (int width : new int[] {256, 1400}) {
      Bitmap original = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
      original.eraseColor(Color.GREEN);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      original.compress(Bitmap.CompressFormat.PNG, 100, bytes);
      original.recycle();
      Bitmap decoded = ArtworkQuality.decode(new ByteArrayInputStream(bytes.toByteArray()));
      assertNotNull(decoded);
      assertEquals(width == 256 ? 256 : 1024, decoded.getWidth());
      assertEquals(Color.GREEN, decoded.getPixel(10, 10));
      decoded.recycle();
    }
    assertNull(ArtworkQuality.decode(new ByteArrayInputStream("bad".getBytes())));
  }

  @Test
  public void higherArtworkUrlsAreLimitedToKnownImageHosts() {
    assertEquals(
        "https://lh3.googleusercontent.com/photo=w1024-h1024-l90-rj",
        ArtworkQuality.higherResolution(
            "https://lh3.googleusercontent.com/photo=w120-h120-l90-rj"));
    assertEquals(
        "https://i.ytimg.com/vi/id/maxresdefault.jpg",
        ArtworkQuality.higherResolution("https://i.ytimg.com/vi/id/hqdefault.jpg"));
    assertEquals(
        "https://example.com/photo=w120-h120",
        ArtworkQuality.higherResolution("https://example.com/photo=w120-h120"));
  }
}
