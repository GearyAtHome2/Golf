package com.gearygolf.golf.ball;

import com.badlogic.gdx.Gdx;
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
import com.gearygolf.golf.Club;
import com.gearygolf.golf.hud.HUD;
import com.gearygolf.golf.hud.SwingGestureAnalyser;
import com.gearygolf.golf.hud.SwingResult;
import com.gearygolf.golf.input.GameInputProcessor;
import com.gearygolf.golf.terrain.Terrain;

public class ShotController {
    private static final float MIN_RENDER_SCALE = 0.15f;
    private static final float MAX_RENDER_SCALE = 2.2f;
    private static final float DISTANCE_SCALE_FACTOR = 0.04f;

    private static final float BASE_VERTICAL_GAP = 0.2f;
    private static final float GAP_SCALE_MODIFIER = 0.4f;

    private static final boolean DEBUG_SHOT = true;

    private Model powerBarModel;
    private ModelInstance powerBarInstance;
    private ModelInstance maxPowerGhost;

    private Model projectionLineModel;
    private ModelInstance projectionLineInstance;
    private Model targetDotModel;
    private ModelInstance targetDotInstance;

    private float spaceHoldTime = 0f;
    private boolean isCharging = false;
    private final float MAX_POWER = 3f;

    private boolean isPowerLocked = false;
    private float lockedPower = 0f;
    private float lockTimer = 0f;
    private final float LOCK_DURATION = 0.4f;

    private boolean waitingForMinigame = false;
    private float cancelCooldown = 0f;
    private final float CANCEL_COOLDOWN_TIME = 0.5f;

    /** Lead time (seconds) between playing the swing sound and ball.hit() firing. */
    private static final float SHOT_SOUND_LEAD = 0.1f;
    /**
     * Peak forward-swing speed (HUD px/frame) that maps linearly to MAX_POWER.
     * Speeds above this are capped. Linear feel matches real golf: a 50% swing = ~50% power.
     * Tune if shots feel too weak/strong: raise to make full power harder to reach, lower to ease it.
     */
    private static final float SWING_FULL_POWER_SPEED = 280f;
    private boolean pendingShot = false;
    private float shotLeadTimer = 0f;
    private MinigameResult pendingResult;
    private Club pendingClub;
    private float pendingPower;

    private float animationTimer = 0f;
    private ShotDifficulty currentDifficulty;

    private com.gearygolf.golf.glamour.SoundManager soundManager;

    public void setSoundManager(com.gearygolf.golf.glamour.SoundManager sm) { this.soundManager = sm; }

    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 projectionVector = new Vector3();
    private final Vector2 tempSpin = new Vector2();

    private Vector3 lastBallPos = new Vector3();
    private boolean ballIsStationary = false;
    private boolean isSpinLocked = false;
    private final Vector2 lockedSpin = new Vector2();
    private final Vector3 lockedCamDir = new Vector3();
    private boolean showGuideline = false;
    private boolean guidelineAvailable = true;

    public ShotController() {
        ModelBuilder mb = new ModelBuilder();
        powerBarModel = mb.createBox(0.5f, 1f, 0.5f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(1f)),
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

    public void setGuidelineAvailable(boolean available) {
        this.guidelineAvailable = available;
        if (!available) this.showGuideline = false;
    }

    public void toggleGuideline() {
        this.showGuideline = !this.showGuideline;
    }

    public void reset() {
        isCharging = false;
        isPowerLocked = false;
        waitingForMinigame = false;
        pendingShot = false;
        shotLeadTimer = 0f;
        spaceHoldTime = 0f;
        lockedPower = 0f;
        lockTimer = 0f;
        cancelCooldown = CANCEL_COOLDOWN_TIME;
        isSpinLocked = false;
        lockedSpin.set(0, 0);
        lockedCamDir.set(0, 0, 0);
        projectionVector.set(0, 0, 0);
    }

    public boolean update(float delta, Ball ball, Vector3 camDir, Club club, HUD hud, Terrain terrain, GameInputProcessor input) {
        animationTimer += delta;
        if (cancelCooldown > 0) cancelCooldown -= delta;

        lastBallPos.set(ball.getPosition());
        ballIsStationary = (ball.getState() == Ball.State.STATIONARY);

        if (ballIsStationary) {
            if (isSpinLocked) {
                calculateShotVector(projectionVector, lockedCamDir, club, lockedSpin, terrain, 0f);
            } else if (waitingForMinigame) {
                calculateShotVector(projectionVector, lockedCamDir, club, hud.getSpinOffset(), terrain, 0f);
            } else {
                calculateShotVector(projectionVector, camDir, club, hud.getSpinOffset(), terrain, 0f);
            }
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.PROJECTION) && guidelineAvailable) {
            toggleGuideline();
        }

        if (pendingShot) {
            shotLeadTimer -= delta;
            if (shotLeadTimer <= 0f) {
                pendingShot = false;
                executeShot(ball, lockedCamDir, pendingClub, pendingPower, lockedSpin, terrain, pendingResult);
                return true;
            }
            return false;
        }

        if (waitingForMinigame) {
            if (hud.wasMinigameCanceled()) {
                reset();
                return false;
            }

            // ── New swing gesture path ────────────────────────────────────────
            if (hud.isSwingViewActive()) {
                SwingResult swingResult = hud.consumeSwingResult();
                if (swingResult != null) {
                    // Linear: power is directly proportional to peak swing speed, capped at MAX_POWER.
                    // Matches real golf feel — a 50% swing genuinely produces ~50% power.
                    pendingPower = MathUtils.clamp(
                            swingResult.peakForwardSpeed / SWING_FULL_POWER_SPEED * MAX_POWER,
                            0.0f, MAX_POWER);
                    Gdx.app.log("SwingDebug", String.format(
                            "[POWER] peakSpeed=%.1f → power=%.2f (%.0f%% max)",
                            swingResult.peakForwardSpeed, pendingPower, pendingPower / MAX_POWER * 100f));
                    waitingForMinigame = false;
                    isSpinLocked = false;
                    if (soundManager != null) soundManager.playBallStrike(clubSoundCategory(club), pendingPower / MAX_POWER);
                    pendingResult = convertSwingResult(swingResult);
                    pendingClub   = club;
                    pendingShot   = true;
                    shotLeadTimer = SHOT_SOUND_LEAD;
                }
                return false;
            }

            // ── Legacy spindicator path ───────────────────────────────────────
            if (hud.isMinigameComplete()) {
                waitingForMinigame = false;
                isSpinLocked = false;
                // Play the swing sound now — the clip has ~50ms of swing before impact,
                // so ball.hit() fires after SHOT_SOUND_LEAD to keep them in sync.
                if (soundManager != null) soundManager.playBallStrike(clubSoundCategory(club), lockedPower / MAX_POWER);
                pendingResult = hud.getMinigameResult();
                pendingClub   = club;
                pendingPower  = lockedPower;
                pendingShot   = true;
                shotLeadTimer = SHOT_SOUND_LEAD;
            }
            return false;
        }

        currentDifficulty = terrain.getShotDifficulty(ball.getPosition().x, ball.getPosition().z, lockedCamDir);
        currentDifficulty.clubDifficulty = MathUtils.clamp(club.powerMult / 20f, 1.0f, 2.0f);
        if (club == Club.SWEDGE && terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z) == Terrain.TerrainType.SAND) {
            currentDifficulty.terrainDifficulty *= 0.5f; // Sand wedge specialist: ~4.6 → ~2.3 (comparable to rough)
        }
        Vector2 spinOffset = hud.getSpinOffset();
        currentDifficulty.swingDifficulty = 1.0f + (spinOffset.len() * 0.75f);

        if (isPowerLocked) {
            lockTimer += delta;
            if (lockTimer >= LOCK_DURATION) {
                isPowerLocked = false;
                float powerMod = 0.5f + (lockedPower / MAX_POWER);

                // lockedCamDir was already captured before the camera started lerping —
                // do NOT overwrite it here or we'd use the lerped swing-view direction.
                hud.logShotInitiated(ball.getPosition(), club, currentDifficulty, powerMod);
                waitingForMinigame = true;
                lockTimer = 0;
            }
            return false;
        }

        // Both hit actions open the swing view immediately — power comes from swing speed.
        if (input.isActionPressed(GameInputProcessor.Action.MAX_POWER_SHOT)
                || input.isActionPressed(GameInputProcessor.Action.CHARGE_SHOT)) {
            if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0 && !waitingForMinigame) {
                lockedCamDir.set(camDir);
                hud.logShotInitiated(ball.getPosition(), club, currentDifficulty, 1.0f);
                waitingForMinigame = true;
            }
            return false;
        }

        return false;
    }

    public void snapshotFinalSpin(Vector2 currentSpin) {
        this.lockedSpin.set(currentSpin);
        this.isSpinLocked = true;
    }

    private void executeShot(Ball ball, Vector3 aimDirFreeform, Club club, float power, Vector2 spin, Terrain terrain, MinigameResult result) {
        float rawR = MathUtils.clamp(spin.len(), 0f, 1f);

        // Edge-of-face mis-hit factor: essentially zero until the last 8% of spindicator radius,
        // then rises sharply to 1.0 at the rim. Simulates topping, toeing, and heeling.
        float edgeNorm = MathUtils.clamp((rawR - 0.92f) / 0.08f, 0f, 1f);
        float edgeFactor = (float) Math.pow(edgeNorm, 5);

        calculateShotVector(tempV1, aimDirFreeform, club, spin, terrain, result.accuracy);

        // Amplify direction deviation at the edge: heel/toe push (yaw) and over/deloft (pitch)
        if (edgeFactor > 0.001f && rawR > 0.001f) {
            tempV2.set(aimDirFreeform.x, 0, aimDirFreeform.z).nor();
            projectionVector.set(tempV2).crs(Vector3.Y).nor();
            tempSpin.set(spin).nor();
            tempV1.rotate(Vector3.Y, tempSpin.x * edgeFactor * 20f);
            tempV1.rotate(projectionVector, tempSpin.y * edgeFactor * 15f);
            tempV1.nor();
        }

        // Power: smooth base off-centre penalty + sharp edge penalty, capped at 75%
        float powerPenalty = Math.min(0.75f, (float) Math.pow(rawR, 6) * 0.40f + edgeFactor * 0.75f);
        float finalPowerMult = club.powerMult * (1.0f - powerPenalty) * result.powerMod;

        // Terrain penalties: scale power and spin based on surface type and shot quality
        // relative to what's achievable with the selected club. Applied before ball.hit()
        // so ShotPacket values are already-penalised — multiplayer replay needs no changes.
        Terrain.TerrainType terrainType = terrain.getTerrainTypeAt(lastBallPos.x, lastBallPos.z);
        float[] terrainPenalty = getTerrainPenalties(terrainType, relativeQuality(result.rating, club));
        finalPowerMult *= terrainPenalty[0];
        float terrainSpinMult = terrainPenalty[1];

        float launchLoft = (float) Math.asin(tempV1.y) * MathUtils.radiansToDegrees;
        ball.hit(tempV1, power, launchLoft, finalPowerMult, result.rating);
        if (soundManager != null) soundManager.startFlightSound(result.rating);

        tempV2.set(aimDirFreeform.x, 0, aimDirFreeform.z).nor();
        projectionVector.set(tempV2).crs(Vector3.Y).nor();

        Vector2 quadOffset = getQuadraticSpinOffset(spin);
        float attackAngle = quadOffset.y * -20.0f;
        float sForce = (float) Math.sin(Math.abs(club.loft - attackAngle) * MathUtils.degreesToRadians);
        float spinCurve = (float) Math.pow(MathUtils.clamp(power / MAX_POWER, 0f, 1f), 1.5f);
        float quality = getQualityFactor(result.rating);

        // As contact drifts off-centre, clean groove contact degrades and the attack-angle
        // backspin bonus diminishes — even a steep downward strike loses spin transfer.
        // rawR^4 * 0.70 means ~41% bonus reduction at rawR=0.8, ~82% at rawR=0.95.
        float attackAngleDamping = 1f - (float) Math.pow(rawR, 4) * 0.70f;

        // Low-loft clubs (long irons) benefit strongly from steep attack angles — stinger effect.
        // High-loft clubs generate spin naturally from loft alone; attack angle adds little extra.
        // loftFactor^2 gives a sharp curve: 2i/3i/4i keep high bonus, 9i/wedges nearly lose it.
        float loftFactor = MathUtils.clamp(1f - (club.loft - 10f) / 45f, 0f, 1f);
        // Long irons benefit strongly from attack angle (stinger effect, high loftFactor → high coeff).
        // Wedges also benefit — groove engagement on a downward strike — but this was near-zero before.
        // lerp gives a floor of 0.4 for high-loft clubs so pressing forward is meaningfully rewarded.
        float attackAngleCoeff = MathUtils.lerp(0.4f, 1.4f, loftFactor * loftFactor);

        // Per-club optimal attack angle sweet spot. Rising to the peak gives maximum backspin
        // efficiency; pushing past it has diminishing returns (over-steep = loss of groove contact).
        // Long irons peak later (~0.50) to reward the stinger zone; short irons peak earlier (~0.28).
        float optimalAttack = MathUtils.lerp(0.28f, 0.50f, loftFactor);
        float attackEfficiency;
        if (quadOffset.y <= 0f) {
            attackEfficiency = 0f; // ascending blow — no backspin bonus
        } else if (quadOffset.y <= optimalAttack) {
            attackEfficiency = quadOffset.y / optimalAttack; // linear rise to peak
        } else {
            attackEfficiency = 1f - 0.65f * (quadOffset.y - optimalAttack) / (1f - optimalAttack);
        }
        attackEfficiency = MathUtils.clamp(attackEfficiency, 0f, 1f);

        float backspin = (power * finalPowerMult) * sForce * 9.0f * (1.0f + (quadOffset.y * attackAngleCoeff * attackAngleDamping * attackEfficiency)) * quality * spinCurve * terrainSpinMult * club.spinMult;
        float sidespin = ((quadOffset.x * (power * finalPowerMult) * -10.0f * quality * spinCurve) + (result.accuracy * (power * finalPowerMult) * 60.0f * spinCurve)) * terrainSpinMult * club.spinMult;

        // Edge spin reversal: crosses zero at edgeFactor=0.5, fully reversed at edgeFactor=1.
        // At the rim, backspin becomes topspin (topping the ball).
        float spinReversalFactor = 1f - (2f * edgeFactor);
        backspin *= spinReversalFactor;
        sidespin *= spinReversalFactor;

        if (DEBUG_SHOT) Gdx.app.log("ShotDebug", String.format(
            "[%s] pwr=%.2f finalPwr=%.1f sForce=%.3f spinMult=%.2f atkCoeff=%.2f atkEff=%.2f back=%.1f side=%.1f",
            club.name, power, finalPowerMult, sForce, club.spinMult, attackAngleCoeff, attackEfficiency, backspin, sidespin));

        ball.getSpin().set(projectionVector).scl(backspin);
        ball.getSpin().add(tempV2.set(Vector3.Y).scl(-sidespin));
    }

    private void calculateShotVector(Vector3 out, Vector3 camDir, Club club, Vector2 spin, Terrain terrain, float accuracy) {
        Vector2 quadOffset = getQuadraticSpinOffset(spin);
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

    /** Maps a Club to a SoundManager category string. */
    private static String clubSoundCategory(Club club) {
        if (club == Club.DRIVER) return "driver";
        if (club == Club.PUTTER) return "putter";
        String n = club.name(); // e.g. "PWEDGE", "GWEDGE", "SWEDGE", "LWEDGE"
        if (n.contains("WEDGE")) return "wedge";
        return "iron"; // covers IRON_*, WOOD_*, HYBRID_*
    }

    private float getQualityFactor(MinigameResult.Rating rating) {
        return switch (rating) {
            case PERFECTION         -> 1.2f;
            case SUPER              -> 1.1f;
            case GREAT              -> 1.05f;
            case GOOD               -> 1.0f;
            case POOR               -> 0.6f;
            case TERRIBLE, ABYSMAL  -> 0.3f;
        };
    }

    /** 0.0 = worst possible, 1.0 = best possible for this club. */
    private float relativeQuality(MinigameResult.Rating rating, Club club) {
        boolean hasPerfection = club.baseDifficulty >= 1.6f;
        boolean hasSuper      = club.baseDifficulty >= 1.3f;
        boolean hasGreat      = club.baseDifficulty >= 1.0f;
        return switch (rating) {
            case PERFECTION         -> 1.0f;
            case SUPER              -> hasPerfection ? 0.67f : 1.0f;
            case GREAT              -> hasPerfection ? 0.5f : (hasSuper ? 0.75f : 1.0f);
            case GOOD               -> hasPerfection ? 0.33f : (hasSuper ? 0.5f : (hasGreat ? 0.5f : 1.0f));
            case POOR               -> 0.1f;
            case TERRIBLE, ABYSMAL  -> 0.0f;
        };
    }

    /**
     * Returns [powerMult, spinMult] for the given terrain and relative shot quality (0..1).
     * Fairway/tee/green/stone/fringe return [1, 1] — no terrain layer applied.
     * MUD is quality-independent (flat penalty regardless of how well you hit it).
     */
    private float[] getTerrainPenalties(Terrain.TerrainType type, float q) {
        return switch (type) {
            case ROUGH      -> new float[]{ MathUtils.lerp(0.95f, 1.0f, q), MathUtils.lerp(0.70f, 1.0f, q) };
            case DEEP_ROUGH -> new float[]{ MathUtils.lerp(0.85f, 1.0f, q), MathUtils.lerp(0.40f, 0.80f, q) };
            case SAND       -> new float[]{ MathUtils.lerp(0.95f, 1.0f, q), MathUtils.lerp(0.55f, 1.0f, q) };
            case MUD        -> new float[]{ 0.80f, 0.20f };
            default         -> new float[]{ 1.0f, 1.0f };
        };
    }

    public Vector3 getLockedCamDir() {
        if (lockedCamDir.isZero()) return lastBallPos;
        return lockedCamDir;
    }

    public void render(ModelBatch batch, Environment env, Vector3 ballPos, Vector3 camPos) {
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

        // In swing-gesture mode, waitingForMinigame is true but there is no power bar to show.
        if (waitingForMinigame) return;

        float height = isPowerLocked ? lockedPower : (isCharging ? spaceHoldTime : 0f);
        if (height <= 0 && !isCharging && !isPowerLocked) return;

        float dist = camPos.dst(ballPos);
        float currentUnitScale = MathUtils.clamp(dist * DISTANCE_SCALE_FACTOR * HUD.UI_SCALE, MIN_RENDER_SCALE, MAX_RENDER_SCALE);

        float vOffset = BASE_VERTICAL_GAP + (currentUnitScale * GAP_SCALE_MODIFIER);

        float bulge = 1.0f;
        if (isPowerLocked || waitingForMinigame) {
            bulge = 1.0f + (MathUtils.sin((lockTimer / LOCK_DURATION) * MathUtils.PI) * 0.4f);
            if (waitingForMinigame) bulge = 1.4f;
        } else if (isCharging && spaceHoldTime >= MAX_POWER) {
            bulge = 1.2f + (MathUtils.sin(animationTimer * 18f) * 0.2f);
        }

        float displayH = waitingForMinigame ? lockedPower : height;

        if (isCharging || isPowerLocked || waitingForMinigame) {
            maxPowerGhost.transform.setToTranslation(ballPos.x, ballPos.y + vOffset + (MAX_POWER * currentUnitScale / 2f), ballPos.z);
            maxPowerGhost.transform.set(maxPowerGhost.transform).scale(currentUnitScale * 1.1f, MAX_POWER * currentUnitScale, currentUnitScale * 1.1f);
            batch.render(maxPowerGhost, env);
        }

        if (displayH > 0) {
            Color color = displayH < (MAX_POWER / 2) ? Color.GREEN.cpy().lerp(Color.YELLOW, displayH / (MAX_POWER / 2)) : Color.YELLOW.cpy().lerp(Color.RED, (displayH - (MAX_POWER / 2)) / (MAX_POWER / 2));
            color.a = (isPowerLocked || waitingForMinigame) ? 0.85f : 1.0f;
            if (isPowerLocked || waitingForMinigame) color.lerp(Color.WHITE, 0.5f);

            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(color));
            ((BlendingAttribute) powerBarInstance.materials.get(0).get(BlendingAttribute.Type)).opacity = color.a;

            powerBarInstance.transform.setToTranslation(ballPos.x, ballPos.y + vOffset + (displayH * currentUnitScale / 2f), ballPos.z);
            powerBarInstance.transform.set(powerBarInstance.transform).scale(currentUnitScale * bulge, displayH * currentUnitScale, currentUnitScale * bulge);
            batch.render(powerBarInstance, env);
        }
    }

    public boolean isCharging() {
        return isCharging || isPowerLocked || waitingForMinigame || pendingShot;
    }

    /**
     * Converts a SwingResult (from the gesture overlay) into a MinigameResult
     * so the existing shot-execution pipeline can consume it unchanged.
     *
     * Composite quality = tempoQuality × contactFactor × followThroughFactor
     *   contactFactor     : 1.0 at centre, 0.4 at full heel/toe
     *   followThroughFactor: 0.65 for HIGH (flip/scoop), 1.0 for LOW/NEUTRAL
     *
     * powerMod lerps 0.60 → 1.20 over composite quality.
     * accuracy maps pathDeg to the direction-error input expected by executeShot:
     *   +ve = push right (fade), -ve = push left (draw).
     */
    private MinigameResult convertSwingResult(SwingResult s) {
        float contactFactor = 1f - (Math.abs(s.contactOffset) * 0.6f);
        float ftFactor = s.followThroughResult == SwingGestureAnalyser.FollowThroughResult.HIGH
                       ? 0.65f : 1.0f;
        float compositeQuality = s.tempoQuality * contactFactor * ftFactor;

        MinigameResult.Rating rating;
        if      (compositeQuality >= 0.90f) rating = MinigameResult.Rating.SUPER;
        else if (compositeQuality >= 0.75f) rating = MinigameResult.Rating.GREAT;
        else if (compositeQuality >= 0.55f) rating = MinigameResult.Rating.GOOD;
        else if (compositeQuality >= 0.30f) rating = MinigameResult.Rating.POOR;
        else if (compositeQuality >= 0.15f) rating = MinigameResult.Rating.TERRIBLE;
        else                                rating = MinigameResult.Rating.ABYSMAL;

        MinigameResult r = new MinigameResult();
        r.rating   = rating;
        r.powerMod = MathUtils.lerp(0.60f, 1.20f, compositeQuality);
        // pathDeg: +ve = in-to-out (draw); map to accuracy: +ve = fade, -ve = draw
        r.accuracy = MathUtils.clamp(-(s.pathDeg / 15f) * (1f - Math.abs(s.contactOffset) * 0.5f), -1f, 1f);

        Gdx.app.log("SwingDebug", String.format(
            "[CONVERT] tempo_q=%.2f contact=%.2f ft=%s → composite=%.2f → %s pwr_mod=%.2f acc=%.2f",
            s.tempoQuality, s.contactOffset, s.followThroughResult,
            compositeQuality, rating, r.powerMod, r.accuracy));

        return r;
    }

    public void dispose() {
        if (powerBarModel != null) powerBarModel.dispose();
        if (projectionLineModel != null) projectionLineModel.dispose();
        if (targetDotModel != null) targetDotModel.dispose();
    }
}