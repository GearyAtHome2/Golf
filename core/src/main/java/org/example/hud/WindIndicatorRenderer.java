package org.example.hud;

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

        float centerX = viewport.getWorldWidth() - 100;
        float centerY = viewport.getWorldHeight() - 100;
        float radius = 50f;

        float windAngle = MathUtils.atan2(wind.z, wind.x);
        float camAngle = MathUtils.atan2(camera.direction.z, camera.direction.x);
        float displayAngle = (windAngle - camAngle) * MathUtils.radDeg + 90f;

        // 1. Pause batch and enable Blending
        batch.end();

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 2. Draw Translucent Circle
        shapeRenderer.setColor(0, 0, 0, 0.4f); // 0.4f is 40% opacity
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
        float rad = degrees * MathUtils.degreesToRadians;

        // Arrow Tip
        float tipX = x + MathUtils.cos(rad) * size;
        float tipY = y + MathUtils.sin(rad) * size;

        // Base points
        float baseLeftX = x + MathUtils.cos(rad + 2.5f) * (size * 0.3f);
        float baseLeftY = y + MathUtils.sin(rad + 2.5f) * (size * 0.3f);
        float baseRightX = x + MathUtils.cos(rad - 2.5f) * (size * 0.3f);
        float baseRightY = y + MathUtils.sin(rad - 2.5f) * (size * 0.3f);

        sr.triangle(tipX, tipY, baseLeftX, baseLeftY, baseRightX, baseRightY);

        // Draw a small line for the tail to make it look "classic"
        sr.rectLine(x, y, tipX, tipY, 3f);
    }
}