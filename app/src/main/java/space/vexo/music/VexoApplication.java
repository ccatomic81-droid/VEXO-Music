package space.vexo.music;

import android.app.Application;

public class VexoApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    AppRecovery.recoverIfNeeded(this);
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          new AppDiagnostics(this).record("app_crash", error, true);
          if (previous != null) previous.uncaughtException(thread, error);
          else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
          }
        });
  }

  @Override
  public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    if (level >= TRIM_MEMORY_RUNNING_LOW) Visuals.clearArtwork();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    Visuals.clearArtwork();
  }
}
