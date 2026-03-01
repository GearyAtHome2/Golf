package org.example.ball;

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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.example.Club;
import org.example.HUD;

public class ShotController {
    private Model powerBarModel;
    private ModelInstance powerBarInstance;
    private ModelInstance maxPowerGhost;

    private float spaceHoldTime = 0f;
    private float shotPower = 0f;
    private boolean isCharging = false;
    private final float MAX_POWER = 3f;

    private float cancelCooldown = 0f;
    private final float CANCEL_COOLDOWN_TIME = 0.5f;

    private float animationTimer = 0f;

    public ShotController() {
        ModelBuilder mb = new ModelBuilder();
        powerBarModel = mb.createBox(0.5f, 1f, 0.5f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        powerBarInstance = new ModelInstance(powerBarModel);
        maxPowerGhost = new ModelInstance(powerBarModel);

        maxPowerGhost.materials.get(0).set(
                new BlendingAttribute(0.5f),
                new DepthTestAttribute(false),
                ColorAttribute.createDiffuse(new Color(1, 1, 1, 0.5f))
        );
    }

    public boolean update(float delta, Ball ball, Vector3 camDir, Club club, HUD hud) {
        animationTimer += delta;
        if (cancelCooldown > 0) cancelCooldown -= delta;

        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0) {
                isCharging = false;
                spaceHoldTime = 0;
                executeShot(ball, camDir, club, MAX_POWER, hud);
                shotPower = MAX_POWER;
                return true;
            }
        }

        if (isCharging && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            isCharging = false;
            spaceHoldTime = 0f;
            cancelCooldown = CANCEL_COOLDOWN_TIME;
            return false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0) {
                isCharging = true;
                spaceHoldTime = Math.min(spaceHoldTime + delta, MAX_POWER);
            }
            return false;
        } else if (isCharging) {
            isCharging = false;
            shotPower = spaceHoldTime;
            executeShot(ball, camDir, club, shotPower, hud);
            spaceHoldTime = 0;
            return true;
        }

        if (shotPower > 0) {
            shotPower -= delta * 6f;
            if (shotPower < 0) shotPower = 0;
        }
        return false;
    }

    private void executeShot(Ball ball, Vector3 camDir, Club club, float power, HUD hud) {
        float MAX_DELOFT = -65.0f;
        float BASE_SPIN_MULT = 14.5f;
        float PINCH_FACTOR = 1.8f; // Increase this to boost the "Pinch" spin on Top hits
        float INPUT_EXP = 2.8f;

        Vector2 rawOffset = hud.getSpinOffset();
        float rawR = MathUtils.clamp(rawOffset.len(), 0f, 1f);
        float quadR = (float)Math.pow(rawR, INPUT_EXP);
        Vector2 quadOffset = new Vector2();
        if (rawR > 0) quadOffset.set(rawOffset).nor().scl(quadR);

        float powerPenalty = (float)Math.pow(rawR, 6) * 0.40f;
        float finalPowerMult = club.powerMult * (1.0f - powerPenalty);
        float totalSpeed = power * finalPowerMult;

        float deloftAbility = MathUtils.clamp(1.2f - (club.loft / 45f), 0.2f, 1.2f);
        float adjustedLoft = MathUtils.clamp(club.loft + (quadOffset.y * MAX_DELOFT * deloftAbility), 0.1f, 85f);

        Vector3 shotDir = new Vector3(camDir.x, 0, camDir.z).nor().rotate(Vector3.Y, quadOffset.x * 2.5f);
        ball.hit(shotDir, power, adjustedLoft, finalPowerMult);

        float attackAngle = quadOffset.y * -20.0f;
        float spinLoft = Math.abs(club.loft - attackAngle);
        float sForce = (float)Math.sin(spinLoft * MathUtils.degreesToRadians);

        // Scaling factor: 1.0 at center, grows by TOP_SPIN_SENSITIVITY at the top, shrinks at bottom
        float pinchFactor = 1.0f + (quadOffset.y * PINCH_FACTOR);

        ball.getSpin().x = totalSpeed * sForce * BASE_SPIN_MULT * Math.max(0.05f, pinchFactor);
        ball.getSpin().y = -quadOffset.x * totalSpeed * 10.0f;

        Gdx.app.log("SHOT_LOG", String.format("%s | Launch: %.1f | SpinX: %.0f | Pinch: %.2f",
                club.name(), adjustedLoft, ball.getSpin().x, pinchFactor));
    }

    public void render(ModelBatch batch, Environment env, Vector3 ballPos, Vector3 camPos) {
        float height = isCharging ? spaceHoldTime : shotPower;
        if (height <= 0 && !isCharging) return;

        float dist = camPos.dst(ballPos);
        float baseScaleFactor = 0.04f;
        float currentUnitScale = MathUtils.clamp(dist * baseScaleFactor, 0.15f, 1.2f);

        float pulseWidthMult = 1.0f;
        if (isCharging && spaceHoldTime >= MAX_POWER) {
            pulseWidthMult = 1.2f + (MathUtils.sin(animationTimer * 18f) * 0.2f);
        }

        float verticalOffset = currentUnitScale * 1.5f;

        if (isCharging) {
            maxPowerGhost.transform.setToTranslation(ballPos.x, ballPos.y + verticalOffset + (MAX_POWER * currentUnitScale / 2f), ballPos.z);
            maxPowerGhost.transform.set(maxPowerGhost.transform).scale(currentUnitScale * 1.1f, MAX_POWER * currentUnitScale, currentUnitScale * 1.1f);
            batch.render(maxPowerGhost, env);
        }

        if (height > 0) {
            Color color = height < (MAX_POWER / 2) ?
                    Color.GREEN.cpy().lerp(Color.YELLOW, height / (MAX_POWER / 2)) :
                    Color.YELLOW.cpy().lerp(Color.RED, (height - (MAX_POWER / 2)) / (MAX_POWER / 2));

            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(color));
            powerBarInstance.transform.setToTranslation(ballPos.x, ballPos.y + verticalOffset + (height * currentUnitScale / 2f), ballPos.z);
            powerBarInstance.transform.set(powerBarInstance.transform).scale(currentUnitScale * pulseWidthMult, height * currentUnitScale, currentUnitScale * pulseWidthMult);
            batch.render(powerBarInstance, env);
        }
    }

    public boolean isCharging() { return isCharging; }
    public void dispose() { if (powerBarModel != null) powerBarModel.dispose(); }
}