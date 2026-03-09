package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ScoreShout;
import org.example.ball.CompetitiveScore;
import org.example.terrain.level.LevelData;

public class VictoryRenderer {

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, int shots, LevelData levelData, CompetitiveScore compScore) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        batch.begin();
        int par = (levelData != null) ? levelData.getPar() : 3;
        String shout = (shots == 1) ? ScoreShout.HIO.shout : ScoreShout.getShout(shots, par);
        int diff = shots - par;

        Color shoutColor = (shots == 1 || diff <= -2) ? Color.GOLD : (diff == -1 ? Color.CYAN : (diff == 0 ? Color.WHITE : (diff == 1 ? Color.ORANGE : Color.RED)));

        font.getData().setScale(4.5f);
        font.setColor(shoutColor);
        font.draw(batch, shout, centerX - 180, viewport.getWorldHeight() - 60);

        font.getData().setScale(1.8f);
        font.setColor(Color.WHITE);
        String scoreType = (diff == 0) ? "Even Par" : (diff > 0 ? "+" + diff : "" + diff);
        font.draw(batch, "Hole Strokes: " + shots + " (" + scoreType + ")", centerX - 160, viewport.getWorldHeight() - 150);
        batch.end();

        if (compScore != null) {
            renderCompetitiveResults(batch, shapeRenderer, font, viewport, compScore, centerX, centerY);
        } else {
            batch.begin();
            font.getData().setScale(1.5f);
            font.setColor(Color.WHITE);
            font.draw(batch, "Press [N] for New Level", centerX - 120, centerY - 100);
            batch.end();
        }
    }

    private void renderCompetitiveResults(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, CompetitiveScore compScore, float centerX, float centerY) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.75f);

        // Modal is now significantly taller (540px) and centered on Y
        shapeRenderer.rect(centerX - 300, centerY - 270, 600, 540);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        // Start table at centerY + 230 to fit all 18 holes + footer comfortably
        renderScoreTable(batch, font, compScore, centerX, centerY + 230);

        if (compScore.isCourseComplete()) {
            int totalDiff = compScore.getTotalToPar();
            int handicapValue = -totalDiff;
            String handicapStr = (handicapValue > 0) ? "+" + handicapValue : String.valueOf(handicapValue);
            font.getData().setScale(1.8f);
            font.setColor(Color.GOLD);
            font.draw(batch, "YOUR HANDICAP: " + handicapStr, centerX - 150, centerY - 180);
        }

        font.getData().setScale(1.5f);
        font.setColor(Color.YELLOW);
        String prompt = compScore.isCourseComplete() ? "TOURNAMENT COMPLETE! [M] Main Menu" : "Press [N] for Next Hole";
        font.draw(batch, prompt, centerX - 210, centerY - 240);
        batch.end();
    }

    private void renderScoreTable(SpriteBatch batch, BitmapFont font, CompetitiveScore compScore, float x, float y) {
        float startX = x - 260;
        float rowH = 20f; // Restored original spacing

        font.getData().setScale(1.0f); // Restored original scale
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "HOLE", startX, y);
        font.draw(batch, "PAR", startX + 80, y);
        font.draw(batch, "SCORE", startX + 160, y);
        font.draw(batch, "TOTAL", startX + 240, y);

        int runningTotal = 0;
        for (int i = 0; i < 18; i++) {
            float rowY = y - 30 - (i * rowH);
            int holeScore = compScore.getScoreForHole(i);
            int holePar = compScore.getParForHole(i);
            boolean isCurrent = (i == (compScore.getCurrentHoleNumber() - 1));

            font.setColor(isCurrent ? Color.YELLOW : Color.WHITE);
            font.draw(batch, String.format("%02d", (i + 1)), startX, rowY);
            font.draw(batch, "" + holePar, startX + 80, rowY);

            if (holeScore > 0) {
                runningTotal += holeScore;
                int holeDiff = holeScore - holePar;
                font.setColor(holeDiff < 0 ? Color.CYAN : (holeDiff > 0 ? Color.RED : Color.WHITE));
                font.draw(batch, "" + holeScore, startX + 160, rowY);
                font.setColor(isCurrent ? Color.YELLOW : Color.WHITE);
                font.draw(batch, "" + runningTotal, startX + 240, rowY);
            } else {
                font.setColor(Color.DARK_GRAY);
                font.draw(batch, "-", startX + 160, rowY);
                font.draw(batch, "-", startX + 240, rowY);
            }
        }

        font.getData().setScale(1.2f);
        font.setColor(Color.GOLD);
        float totalY = y - 30 - (18 * rowH) - 30;
        font.draw(batch, "TOTAL TO PAR: " + compScore.getToParString(), startX, totalY);
    }
}