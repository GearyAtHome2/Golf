package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;

public class WindIndicatorRenderer {
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, Vector3 wind, Camera camera) {
        if (wind == null) return;

        // Position in top right
        float centerX = viewport.getWorldWidth() - 100;
        float centerY = viewport.getWorldHeight() - 100;
        float radius = 50f;

        float windAngle = MathUtils.atan2(wind.z, wind.x) * MathUtils.radDeg;
        float camAngle = MathUtils.atan2(camera.direction.z, camera.direction.x) * MathUtils.radDeg;

        float displayAngle = (camAngle - windAngle) + 90f;

        // 1. Pause batch and enable Blending for the circle background
        batch.end();

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 2. Draw Translucent Circle
        shapeRenderer.setColor(0, 0, 0, 0.4f);
        shapeRenderer.circle(centerX, centerY, radius);

        // 3. Draw the Arrow
        shapeRenderer.setColor(Color.WHITE);
        drawArrow(shapeRenderer, centerX, centerY, displayAngle, radius * 0.7f);

        shapeRenderer.end();
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        // 4. Restart batch for text
        batch.begin();

        font.getData().setScale(1.1f);
        font.setColor(Color.WHITE);

        layout.setText(font, "WIND");
        font.draw(batch, "WIND", centerX - layout.width / 2, centerY + 75);

        String speed = String.format("%.1f mph", wind.len());
        layout.setText(font, speed);
        font.draw(batch, speed, centerX - layout.width / 2, centerY - 65);
    }

    private void drawArrow(ShapeRenderer sr, float x, float y, float degrees, float size) {
        // Convert to radians for manual point calculation
        float rad = degrees * MathUtils.degreesToRadians;

        // Arrow Tip
        float tipX = x + MathUtils.cos(rad) * size;
        float tipY = y + MathUtils.sin(rad) * size;

        // Base points: We use a smaller offset for the "back" of the triangle
        // 2.5 and -2.5 radians are roughly 140 degrees away from the tip,
        // creating a sharp arrowhead.
        float baseLeftX = x + MathUtils.cos(rad + 2.4f) * (size * 0.4f);
        float baseLeftY = y + MathUtils.sin(rad + 2.4f) * (size * 0.4f);
        float baseRightX = x + MathUtils.cos(rad - 2.4f) * (size * 0.4f);
        float baseRightY = y + MathUtils.sin(rad - 2.4f) * (size * 0.4f);

        // Main Triangle
        sr.triangle(tipX, tipY, baseLeftX, baseLeftY, baseRightX, baseRightY);

        // Stem of the arrow
        sr.rectLine(x, y, tipX, tipY, 3f);
    }
}