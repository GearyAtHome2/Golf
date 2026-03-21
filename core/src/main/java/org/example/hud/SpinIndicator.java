package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.input.GameInputProcessor;

public class SpinIndicator extends Actor {
    // --- SCALING CONFIG ---
    private final float UI_SIZE_FACTOR = 0.085f;
    private final float OVERLAY_SIZE_FACTOR = 0.25f;

    // Positioning factors (as % of screen) to prevent "gap drift"
    private final float MARGIN_X_FACTOR = 0.02f;
    private final float MARGIN_Y_FACTOR = 0.05f;

    private final Vector2 spinDot = new Vector2(0, 0);
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private GameInputProcessor input;
    private final float FLUID_SPEED = 2.0f;

    private float dynamicBigRadius = 250f;
    private boolean bigModeActive = false;

    public SpinIndicator(ShapeRenderer shapeRenderer, BitmapFont font) {
        this.shapeRenderer = shapeRenderer;
        this.font = font;

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                bigModeActive = !bigModeActive;
            }
        });
    }

    /**
     * Updates the Actor's size and position based on screen dimensions.
     * This keeps the gap between the edge and the indicator consistent.
     */
    public void updateScaling(Viewport viewport) {
        float screenH = viewport.getWorldHeight();
        float screenW = viewport.getWorldWidth();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        float platformMultiplier = isAndroid ? 1.4f : 1.0f;

        // 1. Scale the small corner widget
        float size = screenH * UI_SIZE_FACTOR * platformMultiplier;
        setSize(size, size);

        // 2. Set dynamic position so it doesn't float away on large screens
        float posX = screenW * MARGIN_X_FACTOR;
        float posY = screenH * MARGIN_Y_FACTOR;
        setPosition(posX, posY);

        // 3. Scale the big overlay ball
        dynamicBigRadius = screenH * OVERLAY_SIZE_FACTOR * platformMultiplier;
    }

    public void setInputProcessor(GameInputProcessor input) {
        this.input = input;
    }

    public boolean isBigModeActive() {
        return bigModeActive;
    }

    public void setBigModeActive(boolean active) {
        this.bigModeActive = active;
    }

    public boolean isInsideBigBall(float touchX, float touchY, Viewport viewport) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;
        float dist = Vector2.dst(touchX, touchY, centerX, centerY);
        return dist <= dynamicBigRadius;
    }

    public void updateBigInput(float touchX, float touchY, Viewport viewport) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        float dx = (touchX - centerX) / dynamicBigRadius;
        float dy = (touchY - centerY) / dynamicBigRadius;

        spinDot.set(dx, dy);
        if (spinDot.len() > 1.0f) spinDot.nor();
    }

    public void renderBigOverlay(Batch batch, Viewport viewport) {
        updateScaling(viewport);
        batch.end();

        float screenH = viewport.getWorldHeight();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapeRenderer.setColor(0, 0, 0, 0.5f);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight());

        shapeRenderer.setColor(Color.GOLD);
        shapeRenderer.circle(centerX, centerY, dynamicBigRadius + (screenH * 0.005f));

        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 0.9f);
        shapeRenderer.circle(centerX, centerY, dynamicBigRadius);

        // Crosshairs
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.5f);
        shapeRenderer.rect(centerX - dynamicBigRadius, centerY - 1, dynamicBigRadius * 2, 2);
        shapeRenderer.rect(centerX - 1, centerY - dynamicBigRadius, 2, dynamicBigRadius * 2);

        shapeRenderer.setColor(Color.RED);
        float dotRadius = dynamicBigRadius * 0.08f;
        float dotX = centerX + spinDot.x * (dynamicBigRadius - dotRadius);
        float dotY = centerY + spinDot.y * (dynamicBigRadius - dotRadius);
        shapeRenderer.circle(dotX, dotY, dotRadius);

        shapeRenderer.end();
        batch.begin();

        float textScale = screenH * 0.002f;
        font.getData().setScale(textScale);
        font.setColor(Color.GOLD);

        layout.setText(font, "ADJUST SPIN");
        font.draw(batch, "ADJUST SPIN", centerX - (layout.width / 2f), centerY + dynamicBigRadius + (screenH * 0.08f));

        font.getData().setScale(textScale * 0.6f);
        font.setColor(Color.WHITE);
        layout.setText(font, "Tap outside to close");
        font.draw(batch, "Tap outside to close", centerX - (layout.width / 2f), centerY - dynamicBigRadius - (screenH * 0.04f));
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (input == null) return;

        float moveX = 0, moveY = 0;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) moveY += FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) moveY -= FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT)) moveX -= FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT)) moveX += FLUID_SPEED * delta;

        if (moveX != 0 || moveY != 0) {
            spinDot.add(moveX, moveY);
            if (spinDot.len() > 1.0f) spinDot.nor();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // Force update scaling to ensure position is correct if resized
        if (getStage() != null) updateScaling(getStage().getViewport());

        batch.end();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.setTransformMatrix(batch.getTransformMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float radius = getWidth() / 2f;
        float centerX = getX() + radius;
        float centerY = getY() + radius;

        shapeRenderer.setColor(bigModeActive ? Color.GOLD : Color.WHITE);
        shapeRenderer.circle(centerX, centerY, radius);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.8f);
        shapeRenderer.circle(centerX, centerY, radius - (radius * 0.05f));

        shapeRenderer.setColor(Color.RED);
        float redDotRadius = radius * 0.12f;
        shapeRenderer.circle(centerX + spinDot.x * (radius * 0.8f),
                centerY + spinDot.y * (radius * 0.8f),
                redDotRadius);
        shapeRenderer.end();

        batch.begin();

        // Centered "SPIN" text
        font.getData().setScale(getHeight() * 0.007f);
        font.setColor(bigModeActive ? Color.GOLD : Color.WHITE);

        layout.setText(font, "SPIN");
        float textX = centerX - (layout.width / 2f);
        float textY = getY() - (getHeight() * 0.12f);

        font.draw(batch, "SPIN", textX, textY);
    }

    public Vector2 getSpinDot() {
        return spinDot;
    }
}