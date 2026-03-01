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
        // --- TUNING KNOBS ---
        float PWR_EXP = 5.0f;           // Sweet spot size (higher = larger flat center)
        float PWR_PENALTY = 0.8f;      // Max power loss at extreme edge
        float SPIN_EXP = 2.0f;          // Spin growth curve (higher = edge is sharper)
        float LOFT_ADJ = -15.0f;        // Max degrees to add/remove from club loft
        float ATTACK_ANGLE_MAX = 18.0f; // Max angle of attack for spin calculation
        float STINGER_LIMIT = 30.0f;    // Clubs with loft below this can "Stinger"
        float STINGER_BOOST = 3.5f;     // Strength of the upward climb effect
        float BASE_SPIN_MULT = 22.0f;   // Global backspin master volume
        float SPIN_LOFT_CAP = 35.0f;    // Prevents infinite spin on high-lofted clubs
        // --------------------

        Vector2 spinOffset = hud.getSpinOffset();
        float r = MathUtils.clamp(spinOffset.len(), 0f, 1f);

        float powerEfficiency = 1.0f - (float)Math.pow(r, PWR_EXP) * PWR_PENALTY;
        float finalPowerMult = club.powerMult * powerEfficiency;

        float spinSensitivity = (float)Math.pow(r, SPIN_EXP);

        float deloftResistance = MathUtils.clamp(1.0f - (club.loft / 60f), 0.2f, 1.0f);
        float loftAdjustment = (spinOffset.y * spinSensitivity) * (LOFT_ADJ * deloftResistance);
        float adjustedLoft = MathUtils.clamp(club.loft + loftAdjustment, 1f, 65f);

        Vector3 shootDir = new Vector3(camDir.x, 0, camDir.z).nor();
        shootDir.rotate(Vector3.Y, spinOffset.x * 2.5f);
        ball.hit(shootDir, power, adjustedLoft, finalPowerMult);

        float totalSpeed = power * finalPowerMult;
        ball.getSpin().y = -spinOffset.x * spinSensitivity * totalSpeed * 8.0f;

        float attackAngle = (spinOffset.y * spinSensitivity) * ATTACK_ANGLE_MAX;
        float spinLoft = MathUtils.clamp(Math.abs(adjustedLoft - attackAngle), 5f, SPIN_LOFT_CAP);

        float spinForce = (float) Math.sin(spinLoft * MathUtils.degreesToRadians);
        float contactQuality = (spinOffset.y > 0) ? 1.0f : 0.4f;
        float clubSpinDamping = MathUtils.clamp(1.2f - (club.loft / 50f), 0.4f, 1.0f);

        float stingerScale = 0.6f;
        if (spinOffset.y > 0) {
            float loftFactor = MathUtils.clamp(1.0f - (club.loft / STINGER_LIMIT), 0f, 1.0f);
            stingerScale += (spinOffset.y * spinSensitivity * STINGER_BOOST * loftFactor);
        }

        float speedNormalized = totalSpeed / 25f;
        ball.getSpin().x = totalSpeed * spinForce * BASE_SPIN_MULT * speedNormalized * contactQuality * stingerScale * clubSpinDamping;
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