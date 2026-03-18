package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, GameConfig config,
                       boolean isPractice, LevelData levelData, Club club, Ball ball,
                       CompetitiveScore compScore, Terrain terrain, int shotCount) {

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();

        if (isAndroid) {
            // --- ANDROID LAYOUT ---
            float uiScale = Math.max(1.0f, viewport.getWorldHeight() / 720f);
            float topY = viewport.getWorldHeight() - (30 * uiScale);
            float marginX = 30 * uiScale;

            if (isPractice) {
                font.getData().setScale(1.8f * uiScale);
                drawShadowedText(batch, font, "PRACTICE RANGE", marginX, topY, Color.CYAN);
            } else if (levelData != null) {
                font.getData().setScale(2.0f * uiScale);
                String header = String.format("%s: PAR %d", levelData.getArchetype().name().replace("_", " "), levelData.getPar());
                drawShadowedText(batch, font, header, marginX, topY, Color.GOLD);

                if (compScore != null) {
                    font.getData().setScale(1.5f * uiScale);
                    String holeInfo = "HOLE " + compScore.getCurrentHoleNumber() + " | " + compScore.getToParString();
                    drawShadowedText(batch, font, holeInfo, marginX, topY - (50 * uiScale), Color.YELLOW);
                }
            }

            font.getData().setScale(1.6f * uiScale);
            float shotsY = topY - (50 * uiScale);
            drawShadowedText(batch, font, "Shots: " + shotCount, marginX, shotsY, Color.WHITE);

            float rightX = viewport.getWorldWidth() * 0.84f;
            font.getData().setScale(2.4f * uiScale);
            drawShadowedText(batch, font, club.name().replace("_", " "), rightX + 130, 140 * uiScale, Color.WHITE);
            renderDebugInfo(batch, font, config, ball, terrain, rightX + 130, 80 * uiScale, 60 * uiScale, uiScale);

        } else {

            // 1. Level Info (Top Left)
            float headerX = 40;
            float headerY = viewport.getWorldHeight() - 40;

            font.getData().setScale(1.5f);
            if (isPractice) {
                drawShadowedText(batch, font, "PRACTICE", headerX, headerY, Color.CYAN);
            } else if (levelData != null) {
                String header = levelData.getArchetype().name().replace("_", " ") + (compScore != null ? " - HOLE " + compScore.getCurrentHoleNumber() : "");
                drawShadowedText(batch, font, header, headerX, headerY, Color.GOLD);

                font.getData().setScale(1.1f);
                drawShadowedText(batch, font, "Par " + levelData.getPar(), headerX, headerY - 30, Color.WHITE);

                if (compScore != null) {
                    drawShadowedText(batch, font, "TO PAR: " + compScore.getToParString(), headerX, headerY - 55, Color.YELLOW);
                }
            }

            // 2. Shots Label (Directly under the last line of header info)
            font.getData().setScale(1.4f);
            float shotsX = 40;
            float shotsY = (compScore != null) ? headerY - 90 : (levelData != null ? headerY - 65 : headerY - 40);
            drawShadowedText(batch, font, "Shots: " + shotCount, shotsX, shotsY, Color.WHITE);

            // 3. Club Info (Bottom Right - Tucked under INFO button)
            // rightAnchorX: Width minus ~200 should put it under the button stack
            float rightAnchorX = viewport.getWorldWidth() - 210;
            float clubNameY = 135; // Positioned to be clearly visible above spin info

            font.getData().setScale(1.8f);
            drawShadowedText(batch, font, club.name().replace("_", " "), rightAnchorX, clubNameY, Color.WHITE);

            // 4. Spin/Speed Debug Info (Below the Club Name)
            float debugSpinY = 90;
            float debugSpeedY = 60;
            renderDebugInfo(batch, font, config, ball, terrain, rightAnchorX, debugSpinY, debugSpeedY, 1.0f);
        }

        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private void renderDebugInfo(SpriteBatch batch, BitmapFont font, GameConfig config, Ball ball,
                                 Terrain terrain, float x, float spinY, float speedY, float scale) {
        font.getData().setScale(1.0f * scale);
        float currentSpinMag = ball.getSpin().len();
        String spinLabel = "NONE";
        Color typeColor = Color.GRAY;

        if (currentSpinMag > 0.1f) {
            Vector3 norm = terrain.getNormalAt(ball.getPosition().x, ball.getPosition().z);
            tempV1.set(ball.getVelocity()).set(tempV1.x, 0, tempV1.z).nor();
            tempV2.set(norm).crs(tempV1).nor();
            float alignment = ball.getSpin().dot(tempV2);
            if (alignment > 25f) {
                spinLabel = "TOP";
                typeColor = Color.CHARTREUSE;
            } else if (alignment < -25f) {
                spinLabel = "BACK";
                typeColor = Color.ORANGE;
            } else {
                spinLabel = "SIDE";
                typeColor = Color.VIOLET;
            }
        }

        String spinStr = String.format("Spin: %.1fk (%s)", (currentSpinMag * 100f) / 1000f, spinLabel);
        drawShadowedText(batch, font, spinStr, x, spinY, (currentSpinMag < 0.1f) ? Color.GRAY : typeColor);
        drawShadowedText(batch, font, String.format("Speed: %.1fx", config.getGameSpeed()), x, speedY, Color.WHITE);
    }

    private void drawShadowedText(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color) {
        float alpha = color.a;
        font.setColor(0, 0, 0, alpha * 0.7f);
        font.draw(batch, text, x + 1.5f, y - 1.5f);
        font.setColor(color);
        font.draw(batch, text, x, y);
    }
}