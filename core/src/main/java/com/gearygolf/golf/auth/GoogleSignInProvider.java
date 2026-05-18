package com.gearygolf.golf.auth;

/**
 * Platform bridge for Google Sign-In.
 * Implemented by AndroidLauncher; null on desktop (button is hidden).
 */
public interface GoogleSignInProvider {

    /**
     * Launch the platform's Google sign-in flow. The callback is always
     * invoked on the LibGDX GL thread via Gdx.app.postRunnable.
     */
    void startSignIn(Callback callback);

    interface Callback {
        void onSuccess(String googleIdToken);
        void onFailure(String error);
    }
}
