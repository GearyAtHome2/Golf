package org.example.ball;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

import static org.example.ball.Ball.BALL_RADIUS;

public class BallPhysics {

    // Internal scratchpad vectors for intermediate math
    private static final Vector3 temp = new Vector3();
    private static final Vector3 temp2 = new Vector3();
    private static final Vector3 relativeVelocity = new Vector3();
    private static final Vector3 vTangent = new Vector3();
    private static final Vector3 vNormalBounce = new Vector3();

    // Result vectors used specifically for returns to avoid 'new Vector3()'
    private static final Vector3 outGravity = new Vector3();
    private static final Vector3 outWind = new Vector3();
    private static final Vector3 outDrag = new Vector3();
    private static final Vector3 outMagnus = new Vector3();
    private static final Vector3 outFriction = new Vector3();
    private static final Vector3 outSlope = new Vector3();
    private static final Vector3 outBounce = new Vector3();

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

        temp.set(velocity).crs(Vector3.X).nor().scl(spin.x * dynamicLift * waterLiftCoeff * immersion);
        velocity.add(temp.scl(delta));

        velocity.y += 6.5f * immersion * delta;
        float dragStrength = 2.4f * immersion * (1.0f + speed * 0.12f);
        velocity.scl(MathUtils.clamp(1.0f - (dragStrength * delta), 0.05f, 1.0f));
        spin.scl(MathUtils.clamp(1.0f - (12.0f * immersion * delta), 0f, 1.0f));
    }

    public static boolean handleTrunkCollision(Vector3 pos, Vector3 vel, Vector3 spin, float dx, float dz) {
        temp.set(dx, 0, dz).nor();
        float speedXZ = temp2.set(vel.x, 0, vel.z).len();
        vel.x = temp.x * speedXZ;
        vel.z = temp.z * speedXZ;
        vel.scl(0.8f);
        spin.scl(0.2f);
        pos.x += temp.x * 0.05f;
        pos.z += temp.z * 0.05f;
        return true;
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
        temp.set(normal).scl(vDotN);
        vTangent.set(velocity).sub(temp);
        vNormalBounce.set(temp).scl(-restitution);

        if (vTangent.len() > 0.1f) {
            Vector3 tanDir = temp.set(vTangent).nor();
            Vector3 sideDir = temp2.set(tanDir).crs(normal).nor();
            float relativeBackspin = spin.dot(sideDir);
            float relativeSidespin = spin.dot(normal);

            float grip = MathUtils.clamp(softness * 0.6f + friction * 0.2f, 0.1f, 0.95f);
            float spinTransferMagnitude = 0.09f;
            vTangent.add(tanDir.scl(-relativeBackspin * spinTransferMagnitude * grip));
            vTangent.add(sideDir.scl(relativeSidespin * spinTransferMagnitude * grip));
        }

        float speedLoss = (friction + softness) * 0.5f;
        vTangent.scl(MathUtils.clamp(1.0f - speedLoss, 0.4f, 0.98f));
        return outBounce.set(vTangent).add(vNormalBounce);
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