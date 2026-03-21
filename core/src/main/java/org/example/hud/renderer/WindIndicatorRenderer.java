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
    // --- TWEAKABLES ---
    private static final float ARROW_SHAFT_THICKNESS = 4.5f;
    private static final float ARROW_HEAD_WIDTH_MULT = 2.8f;
    private static final float ARROW_HEAD_LENGTH_MULT = 4.5f;
    private static final float ARROW_TOTAL_RADIUS_PCT = 1.1f;
    private static final float CIRCLE_ALPHA = 0.35f;
    // ------------------

    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, Vector3 wind, Camera camera) {
        if (wind == null) return;

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        // Base scale linked to screen height for consistency
        float baseScale = (screenH * 0.0013f) * (isAndroid ? 1.5f : 1.0f);

        // --- APPLIED TASK: Move left on Android to clear MAX button ---
        // Desktop uses 6% margin. Android pushed to 18% to sit 'inside' the right-hand button stack.
        float marginX = isAndroid ? screenW * 0.18f : screenW * 0.06f;
        float marginY = screenH * 0.12f;
        float centerX = screenW - marginX;
        float centerY = screenH - marginY;

        // Radius scales with screen height
        float radius = screenH * 0.05f;

        // 2. Calculate Direction (3D Camera aware)
        float windAngle = MathUtils.atan2(wind.z, wind.x) * MathUtils.radDeg;
        float camAngle = MathUtils.atan2(camera.direction.z, camera.direction.x) * MathUtils.radDeg;
        float displayAngle = (camAngle - windAngle) + 90f;

        // 3. Render Shapes
        renderShapes(batch, shapeRenderer, centerX, centerY, radius, baseScale, displayAngle);

        // 4. Render Text
        renderText(batch, font, centerX, centerY, radius, baseScale, wind.len(), isAndroid);
    }

    private void renderShapes(SpriteBatch batch, ShapeRenderer shapeRenderer, float x, float y, float radius, float baseScale, float angle) {
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Background Disc
        shapeRenderer.setColor(0, 0, 0, CIRCLE_ALPHA);
        shapeRenderer.circle(x, y, radius);

        shapeRenderer.setColor(Color.WHITE);
        drawSleekArrow(shapeRenderer, x, y, radius * ARROW_TOTAL_RADIUS_PCT, 4f * baseScale, angle);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        batch.begin();
    }

    private void drawSleekArrow(ShapeRenderer sr, float centerX, float centerY, float totalLength, float thickness, float angle) {
        float cos = MathUtils.cosDeg(angle);
        float sin = MathUtils.sinDeg(angle);

        float headLength = thickness * ARROW_HEAD_LENGTH_MULT;
        float headWidth = thickness * ARROW_HEAD_WIDTH_MULT;

        float tipX = centerX + (cos * totalLength * 0.5f);
        float tipY = centerY + (sin * totalLength * 0.5f);
        float tailX = centerX - (cos * totalLength * 0.5f);
        float tailY = centerY - (sin * totalLength * 0.5f);
        float headBaseX = tipX - (cos * headLength);
        float headBaseY = tipY - (sin * headLength);

        sr.rectLine(tailX, tailY, headBaseX, headBaseY, thickness);

        float px = -sin * headWidth;
        float py = cos * headWidth;

        sr.triangle(
                tipX, tipY,
                headBaseX + px, headBaseY + py,
                headBaseX - px, headBaseY - py
        );
    }

    private void renderText(SpriteBatch batch, BitmapFont font, float x, float y, float radius, float baseScale, float speed, boolean isAndroid) {
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        font.setColor(Color.WHITE);

        String speedText = String.format("%.1f mph", speed);
        float linePadding = radius * 0.4f;

        // Desktop size is 0.6x of the calculated baseScale.
        // Android is kept slightly larger (0.9x) for thumb-friendly readability.
        float textScale = isAndroid ? baseScale * 0.9f : baseScale * 0.6f;

        // "WIND" Label (Above)
        font.getData().setScale(textScale * 0.85f);
        drawCenteredText(batch, font, "WIND", x, y + radius + linePadding + (isAndroid ? 15 : 10));

        // Speed Text (Below)
        font.getData().setScale(textScale);
        drawCenteredText(batch, font, speedText, x, y - radius - linePadding);

        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private void drawCenteredText(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
        layout.setText(font, text);
        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, text, x - layout.width / 2 + 1, y - 1);
        font.setColor(Color.WHITE);
        font.draw(batch, text, x - layout.width / 2, y);
    }
}