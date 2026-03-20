package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.input.GameInputProcessor;

public class SpinIndicator extends Actor {
    private final Vector2 spinDot = new Vector2(0, 0);
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private GameInputProcessor input;
    private final float FLUID_SPEED = 2.0f;
    private final float BIG_RADIUS = 250f;

    private boolean bigModeActive = false;

    public SpinIndicator(ShapeRenderer shapeRenderer, BitmapFont font) {
        this.shapeRenderer = shapeRenderer;
        this.font = font;
        setSize(160, 160);

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                bigModeActive = !bigModeActive;
            }
        });
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

    /**
     * Checks if a world-space coordinate is inside the large spin ball
     */
    public boolean isInsideBigBall(float touchX, float touchY, Viewport viewport) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;
        float dist = Vector2.dst(touchX, touchY, centerX, centerY);
        return dist <= BIG_RADIUS;
    }

    public void updateBigInput(float touchX, float touchY, Viewport viewport) {
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        float dx = (touchX - centerX) / BIG_RADIUS;
        float dy = (touchY - centerY) / BIG_RADIUS;

        spinDot.set(dx, dy);

        if (spinDot.len() > 1.0f) {
            spinDot.nor();
        }
    }

    public void renderBigOverlay(Batch batch, Viewport viewport) {
        batch.end();

        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapeRenderer.setColor(0, 0, 0, 0.5f);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight());

        shapeRenderer.setColor(Color.GOLD);
        shapeRenderer.circle(centerX, centerY, BIG_RADIUS + 4);

        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 0.9f);
        shapeRenderer.circle(centerX, centerY, BIG_RADIUS);

        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.5f);
        shapeRenderer.rect(centerX - BIG_RADIUS, centerY - 1, BIG_RADIUS * 2, 2);
        shapeRenderer.rect(centerX - 1, centerY - BIG_RADIUS, 2, BIG_RADIUS * 2);

        shapeRenderer.setColor(Color.RED);
        float dotX = centerX + spinDot.x * (BIG_RADIUS - 20);
        float dotY = centerY + spinDot.y * (BIG_RADIUS - 20);
        shapeRenderer.circle(dotX, dotY, 20);

        shapeRenderer.end();
        batch.begin();

        font.getData().setScale(2.0f);
        font.setColor(Color.GOLD);
        font.draw(batch, "ADJUST SPIN", centerX - 120, centerY + BIG_RADIUS + 60);

        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
        font.draw(batch, "Tap outside to close", centerX - 120, centerY - BIG_RADIUS - 40);
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
        shapeRenderer.circle(centerX, centerY, radius - 4);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(centerX + spinDot.x * (radius - 12), centerY + spinDot.y * (radius - 12), 8);
        shapeRenderer.end();

        batch.begin();
        font.getData().setScale(1.1f);
        font.setColor(bigModeActive ? Color.GOLD : Color.WHITE);
        font.draw(batch, "SPIN", getX() + radius - 25, getY() - 10);
    }

    public Vector2 getSpinDot() {
        return spinDot;
    }
}