package org.example.ball;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

import static org.example.ball.Ball.BALL_RADIUS;

public class BallPhysics {

    private static final Vector3 temp = new Vector3();
    private static final Vector3 temp2 = new Vector3();
    private static final Vector3 relativeVelocity = new Vector3();

    public static Vector3 getGravityForce(float gravity) {
        return new Vector3(0, gravity, 0);
    }

    public static Vector3 getWindAtHeight(Vector3 baseWind, float height) {
        float heightFactor = MathUtils.clamp(height / 20f, 0.2f, 1.0f);
        return new Vector3(baseWind).scl(heightFactor);
    }

    public static Vector3 getAirDrag(Vector3 velocity, Vector3 wind, float dragCoeff) {
        relativeVelocity.set(velocity).sub(wind);
        float speed = relativeVelocity.len();
        if (speed < 0.01f) return new Vector3(0, 0, 0);
        return new Vector3(relativeVelocity).nor().scl(-dragCoeff * speed * speed);
    }

    public static Vector3 getMagnusForce(Vector3 velocity, Vector3 wind, Vector3 spin, float liftCoeff, float sideCoeff) {
        relativeVelocity.set(velocity).sub(wind);
        float airspeed = relativeVelocity.len();

        if (airspeed < 1f || spin.len() < 0.1f) return new Vector3(0, 0, 0);

        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / airspeed;
        float CL = 1.0f - (float) Math.exp(-0.9f * spinRatio);

        float speedRef = airspeed / 20.0f;
        float dynamicLift = (speedRef * speedRef) * CL;

        // Capture cross product
        temp.set(relativeVelocity).crs(spin).nor();
        float rawCrsLen = temp.len();

        // Directional check: This vector should always be perpendicular to airspeed
        temp.nor();

        float totalForce = -dynamicLift * (liftCoeff + sideCoeff);
        Vector3 finalForce = temp.scl(totalForce);

        // Get normalized airspeed for directional comparison
        temp2.set(relativeVelocity).nor();
        return finalForce;
    }

    public static void applyWaterPhysics(Vector3 position, Vector3 velocity, Vector3 spin, float waterLevel, float delta) {
        float previousY = position.y - (velocity.y * delta);
        float depth = waterLevel - position.y;
        float immersion = MathUtils.clamp((depth + BALL_RADIUS) / (BALL_RADIUS * 2f), 0f, 1f);

        if (previousY >= waterLevel && position.y < waterLevel && velocity.y < 0) {
            float speed = velocity.len();
            if (speed > 5.0f) {
                float angleRad = (float) Math.asin(Math.abs(velocity.y) / speed);
                float angleDeg = angleRad * MathUtils.radiansToDegrees;

                float efficiency = MathUtils.cos(angleRad * 3.0f);
                efficiency = MathUtils.clamp(efficiency, 0, 1);
                efficiency *= efficiency;

                float upwardImpulse = (speed * speed) * 0.0034f * efficiency;

                float oldVy = velocity.y;
                velocity.y += upwardImpulse;
                velocity.scl(0.85f);

                String result = (velocity.y > 0) ? "SKIP SUCCESS" : "SKIP FAIL (SINK)";
                Gdx.app.log("Physics", String.format(
                        "MENISCUS [%s] | Spd: %.1f | Ang: %.1f | Eff: %.2f | Vy: %.2f -> %.2f",
                        result, speed, angleDeg, efficiency, oldVy, velocity.y
                ));

                if (velocity.y > 0) return;
            }
        }

        if (immersion <= 0) return;
        float speed = velocity.len();
        float waterLiftCoeff = 0.8f;
        relativeVelocity.set(velocity);
        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / (speed + 0.1f);
        float CL = 1.0f - (float) Math.exp(-0.9f * spinRatio);
        float dynamicLift = ((speed / 20.0f) * (speed / 20.0f)) * CL;

        temp.set(relativeVelocity).crs(Vector3.X).nor().scl(spin.x * dynamicLift * waterLiftCoeff * immersion);
        velocity.add(temp.scl(delta));

        velocity.y += 6.5f * immersion * delta;
        float dragStrength = 2.4f * immersion * (1.0f + speed * 0.12f);
        velocity.scl(MathUtils.clamp(1.0f - (dragStrength * delta), 0.05f, 1.0f));
        spin.scl(MathUtils.clamp(1.0f - (12.0f * immersion * delta), 0f, 1.0f));
    }

    public static Vector3 getRollingFriction(Vector3 velocity, Vector3 normal, Terrain.TerrainType type, float gravity) {
        float speed = velocity.len();
        if (speed < 0.01f) return new Vector3(0, 0, 0);
        float frictionMag = (type.kineticFriction * Math.abs(normal.y * gravity)) + (type.rollingResistance * speed);
        return new Vector3(velocity).nor().scl(-frictionMag);
    }

    public static Vector3 getSlopeForce(Vector3 gravityForce, Vector3 normal) {
        temp.set(normal).scl(gravityForce.dot(normal));
        return new Vector3(gravityForce).sub(temp);
    }

    public static Vector3 calculateBounceWithSpin(Vector3 velocity, Vector3 normal, Vector3 spin, float restitution, float friction, float softness) {
        float initialHoriz = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        float vDotN = velocity.dot(normal);
        temp.set(normal).scl(vDotN); // vNormal
        Vector3 vTangent = new Vector3(velocity).sub(temp);
        Vector3 vNormalBounce = new Vector3(temp).scl(-restitution);

        // 2. Spin-to-Velocity Transfer (Mechanical Bite)
        if (vTangent.len() > 0.1f) {
            Vector3 tanDir = temp.set(vTangent).nor(); // Horizontal direction of travel
            Vector3 sideDir = temp2.set(tanDir).crs(normal).nor(); // The "Axle" of the bounce

            // Project the actual spin vector onto our bounce axes
            // How much is the ball spinning around the 'axle'? (Backspin/Topspin)
            float relativeBackspin = spin.dot(sideDir);

            // How much is it spinning around the ground normal? (Sidespin/Rifling)
            float relativeSidespin = spin.dot(normal);

            // Grip: Softness (grass depth) makes the ball bite more
            float grip = MathUtils.clamp(softness * 0.6f + friction * 0.2f, 0.1f, 0.95f);
            float spinTransferMagnitude = 0.09f; // Increased slightly for visibility

            // Backspin (negative relativeBackspin) pushes the ball forward (check your sign preference here)
            // If spin.dot(sideDir) is positive (Backspin), it should fight forward momentum
            float forwardKick = -relativeBackspin * spinTransferMagnitude * grip;
            float sideKick = relativeSidespin * spinTransferMagnitude * grip;

            vTangent.add(tanDir.scl(forwardKick));
            vTangent.add(sideDir.scl(sideKick));
        }

        float speedLoss = (friction + softness) * 0.5f;
        float frictionScale = MathUtils.clamp(1.0f - speedLoss, 0.4f, 0.98f);
        vTangent.scl(frictionScale);

        return vTangent.add(vNormalBounce);
    }

    public static boolean handleMonolithCollision(Vector3 pos, Vector3 vel, Vector3 spin,
                                                  Vector3 mPos, float mW, float mH, float mD, float mRot) {
        temp.set(pos).sub(mPos);
        if (mRot != 0) temp.rotate(Vector3.Y, -mRot);
        float halfW = mW / 2f;
        float halfD = mD / 2f;
        float closestX = MathUtils.clamp(temp.x, -halfW, halfW);
        float closestY = MathUtils.clamp(temp.y, 0, mH);
        float closestZ = MathUtils.clamp(temp.z, -halfD, halfD);
        float distSq = temp.dst2(closestX, closestY, closestZ);

        if (distSq < BALL_RADIUS * BALL_RADIUS) {
            temp2.set(temp.x - closestX, temp.y - closestY, temp.z - closestZ).nor();
            if (temp2.len() < 0.01f) {
                temp2.set(vel).rotate(Vector3.Y, -mRot).scl(-1).nor();
            }
            if (mRot != 0) temp2.rotate(Vector3.Y, mRot);

            // Monoliths are hard (Softness 0.05)
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