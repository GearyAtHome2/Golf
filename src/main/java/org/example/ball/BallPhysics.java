package org.example.ball;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

public class BallPhysics {

    private static final Vector3 temp = new Vector3();
    private static final Vector3 temp2 = new Vector3();
    private static final Vector3 relativeVelocity = new Vector3();

    public static Vector3 getGravityForce(float gravity) {
        return new Vector3(0, gravity, 0);
    }

    /**
     * Calculates wind strength based on height.
     * Wind is 20% at ground level and 100% at 20m high.
     */
    public static Vector3 getWindAtHeight(Vector3 baseWind, float height) {
        float heightFactor = MathUtils.clamp(height / 20f, 0.2f, 1.0f);
        return new Vector3(baseWind).scl(heightFactor);
    }

    public static Vector3 getAirDrag(Vector3 velocity, Vector3 wind, float dragCoeff) {
        // Airspeed Velocity = Ball Velocity - Wind Velocity
        relativeVelocity.set(velocity).sub(wind);
        float speed = relativeVelocity.len();

        if (speed < 0.01f) return new Vector3(0, 0, 0);

        // Drag acts in the opposite direction of the relative velocity
        return new Vector3(relativeVelocity).nor().scl(-dragCoeff * speed * speed);
    }

    public static Vector3 getMagnusForce(Vector3 velocity, Vector3 wind, Vector3 spin, float liftCoeff, float sideCoeff) {
        relativeVelocity.set(velocity).sub(wind);
        float airspeed = relativeVelocity.len();

        if (airspeed < 1f) return new Vector3(0, 0, 0);

        Vector3 force = new Vector3(0, 0, 0);

        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / airspeed;
        float CL = 1.0f - (float)Math.exp(-0.9f * spinRatio);

        float speedRef = airspeed / 20.0f;
        float dynamicLift = (speedRef * speedRef) * CL;

        temp.set(relativeVelocity).crs(Vector3.X).nor().scl(spin.x * dynamicLift * liftCoeff);
        force.add(temp);

        temp2.set(relativeVelocity).crs(Vector3.Y).nor().scl(spin.y * dynamicLift * sideCoeff);
        force.add(temp2);

        return force;
    }

    public static Vector3 getNormalForce(Vector3 forceIn, Vector3 terrainNormal) {
        float dot = forceIn.dot(terrainNormal);
        if (dot < 0) return new Vector3(terrainNormal).scl(-dot);
        return new Vector3(0, 0, 0);
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

    public static Vector3 calculateBounce(Vector3 velocity, Vector3 normal, float restitution) {
        float vDotN = velocity.dot(normal);

        Vector3 vNormal = new Vector3(normal).scl(vDotN);
        Vector3 vTangent = new Vector3(velocity).sub(vNormal);

        vNormal.scl(-restitution);

        float impactSpeed = Math.abs(vDotN);
        float frictionScale = MathUtils.clamp(1.0f - (0.2f * impactSpeed), 0.95f, 1.0f);
        vTangent.scl(frictionScale);

        return vTangent.add(vNormal);
    }
}