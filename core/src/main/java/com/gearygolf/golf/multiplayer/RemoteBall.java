package com.gearygolf.golf.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import com.gearygolf.golf.ball.Ball;
import com.gearygolf.golf.ball.BallPhysics;
import com.gearygolf.golf.glamour.ParticleManager;
import com.gearygolf.golf.terrain.Terrain;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * Simulates and displays another player's ball.
 *
 * Each RemoteBall owns its own pending-packet queue.  GolfGame calls
 * {@link #offerPacket(ShotPacket)} whenever a new shot arrives from RTDB.
 * If the ball is stationary the replay starts immediately; otherwise the
 * packet is queued and plays automatically when the current flight ends.
 *
 * The ball is always visible:
 *   - Stationary  → shown at {@link #getLastRestPosition()}.
 *   - In flight   → shown at {@link #getPosition()} with a trail.
 *
 * Rendering (ModelInstances) is owned by GolfGame so RemoteBall stays
 * decoupled from GL resources.
 */
public class RemoteBall {

    // Must match Ball constants exactly for deterministic parity.
    private static final float GRAVITY            = -9.81f;
    private static final float AIR_DRAG_COEFF     = 0.0048f;
    private static final float LIFT_COEFF         = 5.0f;
    private static final float SIDE_COEFF         = 5.0f;
    private static final float STOP_SPEED         = 0.15f;
    /** Mirrors HazardManager.RESET_DELAY — 1 s in water triggers a reset. */
    private static final float RESET_DELAY        = 1.0f;
    private static final float MIN_BOUNCE_VY      = 0.5f;
    private static final float BOUNCE_RESTITUTION = 0.35f;
    private static final float MAX_STEP_UP                  = 1.0f;
    private static final float BALL_RADIUS                  = Ball.BALL_RADIUS;
    private static final float FOLIAGE_DRAG_LIN             = 0.04f;
    private static final float FOLIAGE_DRAG_SQU             = 0.04f;
    private static final float DEFLECTION_CHANCE_PER_METER  = 0.6f;
    private static final float DEFLECTION_MAGNITUDE         = 10f;
    /** Fixed physics step — matches Ball's effective sub-step at 60 fps (16.67ms / 4). */
    private static final float FIXED_STEP         = 1f / 240f;
    /** Max accumulated time per frame — prevents spiral of death on very laggy frames. */
    private static final float MAX_ACCUMULATOR    = FIXED_STEP * 60; // ~250ms

    // Trail
    public  static final int   TRAIL_LENGTH       = 16;
    private static final int   TRAIL_RECORD_EVERY = 2; // sub-steps between trail samples

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------
    public final String uid;
    public final String displayName;

    // -------------------------------------------------------------------------
    // Physics state
    // -------------------------------------------------------------------------
    private final Vector3  position        = new Vector3();
    private final Vector3  velocity        = new Vector3();
    private final Vector3  spin            = new Vector3();
    private final Vector3  tempV1          = new Vector3();
    private final Vector3  vTangent        = new Vector3();
    private Ball.State         state           = Ball.State.STATIONARY;
    private Terrain.TerrainType lastRestType    = Terrain.TerrainType.GREEN;
    private float          hitCooldown     = 0f;
    private boolean        isInitialFlight = false;
    private float          waterTimer      = 0f;
    private int            subStepCounter  = 0;
    private float          accumulator     = 0f;
    private boolean        detLandLogged   = false;
    private boolean        bounceEventPending = false;
    private Terrain.TerrainType lastBounceSurface = Terrain.TerrainType.GREEN;

    // -------------------------------------------------------------------------
    // Visual state
    // -------------------------------------------------------------------------
    /** Position shown when the ball is stationary between shots. */
    private final Vector3  lastRestPosition = new Vector3();
    private boolean        hasRestPosition  = false;

    // Authoritative rest correction — lerp lastRestPosition toward the server's position.
    private static final float LERP_DURATION  = 2.0f;
    private final Vector3  lerpTarget    = new Vector3();
    private float          lerpTimer     = 0f;
    private boolean        isLerping     = false;
    private boolean        lerpIsHoleOut = false;
    /** True once a holed-out lerp has completed — ball should no longer be rendered. */
    private boolean        isHoledOut    = false;
    /** Rest packet received while in-flight; applied once ball comes to rest. */
    private RoomService.RestPacket pendingRest = null;

    /** Ring buffer of recent in-flight positions for the trail. */
    private final Vector3[] trail      = new Vector3[TRAIL_LENGTH];
    private int              trailHead = 0;
    private int              trailSize = 0;

    // -------------------------------------------------------------------------
    // Queue
    // -------------------------------------------------------------------------
    private final Queue<ShotPacket> pending = new LinkedList<>();
    private int lastReplayedStroke = 0;

    // -------------------------------------------------------------------------
    // Seeded RNG — mirrors Ball.physicsRandom for deterministic foliage deflection
    // -------------------------------------------------------------------------
    private long   levelSeed     = 0L;
    private Random physicsRandom = new Random(0L);

    // -------------------------------------------------------------------------
    // Optional determinism recorder — used by the determ self-test
    // -------------------------------------------------------------------------
    private DeterminismRecorder recorder = null;

    public void setRecorder(DeterminismRecorder r)  { recorder = r; }
    public DeterminismRecorder.Recording detachRecorder() {
        DeterminismRecorder r = recorder;
        recorder = null;
        return r != null ? r.finish() : new DeterminismRecorder.Recording();
    }

    /** Must be called whenever the level changes so foliage RNG matches the local ball. */
    public void setLevelSeed(long seed) {
        this.levelSeed = seed;
    }

    // -------------------------------------------------------------------------
    // Optional particles
    // -------------------------------------------------------------------------
    private ParticleManager particleManager;

    // -------------------------------------------------------------------------

    public RemoteBall(String uid, String displayName, ParticleManager particleManager) {
        this.uid             = uid;
        this.displayName     = displayName;
        this.particleManager = particleManager;
        for (int i = 0; i < TRAIL_LENGTH; i++) trail[i] = new Vector3();
    }

    // -------------------------------------------------------------------------
    // Packet intake
    // -------------------------------------------------------------------------

    /**
     * Called by GolfGame when a new shot packet arrives for this player.
     * Starts replay immediately if stationary, otherwise queues for later.
     */
    public void offerPacket(ShotPacket packet) {
        // Discard duplicate deliveries of a stroke we are already replaying or have queued.
        if (packet.strokeNum > 0 && packet.strokeNum <= lastReplayedStroke) {
            Gdx.app.log("ShotDet", "DISCARD duplicate n=" + packet.strokeNum
                    + " (lastReplayed=" + lastReplayedStroke + ")");
            return;
        }
        for (ShotPacket queued : pending) {
            if (queued.strokeNum == packet.strokeNum) {
                Gdx.app.log("ShotDet", "DISCARD already-queued n=" + packet.strokeNum);
                return;
            }
        }
        if (state == Ball.State.STATIONARY) {
            startReplay(packet);
        } else {
            pending.add(packet);
        }
    }

    /**
     * Offer an authoritative rest position for the given stroke.
     * If stationary (and stroke matches), start lerping toward the correct position.
     * If still in flight, store it; it will be applied when the ball comes to rest.
     */
    public void offerRestPacket(RoomService.RestPacket packet) {
        // Once holed out or already lerping into hole, don't let polls interrupt.
        if (isHoledOut) return;
        if (isLerping && lerpIsHoleOut) return;
        // Discard rest packets that belong to a different stroke than the one being replayed.
        if (packet.strokeNum != 0 && packet.strokeNum != lastReplayedStroke) return;
        if (state == Ball.State.STATIONARY) {
            startRestLerp(packet);
        } else {
            // Only keep the latest rest packet
            pendingRest = packet;
        }
    }

    private void startRestLerp(RoomService.RestPacket packet) {
        // Sink holed-out balls 0.7m underground so they disappear into the cup.
        float yTarget = packet.holed ? packet.y - 0.7f : packet.y;
        lerpTarget.set(packet.x, yTarget, packet.z);
        lerpTimer     = 0f;
        isLerping     = true;
        lerpIsHoleOut = packet.holed;
    }

    private void startReplay(ShotPacket p) {
        // Cancel any active rest correction — new shot has authoritative start position.
        isLerping          = false;
        lerpIsHoleOut      = false;
        isHoledOut         = false;
        pendingRest        = null;
        lastReplayedStroke = p.strokeNum;
        // Show ball at tee/last position immediately, even before it lands.
        if (!hasRestPosition) {
            lastRestPosition.set(p.sx, p.sy, p.sz);
            hasRestPosition = true;
        }
        position.set(p.sx, p.sy, p.sz);
        velocity.set(p.vx, p.vy, p.vz);
        spin.set(p.wx, p.wy, p.wz);
        Gdx.app.log("DET", String.format("R PACKET n=%d p=%08x,%08x,%08x v=%08x,%08x,%08x s=%08x,%08x,%08x",
                p.strokeNum,
                Float.floatToRawIntBits(position.x), Float.floatToRawIntBits(position.y), Float.floatToRawIntBits(position.z),
                Float.floatToRawIntBits(velocity.x), Float.floatToRawIntBits(velocity.y), Float.floatToRawIntBits(velocity.z),
                Float.floatToRawIntBits(spin.x), Float.floatToRawIntBits(spin.y), Float.floatToRawIntBits(spin.z)));
        state           = Ball.State.AIR;
        isInitialFlight = true;
        hitCooldown     = 0.1f;
        waterTimer      = 0f;
        subStepCounter  = 0;
        accumulator    = 0f;
        detLandLogged   = false;
        physicsRandom   = new Random(levelSeed);
        clearTrail();
        Gdx.app.log("DET", String.format("R SHOT_START p=%08x,%08x,%08x v=%08x,%08x,%08x",
                Float.floatToRawIntBits(position.x), Float.floatToRawIntBits(position.y), Float.floatToRawIntBits(position.z),
                Float.floatToRawIntBits(velocity.x), Float.floatToRawIntBits(velocity.y), Float.floatToRawIntBits(velocity.z)));
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(float delta, Terrain terrain, Vector3 wind) {
        if (isLerping) {
            lerpTimer += delta;
            float t = Math.min(lerpTimer / LERP_DURATION, 1f);
            lastRestPosition.lerp(lerpTarget, t);
            if (t >= 1f) {
                isLerping = false;
                if (lerpIsHoleOut) isHoledOut = true;
            }
        }

        if (state == Ball.State.STATIONARY) return;
        if (hitCooldown > 0) hitCooldown -= delta;

        accumulator = Math.min(accumulator + delta, MAX_ACCUMULATOR);
        while (accumulator >= FIXED_STEP && state != Ball.State.STATIONARY) {
            processStep(FIXED_STEP, terrain, wind);
            accumulator -= FIXED_STEP;
            subStepCounter++;
            if (subStepCounter % TRAIL_RECORD_EVERY == 0) recordTrailPoint();
        }

        // Mirror HazardManager: if the ball stays in water for RESET_DELAY seconds,
        // snap back to lastRestPosition and go stationary so the pending queue drains.
        // Use the same zone as applyWaterPhysics (< waterLevel + BALL_RADIUS) so a ball
        // oscillating at the surface still accumulates toward the reset threshold.
        if (state != Ball.State.STATIONARY && position.y < terrain.getWaterLevel() + BALL_RADIUS) {
            waterTimer += delta;
            if (waterTimer >= RESET_DELAY) {
                waterTimer = 0f;
                velocity.setZero();
                spin.setZero();
                if (hasRestPosition) position.set(lastRestPosition);
                state = Ball.State.STATIONARY;
            }
        } else {
            waterTimer = 0f;
        }

        if (state == Ball.State.STATIONARY) {
            accumulator = 0f;
            onBallRested();
        }
    }

    private void onBallRested() {
        lastRestPosition.set(position);
        hasRestPosition = true;
        Gdx.app.log("DET", String.format("R REST   s=%04d %-5s p=%08x,%08x,%08x (%.3f,%.3f,%.3f)",
                subStepCounter, lastRestType.name(),
                Float.floatToRawIntBits(position.x), Float.floatToRawIntBits(position.y), Float.floatToRawIntBits(position.z),
                position.x, position.y, position.z));
        if (pendingRest != null) {
            if (pendingRest.strokeNum == 0 || pendingRest.strokeNum == lastReplayedStroke) {
                startRestLerp(pendingRest);
            }
            pendingRest = null;
        }
        if (!pending.isEmpty()) {
            startReplay(pending.poll());
        }
    }

    // -------------------------------------------------------------------------
    // Physics (mirrors Ball, minus particles/renderer/audio)
    // -------------------------------------------------------------------------

    private void processStep(float delta, Terrain terrain, Vector3 wind) {
        float preStepY = position.y;
        if (BallPhysics.checkWaterTransition(position, velocity, terrain.getWaterLevel())) {
            // just entered water — viscous physics takes over below
        }

        float waterLevel = terrain.getWaterLevel();
        if (position.y < waterLevel + BALL_RADIUS) {
            BallPhysics.applyWaterPhysics(position, velocity, spin, waterLevel, delta);
            // No stationary check exists in the water branch — add one so a ball
            // that genuinely stops underwater doesn't block the pending queue for a
            // full second waiting for the water timer.
            if (velocity.len() < STOP_SPEED) {
                velocity.setZero();
                spin.setZero();
                state = Ball.State.STATIONARY;
            }
        } else {
            float terrainHeight = Ball.q(terrain.getHeightAt(position.x, position.z) + BALL_RADIUS / 3f);
            if ((state == Ball.State.ROLLING && position.y <= terrainHeight + 0.05f)
                    || position.y < terrainHeight) {
                handleTerrainContact(delta, terrain, terrainHeight);
                position.add(tempV1.set(velocity).scl(delta));
            } else {
                state = Ball.State.AIR;
                Vector3 windAtH = BallPhysics.getWindAtHeight(wind, position.y);
                velocity.add(tempV1.setZero()
                        .add(BallPhysics.getGravityForce(GRAVITY))
                        .add(BallPhysics.getAirDrag(velocity, windAtH, AIR_DRAG_COEFF))
                        .add(BallPhysics.getMagnusForce(velocity, windAtH, spin, LIFT_COEFF, SIDE_COEFF))
                        .scl(delta));
                if (!handleAirCollisions(terrain)) applyAirMovement(delta, terrain);
                if (Ball.q(velocity.len()) > 0.1f) {
                    spin.scl(Math.max(0f, Ball.q(1f - (0.05f + velocity.len2() * 0.0002f) * delta)));
                }
            }
        }

        handleTreeTrunkCollisions(terrain, delta);
        handleMonolithCollisions(terrain);
        handleFlagPoleCollision(terrain);

        float stepYDelta = position.y - preStepY;
        if (Math.abs(stepYDelta) > 2.0f && hitCooldown <= 0f) {
            position.y = preStepY + MathUtils.clamp(stepYDelta, -1f, 1f);
        }

        quantize();

        // Periodic state snapshot every 24 sub-steps (≈100 ms at 240 Hz).
        // Compare with "DET L S..." lines from Ball on the shooting device.
        if (subStepCounter % 24 == 0) {
            Gdx.app.log("DET", String.format("R S%04d %-7s p=%08x,%08x,%08x v=%08x,%08x,%08x",
                    subStepCounter, state.name(),
                    Float.floatToRawIntBits(position.x), Float.floatToRawIntBits(position.y), Float.floatToRawIntBits(position.z),
                    Float.floatToRawIntBits(velocity.x), Float.floatToRawIntBits(velocity.y), Float.floatToRawIntBits(velocity.z)));
        }
    }

    private boolean handleAirCollisions(Terrain terrain) {
        tempV1.set(velocity).scl(0.016f);
        for (float t = 0.33f; t <= 1.0f; t += 0.33f) {
            float cx = position.x + tempV1.x * t;
            float cz = position.z + tempV1.z * t;
            float cy = position.y + tempV1.y * t;
            float h  = Ball.q(terrain.getHeightAt(cx, cz) + BALL_RADIUS / 3f);
            if (cy < h) {
                Vector3 n = terrain.getNormalAt(cx, cz);
                if (BallPhysics.isWallCollision(n, 45f) || (h - cy) > MAX_STEP_UP) {
                    if (hitCooldown <= 0f) {
                        // Mirror Ball.java fix: use full normal so steep walls give upward impulse
                        Vector3 bn = new Vector3(Ball.q(n.x), Ball.q(n.y), Ball.q(n.z));
                        velocity.set(BallPhysics.calculateBounceWithSpin(
                                velocity, bn, spin, BOUNCE_RESTITUTION, 0.4f, 0f));
                        Vector3 wn = new Vector3(n.x, 0, n.z).nor();
                        wn.set(Ball.q(wn.x), 0f, Ball.q(wn.z));
                        if (wn.len() > 0.05f) position.add(tempV1.set(wn).scl(0.15f));
                        hitCooldown = 0.05f;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void applyAirMovement(float delta, Terrain terrain) {
        float nextH = Ball.q(terrain.getHeightAt(
                position.x + velocity.x * delta,
                position.z + velocity.z * delta) + BALL_RADIUS / 3f);
        if (position.y + velocity.y * delta < nextH && hitCooldown <= 0f) {
            handleTerrainContact(delta, terrain, nextH);
            position.add(tempV1.set(velocity).scl(delta));
        } else {
            position.add(velocity.x * delta, velocity.y * delta, velocity.z * delta);
        }
    }

    private void handleTerrainContact(float delta, Terrain terrain, float terrainHeight) {
        Vector3 normal = terrain.getNormalAt(position.x, position.z); // already normalised and quantised
        Terrain.TerrainType type = terrain.getTerrainTypeAt(position.x, position.z);
        lastRestType = type;

        if (terrainHeight - position.y > MAX_STEP_UP) {
            tempV1.set(normal.x, 0, normal.z).nor();
            tempV1.set(Ball.q(tempV1.x), 0f, Ball.q(tempV1.z));
            if (tempV1.len() < 0.1f) {
                position.y = terrainHeight;
            } else {
                position.add(tempV1.scl(0.4f));
                velocity.set(BallPhysics.calculateBounceWithSpin(
                        velocity, tempV1, spin, 0.3f, type.kineticFriction, type.softness));
            }
            return;
        }

        if (state == Ball.State.ROLLING && velocity.dot(normal) > 1.5f) { state = Ball.State.AIR; return; }
        if (position.y < terrainHeight && !BallPhysics.isWallCollision(normal, 45f)) {
            position.y = terrainHeight;
        }

        if (state == Ball.State.AIR && velocity.dot(normal) < -MIN_BOUNCE_VY) {
            if (!detLandLogged) {
                detLandLogged = true;
                Gdx.app.log("DET", String.format("R LAND  s=%04d %-5s p=%08x,%08x,%08x v=%08x,%08x,%08x",
                        subStepCounter, type.name(),
                        Float.floatToRawIntBits(position.x), Float.floatToRawIntBits(position.y), Float.floatToRawIntBits(position.z),
                        Float.floatToRawIntBits(velocity.x), Float.floatToRawIntBits(velocity.y), Float.floatToRawIntBits(velocity.z)));
            }
            if (isInitialFlight && particleManager != null) {
                particleManager.spawnRatingBurst(position, new Color(0.9f, 0.3f, 0.3f, 1f), 3, 0.8f);
            }
            isInitialFlight = false;
            velocity.set(BallPhysics.calculateBounceWithSpin(
                    velocity, normal, spin, type.restitution, type.kineticFriction, type.softness));
            state = Ball.State.CONTACT;
            bounceEventPending = true;
            lastBounceSurface  = type;
        } else {
            state = Ball.State.ROLLING;
            vTangent.set(velocity).sub(tempV1.set(normal).scl(velocity.dot(normal)));
            BallPhysics.resolveRollingSpin(vTangent, spin, normal, type, delta);
            vTangent.add(BallPhysics.getSlopeForce(BallPhysics.getGravityForce(GRAVITY), normal).scl(delta))
                    .add(BallPhysics.getRollingFriction(vTangent, normal, type, GRAVITY).scl(delta));
            velocity.set(vTangent);
        }

        float slopeMag = Ball.q(BallPhysics.getSlopeForce(
                BallPhysics.getGravityForce(GRAVITY), normal).len());
        if (Ball.q(velocity.len()) < STOP_SPEED
                && slopeMag < type.kineticFriction * Math.abs(normal.y * GRAVITY) * type.staticMultiplier) {
            velocity.setZero();
            spin.setZero();
            state = Ball.State.STATIONARY;
        }
    }

    private void quantize() {
        position.set(Ball.q(position.x), Ball.q(position.y), Ball.q(position.z));
        velocity.set(Ball.q(velocity.x), Ball.q(velocity.y), Ball.q(velocity.z));
        spin.set(Ball.q(spin.x),    Ball.q(spin.y),    Ball.q(spin.z));
        if (recorder != null) {
            recorder.onStep(subStepCounter,
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                spin.x, spin.y, spin.z);
        }
    }

    // -------------------------------------------------------------------------
    // Missing-from-original collision handlers — mirrors Ball's equivalents
    // -------------------------------------------------------------------------

    private void handleTreeTrunkCollisions(Terrain terrain, float delta) {
        if (hitCooldown > 0) return;
        for (com.gearygolf.golf.terrain.objects.Tree tree : terrain.getTreesAt(position.x, position.z)) {
            if (tree.checkTrunkCollision(position, BALL_RADIUS)) {
                if (BallPhysics.handleTrunkCollision(position, velocity, spin,
                        position.x - tree.getPosition().x, position.z - tree.getPosition().z)) {
                    hitCooldown = 0.1f;
                }
            } else if (tree.isInsideFoliage(position, BALL_RADIUS)) {
                BallPhysics.applyFoliagePhysics(velocity, spin, delta,
                        FOLIAGE_DRAG_LIN, FOLIAGE_DRAG_SQU,
                        DEFLECTION_CHANCE_PER_METER, DEFLECTION_MAGNITUDE, physicsRandom);
            }
        }
    }

    private void handleMonolithCollisions(Terrain terrain) {
        if (hitCooldown > 0) return;
        for (com.gearygolf.golf.terrain.objects.Monolith m : terrain.getMonolithsAt(position.x, position.z)) {
            if (BallPhysics.handleMonolithCollision(position, velocity, spin,
                    m.getPosition(), m.getWidth(), m.getHeight(), m.getDepth(), m.getRotationY())) {
                hitCooldown = 0.05f;
                break;
            }
        }
    }

    private void handleFlagPoleCollision(Terrain terrain) {
        if (hitCooldown > 0) return;
        Vector3 holePos = terrain.getHolePosition();
        float dx = position.x - holePos.x;
        float dz = position.z - holePos.z;
        if (dx * dx + dz * dz > 9f) return;
        Vector3 poleBase = terrain.getFlagPoleBase(position);
        if (BallPhysics.handleFlagPoleCollision(position, velocity, spin, poleBase, 0.1f, 5f)) {
            hitCooldown = 0.05f;
        }
    }

    // -------------------------------------------------------------------------
    // Trail
    // -------------------------------------------------------------------------

    private void recordTrailPoint() {
        trail[trailHead].set(position);
        trailHead = (trailHead + 1) % TRAIL_LENGTH;
        if (trailSize < TRAIL_LENGTH) trailSize++;
    }

    private void clearTrail() {
        trailHead = 0;
        trailSize = 0;
    }

    /** Iterate trail from oldest to newest. Index 0 = oldest, (size-1) = most recent. */
    public int     getTrailSize()              { return trailSize; }
    public Vector3 getTrailPoint(int i) {
        // Oldest entry is at (trailHead - trailSize + i + TRAIL_LENGTH) % TRAIL_LENGTH
        return trail[((trailHead - trailSize + i) % TRAIL_LENGTH + TRAIL_LENGTH) % TRAIL_LENGTH];
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Vector3              getPosition()          { return position; }
    public Vector3              getVelocity()          { return velocity; }
    public Vector3              getLastRestPosition()  { return lastRestPosition; }
    public boolean              hasRestPosition()      { return hasRestPosition; }
    public boolean              isHoledOut()           { return isHoledOut; }
    public Ball.State           getState()             { return state; }
    public boolean              isInFlight()           { return state != Ball.State.STATIONARY; }
    /** Returns true once per bounce event and resets the flag. */
    public boolean              consumeBounceEvent()   { boolean e = bounceEventPending; bounceEventPending = false; return e; }
    public Terrain.TerrainType  getLastBounceSurface() { return lastBounceSurface; }
}
