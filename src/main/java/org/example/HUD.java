package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ball.Ball;

public class HUD {
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private int shotCount = 0;
    private float lastDisplayedSpin = 0f;

    private final Vector2 spinDot = new Vector2(0, 0);
    private final float SPIN_UI_RADIUS = 50f;

    // Reusable vectors for wind projection to avoid GC pressure
    private final Vector3 tempWind = new Vector3();
    private final Vector3 camForward = new Vector3();
    private final Vector3 camRight = new Vector3();

    public HUD() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        viewport = new ScreenViewport();
    }

    public void incrementShots() { shotCount++; }
    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void renderStartMenu(int selection) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;
        font.getData().setScale(3f);
        font.setColor(Color.WHITE);
        font.draw(batch, "ULTIMATE GOLF", centerX - 180, centerY + 150);
        font.getData().setScale(1.5f);
        font.setColor(selection == 0 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selection == 0 ? "> " : "  ") + "PLAY GAME", centerX - 80, centerY);
        font.setColor(selection == 1 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selection == 1 ? "> " : "  ") + "PRACTICE RANGE", centerX - 80, centerY - 60);
        font.setColor(selection == 2 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selection == 2 ? "> " : "  ") + "PLAY SEED FROM CLIPBOARD", centerX - 80, centerY - 120);
        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 210);
        batch.end();
    }

    public void renderPauseMenu() {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(2.5f);
        font.draw(batch, "PAUSED", viewport.getWorldWidth()/2f - 80, viewport.getWorldHeight() - 100);
        font.getData().setScale(1.2f);
        font.draw(batch, "[R] RESET BALL  |  [N] NEW LEVEL  |  [ESC] RESUME", 50, 100);
        batch.end();
    }

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera) {
        updateSpinInput(Gdx.graphics.getDeltaTime());

        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        // --- Render Spin Selector (Bottom Left) ---
        float spinX = 80;
        float spinY = 80;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS - 3);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(spinX + (spinDot.x * (SPIN_UI_RADIUS - 10)),
                spinY + (spinDot.y * (SPIN_UI_RADIUS - 10)), 5);
        shapeRenderer.end();

        // --- Render Wind (Top Right) ---
        if (levelData != null) {
            renderWindIndicator(levelData.getWind(), gameCamera);
        }

        batch.begin();
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTACT POINT", spinX - 45, spinY - 60);

        font.getData().setScale(1.5f);
        if (isPractice) {
            font.setColor(Color.CYAN);
            font.draw(batch, "PRACTICE RANGE", 40, viewport.getWorldHeight() - 40);
        } else if (levelData != null) {
            font.setColor(Color.GOLD);
            font.draw(batch, levelData.getArchetype().name().replace("_", " "), 40, viewport.getWorldHeight() - 40);
        }

        font.setColor(Color.WHITE);
        font.draw(batch, "Shots: " + shotCount, 40, viewport.getWorldHeight() - 80);

        float rightX = viewport.getWorldWidth() - 250;
        font.draw(batch, "Club: " + currentClub.name(), rightX, 140);

        float currentSpinMag = ball.getSpin().len();
        if (currentSpinMag > 0.1f) {
            font.setColor(currentSpinMag > lastDisplayedSpin ? Color.GREEN : Color.RED);
            font.draw(batch, String.format("Spin: %.1fk RPM", (currentSpinMag * 100f) / 1000f), rightX, 100);
        } else {
            font.setColor(Color.GRAY);
            font.draw(batch, "Spin: 0k RPM", rightX, 100);
        }
        lastDisplayedSpin = currentSpinMag;

        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Speed: %.1fx", gameSpeed), rightX, 60);
        batch.end();
    }

    private void renderWindIndicator(Vector3 worldWind, Camera camera) {
        float uiX = viewport.getWorldWidth() - 80;
        float uiY = viewport.getWorldHeight() - 80;
        float radius = 40f;
        float windSpeed = worldWind.len();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(uiX, uiY, radius);
        shapeRenderer.end();

        if (windSpeed > 0.1f) {
            // Calculate wind relative to camera view
            // Project world wind onto camera's Right and Forward (XZ plane)
            camForward.set(camera.direction.x, 0, camera.direction.z).nor();
            camRight.set(camera.direction).crs(camera.up).nor();
            camRight.y = 0;
            camRight.nor();

            // Dot products to find how much wind is moving relative to camera "screen"
            float screenX = worldWind.dot(camRight);
            float screenY = worldWind.dot(camForward);

            // Calculate HUD angle based on these components
            float angle = MathUtils.atan2(screenY, screenX);
            float cos = MathUtils.cos(angle);
            float sin = MathUtils.sin(angle);

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.YELLOW);
            // Draw arrow
            shapeRenderer.rectLine(uiX - cos * (radius - 10), uiY - sin * (radius - 10),
                    uiX + cos * (radius - 10), uiY + sin * (radius - 10), 3f);

            float headAngle = 0.5f;
            shapeRenderer.triangle(
                    uiX + cos * radius, uiY + sin * radius,
                    uiX + MathUtils.cos(angle + MathUtils.PI - headAngle) * 15f, uiY + MathUtils.sin(angle + MathUtils.PI - headAngle) * 15f,
                    uiX + MathUtils.cos(angle + MathUtils.PI + headAngle) * 15f, uiY + MathUtils.sin(angle + MathUtils.PI + headAngle) * 15f
            );
            shapeRenderer.end();
        }

        batch.begin();
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
        String windText = String.format("%.0f MPH", windSpeed);
        font.draw(batch, windText, uiX - 30, uiY - radius - 10);
        batch.end();
    }

    public void renderVictory(int finalShots) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(4f);
        font.setColor(Color.GOLD);
        font.draw(batch, "HOLE IN ONE!", viewport.getWorldWidth() / 2f - 200, viewport.getWorldHeight() / 2f + 100);
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        font.draw(batch, "Total Strokes: " + finalShots, viewport.getWorldWidth() / 2f - 100, viewport.getWorldHeight() / 2f);
        font.getData().setScale(1.2f);
        font.draw(batch, "Press [N] for a New Level", viewport.getWorldWidth() / 2f - 110, viewport.getWorldHeight() / 2f - 100);
        batch.end();
    }

    public void updateSpinInput(float delta) {
        float speed = 2f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) spinDot.y = MathUtils.clamp(spinDot.y + delta * speed, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) spinDot.y = MathUtils.clamp(spinDot.y - delta * speed, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) spinDot.x = MathUtils.clamp(spinDot.x - delta * speed, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) spinDot.x = MathUtils.clamp(spinDot.x + delta * speed, -1f, 1f);
        if (spinDot.len() > 1f) spinDot.nor();
    }

    public Vector2 getSpinOffset() { return spinDot; }

    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
    }
}