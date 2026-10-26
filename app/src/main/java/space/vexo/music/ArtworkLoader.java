package space.vexo.music;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.view.View;
import java.lang.ref.WeakReference;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
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
  private static final Map<ImageView, Load> targets = new WeakHashMap<>();
  private static final class Load {
    final String artwork, trackUrl, key;
    final Drawable placeholder;
    int retries;
    boolean active, ready;
    Load(String artwork, String trackUrl, Drawable placeholder) {
      this.artwork = artwork;
      this.trackUrl = trackUrl;
      this.key = artwork.isEmpty() ? "track:" + trackUrl : artwork;
      this.placeholder = placeholder;
    }
  }
  private static final View.OnAttachStateChangeListener attachment =
      new View.OnAttachStateChangeListener() {
        public void onViewAttachedToWindow(View v) {
          ImageView view = (ImageView) v;
          Load load = targets.get(view);
          if (load != null && !load.active && !load.ready) start(view, load);
        }
        public void onViewDetachedFromWindow(View v) {}
      };
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
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
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
    load(view, url, "", placeholder);
  }

  static void load(ImageView view, String artwork, String trackUrl, Drawable placeholder) {
    initialize(view.getContext());
    artwork = ArtworkQuality.normalizeSource(artwork);
    trackUrl = trackUrl == null ? "" : trackUrl;
    String key = artwork.isEmpty() ? "track:" + trackUrl : artwork;
    Load existing = targets.get(view);
    if (existing != null && existing.key.equals(key) && existing.trackUrl.equals(trackUrl)
        && key.equals(view.getTag())) return;
    if (existing == null) view.addOnAttachStateChangeListener(attachment);
    Load load = new Load(artwork, trackUrl, placeholder);
    targets.put(view, load);
    // Keep the outline independent of Glide's mutable placeholder bounds.
    if (view.getBackground() == null)
      view.setBackground(Visuals.round(Visuals.PANEL,
          8 * view.getResources().getDisplayMetrics().density));
    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
    view.setClipToOutline(true);
    start(view, load);
  }

  private static void start(ImageView view, Load load) {
    if (targets.get(view) != load || load.active) return;
    load.active = true;
    view.setTag(load.key);
    int size = size(view);
    List<String> sources = ArtworkQuality.candidates(load.artwork, load.trackUrl, size);
    if (sources.isEmpty()) {
      Glide.with(view).clear(view);
      view.setImageDrawable(load.placeholder);
      load.active = false;
      load.ready = true;
      return;
    }
    WeakReference<ImageView> target = new WeakReference<>(view);
    RequestBuilder<Bitmap> request = null;
    for (int i = sources.size() - 1; i >= 0; i--) {
      final boolean last = i == sources.size() - 1;
      RequestBuilder<Bitmap> next = Glide.with(view).asBitmap().load(sources.get(i))
          .override(size, size).centerCrop().dontAnimate()
          .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
          .placeholder(load.placeholder).error(load.placeholder)
          .listener(new RequestListener<Bitmap>() {
            public boolean onResourceReady(Bitmap image, Object model, Target<Bitmap> ignored,
                DataSource source, boolean first) {
              ImageView bound = target.get();
              if (bound != null && targets.get(bound) == load) {
                load.active = false;
                load.ready = true;
              }
              return false;
            }
            public boolean onLoadFailed(GlideException error, Object model,
                Target<Bitmap> ignored, boolean first) {
              if (last) {
                ImageView bound = target.get();
                if (bound != null && targets.get(bound) == load) {
                  load.active = false;
                  if (load.retries < 2) {
                    long delay = load.retries++ == 0 ? 1500 : 5000;
                    main.postDelayed(() -> {
                      ImageView current = target.get();
                      if (current != null && targets.get(current) == load
                          && current.isAttachedToWindow() && !load.ready && !load.active)
                        start(current, load);
                    }, delay);
                  }
                }
              }
              return false;
            }
          });
      if (request != null) next.error(request);
      request = next;
    }
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
          for (ImageView view : new ArrayList<>(targets.keySet())) {
            if (view == null) continue;
            Load previous = targets.get(view);
            Glide.with(app).clear(view);
            view.setTag(null);
            if (previous != null) {
              Load fresh = new Load(previous.artwork, previous.trackUrl, previous.placeholder);
              targets.put(view, fresh);
              WeakReference<ImageView> target = new WeakReference<>(view);
              main.post(() -> {
                ImageView current = target.get();
                if (current != null && current.isAttachedToWindow()
                    && targets.get(current) == fresh) start(current, fresh);
              });
            }
          }
          Glide.get(app).clearMemory();
        };
    if (Looper.myLooper() == Looper.getMainLooper()) clear.run();
    else main.post(clear);
  }
}
