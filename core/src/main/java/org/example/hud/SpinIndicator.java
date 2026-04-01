package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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
    private final float UI_SIZE_FACTOR_DESKTOP = 0.12f;
    private final float UI_SIZE_FACTOR_ANDROID = 0.09f;
    private final float OVERLAY_SIZE_FACTOR = 0.25f;
    private final float DOT_RADIUS_FACTOR_DESKTOP = 0.10f;
    private final float DOT_RADIUS_FACTOR_ANDROID = 0.20f;

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
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                bigModeActive = !bigModeActive;
            }
        });
    }

    public void updateScaling(Viewport viewport) {
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        // Size Logic: Bigger on Desktop for better visibility
        float size;
        if (isAndroid) {
            size = screenH * UI_SIZE_FACTOR_ANDROID * 1.5f;
        } else {
            size = screenH * UI_SIZE_FACTOR_DESKTOP;
        }
        setSize(size, size);

        // Position Logic:
        // Desktop stays pinned to bottom-left (20px margin).
        // Android stays lifted (15% height) to avoid system bars.
        float posX = 20f;
        float posY = isAndroid ? (screenH * 0.15f) : 20f;

        setPosition(posX, posY);
        dynamicBigRadius = screenH * OVERLAY_SIZE_FACTOR * (isAndroid ? 1.4f : 1.0f);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float radius = getWidth() / 2f;
        float centerX = getX() + radius;
        float centerY = getY() + radius;
        float baseScale = Gdx.graphics.getHeight() * 0.0013f;
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        batch.end();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.circle(centerX, centerY, radius);

        shapeRenderer.setColor(0.95f, 0.95f, 0.95f, 1f);
        shapeRenderer.circle(centerX, centerY, radius * 0.93f);

        shapeRenderer.setColor(Color.RED);
        float dotRadius = radius * (isAndroid ? DOT_RADIUS_FACTOR_ANDROID : DOT_RADIUS_FACTOR_DESKTOP);
        float limit = radius - dotRadius - (radius * 0.05f);
        shapeRenderer.circle(centerX + (spinDot.x * limit), centerY + (spinDot.y * limit), dotRadius);

        shapeRenderer.end();
        batch.begin();

        // Label - Drawn above the ball
        font.getData().setScale(baseScale * 0.85f);
        font.setColor(Color.WHITE);
        layout.setText(font, "SPIN");
        font.draw(batch, "SPIN", centerX - (layout.width / 2f), getY() + (radius * 2.5f));
        font.getData().setScale(1.0f);
    }

    public void setInputProcessor(GameInputProcessor input) { this.input = input; }

    public boolean isBigModeActive() { return bigModeActive; }

    public void setBigModeActive(boolean active) { this.bigModeActive = active; }

    public boolean isInsideBigBall(float touchX, float touchY, Viewport viewport) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;
        return Vector2.dst(touchX, touchY, centerX, centerY) <= dynamicBigRadius;
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
        batch.end();
        float screenH = viewport.getWorldHeight();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setColor(0, 0, 0, 0.5f);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight());
        shapeRenderer.setColor(Color.GOLD);
        shapeRenderer.circle(centerX, centerY, dynamicBigRadius + (screenH * 0.005f));
        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 0.9f);
        shapeRenderer.circle(centerX, centerY, dynamicBigRadius);
        shapeRenderer.setColor(Color.RED);
        float dr = dynamicBigRadius * 0.02f;
        shapeRenderer.circle(centerX + spinDot.x * (dynamicBigRadius - dr), centerY + spinDot.y * (dynamicBigRadius - dr), dr);
        shapeRenderer.end();
        batch.begin();
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

    public void reset() { this.spinDot.set(0, 0); }

    public Vector2 getSpinDot() { return spinDot; }
}