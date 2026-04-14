package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import org.example.Platform;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
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
    private final float DOT_RADIUS_FACTOR_ANDROID = 0.12f;

    private final Vector2 spinDot = new Vector2(0, 0);
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private GameInputProcessor input;
    private final float FLUID_SPEED = 2.0f;
    private float dynamicBigRadius = 250f;
    private boolean bigModeActive = false;

    // Pre-rendered ball texture: sphere gradient + dimples, generated once at first use
    private Texture ballTexture;

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
        boolean isAndroid = Platform.isAndroid();

        float size;
        if (isAndroid) {
            size = screenH * UI_SIZE_FACTOR_ANDROID * 1.5f;
        } else {
            size = screenH * UI_SIZE_FACTOR_DESKTOP;
        }
        setSize(size, size);

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
        boolean isAndroid = Platform.isAndroid();

        batch.end();
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Outer dark ring
        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 1f);
        shapeRenderer.circle(centerX, centerY, radius);

        // Shadow-side base (warm grey — ambient / unlit hemisphere)
        shapeRenderer.setColor(0.76f, 0.76f, 0.74f, 1f);
        shapeRenderer.circle(centerX, centerY, radius * 0.93f);

        // Gradient layers: each step shifts toward upper-left and gets brighter.
        // Light direction in screen space (upper-left): (-0.707, +0.707)
        float lx = -0.707f, ly = 0.707f;
        float baseOff = radius * 0.11f;

        shapeRenderer.setColor(0.82f, 0.82f, 0.81f, 1f);
        shapeRenderer.circle(centerX + lx * baseOff,        centerY + ly * baseOff,        radius * 0.84f);

        shapeRenderer.setColor(0.87f, 0.87f, 0.86f, 1f);
        shapeRenderer.circle(centerX + lx * baseOff * 1.8f, centerY + ly * baseOff * 1.8f, radius * 0.72f);

        shapeRenderer.setColor(0.91f, 0.91f, 0.90f, 1f);
        shapeRenderer.circle(centerX + lx * baseOff * 2.5f, centerY + ly * baseOff * 2.5f, radius * 0.58f);

        shapeRenderer.setColor(0.95f, 0.95f, 0.94f, 1f);
        shapeRenderer.circle(centerX + lx * baseOff * 3.0f, centerY + ly * baseOff * 3.0f, radius * 0.41f);

        // Specular — three concentric steps so the edge is soft rather than a hard disc
        float sxBase = centerX + lx * baseOff * 3.4f;
        float syBase = centerY + ly * baseOff * 3.4f;
        shapeRenderer.setColor(0.96f, 0.96f, 0.95f, 1f);
        shapeRenderer.circle(sxBase, syBase, radius * 0.24f);
        shapeRenderer.setColor(0.98f, 0.98f, 0.97f, 1f);
        shapeRenderer.circle(sxBase, syBase, radius * 0.16f);
        shapeRenderer.setColor(1.00f, 1.00f, 0.99f, 1f);
        shapeRenderer.circle(sxBase, syBase, radius * 0.09f);

        // Red spin dot
        shapeRenderer.setColor(Color.RED);
        float dotRadius = radius * (isAndroid ? DOT_RADIUS_FACTOR_ANDROID : DOT_RADIUS_FACTOR_DESKTOP);
        float limit = radius - dotRadius - (radius * 0.05f);
        shapeRenderer.circle(centerX + (spinDot.x * limit), centerY + (spinDot.y * limit), dotRadius);

        shapeRenderer.end();
        batch.begin();

        // Label
        font.getData().setScale(baseScale * 0.85f);
        font.setColor(Color.WHITE);
        layout.setText(font, "SPIN");
        font.draw(batch, "SPIN", centerX - (layout.width / 2f), getY() + (radius * 2.9f));
        font.getData().setScale(1.0f);
    }

    public void renderBigOverlay(Batch batch, Viewport viewport) {
        batch.end();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Dim background
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f);
        shapeRenderer.rect(0, 0, viewport.getWorldWidth(), viewport.getWorldHeight());

        // Gold bezel — drop shadow, bright ring, dark inner bevel
        float border = dynamicBigRadius * 0.05f;
        float shadowOff = border * 0.6f;
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f);
        shapeRenderer.circle(centerX + shadowOff, centerY - shadowOff, dynamicBigRadius + border * 1.4f);
        shapeRenderer.setColor(1.0f, 0.80f, 0.15f, 1f);
        shapeRenderer.circle(centerX, centerY, dynamicBigRadius + border);
        shapeRenderer.setColor(0.38f, 0.26f, 0.04f, 1f);
        shapeRenderer.circle(centerX, centerY, dynamicBigRadius + border * 0.38f);

        shapeRenderer.end();

        // Pre-rendered shaded ball (sphere gradient + dimples)
        batch.begin();
        batch.draw(getBallTexture(),
                centerX - dynamicBigRadius, centerY - dynamicBigRadius,
                dynamicBigRadius * 2f, dynamicBigRadius * 2f);
        batch.end();

        // Red spin dot on top
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.RED);
        float dr = dynamicBigRadius * 0.07f;
        shapeRenderer.circle(
                centerX + spinDot.x * (dynamicBigRadius - dr),
                centerY + spinDot.y * (dynamicBigRadius - dr),
                dr);
        shapeRenderer.end();

        batch.begin();
    }

    // -------------------------------------------------------------------------
    // Ball texture generation
    // -------------------------------------------------------------------------

    private Texture getBallTexture() {
        if (ballTexture == null) ballTexture = buildBallTexture();
        return ballTexture;
    }

    /** Generates a 512×512 pre-lit golf ball texture: per-pixel diffuse + specular from upper-left, soft edge feathering. */
    private Texture buildBallTexture() {
        int size = 512;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();

        float cx = size / 2f;
        float cy = size / 2f;
        float r  = size / 2f - 2f;

        // Light direction — upper-left in pixmap space (y=0 is top row)
        // lx < 0 → toward left,  ly < 0 → toward top of pixmap
        float lx = -0.45f, ly = -0.62f, lz = 0.64f; // already ~unit length

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float sx = (px - cx) / r;
                float sy = (py - cy) / r;
                float d2 = sx * sx + sy * sy;
                if (d2 > 1f) continue;

                float sz = (float) Math.sqrt(1.0 - d2);

                // Diffuse
                float diff = Math.max(0f, lx * sx + ly * sy + lz * sz);

                // Specular — fast integer-exponent version of pow(dot, 28)
                float NdotL = lx * sx + ly * sy + lz * sz;
                float rz = Math.max(0f, 2f * NdotL * sz - lz); // reflected-ray z component
                float s2 = rz * rz; float s4 = s2 * s2; float s8 = s4 * s4;
                float spec = s8 * s8 * s8 * s4 * 0.85f; // rz^28

                // Gentle limb darkening — less aggressive so the ball reads white, not grey
                float ao = sz * sz * 0.35f + 0.65f;

                float ambient = 0.38f;
                float lit = ambient + diff * 0.62f;

                float fr = Math.min(1f, 0.97f * lit * ao + spec);
                float fg = Math.min(1f, 0.97f * lit * ao + spec);
                float fb = Math.min(1f, 0.95f * lit * ao + spec * 0.96f); // faint warm tint

                // Soft edge feathering
                float alpha = d2 > 0.96f ? (1f - d2) / 0.04f : 1f;

                pixmap.drawPixel(px, py, Color.rgba8888(fr, fg, fb, alpha));
            }
        }

        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return tex;
    }

    public void dispose() {
        if (ballTexture != null) {
            ballTexture.dispose();
            ballTexture = null;
        }
    }

    // -------------------------------------------------------------------------
    // Boilerplate
    // -------------------------------------------------------------------------

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

    @Override
    public void act(float delta) {
        super.act(delta);
        if (input == null) return;
        float moveX = 0, moveY = 0;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP))    moveY += FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN))  moveY -= FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT))  moveX -= FLUID_SPEED * delta;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT)) moveX += FLUID_SPEED * delta;
        if (moveX != 0 || moveY != 0) {
            spinDot.add(moveX, moveY);
            if (spinDot.len() > 1.0f) spinDot.nor();
        }
    }

    public void reset() { this.spinDot.set(0, 0); }

    public Vector2 getSpinDot() { return spinDot; }
}
