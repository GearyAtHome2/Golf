package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import org.example.input.GameInputProcessor;

public class SpinIndicator extends Actor {
    private final Vector2 spinDot = new Vector2(0, 0);
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private GameInputProcessor input;
    private final float FLUID_SPEED = 2.0f; // Adjust this for faster/slower movement

    public SpinIndicator(ShapeRenderer shapeRenderer, BitmapFont font) {
        this.shapeRenderer = shapeRenderer;
        this.font = font;
        setSize(160, 160);

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                updateSpin(x, y);
                return true;
            }
            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                updateSpin(x, y);
            }
            private void updateSpin(float x, float y) {
                float radius = getWidth() / 2f;
                float dx = (x - radius) / radius;
                float dy = (y - radius) / radius;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 1) {
                    dx /= len; dy /= len;
                }
                spinDot.set(dx, dy);
            }
        });
    }

    public void setInputProcessor(GameInputProcessor input) {
        this.input = input;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (input == null) return;

        // Fluid WASD movement
        float moveX = 0;
        float moveY = 0;

        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) moveY += FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) moveY -= FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT)) moveX -= FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT)) moveX += FLUID_SPEED * delta;

        if (moveX != 0 || moveY != 0) {
            spinDot.add(moveX, moveY);

            // Constrain to circle
            float len = spinDot.len();
            if (len > 1.0f) {
                spinDot.nor();
            }
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

        shapeRenderer.setColor(1, 1, 1, 0.4f * parentAlpha);
        shapeRenderer.circle(centerX, centerY, radius);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.6f * parentAlpha);
        shapeRenderer.circle(centerX, centerY, radius - 4);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(centerX + spinDot.x * (radius - 12), centerY + spinDot.y * (radius - 12), 8);
        shapeRenderer.end();

        batch.begin();
        font.getData().setScale(1.1f);
        font.setColor(Color.WHITE);
        font.draw(batch, "SPIN", getX() + radius - 25, getY() - 10);
    }

    public Vector2 getSpinDot() { return spinDot; }
}