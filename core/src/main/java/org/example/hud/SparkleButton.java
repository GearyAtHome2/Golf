package org.example.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * A TextButton that draws an animated star-twinkle effect on its label when enabled.
 */
public class SparkleButton extends TextButton {

    private boolean sparkleEnabled = false;

    public SparkleButton(String text, TextButtonStyle style) {
        super(text, style);
    }

    public void setSparkleEnabled(boolean enabled) {
        this.sparkleEnabled = enabled;
        if (!enabled) getLabel().setColor(Color.WHITE);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (sparkleEnabled) {
            getLabel().setColor(Color.GOLD);
        } else {
            getLabel().setColor(Color.WHITE);
        }

        super.draw(batch, parentAlpha);

        if (sparkleEnabled) {
            drawStars(batch, parentAlpha);
        }
    }

    private void drawStars(Batch batch, float parentAlpha) {
        BitmapFont font = getStyle().font;
        if (font == null) return;
        float savedScaleX = font.getScaleX(), savedScaleY = font.getScaleY();
        Color savedColor = new Color(font.getColor());

        float starScale = getLabel().getFontScaleY() * 0.55f;
        font.getData().setScale(starScale);

        // Constrain stars to the label's actual text area
        float labelWorldX = getX() + getLabel().getX();
        float labelW = getLabel().getWidth();
        float labelWorldY = getY() + getLabel().getY();
        float labelH = getLabel().getHeight();

        long ms = TimeUtils.millis();
        // Per-button seed so different buttons have different constellations
        int btnHash = getLabel().getText().toString().hashCode();
        float btnSeed = (btnHash & 0x7FFF) * 0.001f; // small positive float
        int starCount = 8;

        for (int i = 0; i < starCount; i++) {
            // All cycle arithmetic in longs to avoid float precision collapse
            long cycleMs = (long)((1.8f + i * 0.13f) * 1000f);
            long offsetMs = ms + (long)(i * 0.137f * cycleMs); // stagger phases per star
            long cycleNumber = offsetMs / cycleMs;
            float phase = (float)(offsetMs % cycleMs) / cycleMs; // precise 0..1

            // Position re-randomised each cycle; seed keeps it per-button too
            float seed = i * 2.399f + (cycleNumber % 997) * 1.618f + btnSeed;
            float relX = MathUtils.sin(seed) * 0.5f + 0.5f;
            float relY = MathUtils.sin(seed * 1.531f + 0.7f) * 0.55f;

            // Cosine curve: starts at 0, peaks at phase=0.5, returns to 0 — no sudden pop-in
            float alpha = (1f - MathUtils.cos(phase * MathUtils.PI2)) * 0.5f * parentAlpha;
            if (alpha < 0.05f) continue;

            float sx = labelWorldX + relX * labelW;
            float sy = labelWorldY + labelH * 0.5f + relY * labelH;
            font.setColor(1f, 0.88f, 0.2f, alpha);
            font.draw(batch, "*", sx, sy);
        }

        font.getData().setScale(savedScaleX, savedScaleY);
        font.setColor(savedColor);
    }
}
