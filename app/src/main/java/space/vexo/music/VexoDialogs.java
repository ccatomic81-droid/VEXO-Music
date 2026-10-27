package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** Shared VEXO card styling, retaining platform dialog focus, selection and dismissal semantics. */
final class VexoDialogs {
  static final class Builder extends AlertDialog.Builder {
    private CharSequence title;

    Builder(Context context) {
      super(context, R.style.VexoDialogTheme);
    }

    @Override
    public Builder setTitle(CharSequence value) {
      title = value;
      super.setTitle(value);
      return this;
    }

    @Override
    public Builder setTitle(int value) {
      return setTitle(getContext().getText(value));
    }

    @Override
    public AlertDialog create() {
      Context c = getContext();
      LinearLayout header = new LinearLayout(c);
      header.setOrientation(LinearLayout.VERTICAL);
      header.setPadding(dp(c, 22), dp(c, 22), dp(c, 22), dp(c, 12));
      LinearLayout brand = new LinearLayout(c);
      brand.setGravity(Gravity.CENTER_VERTICAL);
      ImageView mark = new ImageView(c);
      mark.setImageResource(R.drawable.vexo_logo_v2);
      mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      brand.addView(mark, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));
      TextView label = label(c, "VEXO MUSIC", 11, LIME);
      label.setLetterSpacing(.14f);
      label.setPadding(dp(c, 10), 0, 0, 0);
      brand.addView(label);
      header.addView(brand);
      if (title != null && title.length() > 0) {
        TextView heading = label(c, title, 22, TEXT);
        heading.setPadding(0, dp(c, 16), 0, dp(c, 4));
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        header.addView(heading);
      }
      super.setCustomTitle(header);
      AlertDialog dialog = super.create();
      dialog.setOnShowListener(unused -> style(dialog));
      return dialog;
    }

    @Override
    public AlertDialog show() {
      AlertDialog dialog = create();
      dialog.show();
      return dialog;
    }
  }

  static void style(AlertDialog dialog) {
    VexoNotice.host(dialog);
    Context c = dialog.getContext();
    Window window = dialog.getWindow();
    if (window != null) {
      android.graphics.drawable.GradientDrawable background = round(0xff202322, dp(c, 26));
      background.setStroke(dp(c, 1), 0xff37443b);
      window.setBackgroundDrawable(background);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setDimAmount(.72f);
      int available = c.getResources().getDisplayMetrics().widthPixels - dp(c, 32);
      window.setLayout(Math.min(dp(c, 420), available), -2);
    }
    TextView message = dialog.findViewById(android.R.id.message);
    if (message != null) {
      message.setTextColor(MUTED);
      message.setTextSize(14);
      message.setLineSpacing(dp(c, 4), 1);
    }
    for (int which :
        new int[] {
          AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE
        }) {
      Button button = dialog.getButton(which);
      if (button == null) continue;
      boolean primary = which == AlertDialog.BUTTON_POSITIVE;
      button.setAllCaps(false);
      button.setTextSize(12);
      button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      button.setTextColor(primary ? BG : TEXT);
      button.setMinWidth(0);
      button.setMinimumWidth(0);
      button.setMinHeight(dp(c, 48));
      button.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
      button.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(0x30ffffff),
              round(primary ? LIME : 0xff343a36, dp(c, 24)),
              null));
      if (button.getLayoutParams() instanceof LinearLayout.LayoutParams) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
        params.setMargins(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 10));
        button.setLayoutParams(params);
      }
    }
    ListView list = dialog.getListView();
    if (list != null) {
      list.setDivider(null);
      list.setDividerHeight(dp(c, 4));
      list.setSelector(round(0xff314538, dp(c, 12)));
      list.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 8));
      list.setOnHierarchyChangeListener(
          new android.view.ViewGroup.OnHierarchyChangeListener() {
            public void onChildViewAdded(View parent, View child) {
              if (child instanceof TextView) {
                TextView text = (TextView) child;
                text.setTextColor(TEXT);
                text.setTextSize(15);
                text.setMinHeight(dp(c, 52));
              }
            }

            public void onChildViewRemoved(View parent, View child) {}
          });
    }
  }

  private static TextView label(Context c, CharSequence value, int size, int color) {
    TextView text = new TextView(c);
    text.setText(value);
    text.setTextSize(size);
    text.setTextColor(color);
    text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return text;
  }

  private static int dp(Context c, int value) {
    return Math.round(value * c.getResources().getDisplayMetrics().density);
  }
}
