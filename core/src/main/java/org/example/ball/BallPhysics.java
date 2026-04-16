package org.example.ball;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

import java.util.Random;

import static org.example.ball.Ball.BALL_RADIUS;

public class BallPhysics {

    private static final Vector3 temp = new Vector3();
    private static final Vector3 temp2 = new Vector3();
    private static final Vector3 relativeVelocity = new Vector3();
    private static final Vector3 vTangent = new Vector3();
    private static final Vector3 vNormalBounce = new Vector3();

    private static final Vector3 outGravity = new Vector3();
    private static final Vector3 outWind = new Vector3();
    private static final Vector3 outDrag = new Vector3();
    private static final Vector3 outMagnus = new Vector3();
    private static final Vector3 outFriction = new Vector3();
    private static final Vector3 outSlope = new Vector3();
    private static final Vector3 outBounce = new Vector3();

    private static final float VERTICAL_STOP_THRESHOLD = 0.20f;
    private static final float SOFTNESS_ABSORPTION_FACTOR = 0.2f;
    private static final float MIN_RESTITUTION = 0.15f;
    private static final float SPIN_TRANSFER_RATIO = 0.35f;
    private static final float FRICTION_SOFTNESS_BOOST = 0.5f;
    private static final float NORMAL_GRIP_SCALER = 0.012f;
    private static final float NORMAL_GRIP_MIN = 0.5f;
    private static final float NORMAL_GRIP_MAX = 2.5f;
    private static final float MAX_ENERGY_RETAINED = 0.99f;
    private static final float MIN_ENERGY_RETAINED = 0.85f;

    public static Vector3 getGravityForce(float gravity) {
        return outGravity.set(0, gravity, 0);
    }

    public static Vector3 getWindAtHeight(Vector3 baseWind, float height) {
        float heightFactor = MathUtils.clamp(height / 20f, 0.2f, 1.0f);
        return outWind.set(baseWind).scl(heightFactor);
    }

    public static Vector3 getAirDrag(Vector3 velocity, Vector3 wind, float dragCoeff) {
        relativeVelocity.set(velocity).sub(wind);
        float speed = relativeVelocity.len();
        if (speed < 0.01f) return outDrag.setZero();
        return outDrag.set(relativeVelocity).nor().scl(-dragCoeff * speed * speed);
    }

    public static Vector3 getMagnusForce(Vector3 velocity, Vector3 wind, Vector3 spin, float liftCoeff, float sideCoeff) {
        relativeVelocity.set(velocity).sub(wind);
        float airspeed = relativeVelocity.len();
        if (airspeed < 2.5f || spin.len() < 0.1f) return outMagnus.setZero();

        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / airspeed;
        float cappedSpinRatio = Math.min(spinRatio, 3.0f);
        float CL = 1.0f - (float) Math.exp(-0.9f * cappedSpinRatio);
        float airFoilEfficiency = MathUtils.clamp(airspeed / 15.0f, 0.0f, 1.0f);
        float speedRef = airspeed / 20.0f;
        float dynamicLift = (speedRef * speedRef) * CL * (airFoilEfficiency * airFoilEfficiency);

        temp.set(relativeVelocity).crs(spin).nor();
        return outMagnus.set(temp).scl(-dynamicLift * (liftCoeff + sideCoeff));
    }

    public static boolean checkWaterTransition(Vector3 position, Vector3 velocity, float waterLevel) {
        float previousY = position.y - (velocity.y * 0.016f);
        return previousY >= waterLevel && position.y < waterLevel;
    }

    public static void applyWaterPhysics(Vector3 position, Vector3 velocity, Vector3 spin, float waterLevel, float delta) {
        float depth = waterLevel - position.y;
        float immersion = MathUtils.clamp((depth + BALL_RADIUS) / (BALL_RADIUS * 2f), 0f, 1f);
        float previousY = position.y - (velocity.y * delta);

        if (previousY >= waterLevel && position.y < waterLevel && velocity.y < 0) {
            float speed = velocity.len();
            if (speed > 5.0f) {
                float angleRad = (float) Math.asin(Math.abs(velocity.y) / speed);
                float efficiency = MathUtils.cos(angleRad * 3.0f);
                efficiency = MathUtils.clamp(efficiency, 0, 1) * efficiency;
                velocity.y += (speed * speed) * 0.005f * efficiency;
                velocity.scl(0.85f);
                if (velocity.y > 0) return;
            }
        }

        if (immersion <= 0) return;

        float speed = velocity.len();
        float surfaceSpeed = spin.len() * 0.02f;
        float CL = 1.0f - (float) Math.exp(-0.9f * (surfaceSpeed / (speed + 0.1f)));
        float dynamicLift = ((speed / 20.0f) * (speed / 20.0f)) * CL;

        temp.set(velocity).crs(Vector3.Y).nor();
        temp2.set(velocity).crs(temp).nor().scl(spin.dot(temp) * dynamicLift * 0.8f * immersion);
        velocity.add(temp2.scl(delta));
        velocity.y += 6.5f * immersion * delta;
        velocity.scl(MathUtils.clamp(1.0f - (2.4f * immersion * (1.0f + speed * 0.12f) * delta), 0.05f, 1.0f));
        spin.scl(MathUtils.clamp(1.0f - (12.0f * immersion * delta), 0f, 1.0f));
    }

    public static boolean handleTrunkCollision(Vector3 pos, Vector3 vel, Vector3 spin, float dx, float dz) {
        temp.set(dx, 0, dz).nor();
        float dot = vel.dot(temp);
        if (dot < 0) {
            vel.mulAdd(temp, -dot * 1.6f).scl(0.85f);
            spin.scl(0.3f);
            pos.add(temp.scl(0.1f));
            return true;
        }
        return false;
    }

    public static Vector3 getRollingFriction(Vector3 velocity, Vector3 normal, Terrain.TerrainType type, float gravity) {
        float speed = velocity.len();
        if (speed < 0.01f) return outFriction.setZero();
        float frictionMag = (type.kineticFriction * Math.abs(normal.y * gravity)) + (type.rollingResistance * speed);
        return outFriction.set(velocity).nor().scl(-frictionMag);
    }

    public static Vector3 getSlopeForce(Vector3 gravityForce, Vector3 normal) {
        return outSlope.set(gravityForce).sub(temp.set(normal).scl(gravityForce.dot(normal)));
    }

    public static Vector3 calculateBounceWithSpin(Vector3 velocity, Vector3 normal, Vector3 spin, float restitution, float friction, float softness) {
        float vDotN = velocity.dot(normal);
        float absVDotN = Math.abs(vDotN);
        temp.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(temp);

        resolveVerticalBounce(vNormalBounce, temp, absVDotN, restitution, softness);
        if (vTangent.len() > 0.05f || spin.len() > 0.05f) {
            resolveTangentialBounce(vTangent, spin, normal, absVDotN, friction, softness);
        }
        return outBounce.set(vTangent).add(vNormalBounce);
    }

    private static void resolveVerticalBounce(Vector3 outNormal, Vector3 normalVector, float absVDotN, float restitution, float softness) {
        float finalRestitution = restitution * Math.max(MIN_RESTITUTION, 1.0f - (softness * SOFTNESS_ABSORPTION_FACTOR));
        if (absVDotN * finalRestitution < VERTICAL_STOP_THRESHOLD) outNormal.setZero();
        else outNormal.set(normalVector).scl(-finalRestitution);
    }

    private static void resolveTangentialBounce(Vector3 tangent, Vector3 spin, Vector3 normal, float absVDotN, float friction, float softness) {
        Vector3 tanDir = temp.set(tangent).nor();
        if (tangent.len() < 0.01f && spin.len() > 0.1f) tanDir.set(normal).crs(spin).nor().scl(-1);

        Vector3 sideDir = temp2.set(normal).crs(tanDir).nor();
        float surfaceVel = tangent.len() - (spin.dot(sideDir) * BALL_RADIUS);
        float pitchMarkGrip = (softness > 0.05f && softness < 0.5f) ? (absVDotN / 18f) * 0.85f * MathUtils.clamp(Math.abs(surfaceVel) / 10f, 0.2f, 1.0f) : 0;

        float normalGrip = MathUtils.clamp(absVDotN * NORMAL_GRIP_SCALER * MathUtils.lerp(0.35f, 1.0f, softness), NORMAL_GRIP_MIN, NORMAL_GRIP_MAX);
        float gripImpulse = surfaceVel * (friction + (softness * FRICTION_SOFTNESS_BOOST) + pitchMarkGrip) * normalGrip;

        tangent.mulAdd(tanDir, -gripImpulse * SPIN_TRANSFER_RATIO);
        spin.mulAdd(sideDir, gripImpulse / BALL_RADIUS);

        float energyRetention = MathUtils.lerp(MAX_ENERGY_RETAINED, MIN_ENERGY_RETAINED, softness);
        tangent.scl(energyRetention);
        spin.scl(energyRetention);
    }

    public static void resolveRollingSpin(Vector3 velocity, Vector3 spin, Vector3 normal, Terrain.TerrainType type, float delta) {
        float speed = velocity.len();
        if (speed < 0.01f) {
            spin.scl(1.0f - (10.0f * delta));
            return;
        }
        temp.set(normal).crs(velocity).nor();
        Vector3 spinDiff = temp2.set(temp).scl(speed / 0.2f).sub(spin);
        temp.set(spinDiff).scl(MathUtils.clamp(8.0f * type.kineticFriction * delta, 0f, 1f));
        spin.add(temp);
        if (spinDiff.len() > 0.1f) velocity.scl(MathUtils.clamp(1.0f - (temp.len() * 0.005f), 0.8f, 1.0f));
    }

    public static boolean handleMonolithCollision(Vector3 pos, Vector3 vel, Vector3 spin, Vector3 mPos, float mW, float mH, float mD, float mRot) {
        temp.set(pos).sub(mPos);
        if (mRot != 0) temp.rotate(Vector3.Y, -mRot);
        float closestX = MathUtils.clamp(temp.x, -mW / 2f, mW / 2f);
        float closestY = MathUtils.clamp(temp.y, 0, mH);
        float closestZ = MathUtils.clamp(temp.z, -mD / 2f, mD / 2f);
        float distSq = temp.dst2(closestX, closestY, closestZ);

        if (distSq < BALL_RADIUS * BALL_RADIUS) {
            temp2.set(temp.x - closestX, temp.y - closestY, temp.z - closestZ).nor();
            if (temp2.len() < 0.01f) temp2.set(vel).rotate(Vector3.Y, -mRot).scl(-1).nor();
            if (mRot != 0) temp2.rotate(Vector3.Y, mRot);
            vel.set(calculateBounceWithSpin(vel, temp2, spin, 0.45f, 0.3f, 0.05f));
            pos.add(temp.set(temp2).scl(BALL_RADIUS - (float) Math.sqrt(distSq) + 0.01f));
            return true;
        }
        return false;
    }

    public static boolean handleFlagPoleCollision(Vector3 pos, Vector3 vel, Vector3 spin,
                                                   Vector3 poleBase, float poleRadius, float poleHeight) {
        float dx = pos.x - poleBase.x;
        float dz = pos.z - poleBase.z;
        float combinedRadius = BALL_RADIUS + poleRadius;
        if (dx * dx + dz * dz >= combinedRadius * combinedRadius) return false;
        if (pos.y < poleBase.y || pos.y > poleBase.y + poleHeight) return false;

        temp.set(dx, 0, dz).nor();
        if (vel.dot(temp) >= 0) return false; // Already separating

        float overlap = combinedRadius - (float) Math.sqrt(dx * dx + dz * dz);
        vel.set(calculateBounceWithSpin(vel, temp, spin, 0.28f, 0.15f, 0.0f));
        pos.add(temp.scl(overlap + 0.01f));
        return true;
    }

    public static void applyFoliagePhysics(Vector3 vel, Vector3 spin, float delta,
                                           float dragLin, float dragSqu, float deflectChance, float deflectMag, Random deterministicRandom) {
        float speed = vel.len();
        if (speed < 0.1f) return;
        vel.scl(Math.max(0, 1 - (dragLin + dragSqu * speed) * delta));
        spin.scl(0.8f);

        float chance = 1.0f - (float) Math.pow(1.0f - deflectChance, speed * delta);
        if (deterministicRandom.nextFloat() < chance) {
            float rx = (deterministicRandom.nextFloat() * 2 - 1) * deflectMag;
            float ry = (deterministicRandom.nextFloat() * 2 - 1) * deflectMag;
            float rz = (deterministicRandom.nextFloat() * 2 - 1) * deflectMag;
            vel.rotate(Vector3.X, rx).rotate(Vector3.Y, ry).rotate(Vector3.Z, rz).scl(0.95f);
        }
    }

    public static boolean isWallCollision(Vector3 normal, float steepnessThreshold) {
        return (MathUtils.acos(normal.y) * MathUtils.radiansToDegrees) > steepnessThreshold;
    }
}