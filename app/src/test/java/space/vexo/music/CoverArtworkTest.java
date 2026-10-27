package space.vexo.music;

import static org.junit.Assert.*;
import android.graphics.*;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CoverArtworkTest {
  @Test public void embeddedLetterboxingIsRemovedAndCenterArtworkIsNotStretched() {
    Bitmap source = Bitmap.createBitmap(480, 360, Bitmap.Config.ARGB_8888);
    source.eraseColor(Color.BLACK);
    Canvas canvas = new Canvas(source);
    Paint paint = new Paint();
    paint.setColor(Color.BLUE);
    canvas.drawRect(0, 45, 480, 315, paint);
    paint.setColor(Color.RED);
    canvas.drawCircle(240, 180, 70, paint);
    assertEquals(new Rect(0, 45, 480, 315), CoverArtwork.contentBounds(source));
    Bitmap result = CoverArtwork.render(source, new BitmapPoolAdapter(), 270, 270);
    assertEquals(Color.BLUE, result.getPixel(135, 1));
    assertEquals(Color.BLUE, result.getPixel(135, 268));
    int horizontal = 0, vertical = 0;
    for (int i = 0; i < 270; i++) {
      if (result.getPixel(i, 135) == Color.RED) horizontal++;
      if (result.getPixel(135, i) == Color.RED) vertical++;
    }
    assertEquals(horizontal, vertical, 2);
    assertEquals(140, horizontal, 3);
  }

  @Test public void squareAlbumsAndAsymmetricDarkPhotographsKeepTheirComposition() {
    Bitmap square = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
    square.eraseColor(Color.BLACK);
    assertEquals(new Rect(0, 0, 300, 300), CoverArtwork.contentBounds(square));
    Bitmap wide = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
    wide.eraseColor(Color.BLACK);
    Paint paint = new Paint(); paint.setColor(Color.GREEN);
    new Canvas(wide).drawRect(0, 35, 480, 270, paint);
    assertEquals(new Rect(0, 0, 480, 270), CoverArtwork.contentBounds(wide));
    assertEquals(0xff263c33, ArtworkPalette.shade(square));
    int shade = ArtworkPalette.shade(wide);
    assertTrue(Color.green(shade) > Color.red(shade));
    assertTrue(Color.green(shade) < 85);
  }
}
