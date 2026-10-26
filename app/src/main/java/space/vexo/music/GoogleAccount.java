package space.vexo.music;

import android.app.Activity;
import android.os.CancellationSignal;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.GoogleAuthProvider;
import java.util.concurrent.Executor;

final class GoogleAccount {

  interface Listener {
    void failed(String str);

    void token(String str);
  }

  GoogleAccount() {}

  static CancellationSignal start(Activity activity, Listener listener) {
    CancellationSignal cancellationSignal = new CancellationSignal();
    try {
      CredentialManager.create(activity)
          .getCredentialAsync(
              activity,
              new GetCredentialRequest.Builder()
                  .addCredentialOption(
                      new GetSignInWithGoogleOption.Builder(
                              activity.getString(R.string.default_web_client_id))
                          .build())
                  .build(),
              cancellationSignal,
              ContextCompat.getMainExecutor(activity),
              new AnonymousClass1(cancellationSignal, activity, listener));
      return cancellationSignal;
    } catch (Exception unused) {
      listener.failed("Google sign-in is unavailable. You can still use email sign-in.");
      return cancellationSignal;
    }
  }

  static class AnonymousClass1
      implements CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
    final /* synthetic */ Activity val$activity;
    final /* synthetic */ CancellationSignal val$cancellation;
    final /* synthetic */ Listener val$listener;

    AnonymousClass1(CancellationSignal cancellationSignal, Activity activity, Listener listener) {
      this.val$cancellation = cancellationSignal;
      this.val$activity = activity;
      this.val$listener = listener;
    }

    public void onResult(GetCredentialResponse getCredentialResponse) {
      if (this.val$cancellation.isCanceled() || this.val$activity.isDestroyed()) {
        return;
      }
      try {
        Credential credential = getCredentialResponse.getCredential();
        if (!(credential instanceof CustomCredential)
            || !"com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL"
                .equals(credential.getType())) {
          throw new IllegalArgumentException();
        }
        String idToken = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
        final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        Task taskSignInWithCredential =
            firebaseAuth.signInWithCredential(
                GoogleAuthProvider.getCredential(idToken, (String) null));
        Executor mainExecutor = ContextCompat.getMainExecutor(this.val$activity);
        final CancellationSignal cancellationSignal = this.val$cancellation;
        final Activity activity = this.val$activity;
        final Listener listener = this.val$listener;
        taskSignInWithCredential.addOnCompleteListener(
            mainExecutor,
            new OnCompleteListener() {
              public final void onComplete(Task task) {
                GoogleAccount.AnonymousClass1.lambda$onResult$1(
                    cancellationSignal, activity, firebaseAuth, listener, task);
              }
            });
      } catch (Exception unused) {
        this.val$listener.failed("Google sign-in is unavailable. You can still use email sign-in.");
      }
    }

    static /* synthetic */ void lambda$onResult$1(
        final CancellationSignal cancellationSignal,
        final Activity activity,
        final FirebaseAuth firebaseAuth,
        final Listener listener,
        Task task) {
      if (cancellationSignal.isCanceled() || activity.isDestroyed()) {
        firebaseAuth.signOut();
      } else if (!task.isSuccessful() || ((AuthResult) task.getResult()).getUser() == null) {
        firebaseAuth.signOut();
        listener.failed("Google sign-in failed. Check your connection and try again.");
      } else {
        ((AuthResult) task.getResult())
            .getUser()
            .getIdToken(true)
            .addOnCompleteListener(
                ContextCompat.getMainExecutor(activity),
                new OnCompleteListener() {
                  public final void onComplete(Task task2) {
                    GoogleAccount.AnonymousClass1.lambda$onResult$0(
                        firebaseAuth, cancellationSignal, activity, listener, task2);
                  }
                });
      }
    }

    static /* synthetic */ void lambda$onResult$0(
        FirebaseAuth firebaseAuth,
        CancellationSignal cancellationSignal,
        Activity activity,
        Listener listener,
        Task task) {
      firebaseAuth.signOut();
      if (cancellationSignal.isCanceled() || activity.isDestroyed()) {
        return;
      }
      if (!task.isSuccessful() || ((GetTokenResult) task.getResult()).getToken() == null) {
        listener.failed("Couldn’t verify Google sign-in. Please try again.");
      } else {
        listener.token(((GetTokenResult) task.getResult()).getToken());
      }
    }

    public void onError(GetCredentialException getCredentialException) {
      String str;
      if (this.val$activity.isDestroyed()) {
        return;
      }
      Listener listener = this.val$listener;
      if (getCredentialException instanceof GetCredentialCancellationException) {
        str = "Google sign-in cancelled.";
      } else if (getCredentialException instanceof NoCredentialException) {
        str = "Add a Google account in Android Settings, or use email sign-in.";
      } else {
        str = "Google sign-in couldn’t start. Check Google Play services and your connection.";
      }
      listener.failed(str);
    }
  }

  static void clear(Activity activity) {
    try {
      FirebaseAuth.getInstance().signOut();
      CredentialManager.create(activity)
          .clearCredentialStateAsync(
              new ClearCredentialStateRequest(),
              (CancellationSignal) null,
              ContextCompat.getMainExecutor(activity),
              new CredentialManagerCallback<Void, ClearCredentialException>() {
                public void onError(ClearCredentialException clearCredentialException) {}

                public void onResult(Void r1) {}
              });
    } catch (Exception unused) {
    }
  }
}
