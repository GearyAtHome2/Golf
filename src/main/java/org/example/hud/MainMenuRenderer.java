package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MainMenuRenderer {
    private float pulseTimer = 0;

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        // --- SEED VALIDATION ---
        String clipboardContent = Gdx.app.getClipboard().getContents();
        boolean hasValidSeed = false;
        if (clipboardContent != null && !clipboardContent.isEmpty()) {
            try {
                Long.parseLong(clipboardContent.trim());
                hasValidSeed = true;
            } catch (NumberFormatException ignored) {}
        }

        // --- THE TITLE FIX ---
        // 1. Temporarily switch to Nearest filter to stop the "bleed" from other letters
        Texture fontTexture = font.getRegion().getTexture();
        Texture.TextureFilter originalMin = fontTexture.getMinFilter();
        Texture.TextureFilter originalMag = fontTexture.getMagFilter();
        fontTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        float pulse = 3.0f + MathUtils.sin(pulseTimer * 2f) * 0.15f;
        font.getData().setScale(pulse);

        String title = "GEARY GOLF";
        float titleX = (float)Math.floor(centerX - (180 * (pulse / 3.0f)));
        float titleY = (float)Math.floor(centerY + 180);

        // Draw shadow and title
        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, titleX + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, titleX, titleY);

        // 2. Switch back to original filtering for the rest of the HUD/Options
        fontTexture.setFilter(originalMin, originalMag);

        // --- MENU OPTIONS ---
        font.getData().setScale(1.5f);
        drawOption(batch, font, selection == 0, true, "PLAY GAME", centerX - 80, centerY + 80);
        drawOption(batch, font, selection == 1, true, "18 HOLES (COMPETITIVE)", centerX - 80, centerY + 40);
        drawOption(batch, font, selection == 2, true, "INSTRUCTIONS", centerX - 80, centerY);
        drawOption(batch, font, selection == 3, true, "PRACTICE RANGE", centerX - 80, centerY - 40);
        drawOption(batch, font, selection == 4, true, "PUTTING GREEN", centerX - 80, centerY - 80);

        String seedText = hasValidSeed ? "PLAY SEED [" + clipboardContent.trim() + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawOption(batch, font, selection == 5, hasValidSeed, seedText, centerX - 80, centerY - 120);

        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 240);

        batch.end();
    }

    private void drawOption(SpriteBatch batch, BitmapFont font, boolean selected, boolean enabled, String text, float x, float y) {
        Color targetColor = !enabled ? Color.DARK_GRAY : (selected ? Color.YELLOW : Color.WHITE);

        font.setColor(0, 0, 0, 0.7f);
        font.draw(batch, (selected && enabled ? "> " : "  ") + text, x + 1, y - 1);
        font.setColor(targetColor);
        font.draw(batch, (selected && enabled ? "> " : "  ") + text, x, y);
    }
}