package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MainMenuRenderer {
    private float pulseTimer = 0;
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        float centerX = screenW / 2f;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        // --- SEED VALIDATION ---
        String clipboardContent = Gdx.app.getClipboard().getContents();
        boolean hasValidSeed = false;
        String cleanSeed = "";
        if (clipboardContent != null && !clipboardContent.isEmpty()) {
            try {
                cleanSeed = clipboardContent.trim();
                Long.parseLong(cleanSeed);
                hasValidSeed = true;
            } catch (NumberFormatException ignored) {}
        }

        // --- TITLE ---
        // Using a tighter base scale (0.0035f) and a smaller Android multiplier (1.2f)
        // to prevent the massive overlap seen in the screenshot.
        float baseTitleScale = (screenH * 0.0035f) * (isAndroid ? 1.2f : 1.0f);
        float pulse = baseTitleScale + MathUtils.sin(pulseTimer * 2f) * (baseTitleScale * 0.03f);
        font.getData().setScale(pulse);

        String title = "GEARY GOLF";
        layout.setText(font, title);

        float titleX = centerX - (layout.width / 2f);
        // Pushed up to 92% height to clear the separate Android button UI
        float titleY = screenH * 0.92f;

        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, titleX + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, titleX, titleY);

        // --- MENU OPTIONS ---
        // Keep the original 0.35f scale for desktop
        float optionScale = baseTitleScale * 0.35f;
        font.getData().setScale(optionScale);

        float spacing = screenH * 0.065f;
        float menuStartY = screenH * 0.55f;

        // ONLY render these on Desktop to avoid overlapping your Android Scene2D/UI buttons
        if (!isAndroid) {
            drawCenteredOption(batch, font, selection == 0, true, "PLAY GAME", centerX, menuStartY);
            drawCenteredOption(batch, font, selection == 1, true, "18 HOLES (COMPETITIVE)", centerX, menuStartY - spacing);
            drawCenteredOption(batch, font, selection == 2, true, "INSTRUCTIONS", centerX, menuStartY - (spacing * 2));
            drawCenteredOption(batch, font, selection == 3, true, "PRACTICE RANGE", centerX, menuStartY - (spacing * 3));
            drawCenteredOption(batch, font, selection == 4, true, "PUTTING GREEN", centerX, menuStartY - (spacing * 4));

            String seedText = hasValidSeed ? "PLAY SEED [" + cleanSeed + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
            drawCenteredOption(batch, font, selection == 5, hasValidSeed, seedText, centerX, menuStartY - (spacing * 5));

            // --- HINT TEXT ---
            font.getData().setScale(optionScale * 0.6f);
            font.setColor(Color.GRAY);
            String hint = "Use UP/DOWN to select, ENTER to start";
            layout.setText(font, hint);
            font.draw(batch, hint, centerX - (layout.width / 2f), menuStartY - (spacing * 6.5f));
        }

        font.getData().setScale(1.0f);
    }

    private void drawCenteredOption(SpriteBatch batch, BitmapFont font, boolean selected, boolean enabled, String text, float centerX, float y) {
        // This is only called on Desktop now
        String prefix = (selected && enabled) ? "> " : "";
        String fullText = prefix + text;

        layout.setText(font, fullText);
        float x = centerX - (layout.width / 2f);

        Color targetColor = !enabled ? Color.DARK_GRAY : (selected ? Color.YELLOW : Color.WHITE);

        font.setColor(0, 0, 0, 0.7f);
        font.draw(batch, fullText, x + 1, y - 1);
        font.setColor(targetColor);
        font.draw(batch, fullText, x, y);
    }
}