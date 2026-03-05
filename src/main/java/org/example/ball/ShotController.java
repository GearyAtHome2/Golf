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
import org.example.hud.HUD;
import org.example.terrain.Terrain;

public class ShotController {
    private Model powerBarModel;
    private ModelInstance powerBarInstance;
    private ModelInstance maxPowerGhost;

    private float spaceHoldTime = 0f;
    private float shotPower = 0f;
    private boolean isCharging = false;
    private final float MAX_POWER = 3f;

    private boolean isPowerLocked = false;
    private float lockedPower = 0f;
    private float lockTimer = 0f;
    private final float LOCK_DURATION = 0.4f;

    private boolean waitingForMinigame = false;

    private float cancelCooldown = 0f;
    private final float CANCEL_COOLDOWN_TIME = 0.5f;

    private float animationTimer = 0f;
    private ShotDifficulty currentDifficulty;

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

    public void reset() {
        isCharging = false;
        isPowerLocked = false;
        waitingForMinigame = false;
        spaceHoldTime = 0f;
        lockedPower = 0f;
        lockTimer = 0f;
        cancelCooldown = CANCEL_COOLDOWN_TIME;
    }

    public boolean update(float delta, Ball ball, Vector3 camDir, Club club, HUD hud, Terrain terrain) {
        animationTimer += delta;
        if (cancelCooldown > 0) cancelCooldown -= delta;

        if (waitingForMinigame) {
            // If the HUD was canceled (which now only happens BEFORE the needle stop)
            if (hud.wasMinigameCanceled()) {
                reset();
                return false;
            }

            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            }

            if (hud.isMinigameComplete()) {
                waitingForMinigame = false;
                executeShot(ball, camDir, club, lockedPower, hud, terrain, hud.getMinigameResult());
                return true;
            }
            return false;
        }

        currentDifficulty = terrain.getShotDifficulty(ball.getPosition().x, ball.getPosition().z, camDir);
        currentDifficulty.clubDifficulty = MathUtils.clamp(club.powerMult / 20f, 1.0f, 2.0f);
        Vector2 spinOffset = hud.getSpinOffset();
        currentDifficulty.swingDifficulty = 1.0f + (spinOffset.len() * 0.75f);

        if (isPowerLocked) {
            lockTimer += delta;
            if (lockTimer >= LOCK_DURATION) {
                isPowerLocked = false;
                float powerMod = 0.5f + (lockedPower / MAX_POWER);
                hud.logShotInitiated(ball.getPosition(), club, terrain, currentDifficulty, powerMod);
                waitingForMinigame = true;
                lockTimer = 0;
            }
            return false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0) {
                isCharging = false;
                spaceHoldTime = 0;
                isPowerLocked = true;
                lockedPower = MAX_POWER;
                lockTimer = 0;
                return false;
            }
        }

        if (isCharging && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            reset();
            return false;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0) {
                isCharging = true;
                spaceHoldTime = Math.min(spaceHoldTime + (delta * 2f), MAX_POWER);
            }
            return false;
        } else if (isCharging) {
            isCharging = false;
            isPowerLocked = true;
            lockedPower = spaceHoldTime;
            lockTimer = 0;
            spaceHoldTime = 0;
            return false;
        }

        if (shotPower > 0) {
            shotPower -= delta * 6f;
            if (shotPower < 0) shotPower = 0;
        }
        return false;
    }

    private void executeShot(Ball ball, Vector3 camDir, Club club, float power, HUD hud, Terrain terrain, MinigameResult result) {
        float accuracy = result.accuracy;
        float accuracyPowerMod = result.powerMod;

        float MAX_DELOFT = -65.0f;
        float BASE_SPIN_MULT = 14.5f;
        float PINCH_FACTOR = 1.8f;
        float INPUT_EXP = 2.8f;

        Vector2 rawOffset = hud.getSpinOffset();
        float rawR = MathUtils.clamp(rawOffset.len(), 0f, 1f);
        float quadR = (float)Math.pow(rawR, INPUT_EXP);
        Vector2 quadOffset = new Vector2();
        if (rawR > 0) quadOffset.set(rawOffset).nor().scl(quadR);

        float powerPenalty = (float)Math.pow(rawR, 6) * 0.40f;
        float finalPowerMult = club.powerMult * (1.0f - powerPenalty) * accuracyPowerMod;

        // --- PHYSICAL TERRAIN KICK ---
        // Get the normal of the ground under the ball
        Vector3 terrainNormal = terrain.getNormalAt(ball.getPosition().x, ball.getPosition().z);
        Vector3 aimDir = new Vector3(camDir.x, 0, camDir.z).nor();
        Vector3 rightOfAim = new Vector3(aimDir).crs(Vector3.Y).nor();

        // Dot product: how much does the ground tilt toward our 'right' vector?
        float sidePush = terrainNormal.dot(rightOfAim);
        // A side tilt of 0.2 (pretty steep) results in a ~10 degree kick
        float physicalKickDegrees = sidePush * 30.0f;

        float accuracySquirt = accuracy * 12.0f;
        Vector3 shotDir = new Vector3(aimDir);

        // Apply: Input rotation + Accuracy error + Physics kick from slope
        shotDir.rotate(Vector3.Y, (quadOffset.x * 2.5f) - accuracySquirt - physicalKickDegrees);

        float deloftAbility = MathUtils.clamp(1.2f - (club.loft / 45f), 0.2f, 1.2f);
        float adjustedLoft = MathUtils.clamp(club.loft + (quadOffset.y * MAX_DELOFT * deloftAbility), 0.1f, 85f);

        ball.hit(shotDir, power, adjustedLoft, finalPowerMult, result.rating);

        float totalSpeed = power * finalPowerMult;
        float attackAngle = quadOffset.y * -20.0f;
        float spinLoft = Math.abs(club.loft - attackAngle);
        float sForce = (float)Math.sin(spinLoft * MathUtils.degreesToRadians);
        float pinchFactor = 1.0f + (quadOffset.y * PINCH_FACTOR);

        float finalSpinX = totalSpeed * sForce * BASE_SPIN_MULT * Math.max(0.05f, pinchFactor);
        float sideSpinFromAccuracy = accuracy * totalSpeed * 45.0f;

        float finalSpinY = (quadOffset.x * totalSpeed * -10.0f) + sideSpinFromAccuracy;

        ball.getSpin().x = finalSpinX;
        ball.getSpin().y = finalSpinY;

        Gdx.app.log("SHOT_MASTER", "--- TERRAIN KICK: " + physicalKickDegrees + " deg ---");
    }

    public ShotDifficulty getCurrentDifficulty() { return currentDifficulty; }

    public void render(ModelBatch batch, Environment env, Vector3 ballPos, Vector3 camPos) {
        float height = isPowerLocked ? lockedPower : (isCharging ? spaceHoldTime : shotPower);
        if (height <= 0 && !isCharging && !isPowerLocked && !waitingForMinigame) return;

        float dist = camPos.dst(ballPos);
        float baseScaleFactor = 0.04f;
        float currentUnitScale = MathUtils.clamp(dist * baseScaleFactor, 0.15f, 1.2f);

        float bulgeMult = 1.0f;
        if (isPowerLocked || waitingForMinigame) {
            bulgeMult = 1.0f + (MathUtils.sin((lockTimer / LOCK_DURATION) * MathUtils.PI) * 0.4f);
            if (waitingForMinigame) bulgeMult = 1.4f;
        } else if (isCharging && spaceHoldTime >= MAX_POWER) {
            bulgeMult = 1.2f + (MathUtils.sin(animationTimer * 18f) * 0.2f);
        }

        float verticalOffset = currentUnitScale * 1.5f;

        if (isCharging || isPowerLocked || waitingForMinigame) {
            maxPowerGhost.transform.setToTranslation(ballPos.x, ballPos.y + verticalOffset + (MAX_POWER * currentUnitScale / 2f), ballPos.z);
            maxPowerGhost.transform.set(maxPowerGhost.transform).scale(currentUnitScale * 1.1f, MAX_POWER * currentUnitScale, currentUnitScale * 1.1f);
            batch.render(maxPowerGhost, env);
        }

        float displayHeight = waitingForMinigame ? lockedPower : height;
        if (displayHeight > 0) {
            Color color = displayHeight < (MAX_POWER / 2) ?
                    Color.GREEN.cpy().lerp(Color.YELLOW, displayHeight / (MAX_POWER / 2)) :
                    Color.YELLOW.cpy().lerp(Color.RED, (displayHeight - (MAX_POWER / 2)) / (MAX_POWER / 2));

            if (isPowerLocked || waitingForMinigame) color.lerp(Color.WHITE, 0.5f);

            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(color));
            powerBarInstance.transform.setToTranslation(ballPos.x, ballPos.y + verticalOffset + (displayHeight * currentUnitScale / 2f), ballPos.z);
            powerBarInstance.transform.set(powerBarInstance.transform).scale(currentUnitScale * bulgeMult, displayHeight * currentUnitScale, currentUnitScale * bulgeMult);
            batch.render(powerBarInstance, env);
        }
    }

    public boolean isCharging() { return isCharging || isPowerLocked || waitingForMinigame; }
    public void dispose() { if (powerBarModel != null) powerBarModel.dispose(); }
}