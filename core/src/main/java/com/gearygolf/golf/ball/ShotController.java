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
import com.gearygolf.golf.hud.SwingUIController;
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

    private enum ShotPhase { IDLE, CHARGING, POWER_LOCKED, WAITING, PENDING_SHOT }
    private ShotPhase phase = ShotPhase.IDLE;

    private float spaceHoldTime = 0f;
    private final float MAX_POWER = 3f;

    private float lockedPower = 0f;
    private float lockTimer = 0f;
    private final float LOCK_DURATION = 0.4f;

    private float cancelCooldown = 0f;
    private final float CANCEL_COOLDOWN_TIME = 0.5f;

    /**
     * tempoQuality below this → complete whiff; convertSwingResult returns null
     * and ShotController resets without firing the shot.
     */
    private static final float MISS_THRESHOLD = 0.02f;
    /** Lead time (seconds) between playing the swing sound and ball.hit() firing. */
    private static final float SHOT_SOUND_LEAD = 0.1f;
    /**
     * Club-head speed in screen widths per second (sw/s) that maps to 100% power ceiling.
     * 2.0 = traversing 200% of screen width per second — a fast but achievable forward swing.
     * Tune via playtest; kept in sync with SwingOverlay.fullPowerSpeed.
     */
    public static final float SWING_FULL_POWER_SPEED  = 2.0f;
    private float shotLeadTimer = 0f;
    private MinigameResult pendingResult;
    private Club pendingClub;
    private float pendingPower;

    private float animationTimer = 0f;
    private ShotDifficulty currentDifficulty;
    private boolean swingModeNew = false;
    private Terrain.TerrainType lastTerrainType = Terrain.TerrainType.FAIRWAY;
    private Club shotStartClub = Club.DRIVER;
    /** Difficulty and contact parameters — updated once per shot via setDifficultyParams(). */
    private SwingDifficultyCalculator difficultyParams = SwingDifficultyCalculator.DEFAULTS;

    public void setDifficultyParams(SwingDifficultyCalculator p) { this.difficultyParams = p; }

    private com.gearygolf.golf.glamour.SoundManager soundManager;

    public void setSoundManager(com.gearygolf.golf.glamour.SoundManager sm) { this.soundManager = sm; }
    public void setSwingModeNew(boolean v) { this.swingModeNew = v; }

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
        phase = ShotPhase.IDLE;
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

    public boolean update(float delta, Ball ball, Vector3 camDir, Club club, HUD hud, SwingUIController swingUI, Terrain terrain, GameInputProcessor input) {
        animationTimer += delta;
        if (cancelCooldown > 0) cancelCooldown -= delta;

        lastBallPos.set(ball.getPosition());
        ballIsStationary = (ball.getState() == Ball.State.STATIONARY);

        if (ballIsStationary) {
            if (isSpinLocked) {
                calculateShotVector(projectionVector, lockedCamDir, club, lockedSpin, terrain, 0f);
            } else if (phase == ShotPhase.WAITING || phase == ShotPhase.PENDING_SHOT) {
                // PENDING_SHOT: swing has resolved but shot hasn't fired yet — keep projection
                // locked on lockedCamDir so the guideline doesn't swing with the overhead camera.
                calculateShotVector(projectionVector, lockedCamDir, club, hud.getSpinOffset(), terrain, 0f);
            } else {
                calculateShotVector(projectionVector, camDir, club, hud.getSpinOffset(), terrain, 0f);
            }
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.PROJECTION) && guidelineAvailable) {
            toggleGuideline();
        }

        if (phase == ShotPhase.PENDING_SHOT) {
            shotLeadTimer -= delta;
            if (shotLeadTimer <= 0f) {
                phase = ShotPhase.IDLE;
                executeShot(ball, lockedCamDir, pendingClub, pendingPower, lockedSpin, terrain, pendingResult);
                return true;
            }
            return false;
        }

        if (phase == ShotPhase.WAITING) {
            if (hud.wasMinigameCanceled()) {
                reset();
                return false;
            }

            // In new swing mode, pressing HIT while waiting cancels the shot —
            // unless the overview panel is up, in which case the tap confirms the shot.
            if (swingModeNew && !swingUI.isShowingOverview()
                    && (input.isActionJustPressed(GameInputProcessor.Action.CHARGE_SHOT)
                        || input.isActionJustPressed(GameInputProcessor.Action.MAX_POWER_SHOT))) {
                reset();
                return false;
            }

            // TEST_SWING: tee fat driver — full power, LATE_FAT (no power penalty when teed), +2° attack (extra loft/less spin).
            // Injects into SwingOverlay so the overview panel shows; tap-to-confirm fires the shot via the normal path.
            if (swingModeNew && input.isActionJustPressed(GameInputProcessor.Action.TEST_SWING)) {
                SwingResult testSwing = new SwingResult(
                        0f,                                          // contactOffset: dead centre
                        0f,                                        // pathDeg: gentle draw
                        SWING_FULL_POWER_SPEED,                      // peakForwardSpeed: full power
                        SwingGestureAnalyser.TempoResult.LATE_FAT,  // tempoResult: fat — no penalty when teed (q>=0.3)
                        0.7f,                                       // tempoQuality: solid fat (miss<0.02, tee-fat threshold=0.3)
                        SwingGestureAnalyser.FollowThroughResult.HIGH,// followThroughResult: flip (tests spin effect)
                        2.0f,                                        // followThroughAngleDeg: outside threshold → (5×1 + 3×2)/30 = 0.37 ftSpin
                        1.0f,                                        // backswingNorm: full backswing
                        0f                                         // attackAngleDeg: ascending driver blow (+1.4° loft, -5% spin)
                );
                swingUI.injectTestResult(testSwing);
                return false;
            }

            // ── New swing gesture path ────────────────────────────────────────
            if (swingUI.isActive()) {
                SwingResult swingResult = swingUI.consumeResult();
                if (swingResult != null) {
                    // Power = forward speed fraction × backswing fraction — strictly linear.
                    // 10% backswing + 100% speed = 10% power; 50% backswing + 80% speed = 40% power.
                    float spd      = swingResult.peakForwardSpeed;
                    float spdFrac  = MathUtils.clamp(spd / SWING_FULL_POWER_SPEED, 0f, 1f);
                    float bkFrac   = MathUtils.clamp(swingResult.backswingNorm, 0f, 1f);
                    float fraction = spdFrac * bkFrac;
                    pendingPower = MathUtils.clamp(fraction * MAX_POWER, 0f, MAX_POWER);
                    Gdx.app.log("SwingDebug", String.format(
                            "[POWER] peakSpeed=%.3fsw/s(%.0f%%) bkNorm=%.2f → power=%.2f (%.0f%% max)",
                            spd, spdFrac * 100f, swingResult.backswingNorm,
                            pendingPower, pendingPower / MAX_POWER * 100f));
                    pendingResult = convertSwingResult(swingResult,
                            lastTerrainType,
                            shotStartClub == Club.PUTTER);
                    if (pendingResult == null) {
                        // Complete whiff — too far from expected impact time.
                        Gdx.app.log("SwingDebug", "[WHIFF] q≈0 — complete miss, resetting");
                        phase = ShotPhase.IDLE;
                        isSpinLocked = false;
                        cancelCooldown = CANCEL_COOLDOWN_TIME;
                        return false;
                    }
                    phase = ShotPhase.PENDING_SHOT;
                    isSpinLocked = false;
                    hud.incrementShots();
                    if (soundManager != null) soundManager.playBallStrike(clubSoundCategory(club), pendingPower / MAX_POWER);
                    pendingClub   = club;
                    shotLeadTimer = SHOT_SOUND_LEAD;
                }
                return false;
            }

            // ── Legacy spindicator path ───────────────────────────────────────
            if (hud.isMinigameComplete()) {
                phase = ShotPhase.PENDING_SHOT;
                isSpinLocked = false;
                // Play the swing sound now — the clip has ~50ms of swing before impact,
                // so ball.hit() fires after SHOT_SOUND_LEAD to keep them in sync.
                if (soundManager != null) soundManager.playBallStrike(clubSoundCategory(club), lockedPower / MAX_POWER);
                pendingResult = hud.getMinigameResult();
                pendingClub   = club;
                pendingPower  = lockedPower;
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

        if (phase == ShotPhase.POWER_LOCKED) {
            lockTimer += delta;
            if (lockTimer >= LOCK_DURATION) {
                float powerMod = 0.5f + (lockedPower / MAX_POWER);

                // lockedCamDir was already captured before the camera started lerping —
                // do NOT overwrite it here or we'd use the lerped swing-view direction.
                hud.logShotInitiated(ball.getPosition(), club, currentDifficulty, powerMod);
                phase = ShotPhase.WAITING;
                lockTimer = 0;
            }
            return false;
        }

        if (!swingModeNew) {
            // ── Classic mode: hold-to-charge, release-to-lock, then minigame ──
            boolean holdingShot = input.isActionPressed(GameInputProcessor.Action.CHARGE_SHOT)
                    || input.isActionPressed(GameInputProcessor.Action.MAX_POWER_SHOT);
            if (holdingShot) {
                if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0 && phase == ShotPhase.IDLE) {
                    phase = ShotPhase.CHARGING;
                    lockedCamDir.set(camDir);
                    spaceHoldTime = 0f;
                }
                if (phase == ShotPhase.CHARGING) {
                    float gain = input.isActionPressed(GameInputProcessor.Action.MAX_POWER_SHOT) ? MAX_POWER : delta * 2f;
                    spaceHoldTime = MathUtils.clamp(spaceHoldTime + gain, 0f, MAX_POWER);
                }
            } else if (phase == ShotPhase.CHARGING) {
                // Released — lock the charged power and wait briefly before starting minigame
                phase = ShotPhase.POWER_LOCKED;
                lockedPower = spaceHoldTime;
                spaceHoldTime = 0f;
            }
            return false;
        }

        // ── New swing mode: tap to start, tap again to cancel ──
        if (input.isActionJustPressed(GameInputProcessor.Action.CHARGE_SHOT)
                || input.isActionJustPressed(GameInputProcessor.Action.MAX_POWER_SHOT)) {
            if (phase == ShotPhase.WAITING) {
                reset(); // second tap cancels
            } else if (ball.getState() == Ball.State.STATIONARY && cancelCooldown <= 0) {
                lockedCamDir.set(camDir);
                lastTerrainType = terrain.getTerrainTypeAt(lastBallPos.x, lastBallPos.z);
                shotStartClub   = club;
                phase = ShotPhase.WAITING;
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

        // Tempo loft modifiers (thin/top reduce loft; fat/chunk add loft).
        // In new swing mode spin=(0,0) so baseline adjustedLoft = club.loft — no competing term.
        // Smooth approach to 80° cap — beyond 65° each additional raw degree contributes less,
        // asymptotically approaching 80°. Stops chunks going backwards without a hard cutoff feel.
        // Negative loft (tops) allowed down to -15° — Ball grace period handles floor collision.
        float rawLoft = club.loft * result.loftMult + result.loftDeltaDeg;
        float effectiveLoft;
        if (rawLoft > 65f) {
            effectiveLoft = 65f + (rawLoft - 65f) * 15f / (15f + (rawLoft - 65f));
        } else {
            effectiveLoft = Math.max(rawLoft, -15f);
        }
        if (Math.abs(effectiveLoft - club.loft) > 0.5f) {
            float hLen = (float) Math.sqrt(tempV1.x * tempV1.x + tempV1.z * tempV1.z);
            if (hLen > 0.001f) {
                float newAngleRad = effectiveLoft * MathUtils.degreesToRadians;
                float newHLen = MathUtils.cos(newAngleRad);
                tempV1.x = (tempV1.x / hLen) * newHLen;
                tempV1.z = (tempV1.z / hLen) * newHLen;
                tempV1.y = MathUtils.sin(newAngleRad);
                // sin²+cos²=1 → already unit length, no nor() needed
            }
        }

        // Shank: rotate shot azimuth. Sign convention: positive = LEFT (heel), negative = RIGHT (toe).
        // Done after loft calc so elevation is preserved; only azimuth changes.
        // Spin is zeroed below after backspin/sidespin are computed.
        if (result.shankAngleDeg != 0f) {
            tempV1.rotate(Vector3.Y, result.shankAngleDeg);
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

        getQuadraticSpinOffset(spin);
        // New swing mode sets attackAngleDeg on the result (negative = descending blow).
        // Old swing mode leaves it at 0 and derives attack from the spin dial instead.
        float attackAngle = result.attackAngleDeg != 0f
                ? result.attackAngleDeg          // new swing: e.g. -5° for descending
                : tempSpin.y * -20.0f;           // old swing: dial position → degrees
        // Map back to [0,1] scale used by efficiency curves (positive = descending).
        float attackQuadY = -attackAngle / 20.0f;
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
        if (attackQuadY <= 0f) {
            attackEfficiency = 0f; // ascending blow — no backspin bonus
        } else if (attackQuadY <= optimalAttack) {
            attackEfficiency = attackQuadY / optimalAttack; // linear rise to peak
        } else {
            attackEfficiency = 1f - 0.65f * (attackQuadY - optimalAttack) / (1f - optimalAttack);
        }
        attackEfficiency = MathUtils.clamp(attackEfficiency, 0f, 1f);

        float backspin = (power * finalPowerMult) * sForce * 9.0f * (1.0f + (attackQuadY * attackAngleCoeff * attackAngleDamping * attackEfficiency)) * quality * spinCurve * terrainSpinMult * club.spinMult;
        float sidespin = ((tempSpin.x * (power * finalPowerMult) * -10.0f * quality * spinCurve)
                        + (result.accuracy     * (power * finalPowerMult) * 8.0f * spinCurve)
                        + (result.ftSpinContrib * (power * finalPowerMult) * 5.0f * spinCurve))
                       * terrainSpinMult * club.spinMult;

        // Edge spin reversal: crosses zero at edgeFactor=0.5, fully reversed at edgeFactor=1.
        // At the rim, backspin becomes topspin (topping the ball).
        float spinReversalFactor = 1f - (2f * edgeFactor);
        backspin *= spinReversalFactor;
        sidespin *= spinReversalFactor;

        // Tempo spin modifier: thin/top reduce backspin (negative = topspin for a topped ball).
        // Sidespin uses abs() — path-induced curve reduces in magnitude but doesn't reverse direction.
        backspin *= result.tempoSpinMult;
        sidespin *= Math.abs(result.tempoSpinMult);

        // Shank: hosel has no grooves — zero all spin. Direction already rotated above.
        if (result.shankAngleDeg != 0f) { backspin = 0f; sidespin = 0f; }

        if (DEBUG_SHOT) Gdx.app.log("ShotDebug", String.format(
            "[%s] pwr=%.2f finalPwr=%.1f loft=%.1f→%.1f sForce=%.3f back=%.1f side=%.1f spinMult=%.2f",
            club.name, power, finalPowerMult, club.loft, effectiveLoft, sForce, backspin, sidespin, result.tempoSpinMult));

        ball.getSpin().set(projectionVector).scl(backspin);
        ball.getSpin().add(tempV2.set(Vector3.Y).scl(-sidespin));
    }

    private void calculateShotVector(Vector3 out, Vector3 camDir, Club club, Vector2 spin, Terrain terrain, float accuracy) {
        getQuadraticSpinOffset(spin);
        projectionVector.set(camDir.x, 0, camDir.z).nor();   // aimDir — reuse pre-allocated field
        tempV2.set(projectionVector).crs(Vector3.Y).nor();    // rightOfAim — reuse pre-allocated field

        Vector3 terrainNormal = terrain.getNormalAt(lastBallPos.x, lastBallPos.z);
        float physicalKick = terrainNormal.dot(tempV2) * 30.0f;

        out.set(projectionVector);
        out.rotate(Vector3.Y, (tempSpin.x * 2.5f) - (accuracy * 12.0f) - physicalKick);

        if (club == Club.PUTTER) {
            // Project the aim direction onto the terrain plane so putts follow the slope.
            // Removes the component that would cut into the hill; the result lies on the surface.
            float dot = out.dot(terrainNormal);
            out.sub(terrainNormal.x * dot, terrainNormal.y * dot, terrainNormal.z * dot);
            out.nor();
            return;
        }

        float deloftAbility = MathUtils.clamp(1.2f - (club.loft / 45f), 0.2f, 1.2f);
        float adjustedLoft = MathUtils.clamp(club.loft + (tempSpin.y * -65.0f * deloftAbility), 0.1f, 85f);

        float angleRad = adjustedLoft * MathUtils.degreesToRadians;
        out.y = MathUtils.sin(angleRad);
        float hLen = MathUtils.cos(angleRad);
        out.x *= hLen;
        out.z *= hLen;
        out.nor();
    }

    private void getQuadraticSpinOffset(Vector2 rawOffset) {
        float rawR = MathUtils.clamp(rawOffset.len(), 0f, 1f);
        float quadR = (float) Math.pow(rawR, 2.8f);
        if (rawR > 0) tempSpin.set(rawOffset).nor().scl(quadR);
        else tempSpin.set(0, 0);
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

        // In new swing-gesture mode, phase is WAITING but there is no power bar to show.
        if (phase == ShotPhase.WAITING && swingModeNew) return;

        float height = phase == ShotPhase.POWER_LOCKED ? lockedPower : (phase == ShotPhase.CHARGING ? spaceHoldTime : 0f);
        if (height <= 0 && phase != ShotPhase.CHARGING && phase != ShotPhase.POWER_LOCKED && phase != ShotPhase.WAITING) return;

        float dist = camPos.dst(ballPos);
        float currentUnitScale = MathUtils.clamp(dist * DISTANCE_SCALE_FACTOR * HUD.UI_SCALE, MIN_RENDER_SCALE, MAX_RENDER_SCALE);

        float vOffset = BASE_VERTICAL_GAP + (currentUnitScale * GAP_SCALE_MODIFIER);

        float bulge = 1.0f;
        if (phase == ShotPhase.POWER_LOCKED || phase == ShotPhase.WAITING) {
            bulge = 1.0f + (MathUtils.sin((lockTimer / LOCK_DURATION) * MathUtils.PI) * 0.4f);
            if (phase == ShotPhase.WAITING) bulge = 1.4f;
        } else if (phase == ShotPhase.CHARGING && spaceHoldTime >= MAX_POWER) {
            bulge = 1.2f + (MathUtils.sin(animationTimer * 18f) * 0.2f);
        }

        float displayH = phase == ShotPhase.WAITING ? lockedPower : height;

        if (phase == ShotPhase.CHARGING || phase == ShotPhase.POWER_LOCKED || phase == ShotPhase.WAITING) {
            maxPowerGhost.transform.setToTranslation(ballPos.x, ballPos.y + vOffset + (MAX_POWER * currentUnitScale / 2f), ballPos.z);
            maxPowerGhost.transform.set(maxPowerGhost.transform).scale(currentUnitScale * 1.1f, MAX_POWER * currentUnitScale, currentUnitScale * 1.1f);
            batch.render(maxPowerGhost, env);
        }

        if (displayH > 0) {
            Color color = displayH < (MAX_POWER / 2) ? Color.GREEN.cpy().lerp(Color.YELLOW, displayH / (MAX_POWER / 2)) : Color.YELLOW.cpy().lerp(Color.RED, (displayH - (MAX_POWER / 2)) / (MAX_POWER / 2));
            color.a = (phase == ShotPhase.POWER_LOCKED || phase == ShotPhase.WAITING) ? 0.85f : 1.0f;
            if (phase == ShotPhase.POWER_LOCKED || phase == ShotPhase.WAITING) color.lerp(Color.WHITE, 0.5f);

            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(color));
            ((BlendingAttribute) powerBarInstance.materials.get(0).get(BlendingAttribute.Type)).opacity = color.a;

            powerBarInstance.transform.setToTranslation(ballPos.x, ballPos.y + vOffset + (displayH * currentUnitScale / 2f), ballPos.z);
            powerBarInstance.transform.set(powerBarInstance.transform).scale(currentUnitScale * bulge, displayH * currentUnitScale, currentUnitScale * bulge);
            batch.render(powerBarInstance, env);
        }
    }

    public boolean isCharging() {
        return phase != ShotPhase.IDLE;
    }

    /**
     * Converts a SwingResult into a MinigameResult for the shot pipeline.
     * Returns null for a complete whiff (tempoQuality near zero) — caller must reset.
     *
     * Zone mapping (tempoQuality q, direction from EARLY_THIN / LATE_FAT):
     *
     *   THIN       (EARLY_THIN, q 1.0→0.5): power retained 100%, loft halved, spin halved.
     *   TOP        (EARLY_THIN, q 0.5→0.0): power collapses, loft → negative, topspin.
     *   FAT        (LATE_FAT,   q 1.0→0.5): power reduced, +20° loft, spin reduced.
     *              On tee: no penalty (ball is elevated — can't hit the ground first).
     *   CHUNK      (LATE_FAT,   q 0.5→0.0): power/spin collapse, loft approaches 80°.
     *              On tee: sky/pop-up only below q=0.3.
     *   MISS       (either, q < MISS_THRESHOLD): returns null — no shot fires.
     *   PUTTER     ignores fat/thin — any non-miss timing is treated as perfect.
     *              Shank impossible. Wide contact sweet spot (putterContactThreshold).
     *              Push/pull from path is preserved — that's putting's main mechanic.
     */
    private MinigameResult convertSwingResult(SwingResult s, Terrain.TerrainType terrain, boolean isPutter) {
        TerrainSwingModifier modifier = TerrainSwingModifier.forTerrain(terrain);
        if (s.tempoQuality < MISS_THRESHOLD) return null;

        // ── Contact factor ───────────────────────────────────────────────────
        // Putter has a wide flat face — penalty only kicks in beyond the threshold
        // (set by difficulty via setPutterContactThreshold). Other clubs penalise linearly.
        float absContact = Math.abs(s.contactOffset);
        float contactFactor;
        if (isPutter) {
            float excess = Math.max(0f, absContact - difficultyParams.putterContactThreshold);
            float range  = Math.max(0.01f, 1f - difficultyParams.putterContactThreshold);
            contactFactor = 1f - ((excess / range) * 0.35f); // max 35% penalty at full heel/toe
        } else {
            // Sweet spot: no quality penalty until contact exceeds the difficulty threshold.
            // Beyond it, penalty rises to max 60% at extreme heel/toe — steepness is constant;
            // only the zero-penalty zone widens at easier difficulties.
            float excess = Math.max(0f, absContact - difficultyParams.contactSweetSpot);
            float range  = Math.max(0.01f, 1f - difficultyParams.contactSweetSpot);
            contactFactor = 1f - ((excess / range) * 0.6f);
        }

        float compositeQuality = s.tempoQuality * contactFactor;

        MinigameResult.Rating rating;
        if      (compositeQuality >= 0.97f) rating = MinigameResult.Rating.PERFECTION;
        else if (compositeQuality >= 0.88f) rating = MinigameResult.Rating.SUPER;
        else if (compositeQuality >= 0.73f) rating = MinigameResult.Rating.GREAT;
        else if (compositeQuality >= 0.53f) rating = MinigameResult.Rating.GOOD;
        else if (compositeQuality >= 0.30f) rating = MinigameResult.Rating.POOR;
        else if (compositeQuality >= 0.15f) rating = MinigameResult.Rating.TERRIBLE;
        else                                rating = MinigameResult.Rating.ABYSMAL;

        // ── Shank: extreme heel or toe contact (irons/woods only) ───────────
        // Putters have a flat wide face — skip.
        // shankAngleDeg is signed: positive = LEFT (heel), negative = RIGHT (toe).
        // loftMult = 0.1f: a shanked ball skips out low, not lobbed up.
        if (!isPutter && s.contactOffset < -difficultyParams.shankThreshold) {
            // Heel shank — ball fires LEFT
            float t = (absContact - difficultyParams.shankThreshold) / (1f - difficultyParams.shankThreshold);
            MinigameResult shank = new MinigameResult();
            shank.rating        = MinigameResult.Rating.ABYSMAL;
            shank.shankAngleDeg = MathUtils.lerp(30f, 75f, t);   // positive = LEFT
            shank.powerMod      = MathUtils.lerp(0.70f, 0.50f, t);
            shank.loftMult      = 0.1f;
            shank.tempoSpinMult = 0.1f;
            shank.accuracy      = 0f;
            Gdx.app.log("SwingDebug", String.format("[SHANK-HEEL] contact=%.2f → %.0f° LEFT", s.contactOffset, shank.shankAngleDeg));
            return shank;
        }
        if (!isPutter && s.contactOffset > difficultyParams.shankThreshold) {
            // Toe shank — ball fires RIGHT
            float t = (s.contactOffset - difficultyParams.shankThreshold) / (1f - difficultyParams.shankThreshold);
            MinigameResult shank = new MinigameResult();
            shank.rating        = MinigameResult.Rating.ABYSMAL;
            shank.shankAngleDeg = -MathUtils.lerp(30f, 75f, t);  // negative = RIGHT
            shank.powerMod      = MathUtils.lerp(0.70f, 0.50f, t);
            shank.loftMult      = 0.1f;
            shank.tempoSpinMult = 0.1f;
            shank.accuracy      = 0f;
            Gdx.app.log("SwingDebug", String.format("[SHANK-TOE] contact=%.2f → %.0f° RIGHT", s.contactOffset, shank.shankAngleDeg));
            return shank;
        }

        MinigameResult r = new MinigameResult();
        r.rating = rating;

        // ── Accuracy: path + gear effect ─────────────────────────────────────
        // Non-linear path bracket: first B1 degrees scale at rate 1×; anything beyond
        // scales at 2× per degree (tax-bracket style — no discontinuity at the boundary).
        // B1 is difficulty-scaled (NOVICE=4°, TOUR_PRO=2°) so larger paths are needed to
        // shape a shot on easier settings, while pros see amplified feedback quickly.
        float absPath     = Math.abs(s.pathDeg);
        float b1          = difficultyParams.pathBreakpoint1;
        float effectivePath = Math.signum(s.pathDeg) *
                (Math.min(absPath, b1) + Math.max(0f, absPath - b1) * 2f);
        // pathScale still applies uniformly — Tour Pro gets full sensitivity everywhere.
        float scaledPathDeg = effectivePath * difficultyParams.pathScale;
        float pathAccuracy = (scaledPathDeg / 15f) * (1f - absContact * 0.5f);
        // Gear effect: toe opens the face (fade), heel closes it (draw).
        // Putter base (0.2) is smaller — short putts curve less. Both scaled by difficulty.
        float gearScale = (isPutter ? 0.2f : 0.5f) * difficultyParams.pathScale;
        r.accuracy = MathUtils.clamp(pathAccuracy + s.contactOffset * gearScale, -1f, 1f);

        // ── Follow-through spin contribution ──────────────────────────────────
        // Tax-bracket: inside ±threshold → 1 unit/degree; outside → 2 units/degree.
        // Positive angle (flip side) = hook bias; negative (extension) = fade bias.
        // Scale: effectiveDeg / 30 gives ±1.0 at extreme deviations, roughly half
        // the strength of path at the same angle (path uses /15).
        // Putter: scaled down by 0.2× — short putts are barely affected by follow-through.
        {
            float absFt       = Math.abs(s.followThroughAngleDeg);
            float ftThresh    = difficultyParams.ftAngleThreshold;
            float effectiveFt = Math.signum(s.followThroughAngleDeg) *
                    (Math.min(absFt, ftThresh) + Math.max(0f, absFt - ftThresh) * 2f);
            float putterScale = isPutter ? 0.2f : 1.0f;
            r.ftSpinContrib   = -MathUtils.clamp(effectiveFt / 22f * putterScale, -1f, 1f);
        }

        float q = s.tempoQuality;

        // ── Putter: tempo direction irrelevant, power from compositeQuality ──
        if (isPutter) {
            // Narrow powerMod range — putting distance is mainly from swing speed,
            // not from timing quality. Contact/path still determine direction.
            r.powerMod = MathUtils.lerp(0.85f, 1.05f, compositeQuality);
            // loftMult, loftDeltaDeg, tempoSpinMult all stay at defaults (1.0 / 0.0 / 1.0)
        } else if (s.tempoResult == SwingGestureAnalyser.TempoResult.EARLY_THIN) {
            if (!modifier.handleEarlyThin(s, r, q)) {
                if (q >= 0.5f) {
                    // ── Thin zone (q: 1.0→0.5) ──────────────────────────────────
                    float t = (1f - q) * 2f;
                    r.powerMod      = 1.0f;
                    r.loftMult      = MathUtils.lerp(1.0f,  0.5f,  t);
                    r.tempoSpinMult = MathUtils.lerp(1.0f,  0.5f,  t);
                } else {
                    // ── Top zone (q: 0.5→0.0) ───────────────────────────────────
                    float t = 1f - (q * 2f);
                    r.powerMod      = MathUtils.lerp(1.0f,  0.0f,  t);
                    r.loftMult      = MathUtils.lerp(0.5f, -0.3f,  t);
                    r.tempoSpinMult = MathUtils.lerp(0.5f, -1.0f,  t);
                }
            }
        } else if (s.tempoResult == SwingGestureAnalyser.TempoResult.LATE_FAT) {
            if (!modifier.handleLateFat(s, r, q)) {
                if (q >= 0.5f) {
                    // ── Fat zone (q: 1.0→0.5) ───────────────────────────────
                    float t = (1f - q) * 2f;
                    r.powerMod      = MathUtils.lerp(1.0f,  0.40f, t);
                    r.loftDeltaDeg  = MathUtils.lerp(0f,    20f,   t);
                    r.tempoSpinMult = MathUtils.lerp(1.0f,  0.40f, t);
                } else {
                    // ── Chunk zone (q: 0.5→0.0) ─────────────────────────────
                    float t = 1f - (q * 2f);
                    r.powerMod      = MathUtils.lerp(0.40f, 0.0f,  t);
                    r.loftDeltaDeg  = MathUtils.lerp(20f,   45f,   t);
                    r.tempoSpinMult = MathUtils.lerp(0.40f, 0.05f, t);
                }
                modifier.onAfterLateFat(s, r, q);
            }
        } else {
            // ── Perfect tempo ────────────────────────────────────────────────
            // powerMod ceiling is 1.0 — club powerMult already has the tier multiplier
            // (×1.50/×1.25/×1.10) baked in, so PERFECT tempo at max speed == old PERFECTION.
            r.powerMod = MathUtils.lerp(0.60f, 1.00f, compositeQuality);
            modifier.onPerfect(s, r, compositeQuality);
        }

        // ── Attack angle adjustments ─────────────────────────────────────────
        // Positive attack (ascending blow) adds effective loft and reduces backspin.
        // Negative attack (descending) delofts the shot and increases backspin.
        // 0.7° loft per degree of attack; ±2.5% spin per degree.
        if (!isPutter) {
            r.loftDeltaDeg   += s.attackAngleDeg * 0.7f;
            r.tempoSpinMult  *= (1.0f + (-s.attackAngleDeg * 0.025f));
            r.attackAngleDeg  = s.attackAngleDeg; // passed to executeShot for full spin calculation
        }

        // ── Contact power penalty ────────────────────────────────────────────
        // Smooth power loss from 0 at the sweet spot edge up to 40% at full heel/toe.
        // Stacks multiplicatively with tempo penalties. Putter skipped — its contact
        // penalty already flows through compositeQuality into powerMod above.
        if (!isPutter) {
            float excess = Math.max(0f, absContact - difficultyParams.contactSweetSpot);
            float range  = Math.max(0.01f, 1f - difficultyParams.contactSweetSpot);
            float contactPenalty = MathUtils.clamp(excess / range, 0f, 1f);
            r.powerMod *= 1f - contactPenalty * 0.4f;
        }

        Gdx.app.log("SwingDebug", String.format(
            "[CONVERT] tempo=%s q=%.2f contact=%.2f ft=%s(%.1f°) ftSpin=%.3f putter=%b atk=%.1f° → %s pwr=%.2f loftMult=%.2f loftDelta=%.1f spinMult=%.2f",
            s.tempoResult, q, s.contactOffset, s.followThroughResult, s.followThroughAngleDeg,
            r.ftSpinContrib, isPutter, s.attackAngleDeg,
            rating, r.powerMod, r.loftMult, r.loftDeltaDeg, r.tempoSpinMult));

        return r;
    }

    public void dispose() {
        if (powerBarModel != null) powerBarModel.dispose();
        if (projectionLineModel != null) projectionLineModel.dispose();
        if (targetDotModel != null) targetDotModel.dispose();
    }
}