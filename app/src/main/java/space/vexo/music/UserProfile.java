package space.vexo.music;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** A device-local listening profile. No account, photo upload or broad gallery permission. */
final class UserProfile {
  private final Context context;
  private final PrivateStore prefs;

  UserProfile(Context c) {
    context = c.getApplicationContext();
    prefs = new PrivateStore(context, "user_profile");
  }

  String name() {
    return prefs.getString("name", "");
  }

  boolean complete() {
    return !name().isEmpty();
  }

  static String cleanName(String value) {
    if (value == null) return "";
    return value.replaceAll("\\s+", " ").replaceAll("[\\p{Cc}]", "").trim();
  }

  boolean saveName(String value) {
    String name = cleanName(value);
    if (name.isEmpty() || name.codePointCount(0, name.length()) > 40) return false;
    prefs.edit().putString("name", name).apply();
    return true;
  }

  static String greeting(int hour) {
    if (hour >= 4 && hour < 12) return "Good morning";
    if (hour >= 12 && hour < 17) return "Good afternoon";
    if (hour >= 17 && hour < 21) return "Good evening";
    return "Good night";
  }

  String greeting() {
    return greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
  }

  private File legacyPhoto() {
    return new File(context.getFilesDir(), "profile-photo.jpg");
  }

  private String photoBytes() {
    String stored = prefs.getString("photo_bytes", "");
    File old = legacyPhoto();
    if (!stored.isEmpty()) {
      if (old.isFile()) new AtomicFile(old).delete();
      return stored;
    }
    if (old.isFile() && old.length() <= 2 * 1024 * 1024) {
      try (InputStream in = new FileInputStream(old);
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (out.size() + n > 2 * 1024 * 1024) return "";
          out.write(buffer, 0, n);
        }
        String encoded =
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
        if (prefs.edit().putString("photo_bytes", encoded).commit()) new AtomicFile(old).delete();
        return encoded;
      } catch (IOException e) {
        return "";
      }
    }
    return "";
  }

  boolean hasPhoto() {
    return !photoBytes().isEmpty();
  }

  void removePhoto() {
    prefs.edit().remove("photo_bytes").remove("photo_version").apply();
    new AtomicFile(legacyPhoto()).delete();
  }

  long photoVersion() {
    return prefs.getLong("photo_version", 0);
  }

  Bitmap avatar() {
    String encoded = photoBytes();
    if (encoded.isEmpty()) return null;
    try {
      byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP);
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  void importPhoto(Uri uri) throws IOException {
    File temporary = File.createTempFile("profile-source-", ".image", context.getCacheDir());
    Bitmap decoded = null, square = null;
    try {
      try (InputStream in = context.getContentResolver().openInputStream(uri);
          OutputStream out = new FileOutputStream(temporary)) {
        if (in == null) throw new IOException("Couldn’t open this image");
        byte[] buffer = new byte[16384];
        int count, total = 0;
        while ((count = in.read(buffer)) != -1) {
          total += count;
          if (total > 20 * 1024 * 1024) throw new IOException("Choose a photo smaller than 20 MB");
          out.write(buffer, 0, count);
        }
      }
      if (Build.VERSION.SDK_INT >= 28) {
        try {
          decoded =
              ImageDecoder.decodeBitmap(
                  ImageDecoder.createSource(temporary),
                  (decoder, info, source) -> {
                    int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                    float scale = Math.min(1f, 1024f / Math.max(w, h));
                    decoder.setTargetSize(
                        Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                  });
        } catch (IOException | IllegalArgumentException unsupported) {
          decoded = null;
        }
      }
      if (decoded == null) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(temporary.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
          throw new IOException("This file is not a supported image");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= 1024)
          options.inSampleSize *= 2;
        decoded = BitmapFactory.decodeFile(temporary.getAbsolutePath(), options);
        if (decoded != null) {
          try {
            int orientation =
                new android.media.ExifInterface(temporary.getAbsolutePath())
                    .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            Matrix transform = new Matrix();
            switch (orientation) {
              case 2:
                transform.setScale(-1, 1);
                break;
              case 3:
                transform.setRotate(180);
                break;
              case 4:
                transform.setScale(1, -1);
                break;
              case 5:
                transform.setRotate(90);
                transform.postScale(-1, 1);
                break;
              case 6:
                transform.setRotate(90);
                break;
              case 7:
                transform.setRotate(270);
                transform.postScale(-1, 1);
                break;
              case 8:
                transform.setRotate(270);
                break;
              default:
                break;
            }
            if (!transform.isIdentity()) {
              Bitmap rotated =
                  Bitmap.createBitmap(
                      decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), transform, true);
              if (rotated != decoded) decoded.recycle();
              decoded = rotated;
            }
          } catch (IOException ignored) {
          }
        }
      }
      if (decoded == null) throw new IOException("This file is not a supported image");
      square = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(square);
      canvas.drawColor(Color.rgb(25, 29, 39));
      int side = Math.min(decoded.getWidth(), decoded.getHeight()),
          left = (decoded.getWidth() - side) / 2,
          top = (decoded.getHeight() - side) / 2;
      canvas.drawBitmap(
          decoded,
          new Rect(left, top, left + side, top + side),
          new Rect(0, 0, 512, 512),
          new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
      ByteArrayOutputStream saved = new ByteArrayOutputStream();
      if (!square.compress(Bitmap.CompressFormat.JPEG, 95, saved))
        throw new IOException("Couldn’t save this photo");
      if (!prefs
          .edit()
          .putString(
              "photo_bytes",
              android.util.Base64.encodeToString(saved.toByteArray(), android.util.Base64.NO_WRAP))
          .putLong("photo_version", System.currentTimeMillis())
          .commit())
        throw new IOException("Couldn’t securely save this photo; existing photo kept");
      new AtomicFile(legacyPhoto()).delete();
    } catch (OutOfMemoryError lowMemory) {
      Visuals.clearArtwork();
      throw new IOException("Not enough memory for this photo. Try a smaller image.", lowMemory);
    } finally {
      if (decoded != null) decoded.recycle();
      if (square != null) square.recycle();
      temporary.delete();
    }
  }
}
