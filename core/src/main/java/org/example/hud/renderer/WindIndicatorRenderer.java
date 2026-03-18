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

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        float centerX, centerY, radius, uiScale;

        if (isAndroid) {
            // Mobile: Large and shifted left to avoid the HIT button stack
            uiScale = viewport.getWorldHeight() / 720f;
            if (uiScale < 1.0f) uiScale = 1.0f;

            centerX = viewport.getWorldWidth() * 0.84f;
            centerY = viewport.getWorldHeight() - (110f * uiScale);
            radius = 60f * uiScale;
        } else {
            // Desktop: Original compact corner placement
            uiScale = 1.0f;
            centerX = viewport.getWorldWidth() - 100;
            centerY = viewport.getWorldHeight() - 100;
            radius = 50f;
        }

        float windAngle = MathUtils.atan2(wind.z, wind.x) * MathUtils.radDeg;
        float camAngle = MathUtils.atan2(camera.direction.z, camera.direction.x) * MathUtils.radDeg;
        float displayAngle = (camAngle - windAngle) + 90f;

        batch.end();
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.4f);
        shapeRenderer.circle(centerX, centerY, radius);
        shapeRenderer.setColor(Color.WHITE);
        drawArrow(shapeRenderer, centerX, centerY, displayAngle, radius * 0.7f, 4f * uiScale);
        shapeRenderer.end();

        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        batch.begin();
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();
        font.setColor(Color.WHITE);

        if (isAndroid) {
            // Large mobile labels
            font.getData().setScale(1.2f * uiScale);
            layout.setText(font, "WIND");
            font.draw(batch, "WIND", centerX - layout.width / 2, centerY + (radius + 35 * uiScale));

            font.getData().setScale(1.6f * uiScale);
            String speed = String.format("%.1f mph", wind.len());
            layout.setText(font, speed);
            font.draw(batch, speed, centerX - layout.width / 2, centerY - (radius + 25 * uiScale));
        } else {
            // Original desktop labels
            font.getData().setScale(1.1f);
            layout.setText(font, "WIND");
            font.draw(batch, "WIND", centerX - layout.width / 2, centerY + 75);

            String speed = String.format("%.1f mph", wind.len());
            layout.setText(font, speed);
            font.draw(batch, speed, centerX - layout.width / 2, centerY - 65);
        }

        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private void drawArrow(ShapeRenderer sr, float x, float y, float degrees, float size, float thickness) {
        float rad = degrees * MathUtils.degreesToRadians;
        float tipX = x + MathUtils.cos(rad) * size;
        float tipY = y + MathUtils.sin(rad) * size;
        float baseLeftX = x + MathUtils.cos(rad + 2.4f) * (size * 0.4f);
        float baseLeftY = y + MathUtils.sin(rad + 2.4f) * (size * 0.4f);
        float baseRightX = x + MathUtils.cos(rad - 2.4f) * (size * 0.4f);
        float baseRightY = y + MathUtils.sin(rad - 2.4f) * (size * 0.4f);
        sr.triangle(tipX, tipY, baseLeftX, baseLeftY, baseRightX, baseRightY);
        sr.rectLine(x, y, tipX, tipY, thickness);
    }
}