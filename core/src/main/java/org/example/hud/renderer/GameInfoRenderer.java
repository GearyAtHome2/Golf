package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import org.example.Platform;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.hud.UIUtils;
import org.example.GameConfig;
import org.example.ball.Ball;
import org.example.session.GameSession;
import org.example.terrain.Terrain;
import org.example.ball.CompetitiveScore;
import org.example.terrain.level.LevelData;

public class GameInfoRenderer {
    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final GlyphLayout layout = new GlyphLayout();

    // Removed compScore parameter; now using session exclusively
    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, GameConfig config,
                       boolean isPractice, LevelData levelData, Club club, Ball ball,
                       GameSession session, Terrain terrain, int shotCount) {

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Platform.isAndroid();

        float baseScale = (screenH * 0.0015f) * (isAndroid ? 1.5f : 1.0f);

        float marginX = screenW * 0.02f;
        float currentY = isAndroid ? screenH * 0.97f : screenH * 0.96f;
        float lineSpacing = isAndroid ? screenH * 0.06f : screenH * 0.045f;

        if (isPractice) {
            font.getData().setScale(baseScale * 0.8f);
            UIUtils.drawShadowedText(batch, font, "PRACTICE RANGE", marginX, currentY, Color.CYAN);
        } else if (levelData != null) {
            font.getData().setScale(baseScale * 0.85f);
            String header = (levelData.getArchetype() != null ? levelData.getArchetype().name().replace("_", " ") : "COURSE") + " | PAR " + levelData.getPar();
            UIUtils.drawShadowedText(batch, font, header, marginX, currentY, Color.GOLD);

            currentY -= lineSpacing;
            font.getData().setScale(baseScale * 0.75f);

            if (session != null) {
                // Calculate "To Par" directly from the session data
                String toParStr = calculateToParString(session);
                String scoreStr = "HOLE " + (session.getCurrentHoleIndex() + 1) + " | " + toParStr;

                if (isAndroid) {
                    layout.setText(font, scoreStr);
                    UIUtils.drawShadowedText(batch, font, scoreStr, marginX, currentY, Color.YELLOW);
                    float shotsX = marginX + layout.width + (screenW * 0.04f);
                    UIUtils.drawShadowedText(batch, font, "SHOTS: " + shotCount, shotsX, currentY, Color.WHITE);
                } else {
                    UIUtils.drawShadowedText(batch, font, scoreStr, marginX, currentY, Color.YELLOW);
                    currentY -= lineSpacing;
                    UIUtils.drawShadowedText(batch, font, "SHOTS: " + shotCount, marginX, currentY, Color.WHITE);
                }
            } else {
                UIUtils.drawShadowedText(batch, font, "SHOTS: " + shotCount, marginX, currentY, Color.WHITE);
            }
        }

        // --- BOTTOM RIGHT: CLUB INFO ---
        renderBottomRightInfo(batch, font, config, ball, club, terrain, screenW, screenH, baseScale, lineSpacing, isAndroid);
    }

    // Only counts completed holes (before currentHoleIndex) so the displayed
    // to-par doesn't fluctuate with each shot taken on the current hole.
    private String calculateToParString(GameSession session) {
        CompetitiveScore cs = session.getCompetitiveScore();
        int toPar = 0;
        for (int i = 0; i < session.getCurrentHoleIndex(); i++) {
            int score = cs.getScoreForHole(i);
            if (score > 0) toPar += score - cs.getParForHole(i);
        }
        if (toPar == 0) return "Even";
        return (toPar > 0 ? "+" : "") + toPar;
    }

    private void renderBottomRightInfo(SpriteBatch batch, BitmapFont font, GameConfig config, Ball ball, Club club,
                                       Terrain terrain, float screenW, float screenH, float baseScale,
                                       float lineSpacing, boolean isAndroid) {
        float rightMarginX = screenW * 0.98f;
        float bottomY = screenH * 0.06f;

        float debugScale = baseScale * 0.5f;
        float debugLineSpacing = lineSpacing * 0.6f;
        renderDebugInfo(batch, font, config, ball, terrain, rightMarginX, bottomY, bottomY + debugLineSpacing, debugScale);

        font.getData().setScale(baseScale * 1.0f);
        String clubName = club.name().replace("_", " ");
        layout.setText(font, clubName);

        float clubYOffset = isAndroid ? (lineSpacing * 2.05f) : (lineSpacing * 1.8f);
        UIUtils.drawShadowedText(batch, font, clubName, rightMarginX - layout.width, bottomY + clubYOffset, Color.WHITE);
        font.getData().setScale(1.0f);
    }

    private void renderDebugInfo(SpriteBatch batch, BitmapFont font, GameConfig config, Ball ball,
                                 Terrain terrain, float rightAnchorX, float speedY, float spinY, float scale) {
        if (Platform.isAndroid()) return;

        font.getData().setScale(scale);
        String speedStr = String.format("SPEED: %.1fx", config.getGameSpeed());
        layout.setText(font, speedStr);
        UIUtils.drawShadowedText(batch, font, speedStr, rightAnchorX - layout.width, speedY, Color.LIGHT_GRAY);

        float currentSpinMag = ball.getSpin().len();
        String spinLabel = "NONE";
        Color typeColor = Color.GRAY;

        if (currentSpinMag > 0.1f) {
            Vector3 norm = terrain.getNormalAt(ball.getPosition().x, ball.getPosition().z);
            tempV1.set(ball.getVelocity()).set(tempV1.x, 0, tempV1.z).nor();
            tempV2.set(norm).crs(tempV1).nor();
            float alignment = ball.getSpin().dot(tempV2);
            if (alignment > 25f) { spinLabel = "TOP"; typeColor = Color.CHARTREUSE; }
            else if (alignment < -25f) { spinLabel = "BACK"; typeColor = Color.ORANGE; }
            else { spinLabel = "SIDE"; typeColor = Color.VIOLET; }
        }

        String spinStr = String.format("SPIN: %.1fk (%s)", (currentSpinMag * 100f) / 1000f, spinLabel);
        layout.setText(font, spinStr);
        UIUtils.drawShadowedText(batch, font, spinStr, rightAnchorX - layout.width, spinY, (currentSpinMag < 0.1f) ? Color.GRAY : typeColor);
    }

}