package space.vexo.music;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/** Shared, size-aware requests with memory/disk caching and recycled-view cancellation. */
final class ArtworkLoader {
  private static final Handler main = new Handler(Looper.getMainLooper());
  private static final Map<ImageView, Boolean> targets = new WeakHashMap<>();
  private static Context app;

  static void configure(Context context, OkHttpClient client) {
    app = context.getApplicationContext();
    Glide.get(app)
        .getRegistry()
        .replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));
  }

  private static void initialize(Context context) {
    if (app != null) return;
    configure(
        context,
        new OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build());
  }

  static int size(ImageView view) {
    ViewGroup.LayoutParams p = view.getLayoutParams();
    int width = p != null && p.width > 0 ? p.width : view.getWidth();
    int height = p != null && p.height > 0 ? p.height : view.getHeight();
    int value = Math.max(width, height);
    return Math.max(128, Math.min(1024, value > 0 ? value : 512));
  }

  static void load(ImageView view, String url, Drawable placeholder) {
    initialize(view.getContext());
    view.setTag(url);
    view.setBackground(placeholder);
    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
    view.setClipToOutline(true);
    targets.put(view, true);
    int size = size(view);
    String source = url == null ? "" : url;
    if (!source.startsWith("https://") && !source.startsWith("content://")) source = "";
    String preferred = ArtworkQuality.forSize(source, size);
    RequestBuilder<android.graphics.Bitmap> request =
        Glide.with(view)
            .asBitmap()
            .load(preferred.isEmpty() ? null : preferred)
            .override(size, size)
            .centerCrop()
            .dontAnimate()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(placeholder)
            .error(placeholder);
    if (!preferred.equals(source))
      request =
          request.error(
              Glide.with(view)
                  .asBitmap()
                  .load(source)
                  .override(size, size)
                  .centerCrop()
                  .dontAnimate()
                  .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                  .placeholder(placeholder)
                  .error(placeholder));
    request.into(view);
  }

  static void trim(int level) {
    if (app == null) return;
    if (Looper.myLooper() == Looper.getMainLooper()) Glide.get(app).trimMemory(level);
    else
      main.post(
          () -> {
            if (app != null) Glide.get(app).trimMemory(level);
          });
  }

  static void clear() {
    if (app == null) return;
    Runnable clear =
        () -> {
          for (ImageView view : new ArrayList<>(targets.keySet()))
            if (view != null) Glide.with(app).clear(view);
          targets.clear();
          Glide.get(app).clearMemory();
        };
    if (Looper.myLooper() == Looper.getMainLooper()) clear.run();
    else main.post(clear);
  }
}
