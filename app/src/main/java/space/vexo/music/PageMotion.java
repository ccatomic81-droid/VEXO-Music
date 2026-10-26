package space.vexo.music;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** One short transform, with no layout animation, blur, bitmap capture or queued navigation. */
final class PageMotion {
  static void enter(View page, int direction) {
    page.animate().cancel();
    page.setAlpha(1);
    page.setTranslationX(0);
    if (!ValueAnimator.areAnimatorsEnabled()) return;
    page.setTranslationX(direction * 12 * page.getResources().getDisplayMetrics().density);
    page.animate()
        .translationX(0)
        .setDuration(160)
        .setInterpolator(new DecelerateInterpolator())
        .withEndAction(() -> page.setTranslationX(0))
        .start();
  }
}
