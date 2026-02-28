package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class HUD {
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private int shotCount = 0;
    private float lastDisplayedSpin = 0f;

    // --- Spin Selection ---
    private final Vector2 spinDot = new Vector2(0, 0); // -1 to 1 range
    private final float SPIN_UI_RADIUS = 50f;

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

        // Selection 0: Play
        font.setColor(selection == 0 ? Color.YELLOW : Color.WHITE);
        String playText = (selection == 0 ? "> " : "  ") + "PLAY GAME";
        font.draw(batch, playText, centerX - 80, centerY);

        // Selection 1: Practice
        font.setColor(selection == 1 ? Color.YELLOW : Color.WHITE);
        String practiceText = (selection == 1 ? "> " : "  ") + "PRACTICE RANGE";
        font.draw(batch, practiceText, centerX - 80, centerY - 60);

        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 150);

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

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice) {
        updateSpinInput(Gdx.graphics.getDeltaTime());

        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        // --- Render Spin Selector (Bottom Left) ---
        float spinX = 80;
        float spinY = 80;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        // Outer Ball
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS);
        // Shadow/Depth for the UI ball
        shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 1f);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS - 5);
        // The Contact Dot
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(spinX + (spinDot.x * (SPIN_UI_RADIUS - 10)),
                spinY + (spinDot.y * (SPIN_UI_RADIUS - 10)), 5);
        shapeRenderer.end();

        batch.begin();
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTACT POINT", spinX - 45, spinY - 60);

        // --- Top Left: Mode & Shots ---
        font.getData().setScale(1.5f);
        if (isPractice) {
            font.setColor(Color.CYAN);
            font.draw(batch, "PRACTICE MODE", 40, viewport.getWorldHeight() - 40);
        }
        font.setColor(Color.WHITE);
        font.draw(batch, "Shots: " + shotCount, 40, isPractice ? viewport.getWorldHeight() - 80 : viewport.getWorldHeight() - 40);

        // --- Bottom Right: Equipment & Physics ---
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