package space.vexo.music;

import android.view.*;

/** Vertical drag shortcut without taking over child playback buttons or horizontal seeking. */
final class PlayerGestures {
  static void expand(View view, Runnable expand) {
    float[] start = new float[2];
    view.setOnTouchListener((target, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          start[0] = event.getX(); start[1] = event.getY(); return true;
        case MotionEvent.ACTION_UP:
          float dy = start[1] - event.getY(), dx = event.getX() - start[0];
          float density = target.getResources().getDisplayMetrics().density;
          if (dy > 36 * density && dy > Math.abs(dx) * 1.3f) expand.run();
          else if (Math.hypot(dx, dy) < ViewConfiguration.get(target.getContext()).getScaledTouchSlop())
            target.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL: return true;
        default: return true;
      }
    });
  }
}
