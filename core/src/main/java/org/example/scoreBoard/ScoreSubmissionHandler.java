package org.example.scoreBoard;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import org.example.auth.AuthService;
import org.example.auth.UserSession;
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

    public ScoreSubmissionHandler(GameSession session, CourseType courseType, String displayName, String uid, String idToken, DailySubmissionCache dailyCache, AuthService authService, UserSession userSession, Runnable onSubmitSuccess, Runnable onCancel) {
        this.session = session;
        this.courseType = courseType;
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
        if (!displayName.isEmpty()) {
            if (dailyCache != null && dailyCache.isFetched() && dailyCache.hasSubmitted(courseType)) {
                Gdx.app.error("SCORE_SUBMIT", "Blocked duplicate submission for " + courseType + " (uid: " + uid + ")");
                if (onCancel != null) onCancel.run();
                return;
            }
            submit(displayName, false);
            return;
        }

        Gdx.app.log("SCORE_SUBMIT", "Triggered ScoreSubmissionHandler");

        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            Gdx.input.setCursorCatched(false);
        }

        final TextField nameField = new TextField("", skin);
        nameField.setMessageText("Username");

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
                if (dailyCache != null) dailyCache.markSubmitted(courseType);
                if (onSubmitSuccess != null) onSubmitSuccess.run();
            }
            @Override
            public void onFailure(int statusCode) {
                if (statusCode == 403 && !isRetry && authService != null && userSession != null) {
                    Gdx.app.log("SCORE_SUBMIT", "Token expired (403) — refreshing and retrying");
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
                            if (onCancel != null) onCancel.run();
                        }
                    });
                } else {
                    Gdx.app.error("SCORE_SUBMIT", "Submission failed (status " + statusCode + ")" + (isRetry ? " after token refresh" : ""));
                    if (onCancel != null) onCancel.run();
                }
            }
        });
    }
}
