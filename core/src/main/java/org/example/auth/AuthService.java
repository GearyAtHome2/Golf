package org.example.auth;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Firebase Authentication via REST API.
 * Mirrors the pattern used in HighscoreService — plain HTTP via Gdx.net, no SDK required.
 *
 * Endpoints used:
 *   signUp / signIn  — identitytoolkit.googleapis.com
 *   refreshToken     — securetoken.googleapis.com
 *   sendOobCode      — identitytoolkit.googleapis.com (password reset email)
 *   update           — identitytoolkit.googleapis.com (set display name after sign-up)
 */
public class AuthService {

    private static final String API_KEY   = "AIzaSyAgtF4QdIY1IsxMvYKUHGi8SVT-ZQzLsDI";
    private static final String AUTH_BASE = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String TOKEN_URL = "https://securetoken.googleapis.com/v1/token?key=" + API_KEY;

    // -------------------------------------------------------------------------
    // Public result / callback types
    // -------------------------------------------------------------------------

    public static class AuthResult {
        public final String uid;
        public final String idToken;
        public final String refreshToken;
        public final String email;
        public final String displayName;

        public AuthResult(String uid, String idToken, String refreshToken, String email, String displayName) {
            this.uid          = uid;
            this.idToken      = idToken;
            this.refreshToken = refreshToken;
            this.email        = email;
            this.displayName  = displayName;
        }
    }

    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onFailure(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String message);
    }

    // -------------------------------------------------------------------------
    // Sign up (email + password + display name)
    // -------------------------------------------------------------------------

    public void signUp(String email, String password, String displayName, AuthCallback callback) {
        String body = "{\"email\":\"" + escape(email) + "\","
                    + "\"password\":\"" + escape(password) + "\","
                    + "\"returnSecureToken\":true}";

        post(AUTH_BASE + "signUp", body, response -> {
            String uid          = response.getString("localId",      "");
            String idToken      = response.getString("idToken",      "");
            String refreshToken = response.getString("refreshToken", "");

            // After account creation, set the display name then return.
            setDisplayName(idToken, displayName, uid, refreshToken, email, displayName, callback);
        }, msg -> callback.onFailure(msg));
    }

    // -------------------------------------------------------------------------
    // Sign in (email + password)
    // -------------------------------------------------------------------------

    public void signIn(String email, String password, AuthCallback callback) {
        String body = "{\"email\":\"" + escape(email) + "\","
                    + "\"password\":\"" + escape(password) + "\","
                    + "\"returnSecureToken\":true}";

        post(AUTH_BASE + "signInWithPassword", body, response -> {
            AuthResult result = new AuthResult(
                response.getString("localId",      ""),
                response.getString("idToken",      ""),
                response.getString("refreshToken", ""),
                response.getString("email",        ""),
                response.getString("displayName",  "")
            );
            Gdx.app.postRunnable(() -> callback.onSuccess(result));
        }, msg -> callback.onFailure(msg));
    }

    // -------------------------------------------------------------------------
    // Refresh an expired ID token using the long-lived refresh token
    // -------------------------------------------------------------------------

    public void refreshToken(String refreshToken, AuthCallback callback) {
        // Token endpoint uses form-encoded body, not JSON.
        String body = "grant_type=refresh_token&refresh_token=" + urlEncode(refreshToken);

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(TOKEN_URL);
        request.setContent(body);
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                String raw = r.getResultAsString();
                if (r.getStatus().getStatusCode() != 200) {
                    Gdx.app.postRunnable(() -> callback.onFailure("Session expired — please log in again."));
                    return;
                }
                try {
                    JsonValue json = new JsonReader().parse(raw);
                    // Token refresh response uses snake_case keys.
                    String newIdToken      = json.getString("id_token",      "");
                    String newRefreshToken = json.getString("refresh_token", "");
                    String uid             = json.getString("user_id",       "");
                    // Display name is not returned here; caller should preserve it from UserSession.
                    AuthResult result = new AuthResult(uid, newIdToken, newRefreshToken, "", "");
                    Gdx.app.postRunnable(() -> callback.onSuccess(result));
                } catch (Exception e) {
                    Gdx.app.postRunnable(() -> callback.onFailure("Failed to refresh session."));
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> callback.onFailure("Network error."));
            }
            @Override public void cancelled() {}
        });
    }

    // -------------------------------------------------------------------------
    // Send password reset email
    // -------------------------------------------------------------------------

    public void sendPasswordReset(String email, SimpleCallback callback) {
        String body = "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"" + escape(email) + "\"}";

        post(AUTH_BASE + "sendOobCode", body,
            response -> Gdx.app.postRunnable(callback::onSuccess),
            msg      -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // Internal: set display name after sign-up
    // -------------------------------------------------------------------------

    private void setDisplayName(String idToken, String displayName,
                                String uid, String refreshToken, String email,
                                String name, AuthCallback callback) {
        String body = "{\"idToken\":\"" + escape(idToken) + "\","
                    + "\"displayName\":\"" + escape(displayName) + "\","
                    + "\"returnSecureToken\":false}";

        post(AUTH_BASE + "update", body, response -> {
            AuthResult result = new AuthResult(uid, idToken, refreshToken, email, name);
            Gdx.app.postRunnable(() -> callback.onSuccess(result));
        }, msg -> {
            // Account was created even if the display name update failed — still succeed.
            Gdx.app.log("AuthService", "Display name update failed: " + msg);
            AuthResult result = new AuthResult(uid, idToken, refreshToken, email, displayName);
            Gdx.app.postRunnable(() -> callback.onSuccess(result));
        });
    }

    // -------------------------------------------------------------------------
    // Internal: shared POST helper
    // -------------------------------------------------------------------------

    private void post(String url, String body, SuccessHandler onSuccess, FailureHandler onFailure) {
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url + "?key=" + API_KEY);
        request.setContent(body);
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                String raw    = r.getResultAsString();
                int    status = r.getStatus().getStatusCode();
                try {
                    JsonValue json = new JsonReader().parse(raw);
                    if (status < 200 || status >= 300) {
                        String msg = extractErrorMessage(json);
                        Gdx.app.error("AuthService", "HTTP " + status + ": " + raw);
                        Gdx.app.postRunnable(() -> onFailure.fail(msg));
                    } else {
                        onSuccess.handle(json);
                    }
                } catch (Exception e) {
                    Gdx.app.postRunnable(() -> onFailure.fail("Unexpected server response."));
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> onFailure.fail("Network error — check your connection."));
            }
            @Override public void cancelled() {}
        });
    }

    private interface SuccessHandler {
        void handle(JsonValue response);
    }

    private interface FailureHandler {
        void fail(String message);
    }

    // -------------------------------------------------------------------------
    // Internal: map Firebase error codes to readable messages
    // -------------------------------------------------------------------------

    private String extractErrorMessage(JsonValue json) {
        try {
            String code = json.get("error").get("message").asString();
            switch (code) {
                case "EMAIL_EXISTS":            return "An account with that email already exists.";
                case "EMAIL_NOT_FOUND":         return "No account found with that email.";
                case "INVALID_LOGIN_CREDENTIALS": return "Incorrect email or password.";
                case "INVALID_PASSWORD":        return "Incorrect password.";
                case "INVALID_EMAIL":           return "Please enter a valid email address.";
                case "WEAK_PASSWORD : Password should be at least 6 characters":
                case "WEAK_PASSWORD":           return "Password must be at least 6 characters.";
                case "USER_DISABLED":           return "This account has been disabled.";
                case "TOO_MANY_ATTEMPTS_TRY_LATER": return "Too many attempts — please try again later.";
                default:                        return "Something went wrong. Please try again.";
            }
        } catch (Exception e) {
            return "Something went wrong. Please try again.";
        }
    }

    // -------------------------------------------------------------------------
    // Internal: minimal JSON string escaping
    // -------------------------------------------------------------------------

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String urlEncode(String s) {
        if (s == null) return "";
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
