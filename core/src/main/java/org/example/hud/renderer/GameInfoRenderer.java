package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.GameConfig;
import org.example.ball.Ball;
import org.example.ball.CompetitiveScore;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

public class GameInfoRenderer {
    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, GameConfig config,
                       boolean isPractice, LevelData levelData, Club club, Ball ball,
                       CompetitiveScore compScore, Terrain terrain, int shotCount) {

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        float baseScale = (screenH * 0.0015f) * (isAndroid ? 1.5f : 1.0f);

        // --- TOP LEFT: MISSION / SCORE INFO ---
        float marginX = screenW * 0.02f;
        float currentY = isAndroid ? screenH * 0.97f : screenH * 0.96f;
        float lineSpacing = isAndroid ? screenH * 0.06f : screenH * 0.045f;

        if (isPractice) {
            font.getData().setScale(baseScale * 0.8f);
            drawShadowedText(batch, font, "PRACTICE RANGE", marginX, currentY, Color.CYAN);
        } else if (levelData != null) {
            font.getData().setScale(baseScale * 0.85f);
            String header = (levelData.getArchetype() != null ? levelData.getArchetype().name().replace("_", " ") : "COURSE") + " | PAR " + levelData.getPar();
            drawShadowedText(batch, font, header, marginX, currentY, Color.GOLD);

            currentY -= lineSpacing;
            font.getData().setScale(baseScale * 0.75f);

            if (isAndroid) {
                float currentX = marginX;
                if (compScore != null) {
                    String scoreStr = "HOLE " + compScore.getCurrentHoleNumber() + " | " + compScore.getToParString();
                    layout.setText(font, scoreStr);
                    drawShadowedText(batch, font, scoreStr, currentX, currentY, Color.YELLOW);
                    currentX += layout.width + (screenW * 0.04f);
                }
                drawShadowedText(batch, font, "SHOTS: " + shotCount, currentX, currentY, Color.WHITE);
            } else {
                if (compScore != null) {
                    String scoreStr = "HOLE " + compScore.getCurrentHoleNumber() + " | " + compScore.getToParString();
                    drawShadowedText(batch, font, scoreStr, marginX, currentY, Color.YELLOW);
                    currentY -= lineSpacing;
                }
                drawShadowedText(batch, font, "SHOTS: " + shotCount, marginX, currentY, Color.WHITE);
            }
        }

        // --- BOTTOM RIGHT: CLUB & PHYSICS INFO ---
        float rightMarginX = screenW * 0.98f;
        float bottomY = screenH * 0.06f;

        float debugScale = baseScale * 0.5f;
        float debugLineSpacing = lineSpacing * 0.6f;
        renderDebugInfo(batch, font, config, ball, terrain, rightMarginX, bottomY, bottomY + debugLineSpacing, debugScale);

        font.getData().setScale(baseScale * 1.0f);
        String clubName = club.name().replace("_", " ");
        layout.setText(font, clubName);

        float clubYOffset = isAndroid ? (lineSpacing * 2.05f) : (lineSpacing * 1.8f);
        drawShadowedText(batch, font, clubName, rightMarginX - layout.width, bottomY + clubYOffset, Color.WHITE);

        font.getData().setScale(1.0f);
    }

    private void renderDebugInfo(SpriteBatch batch, BitmapFont font, GameConfig config, Ball ball,
                                 Terrain terrain, float rightAnchorX, float speedY, float spinY, float scale) {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) return;

        font.getData().setScale(scale);
        String speedStr = String.format("SPEED: %.1fx", config.getGameSpeed());
        layout.setText(font, speedStr);
        drawShadowedText(batch, font, speedStr, rightAnchorX - layout.width, speedY, Color.LIGHT_GRAY);

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
        drawShadowedText(batch, font, spinStr, rightAnchorX - layout.width, spinY, (currentSpinMag < 0.1f) ? Color.GRAY : typeColor);
    }

    private void drawShadowedText(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color) {
        float alpha = color.a;
        float offset = font.getScaleX() * 1.1f;
        font.setColor(0, 0, 0, alpha * 0.6f);
        font.draw(batch, text, x + offset, y - offset);
        font.setColor(color);
        font.draw(batch, text, x, y);
    }
}