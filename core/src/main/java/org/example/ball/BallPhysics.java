package org.example.ball;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

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

    // --- TWEAKABLE CONSTANTS ---
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
        if (airspeed < 1f || spin.len() < 0.1f) return outMagnus.setZero();
        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / airspeed;
        float CL = 1.0f - (float) Math.exp(-0.9f * spinRatio);
        float speedRef = airspeed / 20.0f;
        float dynamicLift = (speedRef * speedRef) * CL;
        temp.set(relativeVelocity).crs(spin).nor();
        float totalForce = -dynamicLift * (liftCoeff + sideCoeff);
        return outMagnus.set(temp).scl(totalForce);
    }

    public static boolean checkWaterTransition(Vector3 position, Vector3 velocity, float waterLevel) {
        float previousY = position.y - (velocity.y * 0.016f);
        return previousY >= waterLevel && position.y < waterLevel;
    }

    public static void applyWaterPhysics(Vector3 position, Vector3 velocity, Vector3 spin, float waterLevel, float delta) {
        float previousY = position.y - (velocity.y * delta);
        float depth = waterLevel - position.y;
        float immersion = MathUtils.clamp((depth + BALL_RADIUS) / (BALL_RADIUS * 2f), 0f, 1f);

        if (previousY >= waterLevel && position.y < waterLevel && velocity.y < 0) {
            float speed = velocity.len();
            if (speed > 5.0f) {
                float angleRad = (float) Math.asin(Math.abs(velocity.y) / speed);
                float efficiency = MathUtils.cos(angleRad * 3.0f);
                efficiency = MathUtils.clamp(efficiency, 0, 1) * efficiency;

                float upwardImpulse = (speed * speed) * 0.005f * efficiency;
                velocity.y += upwardImpulse;
                velocity.scl(0.85f);
                if (velocity.y > 0) return;
            }
        }

        if (immersion <= 0) return;

        float speed = velocity.len();
        float waterLiftCoeff = 0.8f;
        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / (speed + 0.1f);
        float CL = 1.0f - (float) Math.exp(-0.9f * spinRatio);
        float dynamicLift = ((speed / 20.0f) * (speed / 20.0f)) * CL;

        temp.set(velocity).crs(Vector3.Y).nor();
        temp2.set(velocity).crs(temp).nor().scl(spin.dot(temp) * dynamicLift * waterLiftCoeff * immersion);
        velocity.add(temp2.scl(delta));

        velocity.y += 6.5f * immersion * delta;
        float dragStrength = 2.4f * immersion * (1.0f + speed * 0.12f);
        velocity.scl(MathUtils.clamp(1.0f - (dragStrength * delta), 0.05f, 1.0f));
        spin.scl(MathUtils.clamp(1.0f - (12.0f * immersion * delta), 0f, 1.0f));
    }

    public static boolean handleTrunkCollision(Vector3 pos, Vector3 vel, Vector3 spin, float dx, float dz) {
        temp.set(dx, 0, dz).nor();
        float dot = vel.dot(temp);
        if (dot < 0) {
            float bounciness = 1.6f;
            vel.mulAdd(temp, -dot * bounciness);
            vel.scl(0.85f);
            spin.scl(0.3f);
            pos.x += temp.x * 0.1f;
            pos.z += temp.z * 0.1f;
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
        temp.set(normal).scl(gravityForce.dot(normal));
        return outSlope.set(gravityForce).sub(temp);
    }

    public static Vector3 calculateBounceWithSpin(Vector3 velocity, Vector3 normal, Vector3 spin, float restitution, float friction, float softness) {
        float vDotN = velocity.dot(normal);
        float absVDotN = Math.abs(vDotN);

        // Split velocity into Normal and Tangent
        temp.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(temp);

        // 1. Resolve Vertical Bounce
        resolveVerticalBounce(vNormalBounce, temp, absVDotN, restitution, softness);

        // 2. Resolve Tangential Spin and Friction
        float energyLoss = 1.0f;
        if (vTangent.len() > 0.05f || spin.len() > 0.05f) {
            energyLoss = resolveTangentialBounce(vTangent, spin, normal, absVDotN, friction, softness);
        }

        Vector3 result = outBounce.set(vTangent).add(vNormalBounce);

        // Detailed Debug Logging
        System.out.printf("[PHYSICS] InSpd: %.2f (V:%.2f) -> OutSpd: %.2f (V:%.2f) | Energy: %.0f%% | Soft: %.4f%n",
                velocity.len(), absVDotN, result.len(), vNormalBounce.len(), energyLoss * 100f, softness);

        return result;
    }

    private static void resolveVerticalBounce(Vector3 outNormal, Vector3 normalVector, float absVDotN, float restitution, float softness) {
        float absorption = Math.max(MIN_RESTITUTION, 1.0f - (softness * SOFTNESS_ABSORPTION_FACTOR));
        float finalRestitution = restitution * absorption;

        if (absVDotN * finalRestitution < VERTICAL_STOP_THRESHOLD) {
            outNormal.setZero();
        } else {
            outNormal.set(normalVector).scl(-finalRestitution);
        }
    }

    private static float resolveTangentialBounce(Vector3 tangent, Vector3 spin, Vector3 normal, float absVDotN, float friction, float softness) {
        Vector3 tanDir = temp.set(tangent).nor();

        // If we have NO forward speed, we must derive the 'tangent direction' from the spin
        // to allow a vertical drop to kick backwards/forwards.
        if (tangent.len() < 0.01f && spin.len() > 0.1f) {
            // Find the direction the bottom of the ball is moving due to spin
            tanDir.set(normal).crs(spin).nor().scl(-1);
        }

        Vector3 sideDir = temp2.set(normal).crs(tanDir).nor();

        float oldSpeed = tangent.len();
        float oldSpinMag = spin.dot(sideDir);

        // This is the key: How fast is the contact point moving?
        // If oldSpeed is 0 but spin is 100, surfaceVel is -2.0.
        float surfaceVel = oldSpeed - (oldSpinMag * BALL_RADIUS);

        // --- PITCH MARK LOGIC ---
        float impactSteepness = MathUtils.clamp(absVDotN / 18f, 0f, 1f);
        float pitchMarkGrip = 0f;
        if (softness > 0.05f && softness < 0.5f) {
            // Bite is now driven by how hard we hit AND how much 'surface sliding' there is
            float slidingFactor = MathUtils.clamp(Math.abs(surfaceVel) / 10f, 0.2f, 1.0f);
            pitchMarkGrip = impactSteepness * 0.85f * slidingFactor;
        }

        float effectiveFriction = friction + (softness * FRICTION_SOFTNESS_BOOST) + pitchMarkGrip;
        float gripHardnessFactor = MathUtils.lerp(0.35f, 1.0f, softness);
        float normalGrip = MathUtils.clamp(absVDotN * NORMAL_GRIP_SCALER * gripHardnessFactor, NORMAL_GRIP_MIN, NORMAL_GRIP_MAX);

        // The impulse is a product of the surface velocity (speed + spin)
        float gripImpulse = surfaceVel * effectiveFriction * normalGrip;

        // Apply the change
        float vChange = gripImpulse * SPIN_TRANSFER_RATIO;
        tangent.mulAdd(tanDir, -vChange);

        float sChange = gripImpulse / BALL_RADIUS;
        spin.mulAdd(sideDir, sChange);

        // Energy retention
        float energyLoss = MathUtils.lerp(MAX_ENERGY_RETAINED, MIN_ENERGY_RETAINED, softness);
        tangent.scl(energyLoss);
        spin.scl(energyLoss);

        return energyLoss;
    }

    public static void resolveRollingSpin(Vector3 velocity, Vector3 spin, Vector3 normal, Terrain.TerrainType type, float delta) {
        float speed = velocity.len();

        if (speed < 0.01f) {
            spin.scl(1.0f - (10.0f * delta));
            return;
        }

        temp.set(normal).crs(velocity).nor();
        float targetSpinMag = speed / 0.2f;
        temp2.set(temp).scl(targetSpinMag);

        Vector3 spinDiff = temp.set(temp2).sub(spin);
        float diffLen = spinDiff.len();

        float gripStrength = 8.0f * type.kineticFriction;
        float matchRatio = MathUtils.clamp(gripStrength * delta, 0f, 1f);

        temp.set(spinDiff).scl(matchRatio);
        float stepSize = temp.len();

        spin.add(temp);

        if (diffLen > 0.1f) {
            float velocityLoss = stepSize * 0.005f;
            velocity.scl(MathUtils.clamp(1.0f - velocityLoss, 0.8f, 1.0f));
        }
    }

    public static boolean handleMonolithCollision(Vector3 pos, Vector3 vel, Vector3 spin,
                                                  Vector3 mPos, float mW, float mH, float mD, float mRot) {
        temp.set(pos).sub(mPos);
        if (mRot != 0) temp.rotate(Vector3.Y, -mRot);
        float halfW = mW / 2f, halfD = mD / 2f;
        float closestX = MathUtils.clamp(temp.x, -halfW, halfW);
        float closestY = MathUtils.clamp(temp.y, 0, mH);
        float closestZ = MathUtils.clamp(temp.z, -halfD, halfD);
        float distSq = temp.dst2(closestX, closestY, closestZ);

        if (distSq < BALL_RADIUS * BALL_RADIUS) {
            temp2.set(temp.x - closestX, temp.y - closestY, temp.z - closestZ).nor();
            if (temp2.len() < 0.01f) temp2.set(vel).rotate(Vector3.Y, -mRot).scl(-1).nor();
            if (mRot != 0) temp2.rotate(Vector3.Y, mRot);

            vel.set(calculateBounceWithSpin(vel, temp2, spin, 0.45f, 0.3f, 0.05f));
            float dist = (float) Math.sqrt(distSq);
            pos.add(temp.set(temp2).scl(BALL_RADIUS - dist + 0.01f));
            return true;
        }
        return false;
    }

    public static void applyFoliagePhysics(Vector3 vel, Vector3 spin, float delta,
                                           float dragLin, float dragSqu, float deflectChance, float deflectMag) {
        float speed = vel.len();
        if (speed < 0.1f) return;
        vel.scl(Math.max(0, 1 - (dragLin + dragSqu * speed) * delta));
        spin.scl(0.8f);
        float chance = 1.0f - (float) Math.pow(1.0f - deflectChance, speed * delta);
        if (MathUtils.random() < chance) {
            vel.rotate(Vector3.X, MathUtils.random(-deflectMag, deflectMag));
            vel.rotate(Vector3.Y, MathUtils.random(-deflectMag, deflectMag));
            vel.rotate(Vector3.Z, MathUtils.random(-deflectMag, deflectMag));
            vel.scl(0.95f);
        }
    }

    public static boolean isWallCollision(Vector3 normal, float steepnessThreshold) {
        float angle = MathUtils.acos(normal.y) * MathUtils.radiansToDegrees;
        return angle > steepnessThreshold;
    }
}