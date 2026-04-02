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
import org.example.session.GameSession;

public class ScoreSubmissionHandler {
    private final GameSession session;
    private final CourseType courseType;
    private final HighscoreService highscoreService;
    private final Runnable onSubmitSuccess;
    private final Runnable onCancel;

    public ScoreSubmissionHandler(GameSession session, Runnable onSubmitSuccess, Runnable onCancel) {
        this.session = session;
        this.courseType = CourseType.fromMode(session.getMode());
        this.onSubmitSuccess = onSubmitSuccess;
        this.onCancel = onCancel;
        this.highscoreService = new HighscoreService();
    }

    public ScoreSubmissionHandler(GameSession session, CourseType courseType, Runnable onSubmitSuccess, Runnable onCancel) {
        this.session = session;
        this.courseType = courseType;
        this.onSubmitSuccess = onSubmitSuccess;
        this.onCancel = onCancel;
        this.highscoreService = new HighscoreService();
    }

    public void trigger(Stage stage, Skin skin) {
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
                submit(text);
                dialog.hide();
                dialog.remove();
                if (onSubmitSuccess != null) onSubmitSuccess.run();
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
        content.add(canBtn).width(150).height(70).padRight(10);
        content.add(nameField).width(380).height(70);
        content.add(subBtn).width(150).height(70).padLeft(10);

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
            case HOLES_1 -> "DAILY 1-HOLE SUBMISSION";
            case HOLES_9 -> "DAILY 9-HOLE SUBMISSION";
            default -> "DAILY CHALLENGE SUBMISSION";
        };
    }

    private void cancel(Dialog dialog) {
        Gdx.app.log("SCORE_SUBMIT", "Cancel action triggered");
        Gdx.input.setOnscreenKeyboardVisible(false);
        dialog.hide();
        dialog.remove();
        if (onCancel != null) onCancel.run();
    }

    private void submit(String username) {
        int finalScore = session.getCompetitiveScore().getTotalStrokes();
        String difficulty = session.getDifficulty().name();
        float elapsedTime = session.getElapsedTimeSeconds();
        highscoreService.submitScore(username, finalScore, difficulty, courseType, elapsedTime);
    }
}
