package space.vexo.music;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Objects;

final class AccountPanel extends MusicSurface {
  private boolean creating;
  private EditText email;
  private TextView google;
  private EditText password;
  private TextView resend;
  private TextView status;
  private TextView submit;
  private TextView switchMode;
  private TextView sync;

  interface Listener {
    void close();

    default void google() {}

    void signOut();

    void submit(String str, String str2, String str3);

    default void sync() {}
  }

  AccountPanel(Context context, AccountAuth accountAuth, final Listener listener) {
    super(context);
    LinearLayout linearLayout = this.body;
    Objects.requireNonNull(listener);
    linearLayout.addView(
        action(
            "Close",
            false,
            new Runnable() {
              @Override // java.lang.Runnable
              public final void run() {
                listener.close();
              }
            }));
    gap(this.body, 24);
    TextView textViewText = text("VEXO ACCOUNT", 11, Visuals.LIME, true);
    textViewText.setLetterSpacing(0.12f);
    this.body.addView(textViewText);
    gap(this.body, 12);
    this.body.addView(
        text(
            accountAuth.signedIn() ? "You’re at home." : "Your music. Your space.",
            28,
            Visuals.TEXT,
            true));
    gap(this.body, 12);
    this.body.addView(
        text(
            "Sign-in is optional. Sync online favourites and playlists when you choose. Phone"
                + " audio, profile photos, history and Smart memories stay on this phone.",
            13,
            Visuals.MUTED,
            false));
    gap(this.body, 24);
    TextView textViewText2 = text("", 14, Visuals.LIME, false);
    this.status = textViewText2;
    textViewText2.setAccessibilityLiveRegion(1);
    if (accountAuth.signedIn()) {
      this.body.addView(text(accountAuth.email(), 17, Visuals.TEXT, true));
      gap(this.body, 24);
      Objects.requireNonNull(listener);
      this.sync =
          action(
              "Sync library now",
              true,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.sync();
                }
              });
      this.body.addView(this.sync);
      gap(this.body, 12);
      Objects.requireNonNull(listener);
      this.submit =
          action(
              "Sign out on this phone",
              false,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.signOut();
                }
              });
      this.body.addView(this.submit);
    } else {
      Objects.requireNonNull(listener);
      TextView textViewAction =
          action(
              "Continue with Google",
              false,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.google();
                }
              });
      this.google = textViewAction;
      textViewAction.setTextColor(-14737633);
      this.google.setBackground(Visuals.round(-855310, dp(24.0f)));
      this.google.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_google, 0, 0, 0);
      this.google.setCompoundDrawablePadding(dp(12.0f));
      this.body.addView(this.google);
      gap(this.body, 18);
      this.body.addView(text("Or use email", 12, Visuals.MUTED, false));
      gap(this.body, 12);
      this.body.addView(text("Email", 13, Visuals.TEXT, true));
      this.email = input("Email address", 33);
      this.body.addView(this.email);
      gap(this.body, 16);
      this.body.addView(text("Password", 13, Visuals.TEXT, true));
      EditText editTextInput = input("Password", 129);
      this.password = editTextInput;
      editTextInput.setImeOptions(6);
      this.body.addView(this.password);
      gap(this.body, 22);
      this.submit =
          action(
              "Sign in",
              true,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  AccountPanel.this.lambda$new$0(listener);
                }
              });
      this.password.setOnEditorActionListener(
          new TextView.OnEditorActionListener() {
            @Override // android.widget.TextView.OnEditorActionListener
            public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
              return AccountPanel.this.lambda$new$1(textView, i, keyEvent);
            }
          });
      this.body.addView(this.submit);
      gap(this.body, 10);
      this.switchMode =
          action(
              "Create an account",
              false,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  AccountPanel.this.lambda$new$2();
                }
              });
      this.body.addView(this.switchMode);
      gap(this.body, 8);
      this.resend =
          action(
              "Resend confirmation",
              false,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  AccountPanel.this.lambda$new$3(listener);
                }
              });
      this.body.addView(this.resend);
      gap(this.body, 8);
      LinearLayout linearLayout2 = this.body;
      Objects.requireNonNull(listener);
      linearLayout2.addView(
          action(
              "Continue as guest",
              false,
              new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                  listener.close();
                }
              }));
    }
    gap(this.body, 18);
    this.body.addView(this.status);
  }

  public /* synthetic */ void lambda$new$0(Listener listener) {
    listener.submit(
        this.creating ? "signup" : "signin",
        this.email.getText().toString(),
        this.password.getText().toString());
  }

  public /* synthetic */ boolean lambda$new$1(TextView textView, int i, KeyEvent keyEvent) {
    if (i != 6) {
      return false;
    }
    this.submit.performClick();
    return true;
  }

  public /* synthetic */ void lambda$new$2() {
    String str;
    boolean z = this.creating;
    this.creating = !z;
    this.submit.setText(!z ? "Create account" : "Sign in");
    this.switchMode.setText(this.creating ? "Already registered? Sign in" : "Create an account");
    TextView textView = this.status;
    if (this.creating) {
      str = "Use at least 8 characters. Confirm your email before signing in.";
    } else {
      str = "";
    }
    textView.setText(str);
  }

  public /* synthetic */ void lambda$new$3(Listener listener) {
    listener.submit("resend", this.email.getText().toString(), "");
  }

  private EditText input(String str, int i) {
    EditText editText = new EditText(getContext());
    editText.setTextSize(16.0f);
    editText.setTextColor(Visuals.TEXT);
    editText.setHintTextColor(Visuals.MUTED);
    editText.setSingleLine();
    editText.setHint(str);
    editText.setContentDescription(str);
    editText.setInputType(i);
    editText.setPadding(dp(16.0f), dp(14.0f), dp(16.0f), dp(14.0f));
    editText.setBackground(Visuals.round(Visuals.PANEL, dp(16.0f)));
    editText.setMinimumHeight(dp(52.0f));
    return editText;
  }

  void busy(boolean z) {
    this.submit.setEnabled(!z);
    this.submit.setAlpha(z ? 0.5f : 1.0f);
    TextView textView = this.switchMode;
    if (textView != null) {
      textView.setEnabled(!z);
    }
    TextView textView2 = this.resend;
    if (textView2 != null) {
      textView2.setEnabled(!z);
    }
    TextView textView3 = this.sync;
    if (textView3 != null) {
      textView3.setEnabled(!z);
    }
    TextView textView4 = this.google;
    if (textView4 != null) {
      textView4.setEnabled(!z);
    }
    EditText editText = this.email;
    if (editText != null) {
      editText.setEnabled(!z);
    }
    EditText editText2 = this.password;
    if (editText2 != null) {
      editText2.setEnabled(!z);
    }
    if (z) {
      this.status.setText("Connecting securely…");
    }
  }

  void result(String str, boolean z) {
    busy(false);
    EditText editText = this.password;
    if (editText != null) {
      editText.setText("");
    }
    this.status.setTextColor(z ? Visuals.LIME : (Visuals.light ? 0xffab2435 : 0xffffb3b9));
    this.status.setText(str);
    if (z) {
      this.creating = false;
      TextView textView = this.switchMode;
      if (textView != null) {
        textView.setText("Create an account");
      }
      if (this.switchMode != null) {
        this.submit.setText("Sign in");
      }
    }
  }
}
