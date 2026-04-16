package org.example;

import android.content.Intent;
import android.os.Bundle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.example.auth.GoogleSignInProvider;

public class AndroidLauncher extends AndroidApplication implements GoogleSignInProvider {

    /**
     * The Web Client ID (OAuth 2.0 client type "Web application") from your Firebase project.
     * Found at: Firebase Console → Authentication → Sign-in methods → Google
     *            → Web SDK configuration → Web client ID.
     * This is NOT the Android client ID.
     */
    private static final String WEB_CLIENT_ID = "91480313945-r0bdrluhdkc749l6chsovcn0be35l17i.apps.googleusercontent.com";

    private static final int RC_SIGN_IN = 9001;

    private GoogleSignInClient googleSignInClient;
    private GoogleSignInProvider.Callback pendingCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.stencil = 8;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useImmersiveMode = true;

        initialize(new GolfGame(this), config);
    }

    // -------------------------------------------------------------------------
    // GoogleSignInProvider
    // -------------------------------------------------------------------------

    @Override
    public void startSignIn(GoogleSignInProvider.Callback callback) {
        // Sign out first so the account chooser always appears, even for returning users.
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            pendingCallback = callback;
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != RC_SIGN_IN || pendingCallback == null) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        GoogleSignInProvider.Callback cb = pendingCallback;
        pendingCallback = null;

        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken == null) {
                Gdx.app.postRunnable(() -> cb.onFailure("Google sign-in returned no ID token."));
            } else {
                Gdx.app.postRunnable(() -> cb.onSuccess(idToken));
            }
        } catch (ApiException e) {
            String msg = "Google sign-in failed (code " + e.getStatusCode() + ").";
            Gdx.app.postRunnable(() -> cb.onFailure(msg));
        }
    }
}
