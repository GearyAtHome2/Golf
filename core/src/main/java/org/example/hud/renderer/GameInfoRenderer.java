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
        
        float topY = viewport.getWorldHeight() - 40;
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        font.getData().setScale(1.5f);

        // --- Left Side: Level/Score Info ---
        if (isPractice) {
            drawShadowedText(batch, font, "PRACTICE", 40, topY, Color.CYAN);
        } else if (levelData != null) {
            String header = levelData.getArchetype().name().replace("_", " ") + 
                           (compScore != null ? " - HOLE " + compScore.getCurrentHoleNumber() : "");
            drawShadowedText(batch, font, header, 40, topY, Color.GOLD);
            drawShadowedText(batch, font, "Par " + levelData.getPar(), 40, topY - 40, Color.WHITE);
            if (compScore != null) {
                drawShadowedText(batch, font, "TO PAR: " + compScore.getToParString(), 40, topY - 80, Color.YELLOW);
            }
        }

        float shotsY = (!isPractice && levelData != null) ? (compScore != null ? topY - 120 : topY - 80) : topY - 40;
        drawShadowedText(batch, font, "Shots: " + shotCount, 40, shotsY, Color.WHITE);

        // --- Right Side: Club/Spin Info ---
        float rightX = viewport.getWorldWidth() - 250;
        if (!isAndroid) {
            drawShadowedText(batch, font, "Club: " + club.name().replace("_", " "), rightX, 140, Color.WHITE);
        } else {
            font.getData().setScale(1.8f);
            drawShadowedText(batch, font, club.name().replace("_", " "), rightX + 50, 180, Color.WHITE);
        }

        // Spin Logic Calculation
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
        drawShadowedText(batch, font, spinStr, rightX, 100, (currentSpinMag < 0.1f) ? Color.GRAY : typeColor);
        drawShadowedText(batch, font, String.format("Speed: %.1fx", config.getGameSpeed()), rightX, 60, Color.WHITE);
    }

    private void drawShadowedText(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color) {
        float alpha = color.a;
        font.setColor(0, 0, 0, alpha * 0.7f);
        font.draw(batch, text, x + 1, y - 1);
        font.setColor(color);
        font.draw(batch, text, x, y);
    }
}