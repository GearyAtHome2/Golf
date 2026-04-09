package org.example.auth;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Persists the logged-in user's identity across app launches.
 *
 * What is stored on-device (Preferences):
 *   uid, email, displayName, refreshToken
 *
 * What is kept in memory only:
 *   idToken — Firebase ID tokens expire after 1 hour, so we always refresh
 *   on startup rather than storing a token that may already be stale.
 *
 * Typical lifecycle:
 *   1. App starts → call load() to read prefs into memory.
 *   2. If isLoggedIn(), call tryAutoLogin() to silently exchange the refresh
 *      token for a fresh ID token. User never sees a login screen.
 *   3. On explicit login/sign-up, call save(AuthResult).
 *   4. On explicit logout, call clear().
 */
public class UserSession {

    private static final String PREFS_NAME      = "golf_session";
    private static final String KEY_UID          = "uid";
    private static final String KEY_EMAIL        = "email";
    private static final String KEY_DISPLAY_NAME = "displayName";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";

    // Persisted fields (loaded from prefs on startup)
    private String uid          = "";
    private String email        = "";
    private String displayName  = "";
    private String refreshToken = "";

    // In-memory only — always obtained fresh via tryAutoLogin
    private String idToken = "";

    // -------------------------------------------------------------------------
    // Load / Save / Clear
    // -------------------------------------------------------------------------

    /** Call once on app startup to restore any previously saved session. */
    public void load() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        uid          = prefs.getString(KEY_UID,           "");
        email        = prefs.getString(KEY_EMAIL,         "");
        displayName  = prefs.getString(KEY_DISPLAY_NAME,  "");
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "");
    }

    /** Saves a successful auth result to disk and updates in-memory state. */
    public void save(AuthService.AuthResult result) {
        uid          = result.uid;
        email        = result.email;
        displayName  = result.displayName;
        refreshToken = result.refreshToken;
        idToken      = result.idToken;

        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putString(KEY_UID,           uid);
        prefs.putString(KEY_EMAIL,         email);
        prefs.putString(KEY_DISPLAY_NAME,  displayName);
        prefs.putString(KEY_REFRESH_TOKEN, refreshToken);
        prefs.flush();
    }

    /** Logs the user out and wipes all stored credentials. */
    public void clear() {
        uid          = "";
        email        = "";
        displayName  = "";
        refreshToken = "";
        idToken      = "";

        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.clear();
        prefs.flush();
    }

    // -------------------------------------------------------------------------
    // Auto-login
    // -------------------------------------------------------------------------

    /**
     * If a refresh token is stored, silently exchanges it for a fresh ID token.
     * Call this on startup after load(). The callback fires on the GL thread.
     *
     * On success: idToken is updated, refreshToken is rotated, and the user
     *             is considered fully logged in.
     * On failure: session is cleared (token was revoked or account deleted).
     *             The callback's onFailure signals that the login screen is needed.
     */
    public void tryAutoLogin(AuthService authService, AuthService.AuthCallback callback) {
        if (!isLoggedIn()) {
            callback.onFailure("No saved session.");
            return;
        }

        authService.refreshToken(refreshToken, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(AuthService.AuthResult result) {
                // Refresh token response doesn't include display name or email —
                // carry them forward from what we already have in memory.
                idToken      = result.idToken;
                refreshToken = result.refreshToken;

                // Persist the rotated refresh token.
                Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
                prefs.putString(KEY_REFRESH_TOKEN, refreshToken);
                prefs.flush();

                // Return a full AuthResult so callers have everything in one place.
                AuthService.AuthResult full = new AuthService.AuthResult(
                    uid, idToken, refreshToken, email, displayName);
                callback.onSuccess(full);
            }

            @Override
            public void onFailure(String message) {
                // Token revoked or account deleted — force a fresh login.
                Gdx.app.log("UserSession", "Auto-login failed: " + message);
                clear();
                callback.onFailure(message);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** True if a refresh token is stored — does NOT guarantee the token is still valid. */
    public boolean isLoggedIn()       { return !refreshToken.isEmpty(); }

    public String getRefreshToken()   { return refreshToken; }

    public String getUid()            { return uid; }
    public String getEmail()          { return email; }
    public String getDisplayName()    { return displayName; }
    /** The current Firebase ID token. Empty until tryAutoLogin or save() succeeds. */
    public String getIdToken()        { return idToken; }

    /** Updates the display name in memory and on disk (e.g. after a profile edit). */
    public void setDisplayName(String name) {
        this.displayName = name;
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putString(KEY_DISPLAY_NAME, name);
        prefs.flush();
    }

    /** Called after a token refresh to keep the in-memory ID token current. */
    public void updateIdToken(String newIdToken, String newRefreshToken) {
        this.idToken      = newIdToken;
        this.refreshToken = newRefreshToken;
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putString(KEY_REFRESH_TOKEN, newRefreshToken);
        prefs.flush();
    }
}
