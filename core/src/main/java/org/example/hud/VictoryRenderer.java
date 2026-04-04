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
import org.example.session.GameSession;
import org.example.terrain.level.LevelData;

public class VictoryRenderer {

    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, int shots, LevelData levelData, GameSession session) {
        float centerX = viewport.getWorldWidth() / 2f;
        float screenH = viewport.getWorldHeight();

        int par = (levelData != null) ? levelData.getPar() : 3;
        String shout = (shots == 1) ? ScoreShout.HIO.shout : ScoreShout.getShout(shots, par);
        int diff = shots - par;

        Color shoutColor;
        if      (shots == 1 || diff <= -3) shoutColor = new Color(0.75f, 0.2f,  1.0f, 1f); // purple  — HIO / Albatross
        else if (diff == -2)               shoutColor = new Color(1.0f,  0.4f,  0.85f, 1f); // pink    — Eagle
        else if (diff == -1)               shoutColor = Color.GREEN;                         // green   — Birdie
        else if (diff == 0)                shoutColor = Color.WHITE;                         // white   — Par
        else                               shoutColor = Color.RED;                           // red     — Bogey+

        String scoreType = (diff == 0) ? "Even Par" : (diff > 0 ? "+" + diff : "" + diff);
        String subText = "Hole Strokes: " + shots + " (" + scoreType + ")";

        // hasModal: competitive 18/9-hole screens that render a score table in a dark box.
        // For these, shout+subtext are drawn INSIDE renderCompetitiveResults, AFTER the modal
        // background, so they appear in front. For all other cases draw them here.
        boolean hasModal = (session != null) && (session.getMode() != GameSession.GameMode.DAILY_1);

        if (!hasModal) {
            // Non-competitive or DAILY_1: text in open screen space (no modal to work around).
            // DAILY_1 needs the same ~0.17 vertical gap as non-competitive so the large shout
            // text doesn't overlap the subtext; it just sits higher to leave room for TIME below.
            float shoutY, subTextY;
            if (session != null && session.getMode() == GameSession.GameMode.DAILY_1) {
                shoutY   = screenH * 0.88f;
                subTextY = screenH * 0.72f; // ~0.16 gap below shout; TIME is at 0.65
            } else if (session == null) {
                shoutY   = screenH * 0.72f;
                subTextY = screenH * 0.55f;
            } else {
                // fallback (shouldn't reach for any current mode)
                shoutY   = screenH * 0.86f;
                subTextY = screenH * 0.81f;
            }

            font.getData().setScale(3.5f);
            font.setColor(shoutColor);
            layout.setText(font, shout);
            font.draw(batch, shout, centerX - (layout.width / 2f), shoutY);

            font.getData().setScale(1.2f);
            font.setColor(Color.WHITE);
            layout.setText(font, subText);
            font.draw(batch, subText, centerX - (layout.width / 2f), subTextY);
        }

        if (session != null) {
            if (session.getMode() == GameSession.GameMode.DAILY_1) {
                renderDaily1Results(batch, font, viewport, session, centerX, screenH);
            } else {
                batch.end();
                renderCompetitiveResults(batch, shapeRenderer, font, viewport, session, centerX, screenH * 0.45f, shout, shoutColor, subText);
                batch.begin();
            }
        } else if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            font.getData().setScale(1.0f);
            font.setColor(Color.YELLOW);
            String prompt = "Press [N] for New Level";
            layout.setText(font, prompt);
            font.draw(batch, prompt, centerX - (layout.width / 2f), screenH * 0.20f);
        }
    }

    private void renderCompetitiveResults(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport,
                                          GameSession session, float centerX, float centerY,
                                          String shout, Color shoutColor, String subText) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.8f);

        float boxWidth  = viewport.getWorldWidth()  * 0.92f;
        float boxHeight = viewport.getWorldHeight() * 0.68f;

        shapeRenderer.rect(centerX - (boxWidth / 2f), centerY - (boxHeight / 2f), boxWidth, boxHeight);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        float worldH   = viewport.getWorldHeight();
        float modalTop = centerY + boxHeight / 2f;

        batch.begin();

        // Shout ("BIRDIE!" etc.): high in the band between modal top and screen top.
        // Drawn here (after the modal rect) so it renders in front of the dark background.
        font.getData().setScale(3.5f);
        font.setColor(shoutColor);
        layout.setText(font, shout);
        float shoutY = modalTop + (worldH - modalTop) * 0.75f;
        font.draw(batch, shout, centerX - (layout.width / 2f), shoutY);

        // Subtext ("Hole Strokes: N (±N)"): just inside the top of the modal, above the table header.
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
        layout.setText(font, subText);
        float subTextY = modalTop - worldH * 0.02f;
        font.draw(batch, subText, centerX - (layout.width / 2f), subTextY);

        renderSplitScoreTable(batch, font, session, viewport, centerX, centerY + (boxHeight * 0.38f));

        float promptY = centerY - (boxHeight * 0.42f);

        if (session.isFinished()) {
            int totalDiff = session.getCompetitiveScore().getTotalToPar();
            // Golf convention: under-par scores give a positive handicap, over-par gives negative
            String handicapStr = (totalDiff <= 0) ? "+" + Math.abs(totalDiff) : String.valueOf(-totalDiff);
            font.getData().setScale(1.3f);
            font.setColor(Color.GOLD);
            String hCapText = "YOUR HANDICAP: " + handicapStr;
            layout.setText(font, hCapText);
            font.draw(batch, hCapText, centerX - (layout.width / 2f), promptY + 55);
        } else {
            font.getData().setScale(1.2f);
            font.setColor(Color.GREEN);
            String totalToPar = "TOTAL TO PAR: " + session.getCompetitiveScore().getToParString();
            layout.setText(font, totalToPar);
            font.draw(batch, totalToPar, centerX - (layout.width / 2f), promptY + 70);
        }

        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            font.getData().setScale(1.0f);
            font.setColor(Color.YELLOW);
            String prompt;
            if (session.isFinished()) {
                if (session.getMode() == GameSession.GameMode.DAILY_18 || session.getMode() == GameSession.GameMode.DAILY_9 || session.getMode() == GameSession.GameMode.DAILY_1) {
                    prompt = "COURSE COMPLETE! [S] Submit Score or [M] Main Menu";
                } else {
                    prompt = "COURSE COMPLETE! [M] Main Menu";
                }
            } else {
                prompt = "HOLE COMPLETE! [N] Next Hole";
            }
            layout.setText(font, prompt);
            font.draw(batch, prompt, centerX - (layout.width / 2f), promptY + 20);
        }
        batch.end();
    }

    private void renderDaily1Results(SpriteBatch batch, BitmapFont font, Viewport viewport, GameSession session, float centerX, float screenH) {
        float lineY = screenH * 0.65f;
        float lineSpacing = screenH * 0.07f;

        font.getData().setScale(1.4f);
        font.setColor(Color.WHITE);

        String timeStr = "TIME: " + session.getElapsedTimeFormatted();
        layout.setText(font, timeStr);
        font.draw(batch, timeStr, centerX - layout.width / 2f, lineY);

        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            font.getData().setScale(1.0f);
            font.setColor(Color.YELLOW);
            String prompt = session.isFinished()
                ? "COURSE COMPLETE! [S] Submit Score or [M] Main Menu"
                : "HOLE COMPLETE! [N] Next Hole";
            layout.setText(font, prompt);
            font.draw(batch, prompt, centerX - layout.width / 2f, lineY - lineSpacing);
        }
    }

    private void renderSplitScoreTable(SpriteBatch batch, BitmapFont font, GameSession session, Viewport viewport, float x, float y) {
        float worldW = viewport.getWorldWidth();

        float colW_Hole  = worldW * 0.08f;
        float colW_Par   = worldW * 0.08f;
        float colW_Score = worldW * 0.09f;
        float colW_Total = worldW * 0.10f;
        float fullColWidth = colW_Hole + colW_Par + colW_Score + colW_Total;

        if (session.getMode() == GameSession.GameMode.DAILY_9) {
            // Single centred column for 9 holes
            float singleColX = x - fullColWidth / 2f;
            drawColumn(batch, font, session, viewport, singleColX, y, 0, 9, colW_Hole, colW_Par, colW_Score, colW_Total);
            return;
        }

        float gutter   = worldW * 0.04f;
        float leftColX = x - fullColWidth - (gutter / 2f);
        float rightColX = x + (gutter / 2f);

        drawColumn(batch, font, session, viewport, leftColX,  y, 0,  9,  colW_Hole, colW_Par, colW_Score, colW_Total);
        drawColumn(batch, font, session, viewport, rightColX, y, 9, 18, colW_Hole, colW_Par, colW_Score, colW_Total);
    }

    private void drawColumn(SpriteBatch batch, BitmapFont font, GameSession session, Viewport viewport, float startX, float y, int startHole, int endHole, float cwH, float cwP, float cwS, float cwT) {
        float worldH = viewport.getWorldHeight();

        float xHole  = startX;
        float xPar   = xHole  + cwH;
        float xScore = xPar   + cwP;
        float xTotal = xScore + cwS;

        font.getData().setScale(0.9f);
        font.setColor(Color.LIGHT_GRAY);

        drawCentered(batch, font, "HOLE",  xHole,  y, cwH);
        drawCentered(batch, font, "PAR",   xPar,   y, cwP);
        drawCentered(batch, font, "SCORE", xScore, y, cwS);
        drawCentered(batch, font, "TOTAL", xTotal, y, cwT);

        int runningTotal = 0;
        for (int j = 0; j < startHole; j++) {
            int s = session.getCompetitiveScore().getScoreForHole(j);
            if (s > 0) runningTotal += s;
        }

        float rowH         = worldH * 0.042f;
        float headerOffset = worldH * 0.05f;

        for (int i = startHole; i < endHole; i++) {
            float rowY      = y - headerOffset - ((i - startHole) * rowH);
            // getCurrentHoleIndex() points to the next hole to play; highlight the one just completed.
            boolean isCurrent = (i == session.getCurrentHoleIndex() - 1);

            int holeScore = session.getCompetitiveScore().getScoreForHole(i);
            int holePar   = session.getCompetitiveScore().getParForHole(i);

            font.setColor(isCurrent ? Color.YELLOW : Color.WHITE);
            font.getData().setScale(0.9f);

            drawCentered(batch, font, String.format("%02d", i + 1), xHole, rowY, cwH);
            drawCentered(batch, font, String.valueOf(holePar),       xPar,  rowY, cwP);

            if (holeScore > 0) {
                runningTotal += holeScore;
                int diff = holeScore - holePar;
                font.setColor(diff < 0 ? Color.CYAN : (diff > 0 ? Color.RED : Color.WHITE));
                drawCentered(batch, font, String.valueOf(holeScore),     xScore, rowY, cwS);

                font.setColor(isCurrent ? Color.YELLOW : Color.WHITE);
                drawCentered(batch, font, String.valueOf(runningTotal), xTotal, rowY, cwT);
            } else {
                font.setColor(Color.DARK_GRAY);
                drawCentered(batch, font, "-", xScore, rowY, cwS);
                drawCentered(batch, font, "-", xTotal, rowY, cwT);
            }
        }
    }

    private void drawCentered(SpriteBatch batch, BitmapFont font, String text, float x, float y, float width) {
        layout.setText(font, text);
        font.draw(batch, text, x + (width / 2f) - (layout.width / 2f), y);
    }
}
