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

        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

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
        Texture fontTexture = font.getRegion().getTexture();
        Texture.TextureFilter originalMin = fontTexture.getMinFilter();
        Texture.TextureFilter originalMag = fontTexture.getMagFilter();
        fontTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        float baseTitleScale = isAndroid ? 5.0f : 3.0f;
        float pulse = baseTitleScale + MathUtils.sin(pulseTimer * 2f) * 0.15f;
        font.getData().setScale(pulse);

        String title = "GEARY GOLF";
        float titleX = (float)Math.floor(centerX - (180 * (pulse / 3.0f)));
        float titleY = (float)Math.floor(centerY + (isAndroid ? 450 : 180));

        // Draw shadow and title
        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, titleX + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, titleX, titleY);

        fontTexture.setFilter(originalMin, originalMag);

        // --- MENU OPTIONS ---
        float optionScale = isAndroid ? 2.5f : 1.5f;
        float spacing = isAndroid ? 80f : 40f;
        font.getData().setScale(optionScale);
        
        if (!isAndroid) {
            drawOption(batch, font, selection == 0, true, "PLAY GAME", centerX - 80, centerY + (spacing * 2));
            drawOption(batch, font, selection == 1, true, "18 HOLES (COMPETITIVE)", centerX - 80, centerY + spacing);
            drawOption(batch, font, selection == 2, true, "INSTRUCTIONS", centerX - 80, centerY);
            drawOption(batch, font, selection == 3, true, "PRACTICE RANGE", centerX - 80, centerY - spacing);
            drawOption(batch, font, selection == 4, true, "PUTTING GREEN", centerX - 80, centerY - (spacing * 2));

            String seedText = hasValidSeed ? "PLAY SEED [" + clipboardContent.trim() + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
            drawOption(batch, font, selection == 5, hasValidSeed, seedText, centerX - 80, centerY - (spacing * 3));
        }

        font.getData().setScale(isAndroid ? 1.5f : 1f);
        font.setColor(Color.GRAY);
        String hint = isAndroid ? "Tap options directly to select" : "Use UP/DOWN to select, ENTER to start";
        font.draw(batch, hint, centerX - (isAndroid ? 180 : 130), centerY - (spacing * 5));
    }

    private void drawOption(SpriteBatch batch, BitmapFont font, boolean selected, boolean enabled, String text, float x, float y) {
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        boolean showSelected = selected && !isAndroid;
        
        Color targetColor = !enabled ? Color.DARK_GRAY : (showSelected ? Color.YELLOW : Color.WHITE);

        font.setColor(0, 0, 0, 0.7f);
        font.draw(batch, (showSelected && enabled ? "> " : "  ") + text, x + 1, y - 1);
        font.setColor(targetColor);
        font.draw(batch, (showSelected && enabled ? "> " : "  ") + text, x, y);
    }
}