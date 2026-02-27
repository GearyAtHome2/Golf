package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class ShotController {
    private Model powerBarModel;
    private ModelInstance powerBarInstance;
    private ModelInstance maxPowerGhost;

    private float spaceHoldTime = 0f;
    private float shotPower = 0f;
    private boolean isCharging = false;
    private final float MAX_POWER = 3f;

    // Animation timer for the pulse effect
    private float animationTimer = 0f;

    public ShotController() {
        ModelBuilder mb = new ModelBuilder();

        powerBarModel = mb.createBox(0.5f, 1f, 0.5f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        powerBarInstance = new ModelInstance(powerBarModel);

        maxPowerGhost = new ModelInstance(powerBarModel);

        // Set up translucency and depth testing so the inner bar is always visible
        maxPowerGhost.materials.get(0).set(
                new BlendingAttribute(0.5f),
                new DepthTestAttribute(false),
                ColorAttribute.createDiffuse(new Color(1, 1, 1, 0.5f))
        );
    }

    public boolean update(float delta, float effectiveDelta, Ball ball, Vector3 camDir) {
        animationTimer += delta;

        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            isCharging = true;
            spaceHoldTime = Math.min(spaceHoldTime + delta, MAX_POWER);
            return false;
        } else if (isCharging) {
            isCharging = false;
            shotPower = spaceHoldTime;
            Vector3 hitDir = new Vector3(camDir.x, 0, camDir.z).nor();
            ball.hit(hitDir, shotPower);
            spaceHoldTime = 0;
            return true;
        }

        if (shotPower > 0) {
            shotPower -= delta * 6f;
            if (shotPower < 0) shotPower = 0;
        }
        return false;
    }

    public void render(ModelBatch batch, Environment env, Vector3 ballPos, Vector3 camPos) {
        float height = isCharging ? spaceHoldTime : shotPower;
        if (height <= 0 && !isCharging) return;

        float dist = camPos.dst(ballPos);

        // 1. INVERSE DISTANCE SCALING
        // This calculates a 'unit scale' that shrinks the object as you get closer
        // to counteract the camera's natural perspective zoom.
        float baseScaleFactor = 0.04f;
        float currentUnitScale = MathUtils.clamp(dist * baseScaleFactor, 0.15f, 1.2f);

        // 2. PULSE LOGIC
        // Only pulse the width when we are at maximum charge
        float pulseWidthMult = 1.0f;
        if (isCharging && spaceHoldTime >= MAX_POWER) {
            // Rapid sine wave pulsing between 1.0 and 1.4
            pulseWidthMult = 1.2f + (MathUtils.sin(animationTimer * 18f) * 0.2f);
        }

        // Adjust the vertical offset based on the new scaled size
        float verticalOffset = currentUnitScale * 1.5f;

        if (isCharging) {
            // We set translation first, then scale.
            // The Y translation accounts for half of the MAX_POWER height scaled by our unit scale.
            maxPowerGhost.transform.setToTranslation(ballPos.x, ballPos.y + verticalOffset + (MAX_POWER * currentUnitScale / 2f), ballPos.z);
            maxPowerGhost.transform.scale(currentUnitScale * 1.1f, MAX_POWER * currentUnitScale, currentUnitScale * 1.1f);
            batch.render(maxPowerGhost, env);
        }

        if (height > 0) {
            Color color = height < (MAX_POWER / 2) ?
                    Color.GREEN.cpy().lerp(Color.YELLOW, height / (MAX_POWER / 2)) :
                    Color.YELLOW.cpy().lerp(Color.RED, (height - (MAX_POWER / 2)) / (MAX_POWER / 2));

            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(color));

            powerBarInstance.transform.setToTranslation(ballPos.x, ballPos.y + verticalOffset + (height * currentUnitScale / 2f), ballPos.z);

            // Apply currentUnitScale to overall size, and pulseWidthMult to the X/Z thickness
            powerBarInstance.transform.scale(currentUnitScale * pulseWidthMult, height * currentUnitScale, currentUnitScale * pulseWidthMult);

            batch.render(powerBarInstance, env);
        }
    }

    public void dispose() { powerBarModel.dispose(); }
}