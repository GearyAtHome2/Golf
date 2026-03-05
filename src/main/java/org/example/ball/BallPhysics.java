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

    // Skip Constants
    private static final float SKIP_MAX_ANGLE = 22f;    // Degrees
    private static final float SKIP_MIN_VELOCITY = 15f; // m/s
    private static final float SKIP_RESTITUTION = 0.6f; // Energy retained after skip
    private static final float WATER_DENSITY_LIFT = 1.2f;

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
        if (airspeed < 1f) return new Vector3(0, 0, 0);

        Vector3 force = new Vector3(0, 0, 0);
        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / airspeed;
        float CL = 1.0f - (float) Math.exp(-0.9f * spinRatio);
        float speedRef = airspeed / 20.0f;
        float dynamicLift = (speedRef * speedRef) * CL;

        temp.set(relativeVelocity).crs(Vector3.X).nor().scl(spin.x * dynamicLift * liftCoeff);
        force.add(temp);
        temp2.set(relativeVelocity).crs(Vector3.Y).nor().scl(spin.y * dynamicLift * sideCoeff);
        force.add(temp2);

        return force;
    }

    /**
     * Handles water interaction including buoyancy, drag, and skipping logic.
     */
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

                //configurable
                //this one is important, can really make or break the skip here.
                float upwardImpulse = (speed * speed) * 0.006f * efficiency;

                float oldVy = velocity.y;
                velocity.y += upwardImpulse;


                velocity.scl(0.85f);

                String result = (velocity.y > 0) ? "SKIP SUCCESS" : "SKIP FAIL (SINK)";
                Gdx.app.log("Physics", String.format(
                        "MENISCUS [%s] | Spd: %.1f | Ang: %.1f | Eff: %.2f | Vy: %.2f -> %.2f",
                        result, speed, angleDeg, efficiency, oldVy, velocity.y
                ));

                // If we successfully skipped, we skip the hydro-drag for this step
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

        // 3. BUOYANCY & HYDRO-DRAG
        velocity.y += 6.5f * immersion * delta;

        // Drag scales with immersion and speed
        float dragStrength = 2.4f * immersion * (1.0f + speed * 0.12f);
        velocity.scl(MathUtils.clamp(1.0f - (dragStrength * delta), 0.05f, 1.0f));

        // Spin decay is very high in water
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

    public static Vector3 calculateBounceWithSpin(Vector3 velocity, Vector3 normal, Vector3 spin, float restitution, float friction) {
        // 1. Standard elastic bounce components
        float vDotN = velocity.dot(normal);
        temp.set(normal).scl(vDotN); // vNormal
        Vector3 vTangent = new Vector3(velocity).sub(temp);

        // Reverse and scale the normal velocity (the actual "bounce")
        Vector3 vNormalBounce = new Vector3(temp).scl(-restitution);

        // 2. Spin-to-Velocity Transfer
        // We assume the local 'forward' for the bounce is the tangent velocity direction
        if (vTangent.len() > 0.1f) {
            Vector3 tanDir = new Vector3(vTangent).nor();
            Vector3 sideDir = new Vector3(tanDir).crs(normal).nor();

            float forwardKick = -spin.x * 0.05f * friction;

            // Spin.y is sidespin. It applies force perpendicular to the travel direction.
            float sideKick = spin.y * 0.05f * friction;

            vTangent.add(tanDir.scl(forwardKick));
            vTangent.add(sideDir.scl(sideKick));
        }

        float frictionScale = MathUtils.clamp(1.0f - (friction * 0.5f), 0.6f, 0.98f);
        vTangent.scl(frictionScale);

        return vTangent.add(vNormalBounce);
    }

    public static boolean handleMonolithCollision(Vector3 pos, Vector3 vel, Vector3 spin,
                                                  Vector3 mPos, float mW, float mH, float mD, float mRot) {
        // 1. Local Space Transform
        temp.set(pos).sub(mPos);
        if (mRot != 0) temp.rotate(Vector3.Y, -mRot);

        float halfW = mW / 2f;
        float halfD = mD / 2f;

        float closestX = MathUtils.clamp(temp.x, -halfW, halfW);
        float closestY = MathUtils.clamp(temp.y, 0, mH);
        float closestZ = MathUtils.clamp(temp.z, -halfD, halfD);

        float distSq = temp.dst2(closestX, closestY, closestZ);

        if (distSq < BALL_RADIUS * BALL_RADIUS) {
            // 2. Local Normal
            temp2.set(temp.x - closestX, temp.y - closestY, temp.z - closestZ).nor();
            if (temp2.len() < 0.01f) {
                temp2.set(vel).rotate(Vector3.Y, -mRot).scl(-1).nor();
            }

            // 3. World Normal
            if (mRot != 0) temp2.rotate(Vector3.Y, mRot);

            // 4. Response
            vel.set(calculateBounceWithSpin(vel, temp2, spin, 0.45f, 0.3f));

            // Push out
            float dist = (float) Math.sqrt(distSq);
            pos.add(temp.set(temp2).scl(BALL_RADIUS - dist + 0.01f));
            return true;
        }
        return false;
    }

    /**
     * Applies drag and random deflection when passing through foliage.
     */
    public static void applyFoliagePhysics(Vector3 vel, Vector3 spin, float delta,
                                           float dragLin, float dragSqu, float deflectChance, float deflectMag) {
        float speed = vel.len();
        if (speed < 0.1f) return;

        // Velocity Drag
        vel.scl(Math.max(0, 1 - (dragLin + dragSqu * speed) * delta));

        // Spin Decay
        spin.scl(0.8f);

        // Random Deflection
        float chance = 1.0f - (float) Math.pow(1.0f - deflectChance, speed * delta);
        if (MathUtils.random() < chance) {
            vel.rotate(Vector3.X, MathUtils.random(-deflectMag, deflectMag));
            vel.rotate(Vector3.Y, MathUtils.random(-deflectMag, deflectMag));
            vel.rotate(Vector3.Z, MathUtils.random(-deflectMag, deflectMag));
            vel.scl(0.95f);
        }
    }

    /**
     * Determines if a terrain contact should be treated as a wall bounce
     * or a ground landing based on the normal angle.
     */
    public static boolean isWallCollision(Vector3 normal, float steepnessThreshold) {
        float angle = MathUtils.acos(normal.y) * MathUtils.radiansToDegrees;
        return angle > steepnessThreshold;
    }
}