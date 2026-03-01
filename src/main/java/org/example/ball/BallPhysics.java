package org.example.ball;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

public class BallPhysics {

    private static final Vector3 temp = new Vector3();
    private static final Vector3 temp2 = new Vector3();

    public static Vector3 getGravityForce(float gravity) {
        return new Vector3(0, gravity, 0);
    }


    public static Vector3 getAirDrag(Vector3 velocity, float dragCoeff) {
        float speed = velocity.len();
        if (speed < 0.01f) return new Vector3(0, 0, 0);
        return new Vector3(velocity).nor().scl(-dragCoeff * speed * speed);
    }

    public static Vector3 getMagnusForce(Vector3 velocity, Vector3 spin, float liftCoeff, float sideCoeff) {
        float speed = velocity.len();
        if (speed < 1f) return new Vector3(0, 0, 0);

        Vector3 force = new Vector3(0, 0, 0);

        float surfaceSpeed = spin.len() * 0.02f;
        float spinRatio = surfaceSpeed / speed;
        float CL = 1.0f - (float)Math.exp(-0.9f * spinRatio);

        // Using your stable reference speed
        float speedRef = speed / 20.0f;
        float dynamicLift = (speedRef * speedRef) * CL;

        temp.set(velocity).crs(Vector3.X).nor().scl(spin.x * dynamicLift * liftCoeff);
        force.add(temp);

        // Side Force (unchanged)
        temp2.set(velocity).crs(Vector3.Y).nor().scl(spin.y * dynamicLift * sideCoeff);
        force.add(temp2);

        if (speed > 50f) {
            Gdx.app.log("MAGNUS_DEBUG", String.format("Speed: %.1f | SpinX: %.0f | ForceY: %.2f",
                    speed, spin.x, force.y));
        }

        return force;
    }

    /**
     * Normal Force: The ground's reaction.
     * It identifies the component of a force (like Gravity) or velocity 
     * acting INTO the normal and returns the equal/opposite force.
     */
    public static Vector3 getNormalForce(Vector3 forceIn, Vector3 terrainNormal) {
        float dot = forceIn.dot(terrainNormal);
        if (dot < 0) {
            // Push back with exactly the amount pushing in
            return new Vector3(terrainNormal).scl(-dot);
        }
        return new Vector3(0, 0, 0);
    }

    /**
     * Rolling Friction: Opposes tangent velocity.
     * Combines kinetic friction (weight-based) and rolling resistance (speed-based).
     */
    public static Vector3 getRollingFriction(Vector3 velocity, Vector3 normal, Terrain.TerrainType type, float gravity) {
        float speed = velocity.len();
        if (speed < 0.01f) return new Vector3(0, 0, 0);

        // Magnitude based on weight on slope and current speed
        float frictionMag = (type.kineticFriction * Math.abs(normal.y * gravity)) + (type.rollingResistance * speed);
        
        // Return vector opposing current velocity
        return new Vector3(velocity).nor().scl(-frictionMag);
    }

    /**
     * Downhill Force: The component of gravity that pulls the ball down a slope.
     */
    public static Vector3 getSlopeForce(Vector3 gravityForce, Vector3 normal) {
        // Project gravity onto the normal to find the "into ground" part
        temp.set(normal).scl(gravityForce.dot(normal));
        // Resultant is the part of gravity not pointing into the ground
        return new Vector3(gravityForce).sub(temp);
    }

    /**
     * Bounce: Reflects the velocity vector across the terrain normal.
     */
    public static Vector3 calculateBounce(Vector3 velocity, Vector3 normal, float restitution) {
        float vDotN = velocity.dot(normal);
        Vector3 vNormal = new Vector3(normal).scl(vDotN);
        Vector3 vTangent = new Vector3(velocity).sub(vNormal);

        vNormal.scl(-restitution); // Flip and dampen vertical
        vTangent.scl(0.7f);        // Dampen horizontal (turf friction)
        
        return vTangent.add(vNormal);
    }
}