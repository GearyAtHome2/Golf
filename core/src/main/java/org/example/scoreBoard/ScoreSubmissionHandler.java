package org.example.scoreBoard;

import com.badlogic.gdx.Gdx;
import org.example.Platform;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import org.example.auth.AuthService;
import org.example.auth.UserSession;
import org.example.hud.UIUtils;
import org.example.session.GameSession;

public class ScoreSubmissionHandler {
    private final GameSession session;
    private final CourseType courseType;
    private final HighscoreService highscoreService;
    private final Runnable onSubmitSuccess;
    private final Runnable onCancel;
    private final String displayName;
    private final String uid;
    private String currentIdToken;
    private final DailySubmissionCache dailyCache;
    private final AuthService authService;
    private final UserSession userSession;

    // Status overlay state
    private Stage stage;
    private Skin skin;
    private String pendingUsername;
    private Table statusOverlay;
    private Label statusLabel;

    public ScoreSubmissionHandler(GameSession session, String displayName, String uid, String idToken, DailySubmissionCache dailyCache, AuthService authService, UserSession userSession, Runnable onSubmitSuccess, Runnable onCancel) {
        this.session = session;
        this.courseType = CourseType.fromMode(session.getMode());
        this.onSubmitSuccess = onSubmitSuccess;
        this.onCancel = onCancel;
        this.highscoreService = new HighscoreService();
        this.displayName = displayName != null ? displayName.trim() : "";
        this.uid = uid != null ? uid : "";
        this.currentIdToken = idToken != null ? idToken : "";
        this.dailyCache = dailyCache;
        this.authService = authService;
        this.userSession = userSession;
    }

    public void trigger(Stage stage, Skin skin) {
        this.stage = stage;
        this.skin = skin;

        if (!displayName.isEmpty()) {
            if (dailyCache != null && dailyCache.isFetched() && dailyCache.hasSubmitted(courseType)) {
                Gdx.app.error("SCORE_SUBMIT", "Blocked duplicate submission for " + courseType + " (uid: " + uid + ")");
                if (onCancel != null) onCancel.run();
                return;
            }
            pendingUsername = displayName;
            showSubmittingOverlay();
            submit(displayName, false);
            return;
        }

        Gdx.app.log("SCORE_SUBMIT", "Triggered ScoreSubmissionHandler");

        if (!Platform.isAndroid()) {
            Gdx.input.setCursorCatched(false);
        }

        final TextField nameField = new TextField("", skin);
        nameField.setMessageText("Username");
        nameField.setMaxLength(30);

        final Dialog dialog = new Dialog("", skin);
        dialog.setMovable(false);

        TextButton subBtn = new TextButton("SUBMIT", skin);
        TextButton canBtn = new TextButton("CANCEL", skin);

        Runnable performSubmit = () -> {
            String text = nameField.getText().trim();
            if (!text.isEmpty()) {
                Gdx.input.setOnscreenKeyboardVisible(false);
                dialog.hide();
                dialog.remove();
                pendingUsername = text;
                showSubmittingOverlay();
                submit(text, false);
            }
        };

        nameField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER) { performSubmit.run(); return true; }
                if (keycode == Input.Keys.ESCAPE) { cancel(dialog); return true; }
                return false;
            }
        });

        canBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                cancel(dialog);
            }
        });

        subBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                performSubmit.run();
            }
        });

        String dialogTitle = buildDialogTitle();

        Table content = dialog.getContentTable();
        content.clear();
        content.pad(6);
        content.add(dialogTitle).colspan(3).padBottom(3).row();
        content.add("Enter name for leaderboard:").colspan(3).padBottom(7).row();
        content.add(canBtn).width(200).height(70).padRight(10);
        content.add(nameField).width(380).height(70);
        content.add(subBtn).width(200).height(70).padLeft(10);

        stage.addActor(dialog);
        dialog.invalidateHierarchy();
        dialog.pack();

        dialog.setWidth(stage.getWidth() * 0.98f);
        dialog.setPosition(stage.getWidth() / 2f, stage.getHeight(), Align.top);

        stage.setKeyboardFocus(nameField);
        Gdx.input.setOnscreenKeyboardVisible(true);
    }

    /**
     * Submits silently with no UI. Used for background auto-retry on launch.
     * On success calls onSubmitSuccess; on failure calls onCancel silently.
     */
    public void triggerSilent() {
        if (dailyCache != null && dailyCache.isFetched() && dailyCache.hasSubmitted(courseType)) return;
        if (displayName.isEmpty()) return;
        submit(displayName, false);
    }

    // ── Overlay helpers ───────────────────────────────────────────────────────

    /** Builds the shared dim backdrop + gold-bordered box, sets statusLabel, and returns the inner box. */
    private Table buildStatusOverlay(String labelText, Color labelColor) {
        if (statusOverlay != null) statusOverlay.remove();
        statusOverlay = new Table();
        statusOverlay.setFillParent(true);
        statusOverlay.setBackground(UIUtils.createRoundedRectDrawable(new Color(0, 0, 0, 0.6f), 0));
        Table box = new Table();
        box.setBackground(UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3));
        box.pad(30, 60, 30, 60);
        statusLabel = new Label(labelText, skin);
        statusLabel.setFontScale(1.8f);
        statusLabel.setColor(labelColor);
        box.add(statusLabel).padBottom(24).row();
        statusOverlay.add(box);
        stage.addActor(statusOverlay);
        return box;
    }

    /** Shows a centred status panel over a dim full-screen backdrop. */
    private void showSubmittingOverlay() {
        if (stage == null) return;
        buildStatusOverlay("SUBMITTING...", Color.WHITE);
    }

    /** Replaces the overlay content with a failure message and Retry/Cancel buttons. */
    private void showFailureOverlay() {
        if (stage == null) { if (onCancel != null) onCancel.run(); return; }
        Table box = buildStatusOverlay("SUBMISSION FAILED", Color.RED);

        TextButton retryBtn  = new TextButton("RETRY",  skin);
        TextButton cancelBtn = new TextButton("CANCEL", skin);

        retryBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                showSubmittingOverlay();
                submit(pendingUsername, false);
            }
        });
        cancelBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (statusOverlay != null) { statusOverlay.remove(); statusOverlay = null; }
                if (onCancel != null) onCancel.run();
            }
        });

        box.add(retryBtn).width(200).height(70).padRight(20);
        box.add(cancelBtn).width(200).height(70);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String buildDialogTitle() {
        return switch (courseType) {
            case HOLES_1  -> "DAILY 1-HOLE SUBMISSION";
            case HOLES_9  -> "DAILY 9-HOLE SUBMISSION";
            case HOLES_18 -> "DAILY 18-HOLE SUBMISSION";
        };
    }

    private void cancel(Dialog dialog) {
        Gdx.app.log("SCORE_SUBMIT", "Cancel action triggered");
        Gdx.input.setOnscreenKeyboardVisible(false);
        dialog.hide();
        dialog.remove();
        if (onCancel != null) onCancel.run();
    }

    private void submit(String username, boolean isRetry) {
        int finalScore = session.getCompetitiveScore().getTotalStrokes();
        String difficulty = session.getDifficulty().name();
        float elapsedTime = session.getElapsedTimeSeconds();
        int[] pars = session.getCompetitiveScore().getPars();
        int[] scores = session.getCompetitiveScore().getScores();

        highscoreService.submitScore(username, uid, currentIdToken, finalScore, difficulty, courseType, elapsedTime, pars, scores, new HighscoreService.SubmitCallback() {
            @Override
            public void onSuccess() {
                session.markSubmitted();
                if (dailyCache != null) dailyCache.markSubmitted(courseType);
                Gdx.app.postRunnable(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("SCORE SUBMITTED!");
                        statusLabel.setColor(Color.GREEN);
                    }
                    final Table overlay = statusOverlay;
                    if (overlay != null) {
                        overlay.addAction(Actions.sequence(
                            Actions.delay(1.5f),
                            Actions.run(() -> {
                                overlay.remove();
                                if (statusOverlay == overlay) statusOverlay = null;
                                if (onSubmitSuccess != null) onSubmitSuccess.run();
                            })
                        ));
                    } else {
                        if (onSubmitSuccess != null) onSubmitSuccess.run();
                    }
                });
            }
            @Override
            public void onFailure(int statusCode) {
                if ((statusCode == 401 || statusCode == 403) && !isRetry && authService != null && userSession != null) {
                    Gdx.app.log("SCORE_SUBMIT", "Token expired (" + statusCode + ") — refreshing and retrying");
                    authService.refreshToken(userSession.getRefreshToken(), new AuthService.AuthCallback() {
                        @Override
                        public void onSuccess(AuthService.AuthResult r) {
                            userSession.updateIdToken(r.idToken, r.refreshToken);
                            currentIdToken = r.idToken;
                            submit(username, true);
                        }
                        @Override
                        public void onFailure(String msg) {
                            Gdx.app.error("SCORE_SUBMIT", "Token refresh failed: " + msg);
                            Gdx.app.postRunnable(() -> showFailureOverlay());
                        }
                    });
                } else {
                    Gdx.app.error("SCORE_SUBMIT", "Submission failed (status " + statusCode + ")" + (isRetry ? " after token refresh" : ""));
                    Gdx.app.postRunnable(() -> showFailureOverlay());
                }
            }
        });
    }
}
