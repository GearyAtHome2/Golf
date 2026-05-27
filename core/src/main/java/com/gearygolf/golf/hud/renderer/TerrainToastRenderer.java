package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.hud.UIUtils;
import com.gearygolf.golf.terrain.Terrain;

/**
 * Renders a terrain-name label that arcs from the ball's screen position to
 * the bottom-centre of the HUD, then stays visible until the ball is hit.
 * Call {@link #trigger} when the ball comes to rest and {@link #dismiss} when
 * it is struck.  Animation duration is scaled by the player's AnimSpeed setting.
 */
public class TerrainToastRenderer {

    private static final float BASE_FLY_DURATION  = 0.55f;
    private static final float BASE_FADE_DURATION = 0.35f;

    private enum Phase { INACTIVE, FLYING_IN, VISIBLE, FADING_OUT }

    private Phase phase = Phase.INACTIVE;
    private float flyDuration;
    private float fadeDuration;
    private float phaseTimer = 0f;

    private String text = "";
    private final Color color = new Color();
    private float startX, startY;
    private float endX,   endY;

    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3      tempV3 = new Vector3();

    // -------------------------------------------------------------------------

    public void trigger(Terrain.TerrainType type, Vector3 ballWorldPos,
                        Camera camera, Viewport viewport, GameConfig.AnimSpeed animSpeed) {
        float speedMult = (animSpeed == GameConfig.AnimSpeed.NONE) ? 1.0f : animSpeed.mult;
        flyDuration  = BASE_FLY_DURATION  / speedMult;
        fadeDuration = BASE_FADE_DURATION / speedMult;
        phaseTimer   = 0f;
        phase        = Phase.FLYING_IN;

        text = toastLabel(type);
        color.set(toastColor(type));

        // Project ball world position → HUD space
        tempV3.set(ballWorldPos);
        camera.project(tempV3);
        startX = (tempV3.x / Gdx.graphics.getWidth())  * viewport.getWorldWidth();
        startY = (tempV3.y / Gdx.graphics.getHeight()) * viewport.getWorldHeight();

        // Target: bottom-centre
        endX = viewport.getWorldWidth()  / 2f;
        endY = viewport.getWorldHeight() * 0.14f;
    }

    /** Begin fading out — called when the ball is hit. */
    public void dismiss() {
        if (phase == Phase.FLYING_IN || phase == Phase.VISIBLE) {
            phase      = Phase.FADING_OUT;
            phaseTimer = 0f;
        }
    }

    public void update(float delta) {
        if (phase == Phase.INACTIVE) return;
        phaseTimer += delta;
        switch (phase) {
            case FLYING_IN  -> { if (phaseTimer >= flyDuration)  { phase = Phase.VISIBLE;  phaseTimer = 0f; } }
            case FADING_OUT -> { if (phaseTimer >= fadeDuration) { phase = Phase.INACTIVE; phaseTimer = 0f; } }
            default -> {}
        }
    }

    public boolean isActive() { return phase != Phase.INACTIVE; }

    // -------------------------------------------------------------------------

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        if (phase == Phase.INACTIVE) return;

        // Position along arc (only moves during FLYING_IN; then stays at end)
        float px, py;
        if (phase == Phase.FLYING_IN) {
            float eased = easeOutBack(MathUtils.clamp(phaseTimer / flyDuration, 0f, 1f));
            px = MathUtils.lerp(startX, endX, eased);
            py = MathUtils.lerp(startY, endY, eased);
        } else {
            px = endX;
            py = endY;
        }

        // Alpha
        float alpha = switch (phase) {
            case FLYING_IN  -> MathUtils.clamp(phaseTimer / flyDuration, 0f, 1f);
            case VISIBLE    -> 1f;
            case FADING_OUT -> 1f - MathUtils.clamp(phaseTimer / fadeDuration, 0f, 1f);
            default         -> 0f;
        };

        // Font scale: same visual size as RESET BALL button label
        float baseScale = viewport.getWorldHeight() * 0.0015f * (Platform.isAndroid() ? 1.4f : 1.0f);
        font.getData().setScale(baseScale);
        layout.setText(font, text);

        float x = px - layout.width / 2f;
        float y = py;

        if (!batch.isDrawing()) {
            batch.setProjectionMatrix(viewport.getCamera().combined);
            batch.begin();
        }
        UIUtils.drawShadowedText(batch, font, text, x, y, new Color(color.r, color.g, color.b, alpha));
        batch.end();

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    // -------------------------------------------------------------------------

    /** Cubic ease-out with a gentle overshoot for the bounce-settle feel. */
    private static float easeOutBack(float t) {
        if (t >= 1f) return 1f;
        final float c1 = 1.15f;
        final float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    private static String toastLabel(Terrain.TerrainType type) {
        return switch (type) {
            case TEE        -> "TEE";
            case FAIRWAY    -> "FAIRWAY";
            case FRINGE     -> "FRINGE";
            case ROUGH      -> "ROUGH";
            case DEEP_ROUGH -> "DEEP ROUGH";
            case SAND       -> "SAND";
            case GREEN      -> "GREEN";
            case STONE      -> "STONE";
            case MUD        -> "MUD";
        };
    }

    /**
     * Terrain-representative colours. The drop shadow from UIUtils.drawShadowedText
     * provides contrast so these can match the terrain character rather than maximising
     * contrast against it.
     */
    private static Color toastColor(Terrain.TerrainType type) {
        return switch (type) {
            case TEE        -> new Color(0.30f, 0.72f, 0.18f, 1f); // green  (tee box)
            case FAIRWAY    -> new Color(0.25f, 0.68f, 0.12f, 1f); // green
            case FRINGE     -> new Color(0.35f, 0.78f, 0.20f, 1f); // lighter green
            case GREEN      -> new Color(0.45f, 0.90f, 0.32f, 1f); // lightest green (putting surface)
            case ROUGH      -> new Color(0.15f, 0.48f, 0.06f, 1f); // forest green
            case DEEP_ROUGH -> new Color(0.06f, 0.30f, 0.03f, 1f); // dark green
            case SAND       -> new Color(0.82f, 0.68f, 0.38f, 1f); // light brown
            case MUD        -> new Color(0.42f, 0.22f, 0.06f, 1f); // dark brown
            case STONE      -> new Color(0.38f, 0.38f, 0.42f, 1f); // deep grey
        };
    }
}
