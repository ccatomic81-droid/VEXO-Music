package space.vexo.music;

import android.content.Context;
import android.util.Patterns;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

final class AccountAuth {
  static final String KEY = "sb_publishable_0ayG6KO3l8rty85oKuYurw_SN2qL9dH";
  private static final Object LOCK = new Object();
  static final String URL = "https://vjkogwihjvlonnyedflq.supabase.co/auth/v1/";
  private final OkHttpClient client;
  private final PrivateStore vault;

  AccountAuth(Context context) {
    this(
        context,
        new OkHttpClient.Builder()
            .callTimeout(25L, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build());
  }

  AccountAuth(Context context, OkHttpClient okHttpClient) {
    this.vault = new PrivateStore(context, "account_session_v1");
    this.client = okHttpClient;
  }

  String email() {
    return session().optString("email");
  }

  boolean signedIn() {
    return !session().optString("refresh_token").isEmpty();
  }

  static final class Credentials {
    final String token;
    final String userId;

    Credentials(String str, String str2) {
      this.userId = str;
      this.token = str2;
    }
  }

  Credentials credentials() throws IOException {
    Credentials credentials;
    synchronized (LOCK) {
      validate();
      String strOptString = session().optString("access_token");
      if (strOptString.isEmpty()) {
        throw new IOException("Sign in before syncing your library.");
      }
      String strOptString2 = request("GET", "user", null, strOptString).optString("id");
      try {
        UUID.fromString(strOptString2);
        credentials = new Credentials(strOptString2, strOptString);
      } catch (Exception unused) {
        throw new IOException("Couldn’t verify this account.");
      }
    }
    return credentials;
  }

  boolean stillCurrent(Credentials credentials) {
    return credentials.token.equals(session().optString("access_token"));
  }

  private JSONObject session() {
    try {
      return new JSONObject(this.vault.getString("session", "{}"));
    } catch (JSONException unused) {
      return new JSONObject();
    }
  }

  static String cleanEmail(String str) throws IOException {
    String strTrim = str == null ? "" : str.trim();
    if (strTrim.length() > 254 || !Patterns.EMAIL_ADDRESS.matcher(strTrim).matches()) {
      throw new IOException("Enter a valid email address.");
    }
    return strTrim;
  }

  String submit(String str, String str2, String str3) throws IOException {
    String str4;
    String strCleanEmail = cleanEmail(str2);
    if (!str.equals("resend") && (str3 == null || str3.isEmpty())) {
      throw new IOException("Enter your password.");
    }
    if (str.equals("signup") && str3.length() < 8) {
      throw new IOException("Use a password with at least 8 characters.");
    }
    synchronized (LOCK) {
      try {
        JSONObject jSONObjectPut = new JSONObject().put("email", strCleanEmail);
        if (str.equals("resend")) {
          jSONObjectPut.put("type", "signup");
        } else {
          jSONObjectPut.put("password", str3);
        }
        if (str.equals("signup")) {
          str4 = "signup";
        } else {
          str4 = str.equals("resend") ? "resend" : "token?grant_type=password";
        }
        JSONObject jSONObjectRequest = request("POST", str4, jSONObjectPut, "");
        if (str.equals("resend")) {
          return "If confirmation is needed, check your email. Then return here to sign in.";
        }
        if (!jSONObjectRequest.optString("access_token").isEmpty()) {
          save(jSONObjectRequest);
          return "You’re signed in.";
        }
        if (str.equals("signup")) {
          return "Check your email to confirm your account, then return here and sign in. If"
              + " already registered, use Sign in.";
        }
        throw new IOException("Sign-in did not complete. Please try again.");
      } catch (JSONException unused) {
        throw new IOException("The account response could not be read.");
      }
    }
  }

  String google(String str) throws IOException {
    String str2;
    if (str == null || str.length() < 100 || str.length() > 10000) {
      throw new IOException("Google sign-in could not be verified.");
    }
    synchronized (LOCK) {
      try {
        Response responseExecute =
            this.client
                .newCall(
                    new Request.Builder()
                        .url(
                            "https://vjkogwihjvlonnyedflq.supabase.co/functions/v1/firebase-signin")
                        .header("apikey", KEY)
                        .post(
                            RequestBody.create(
                                new JSONObject().put("idToken", str).toString(),
                                MediaType.get("application/json")))
                        .build())
                .execute();
        try {
          if (!responseExecute.isSuccessful()) {
            if (responseExecute.code() == 429) {
              str2 = "Too many Google sign-in attempts. Try again later.";
            } else {
              str2 = "Google sign-in could not finish. Check your connection and try again.";
            }
            throw new IOException(str2);
          }
          String strString = responseExecute.peekBody(100001L).string();
          if (strString.length() > 100000) {
            throw new IOException("The account response is too large.");
          }
          save(new JSONObject(strString));
          if (responseExecute != null) {
            responseExecute.close();
          }
        } catch (Throwable th) {
          if (responseExecute != null) {
            try {
              responseExecute.close();
            } catch (Throwable th2) {
              th.addSuppressed(th2);
            }
          }
          throw th;
        }
      } catch (JSONException unused) {
        throw new IOException("Couldn’t read the Google sign-in response.");
      }
    }
    return "You’re signed in with Google.";
  }

  void validate() throws IOException {
    synchronized (LOCK) {
      JSONObject jSONObjectSession = session();
      if (jSONObjectSession.optString("refresh_token").isEmpty()) {
        return;
      }
      try {
        if (jSONObjectSession.optLong("expires_at") <= (System.currentTimeMillis() / 1000) + 60) {
          save(
              request(
                  "POST",
                  "token?grant_type=refresh_token",
                  new JSONObject()
                      .put("refresh_token", jSONObjectSession.getString("refresh_token")),
                  ""));
          jSONObjectSession = session();
        }
        request("GET", "user", null, jSONObjectSession.getString("access_token"));
      } catch (JSONException unused) {
        throw new IOException("Please sign in again.");
      }
    }
  }

  void signOut() throws IOException {
    synchronized (LOCK) {
      String strOptString = session().optString("access_token");
      if (!this.vault.edit().remove("session").commit()) {
        throw new IOException("Couldn’t clear this session. Try again.");
      }
      if (!strOptString.isEmpty()) {
        try {
          request("POST", "logout?scope=local", new JSONObject(), strOptString);
        } catch (IOException unused) {
        }
      }
    }
  }

  private void save(JSONObject jSONObject) throws JSONException, IOException {
    JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("user");
    if (jSONObjectOptJSONObject == null
        || jSONObjectOptJSONObject.optString("id").isEmpty()
        || jSONObject.optString("access_token").isEmpty()
        || jSONObject.optString("refresh_token").isEmpty()) {
      throw new IOException("The server did not provide a complete session.");
    }
    if (!this.vault
        .edit()
        .putString(
            "session",
            new JSONObject()
                .put("access_token", jSONObject.getString("access_token"))
                .put("refresh_token", jSONObject.getString("refresh_token"))
                .put("email", jSONObjectOptJSONObject.optString("email"))
                .put(
                    "expires_at",
                    (System.currentTimeMillis() / 1000) + jSONObject.optLong("expires_in", 3600L))
                .toString())
        .commit()) {
      throw new IOException("Couldn’t securely save your session. Try again.");
    }
  }

  private JSONObject request(String str, String str2, JSONObject jSONObject, String str3)
      throws IOException {
    Request.Builder builderHeader = new Request.Builder().url(URL + str2).header("apikey", KEY);
    if (!str3.isEmpty()) {
      builderHeader.header("Authorization", "Bearer " + str3);
    }
    if (str.equals("POST")) {
      builderHeader.post(
          RequestBody.create(jSONObject.toString(), MediaType.get("application/json")));
    }
    Response responseExecute = this.client.newCall(builderHeader.build()).execute();
    try {
      String strString =
          responseExecute.body() == null ? "{}" : responseExecute.peekBody(100001L).string();
      if (strString.length() > 100000) {
        throw new IOException("The account response is too large.");
      }
      try {
        JSONObject jSONObject2 = new JSONObject(strString);
        if (!responseExecute.isSuccessful()) {
          throw new IOException(
              error(
                  responseExecute.code(),
                  jSONObject2.optString("error_code", jSONObject2.optString("code"))));
        }
        if (responseExecute != null) {
          responseExecute.close();
        }
        return jSONObject2;
      } catch (JSONException unused) {
        throw new IOException("Account service unavailable. Try again shortly.");
      }
    } catch (Throwable th) {
      if (responseExecute != null) {
        try {
          responseExecute.close();
        } catch (Throwable th2) {
          th.addSuppressed(th2);
        }
      }
      throw th;
    }
  }

  static String error(int i, String str) {
    if (i == 429 || str.contains("rate_limit")) {
      return "Too many attempts. Wait a few minutes and try again.";
    }
    if (str.equals("email_not_confirmed")) {
      return "Confirm your email first, then sign in. You can resend the confirmation below.";
    }
    if (str.equals("user_already_exists") || str.equals("email_exists")) {
      return "An account already exists. Use Sign in.";
    }
    if (str.equals("weak_password")) {
      return "Choose a stronger password with at least 8 characters.";
    }
    if (str.equals("email_address_not_authorized")) {
      return "Email delivery is not ready for this address yet. Continue as a guest and contact the"
          + " developer.";
    }
    if (str.equals("signup_disabled")) {
      return "New accounts are temporarily unavailable. You can continue as a guest.";
    }
    if (i >= 500) {
      return "Account service unavailable. Try again shortly.";
    }
    if (i == 401 || i == 400 || i == 422) {
      return "Couldn’t sign in. Check your email and password, or confirm your email first.";
    }
    return "Couldn’t connect to your account. Check your connection and try again.";
  }
}
