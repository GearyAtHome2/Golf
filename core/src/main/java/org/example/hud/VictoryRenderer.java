package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ScoreShout;
import org.example.ball.CompetitiveScore;
import org.example.terrain.level.LevelData;

public class VictoryRenderer {

    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, int shots, LevelData levelData, CompetitiveScore compScore) {
        float centerX = viewport.getWorldWidth() / 2f;
        float screenH = viewport.getWorldHeight();

        int par = (levelData != null) ? levelData.getPar() : 3;
        String shout = (shots == 1) ? ScoreShout.HIO.shout : ScoreShout.getShout(shots, par);
        int diff = shots - par;

        Color shoutColor = (shots == 1 || diff <= -2) ? Color.GOLD : (diff == -1 ? Color.CYAN : (diff == 0 ? Color.WHITE : (diff == 1 ? Color.ORANGE : Color.RED)));

        // 1. TOP TITLE - High and clear
        font.getData().setScale(3.5f);
        font.setColor(shoutColor);
        layout.setText(font, shout);
        font.draw(batch, shout, centerX - (layout.width / 2f), screenH * 0.93f);

        // 2. SUBTITLE - Tucked right under the title
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
        String scoreType = (diff == 0) ? "Even Par" : (diff > 0 ? "+" + diff : "" + diff);
        String subText = "Hole Strokes: " + shots + " (" + scoreType + ")";
        layout.setText(font, subText);
        font.draw(batch, subText, centerX - (layout.width / 2f), screenH * 0.82f);

        if (compScore != null) {
            batch.end();
            renderCompetitiveResults(batch, shapeRenderer, font, viewport, compScore, centerX, screenH * 0.45f);
            batch.begin();
        } else if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            font.getData().setScale(1.0f);
            font.setColor(Color.YELLOW);
            String prompt = "Press [N] for New Level";
            layout.setText(font, prompt);
            font.draw(batch, prompt, centerX - (layout.width / 2f), screenH * 0.20f);
        }
    }

    private void renderCompetitiveResults(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, CompetitiveScore compScore, float centerX, float centerY) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.8f);

        float boxWidth = viewport.getWorldWidth() * 0.85f;
        float boxHeight = viewport.getWorldHeight() * 0.65f;

        // Centered box
        shapeRenderer.rect(centerX - (boxWidth / 2f), centerY - (boxHeight / 2f), boxWidth, boxHeight);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        // Render table near the top of the box
        renderSplitScoreTable(batch, font, compScore, centerX, centerY + (boxHeight * 0.35f));

        float promptY = centerY - (boxHeight * 0.35f);

        if (compScore.isCourseComplete()) {
            int totalDiff = compScore.getTotalToPar();
            String handicapStr = (totalDiff <= 0) ? "+" + Math.abs(totalDiff) : String.valueOf(-totalDiff);
            font.getData().setScale(1.3f);
            font.setColor(Color.GOLD);
            String hCapText = "YOUR HANDICAP: " + handicapStr;
            layout.setText(font, hCapText);
            font.draw(batch, hCapText, centerX - (layout.width / 2f), promptY + 40);
        }

        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            font.getData().setScale(1.0f);
            font.setColor(Color.YELLOW);
            String prompt = compScore.isCourseComplete() ? "TOURNAMENT COMPLETE! [M] Main Menu" : "Press [N] for Next Hole";
            layout.setText(font, prompt);
            font.draw(batch, prompt, centerX - (layout.width / 2f), promptY);
        }
        batch.end();
    }

    private void renderSplitScoreTable(SpriteBatch batch, BitmapFont font, CompetitiveScore compScore, float x, float y) {
        float screenW = Gdx.graphics.getWidth();
        float columnWidth = screenW * 0.35f;
        float leftColX = x - columnWidth;
        float rightColX = x + 40; // Small gutter between columns

        drawColumn(batch, font, compScore, leftColX, y, 0, 9);
        drawColumn(batch, font, compScore, rightColX, y, 9, 18);

        font.getData().setScale(1.2f);
        font.setColor(Color.GOLD);
        String totalToPar = "TOTAL TO PAR: " + compScore.getToParString();
        layout.setText(font, totalToPar);
        // Positioned relative to bottom of table
        font.draw(batch, totalToPar, x - (layout.width / 2f), y - (Gdx.graphics.getHeight() * 0.40f));
    }

    private void drawColumn(SpriteBatch batch, BitmapFont font, CompetitiveScore compScore, float startX, float y, int startHole, int endHole) {
        float screenH = Gdx.graphics.getHeight();
        float rowH = screenH * 0.035f;
        float colSpacing = screenH * 0.08f;

        font.getData().setScale(0.8f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "HOLE", startX, y);
        font.draw(batch, "PAR", startX + colSpacing, y);
        font.draw(batch, "SCORE", startX + colSpacing * 2, y);
        font.draw(batch, "TOTAL", startX + colSpacing * 3, y);

        int runningTotal = 0;
        for(int j = 0; j < startHole; j++) {
            runningTotal += Math.max(0, compScore.getScoreForHole(j));
        }

        for (int i = startHole; i < endHole; i++) {
            float rowY = y - (screenH * 0.04f) - ((i - startHole) * rowH);
            int holeScore = compScore.getScoreForHole(i);
            int holePar = compScore.getParForHole(i);
            boolean isCurrent = (i == (compScore.getCurrentHoleNumber() - 1));

            font.setColor(isCurrent ? Color.YELLOW : Color.WHITE);
            font.draw(batch, String.format("%02d", (i + 1)), startX, rowY);
            font.draw(batch, "" + holePar, startX + colSpacing, rowY);

            if (holeScore > 0) {
                runningTotal += holeScore;
                int holeDiff = holeScore - holePar;
                font.setColor(holeDiff < 0 ? Color.CYAN : (holeDiff > 0 ? Color.RED : Color.WHITE));
                font.draw(batch, "" + holeScore, startX + colSpacing * 2, rowY);
                font.setColor(isCurrent ? Color.YELLOW : Color.WHITE);
                font.draw(batch, "" + runningTotal, startX + colSpacing * 3, rowY);
            } else {
                font.setColor(Color.DARK_GRAY);
                font.draw(batch, "-", startX + colSpacing * 2, rowY);
                font.draw(batch, "-", startX + colSpacing * 3, rowY);
            }
        }
    }
}