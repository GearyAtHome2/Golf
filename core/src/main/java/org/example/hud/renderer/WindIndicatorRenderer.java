package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;

public class WindIndicatorRenderer {
    private static final float ARROW_SHAFT_THICKNESS = 4.5f;
    private static final float CIRCLE_ALPHA = 0.35f;

    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, Vector3 wind, Camera camera) {
        if (wind == null) return;

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        // --- LAYOUT & SIZING TWEAKABLES ---
        // Pushes the indicator left and up to nestle against the MAX button
        float marginX = isAndroid ? screenW * 0.195f : screenW * 0.06f;
        float marginY = isAndroid ? screenH * 0.07f : screenH * 0.12f;

        // The size of the background circle
        float radius = isAndroid ? screenH * 0.045f : screenH * 0.05f;

        // Arrow head dimensions (multipliers of thickness)
        float headWidthMult = isAndroid ? 1.5f : 2.8f;   // Reduced for Android to look sleeker
        float headLengthMult = isAndroid ? 2.5f : 4.5f;  // Reduced for Android
        float totalLengthPct = isAndroid ? 1.3f : 1.1f;  // Lengthened the shaft for Android

        // Text scaling
        float baseScale = (screenH * 0.0013f);
        float speedTextScale = isAndroid ? baseScale * 0.65f : baseScale * 0.6f;
        float speedTextYOffset = isAndroid ? radius * 0.3f : radius * 0.4f;

        // Calculated positions
        float centerX = screenW - marginX;
        float centerY = screenH - marginY;

        // --- DIRECTION CALCULATION ---
        float windAngle = MathUtils.atan2(wind.z, wind.x) * MathUtils.radDeg;
        float camAngle = MathUtils.atan2(camera.direction.z, camera.direction.x) * MathUtils.radDeg;
        float displayAngle = (camAngle - windAngle) + 90f;

        // --- RENDER SHAPES ---
        renderShapes(batch, shapeRenderer, centerX, centerY, radius, headWidthMult, headLengthMult, totalLengthPct, displayAngle);

        // --- RENDER TEXT ---
        renderText(batch, font, centerX, centerY, radius, speedTextScale, speedTextYOffset, wind.len(), isAndroid);
    }

    private void renderShapes(SpriteBatch batch, ShapeRenderer shapeRenderer, float x, float y, float radius,
                              float headWidthMult, float headLengthMult, float lengthPct, float angle) {
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Background Disc
        shapeRenderer.setColor(0, 0, 0, CIRCLE_ALPHA);
        shapeRenderer.circle(x, y, radius);

        shapeRenderer.setColor(Color.WHITE);
        drawSleekArrow(shapeRenderer, x, y, radius * lengthPct, ARROW_SHAFT_THICKNESS, headWidthMult, headLengthMult, angle);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        batch.begin();
    }

    private void drawSleekArrow(ShapeRenderer sr, float centerX, float centerY, float totalLength,
                                float thickness, float headWidthMult, float headLengthMult, float angle) {
        float cos = MathUtils.cosDeg(angle);
        float sin = MathUtils.sinDeg(angle);

        float headLength = thickness * headLengthMult;
        float headWidth = thickness * headWidthMult;

        float tipX = centerX + (cos * totalLength * 0.5f);
        float tipY = centerY + (sin * totalLength * 0.5f);
        float tailX = centerX - (cos * totalLength * 0.5f);
        float tailY = centerY - (sin * totalLength * 0.5f);
        float headBaseX = tipX - (cos * headLength);
        float headBaseY = tipY - (sin * headLength);

        // Shaft
        sr.rectLine(tailX, tailY, headBaseX, headBaseY, thickness);

        // Head
        float px = -sin * headWidth;
        float py = cos * headWidth;

        sr.triangle(
                tipX, tipY,
                headBaseX + px, headBaseY + py,
                headBaseX - px, headBaseY - py
        );
    }

    private void renderText(SpriteBatch batch, BitmapFont font, float x, float y, float radius,
                            float scale, float yPadding, float speed, boolean isAndroid) {
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();

        String speedText = String.format("%.1f mph", speed);
        font.getData().setScale(scale);

        // On Android, we only show the mph text nestled close to the circle
        if (isAndroid) {
            drawCenteredText(batch, font, speedText, x, y - radius - yPadding);
        } else {
            // Desktop still gets the "WIND" label and larger padding
            font.getData().setScale(scale * 0.85f);
            drawCenteredText(batch, font, "WIND", x, y + radius + (radius * 0.4f) + 10);

            font.getData().setScale(scale);
            drawCenteredText(batch, font, speedText, x, y - radius - (radius * 0.4f));
        }

        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private void drawCenteredText(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
        layout.setText(font, text);
        // Shadow
        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, text, x - layout.width / 2 + 1, y - 1);
        // Main
        font.setColor(Color.WHITE);
        font.draw(batch, text, x - layout.width / 2, y);
    }
}