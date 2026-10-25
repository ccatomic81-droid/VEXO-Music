package space.vexo.music;

import static space.vexo.music.Visuals.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;

final class ProfilePanel extends MusicSurface {
  interface Listener {
    void photo();

    void removePhoto();

    void save(String name);

    void back();
  }

  private final EditText name;
  private final FrameLayout avatar;
  private final UserProfile profile;

  ProfilePanel(Context c, UserProfile profile, boolean first, String draft, Listener listener) {
    super(c);
    this.profile = profile;
    body.setPadding(dp(24), dp(24), dp(24), dp(32));
    if (!first) {
      TextView back = action("Back", false, listener::back);
      body.addView(back);
      gap(body, 20);
    }
    TextView label = text("VEXO MUSIC", 12, LIME, true);
    label.setLetterSpacing(.15f);
    body.addView(label);
    gap(body, 26);
    body.addView(text(first ? "Make yourself at home." : "Your profile", 30, TEXT, true));
    gap(body, 12);
    body.addView(text(first ? "What should we call you?" : "A little more you.", 16, MUTED, false));
    gap(body, 26);
    avatar = new FrameLayout(c);
    GradientDrawable oval = new GradientDrawable();
    oval.setShape(GradientDrawable.OVAL);
    oval.setColor(PANEL);
    avatar.setBackground(oval);
    avatar.setClipToOutline(true);
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(112), dp(112));
    ap.gravity = Gravity.CENTER_HORIZONTAL;
    body.addView(avatar, ap);
    avatar.setOnClickListener(v -> listener.photo());
    avatar.setContentDescription("Choose profile photo");
    refreshPhoto();
    gap(body, 16);
    TextView choose = action("Choose profile photo", false, listener::photo);
    body.addView(choose);
    gap(body, 8);
    TextView remove = action("Remove photo", false, listener::removePhoto);
    remove.setVisibility(profile.photo().isFile() ? View.VISIBLE : View.GONE);
    remove.setTag("remove_photo");
    body.addView(remove);
    gap(body, 20);
    body.addView(text("Your name", 13, TEXT, true));
    gap(body, 8);
    name = new EditText(c);
    name.setTextColor(TEXT);
    name.setHintTextColor(MUTED);
    name.setTextSize(18);
    name.setSingleLine();
    name.setHint("Enter your name");
    name.setText(draft);
    name.setContentDescription("Your name");
    name.setPadding(dp(16), dp(15), dp(16), dp(15));
    name.setBackground(round(PANEL, dp(14)));
    name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    name.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
            listener.save(name.getText().toString());
            return true;
          }
          return false;
        });
    body.addView(name, new LinearLayout.LayoutParams(-1, -2));
    gap(body, 20);
    TextView done =
        action(
            first ? "Let’s listen" : "Save profile",
            true,
            () -> listener.save(name.getText().toString()));
    body.addView(done);
    gap(body, 16);
    body.addView(
        text("Your name and photo stay on this phone. No account is needed.", 12, MUTED, false));
  }

  String draft() {
    return name.getText().toString();
  }

  void nameError() {
    name.setError("Enter a name between 1 and 40 characters");
    name.requestFocus();
  }

  void refreshPhoto() {
    avatar.removeAllViews();
    Bitmap bitmap = profile.avatar();
    if (bitmap != null) {
      ImageView image = new ImageView(getContext());
      image.setImageBitmap(bitmap);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      avatar.addView(image, new FrameLayout.LayoutParams(-1, -1));
    } else {
      TextView initial = text("V", 40, LIME, true);
      initial.setGravity(Gravity.CENTER);
      avatar.addView(initial, new FrameLayout.LayoutParams(-1, -1));
    }
    View remove = body.findViewWithTag("remove_photo");
    if (remove != null) remove.setVisibility(profile.photo().isFile() ? View.VISIBLE : View.GONE);
  }
}
