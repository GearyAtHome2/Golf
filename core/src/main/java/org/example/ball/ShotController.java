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

    private Model projectionLineModel;
    private ModelInstance projectionLineInstance;
    private Model targetDotModel;
    private ModelInstance targetDotInstance;

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

    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 projectionVector = new Vector3();

    private Vector3 lastBallPos = new Vector3();
    private boolean ballIsStationary = false;

    // --- NEW FLAG ---
    private boolean showGuideline = false;

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

        projectionLineModel = mb.createBox(0.04f, 0.04f, 1f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(0.4f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        projectionLineInstance = new ModelInstance(projectionLineModel);

        targetDotModel = mb.createSphere(0.12f, 0.12f, 0.12f, 12, 12,
                new Material(ColorAttribute.createDiffuse(Color.RED)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        targetDotInstance = new ModelInstance(targetDotModel);
    }

    public void setGuidelineEnabled(boolean enabled) {
        this.showGuideline = enabled;
    }

    public void toggleGuideline() {
        this.showGuideline = !this.showGuideline;
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

        lastBallPos.set(ball.getPosition());
        ballIsStationary = (ball.getState() == Ball.State.STATIONARY);

        if (ballIsStationary) {
            calculateShotVector(projectionVector, camDir, club, hud, terrain, 0f);
        }

        if (waitingForMinigame) {
            if (hud.wasMinigameCanceled()) {
                reset();
                return false;
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
        calculateShotVector(tempV1, camDir, club, hud, terrain, result.accuracy);
        float rawR = MathUtils.clamp(hud.getSpinOffset().len(), 0f, 1f);
        float powerPenalty = (float) Math.pow(rawR, 6) * 0.40f;
        float finalPowerMult = club.powerMult * (1.0f - powerPenalty) * result.powerMod;
        float launchLoft = (float) Math.asin(tempV1.y) * MathUtils.radiansToDegrees;
        ball.hit(tempV1, power, launchLoft, finalPowerMult, result.rating);

        Vector3 aimDir = new Vector3(camDir.x, 0, camDir.z).nor();
        Vector3 rightOfAim = new Vector3(aimDir).crs(Vector3.Y).nor();
        Vector2 quadOffset = getQuadraticSpinOffset(hud.getSpinOffset());
        float attackAngle = quadOffset.y * -20.0f;
        float sForce = (float) Math.sin(Math.abs(club.loft - attackAngle) * MathUtils.degreesToRadians);
        float spinCurve = (float)Math.pow(MathUtils.clamp(power / MAX_POWER, 0f, 1f), 1.5f);
        float quality = getQualityFactor(result.rating);
        float backspin = (power * finalPowerMult) * sForce * 14.5f * (1.0f + (quadOffset.y * 1.8f)) * quality * spinCurve;
        float sidespin = (quadOffset.x * (power * finalPowerMult) * -10.0f * quality * spinCurve) + (result.accuracy * (power * finalPowerMult) * 60.0f * spinCurve);

        ball.getSpin().set(rightOfAim).scl(backspin);
        ball.getSpin().add(tempV2.set(Vector3.Y).scl(-sidespin));
    }

    private void calculateShotVector(Vector3 out, Vector3 camDir, Club club, HUD hud, Terrain terrain, float accuracy) {
        Vector2 quadOffset = getQuadraticSpinOffset(hud.getSpinOffset());
        Vector3 aimDir = new Vector3(camDir.x, 0, camDir.z).nor();
        Vector3 rightOfAim = new Vector3(aimDir).crs(Vector3.Y).nor();

        Vector3 terrainNormal = terrain.getNormalAt(lastBallPos.x, lastBallPos.z);
        float physicalKick = terrainNormal.dot(rightOfAim) * 30.0f;

        out.set(aimDir);
        out.rotate(Vector3.Y, (quadOffset.x * 2.5f) - (accuracy * 12.0f) - physicalKick);

        float deloftAbility = MathUtils.clamp(1.2f - (club.loft / 45f), 0.2f, 1.2f);
        float adjustedLoft = MathUtils.clamp(club.loft + (quadOffset.y * -65.0f * deloftAbility), 0.1f, 85f);

        float angleRad = adjustedLoft * MathUtils.degreesToRadians;
        out.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        out.x *= hLen;
        out.z *= hLen;
        out.nor();
    }

    private Vector2 getQuadraticSpinOffset(Vector2 rawOffset) {
        float rawR = MathUtils.clamp(rawOffset.len(), 0f, 1f);
        float quadR = (float) Math.pow(rawR, 2.8f);
        return (rawR > 0) ? new Vector2(rawOffset).nor().scl(quadR) : new Vector2(0, 0);
    }

    private float getQualityFactor(MinigameResult.Rating rating) {
        return switch (rating) {
            case PERFECTION -> 1.2f;
            case SUPER -> 1.1f;
            case POOR -> 0.6f;
            case WANK, SHIT -> 0.3f;
            default -> 1f;
        };
    }

    public void render(ModelBatch batch, Environment env, Vector3 ballPos, Vector3 camPos) {
        // --- GUIDELINE RENDERING ---
        if (showGuideline && ballIsStationary && ballPos != null) {
            float lineLength = 5.0f;
            projectionLineInstance.transform.setToTranslation(ballPos);
            projectionLineInstance.transform.rotateTowardDirection(projectionVector, Vector3.Y);
            projectionLineInstance.transform.scale(1f, 1f, lineLength);
            projectionLineInstance.transform.translate(0, 0, -0.5f);
            batch.render(projectionLineInstance, env);

            tempV1.set(ballPos).add(tempV2.set(projectionVector).scl(lineLength));
            targetDotInstance.transform.setToTranslation(tempV1);
            batch.render(targetDotInstance, env);
        }

        // --- POWER BAR RENDERING ---
        float height = isPowerLocked ? lockedPower : (isCharging ? spaceHoldTime : shotPower);
        if (height <= 0 && !isCharging && !isPowerLocked && !waitingForMinigame) return;

        float dist = camPos.dst(ballPos);
        float currentUnitScale = MathUtils.clamp(dist * 0.04f, 0.15f, 1.2f);
        float bulge = 1.0f;

        if (isPowerLocked || waitingForMinigame) {
            bulge = 1.0f + (MathUtils.sin((lockTimer / LOCK_DURATION) * MathUtils.PI) * 0.4f);
            if (waitingForMinigame) bulge = 1.4f;
        } else if (isCharging && spaceHoldTime >= MAX_POWER) {
            bulge = 1.2f + (MathUtils.sin(animationTimer * 18f) * 0.2f);
        }

        float vOffset = currentUnitScale * 1.5f;
        float displayH = waitingForMinigame ? lockedPower : height;

        if (isCharging || isPowerLocked || waitingForMinigame) {
            maxPowerGhost.transform.setToTranslation(ballPos.x, ballPos.y + vOffset + (MAX_POWER * currentUnitScale / 2f), ballPos.z);
            maxPowerGhost.transform.set(maxPowerGhost.transform).scale(currentUnitScale * 1.1f, MAX_POWER * currentUnitScale, currentUnitScale * 1.1f);
            batch.render(maxPowerGhost, env);
        }

        if (displayH > 0) {
            Color color = displayH < (MAX_POWER / 2) ? Color.GREEN.cpy().lerp(Color.YELLOW, displayH / (MAX_POWER / 2)) : Color.YELLOW.cpy().lerp(Color.RED, (displayH - (MAX_POWER / 2)) / (MAX_POWER / 2));
            if (isPowerLocked || waitingForMinigame) color.lerp(Color.WHITE, 0.5f);

            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(color));
            powerBarInstance.transform.setToTranslation(ballPos.x, ballPos.y + vOffset + (displayH * currentUnitScale / 2f), ballPos.z);
            powerBarInstance.transform.set(powerBarInstance.transform).scale(currentUnitScale * bulge, displayH * currentUnitScale, currentUnitScale * bulge);
            batch.render(powerBarInstance, env);
        }
    }

    public boolean isCharging() { return isCharging || isPowerLocked || waitingForMinigame; }

    public void dispose() {
        if (powerBarModel != null) powerBarModel.dispose();
        if (projectionLineModel != null) projectionLineModel.dispose();
        if (targetDotModel != null) targetDotModel.dispose();
    }
}