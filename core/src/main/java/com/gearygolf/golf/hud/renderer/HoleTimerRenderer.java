package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.hud.UIUtils;

public class HoleTimerRenderer {

    // Position constants — easy to reposition
    private static final float TIMER_X_FACTOR = 0.5f;  // centre of screen
    private static final float TIMER_Y_FACTOR = 0.155f; // above terrain toast

    private final GlyphLayout layout = new GlyphLayout();
    private final Color pulseColor = new Color();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, float elapsedSeconds, boolean isStarted) {
        String text = formatTime(elapsedSeconds);

        Color color;
        if (!isStarted) {
            color = Color.RED;
        } else {
            // Flash yellow at each second tick, fade back to white over 0.25 s
            float frac = elapsedSeconds % 1.0f;
            float pulse = (frac < 0.25f) ? MathUtils.clamp(1f - frac / 0.25f, 0f, 1f) : 0f;
            pulseColor.set(1f, 1f, 1f - pulse, 1f); // white → yellow (drop blue)
            color = pulseColor;
        }

        float scale = viewport.getWorldHeight() * 0.0018f;
        font.getData().setScale(scale);
        layout.setText(font, text);

        float x = viewport.getWorldWidth() * TIMER_X_FACTOR - layout.width / 2f;
        float y = viewport.getWorldHeight() * TIMER_Y_FACTOR + layout.height;

        UIUtils.drawShadowedText(batch, font, text, x, y, color);
        font.getData().setScale(1.0f);
    }

    private String formatTime(float seconds) {
        int total = (int) seconds;
        int tenths = (int) (seconds * 10) % 10;
        return String.format("%02d:%02d.%d", total / 60, total % 60, tenths);
    }
}
