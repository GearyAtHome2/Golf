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
    private static final float ARROW_HEAD_WIDTH_MULT = 2.8f;  // Width relative to thickness
    private static final float ARROW_HEAD_LENGTH_MULT = 4.5f; // Length relative to thickness
    private static final float ARROW_TOTAL_RADIUS_PCT = 1.3f; // Size relative to circle radius
    private static final float CIRCLE_ALPHA = 0.4f;
    // ------------------

    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, Vector3 wind, Camera camera) {
        if (wind == null) return;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        float uiScale = isAndroid ? Math.max(1.0f, viewport.getWorldHeight() / 720f) : 1.0f;

        // 1. Calculate Positioning
        float centerX = isAndroid ? viewport.getWorldWidth() * 0.84f : viewport.getWorldWidth() - 100;
        float centerY = isAndroid ? viewport.getWorldHeight() - (110f * uiScale) : viewport.getWorldHeight() - 100;
        float radius = (isAndroid ? 60f : 50f) * uiScale;

        // 2. Calculate Direction (3D Camera aware)
        float windAngle = MathUtils.atan2(wind.z, wind.x) * MathUtils.radDeg;
        float camAngle = MathUtils.atan2(camera.direction.z, camera.direction.x) * MathUtils.radDeg;
        float displayAngle = (camAngle - windAngle) + 90f;

        // 3. Render Shapes
        renderShapes(batch, shapeRenderer, centerX, centerY, radius, uiScale, displayAngle);

        // 4. Render Text
        renderText(batch, font, centerX, centerY, radius, uiScale, wind.len(), isAndroid);
    }

    private void renderShapes(SpriteBatch batch, ShapeRenderer shapeRenderer, float x, float y, float radius, float uiScale, float angle) {
        batch.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Background Disc
        shapeRenderer.setColor(0, 0, 0, CIRCLE_ALPHA);
        shapeRenderer.circle(x, y, radius);

        shapeRenderer.setColor(Color.WHITE);
        drawSleekArrow(shapeRenderer, x, y, radius * ARROW_TOTAL_RADIUS_PCT, ARROW_SHAFT_THICKNESS * uiScale, angle);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        batch.begin();
    }

    private void drawSleekArrow(ShapeRenderer sr, float centerX, float centerY, float totalLength, float thickness, float angle) {
        float cos = MathUtils.cosDeg(angle);
        float sin = MathUtils.sinDeg(angle);

        float headLength = thickness * ARROW_HEAD_LENGTH_MULT;
        float headWidth = thickness * ARROW_HEAD_WIDTH_MULT;

        // 1. Calculate Points
        // The tip of the arrow
        float tipX = centerX + (cos * totalLength * 0.5f);
        float tipY = centerY + (sin * totalLength * 0.5f);

        // The back of the arrow (tail)
        float tailX = centerX - (cos * totalLength * 0.5f);
        float tailY = centerY - (sin * totalLength * 0.5f);

        // The base of the triangle head (where the shaft should stop)
        float headBaseX = tipX - (cos * headLength);
        float headBaseY = tipY - (sin * headLength);

        // 2. Draw Shaft (Starts at tail, ends exactly at the triangle base)
        sr.rectLine(tailX, tailY, headBaseX, headBaseY, thickness);

        // 3. Draw Head (Triangle)
        float px = -sin * headWidth; // Perpendicular offset
        float py = cos * headWidth;

        sr.triangle(
                tipX, tipY,               // The sharp tip
                headBaseX + px, headBaseY + py, // Corner 1
                headBaseX - px, headBaseY - py  // Corner 2
        );
    }

    private void renderText(SpriteBatch batch, BitmapFont font, float x, float y, float radius, float uiScale, float speed, boolean isAndroid) {
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        font.setColor(Color.WHITE);

        String speedText = String.format("%.1f mph", speed);

        if (isAndroid) {
            font.getData().setScale(1.2f * uiScale);
            drawCenteredText(batch, font, "WIND", x, y + (radius + 35 * uiScale));

            font.getData().setScale(1.6f * uiScale);
            drawCenteredText(batch, font, speedText, x, y - (radius + 25 * uiScale));
        } else {
            font.getData().setScale(1.1f);
            drawCenteredText(batch, font, "WIND", x, y + 75);
            drawCenteredText(batch, font, speedText, x, y - 65);
        }

        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private void drawCenteredText(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
        layout.setText(font, text);
        font.draw(batch, text, x - layout.width / 2, y);
    }
}