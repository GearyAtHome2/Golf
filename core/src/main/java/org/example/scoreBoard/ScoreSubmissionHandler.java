package org.example.scoreBoard;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import org.example.session.GameSession;

public class ScoreSubmissionHandler {
    private final GameSession session;
    private final HighscoreService highscoreService;
    private final Runnable onComplete;
    private boolean isFinished = false; // Prevents double execution

    public ScoreSubmissionHandler(GameSession session, Runnable onComplete) {
        this.session = session;
        this.onComplete = onComplete;
        this.highscoreService = new HighscoreService();
    }

    public void trigger(Stage stage, Skin skin) {
        final TextField nameField = new TextField("", skin);
        nameField.setMessageText("Username");

        final Dialog dialog = new Dialog("Daily Challenge Submission", skin) {
            @Override
            protected void result(Object object) {
                if (isFinished) return;

                Gdx.input.setOnscreenKeyboardVisible(false);

                if (object instanceof Boolean && (Boolean) object) {
                    String text = nameField.getText().trim();
                    if (!text.isEmpty()) {
                        submit(text);
                    }
                }
                finish();
            }
        };

        nameField.setTextFieldListener((textField, c) -> {
            if (isFinished) return;
            if (c == '\n' || c == '\r') {
                String text = textField.getText().trim();
                if (!text.isEmpty()) {
                    submit(text);
                    Gdx.input.setOnscreenKeyboardVisible(false);
                    dialog.hide();
                    finish();
                }
            }
        });

        dialog.getContentTable().add("Enter name for leaderboard:").pad(10);
        dialog.getContentTable().row();
        dialog.getContentTable().add(nameField).width(300).pad(10);
        dialog.button("Submit", true);
        dialog.button("Cancel", false);

        dialog.show(stage);
        stage.setKeyboardFocus(nameField);

        Gdx.input.setOnscreenKeyboardVisible(true);
    }

    private void finish() {
        if (!isFinished) {
            isFinished = true;
            onComplete.run();
        }
    }

    private void submit(String username) {
        int finalScore = session.getCompetitiveScore().getTotalStrokes();
        String difficulty = session.getDifficulty().name();
        highscoreService.submitScore(username, finalScore, difficulty);
    }
}