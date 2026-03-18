package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.GameConfig;

public class PauseMenuRenderer {

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, GameConfig config, float seedFeedbackTimer) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        
        float titleScale = isAndroid ? 4.0f : 2.5f;
        float textScale = isAndroid ? 2.0f : 1.2f;
        float spacing = isAndroid ? 80f : 40f;

        // --- Title ---
        font.getData().setScale(titleScale);
        drawShadowedText(batch, font, "PAUSED", centerX - (80 * (titleScale / 2.5f)), viewport.getWorldHeight() - 100, Color.WHITE);

        // --- Desktop Settings List ---
        // Android uses Scene2D Stage for buttons, so we only draw the manual text for Desktop
        if (!isAndroid) {
            font.getData().setScale(textScale);
            drawShadowedText(batch, font, "--- SETTINGS ---", centerX - (80 * (textScale / 1.2f)), centerY + (spacing * 3), Color.YELLOW);
            drawShadowedText(batch, font, "[A] ANIMATION: " + config.animSpeed.name(), centerX - (120 * (textScale / 1.2f)), centerY + (spacing * 2), Color.WHITE);
            drawShadowedText(batch, font, "[D] DIFFICULTY: " + config.difficulty.name(), centerX - (120 * (textScale / 1.2f)), centerY + spacing, Color.WHITE);
            drawShadowedText(batch, font, "[L] PARTICLES: " + (config.particlesEnabled ? "ON" : "OFF"), centerX - (120 * (textScale / 1.2f)), centerY, config.particlesEnabled ? Color.GREEN : Color.RED);

            if (seedFeedbackTimer > 0) {
                font.setColor(0, 1, 0, MathUtils.clamp(seedFeedbackTimer, 0, 1));
                font.draw(batch, "SEED COPIED TO CLIPBOARD", centerX - (120 * (textScale / 1.2f)), centerY - spacing - 20);
            }

            font.setColor(Color.GRAY);
            font.draw(batch, "----------------", centerX - (80 * (textScale / 1.2f)), centerY - spacing);

            font.setColor(Color.WHITE);
            drawShadowedText(batch, font, "[O] CAMERA CONFIG", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 2), Color.WHITE);
            drawShadowedText(batch, font, "[I] INSTRUCTIONS", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 3), Color.WHITE);
            drawShadowedText(batch, font, "[C] COPY SEED", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 4), Color.WHITE);
            drawShadowedText(batch, font, "[M] EXIT TO MAIN MENU", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 5), Color.WHITE);
            drawShadowedText(batch, font, "[ESC] RESUME", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 6), Color.YELLOW);
        }
    }

    private void drawShadowedText(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color) {
        float alpha = color.a;
        font.setColor(0, 0, 0, alpha * 0.7f);
        font.draw(batch, text, x + 1, y - 1);
        font.setColor(color);
        font.draw(batch, text, x, y);
    }
}