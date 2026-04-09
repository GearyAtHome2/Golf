package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.GameConfig;
import org.example.session.GameSession;

public class PauseMenuRenderer {
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, GameConfig config, float seedFeedbackTimer, GameSession session) {
        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        float centerX = screenW / 2f;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        float baseScale = (screenH * 0.0009f) * (isAndroid ? 1.5f : 1.0f);
        float titleScale = baseScale * 2.5f;
        float textScale = baseScale * 1.0f;
        float spacing = screenH * 0.038f;

        // --- Title ---
        font.getData().setScale(titleScale);
        drawCenteredShadowedText(batch, font, "PAUSED", centerX, screenH * 0.80f, Color.WHITE);

        // --- Desktop Settings List ---
        if (!isAndroid) {
            font.getData().setScale(textScale);
            float currentY = screenH * 0.65f;

            drawCenteredShadowedText(batch, font, "--- SETTINGS ---", centerX, currentY, Color.YELLOW);
            currentY -= spacing;

            drawCenteredShadowedText(batch, font, "[A] ANIMATION: " + config.animSpeed.name(), centerX, currentY, Color.WHITE);
            currentY -= spacing;

            if (session != null) {
                String diffText = "DIFFICULTY: " + session.getDifficulty().name() + " (LOCKED)";
                drawCenteredShadowedText(batch, font, diffText, centerX, currentY, Color.GRAY);
            } else {
                String diffText = "[D] DIFFICULTY: " + config.difficulty.name();
                drawCenteredShadowedText(batch, font, diffText, centerX, currentY, Color.WHITE);
            }

            currentY -= spacing;

            Color particleColor = config.particlesEnabled ? Color.GREEN : Color.RED;
            drawCenteredShadowedText(batch, font, "[L] PARTICLES: " + (config.particlesEnabled ? "ON" : "OFF"), centerX, currentY, particleColor);
            currentY -= (spacing * 0.8f);

            if (seedFeedbackTimer > 0) {
                float alpha = MathUtils.clamp(seedFeedbackTimer, 0, 1f);
                font.setColor(0, 1, 0, alpha);
                drawCenteredText(batch, font, "SEED COPIED TO CLIPBOARD", centerX, currentY - 5);
            }

            currentY -= (spacing * 0.5f);
            drawCenteredShadowedText(batch, font, "----------------", centerX, currentY, Color.GRAY);
            currentY -= spacing;

            drawCenteredShadowedText(batch, font, "[O] CAMERA CONFIG", centerX, currentY, Color.WHITE);
            currentY -= spacing;

            drawCenteredShadowedText(batch, font, "[I] INSTRUCTIONS", centerX, currentY, Color.WHITE);
            currentY -= spacing;

            drawCenteredShadowedText(batch, font, "[C] COPY SEED", centerX, currentY, Color.WHITE);
            currentY -= spacing;

            drawCenteredShadowedText(batch, font, "[M] EXIT TO MAIN MENU", centerX, currentY, Color.WHITE);
            currentY -= spacing;

            drawCenteredShadowedText(batch, font, "[U] LOG OUT", centerX, currentY, Color.WHITE);
            currentY -= (spacing * 1.2f);

            drawCenteredShadowedText(batch, font, "[ESC] RESUME", centerX, currentY, Color.YELLOW);
        }

        font.getData().setScale(1.0f);
    }

    private void drawCenteredShadowedText(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color) {
        layout.setText(font, text);
        float drawX = x - (layout.width / 2f);
        float offset = font.getScaleX() * 1.1f;

        font.setColor(0, 0, 0, color.a * 0.6f);
        font.draw(batch, text, drawX + offset, y - offset);

        font.setColor(color);
        font.draw(batch, text, drawX, y);
    }

    private void drawCenteredText(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
        layout.setText(font, text);
        font.draw(batch, text, x - (layout.width / 2f), y);
    }
}