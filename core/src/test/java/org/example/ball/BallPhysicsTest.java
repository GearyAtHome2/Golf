package org.example.ball;

import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure-math static methods in BallPhysics.
 *
 * NOTE: BallPhysics uses static mutable Vector3 return values, so every
 * result must be .cpy()'d before calling another BallPhysics method.
 */
public class BallPhysicsTest {

    private static final float DELTA = 0.0001f;

    // ── getGravityForce ────────────────────────────────────────────────────────

    @Test
    public void testGravityPointsDownward() {
        Vector3 g = BallPhysics.getGravityForce(-9.81f).cpy();
        assertEquals(0f, g.x, DELTA);
        assertEquals(-9.81f, g.y, DELTA);
        assertEquals(0f, g.z, DELTA);
    }

    @Test
    public void testGravityMagnitudeMatchesInput() {
        Vector3 g = BallPhysics.getGravityForce(-5f).cpy();
        assertEquals(-5f, g.y, DELTA);
    }

    // ── getWindAtHeight ────────────────────────────────────────────────────────

    @Test
    public void testWindAtFullHeight() {
        // At height 20+, heightFactor should be clamped to 1.0
        Vector3 base = new Vector3(10f, 0f, 0f);
        Vector3 wind = BallPhysics.getWindAtHeight(base, 20f).cpy();
        assertEquals(10f, wind.x, DELTA);
    }

    @Test
    public void testWindReducedAtLowHeight() {
        // At height 0 → heightFactor = clamp(0/20, 0.2, 1.0) = 0.2
        Vector3 base = new Vector3(10f, 0f, 0f);
        Vector3 wind = BallPhysics.getWindAtHeight(base, 0f).cpy();
        assertEquals(2f, wind.x, DELTA, "Wind at ground should be 20% of base");
    }

    @Test
    public void testWindInterpolatedAtMidHeight() {
        // At height 10 → heightFactor = 10/20 = 0.5
        Vector3 base = new Vector3(10f, 0f, 0f);
        Vector3 wind = BallPhysics.getWindAtHeight(base, 10f).cpy();
        assertEquals(5f, wind.x, DELTA);
    }

    @Test
    public void testWindHeightClampedAbove20() {
        Vector3 base = new Vector3(10f, 0f, 0f);
        Vector3 windAt20  = BallPhysics.getWindAtHeight(base, 20f).cpy();
        Vector3 windAt100 = BallPhysics.getWindAtHeight(base, 100f).cpy();
        assertEquals(windAt20.x, windAt100.x, DELTA);
    }

    // ── getAirDrag ─────────────────────────────────────────────────────────────

    @Test
    public void testAirDragOpposesRelativeVelocity() {
        // Ball moving in +X, no wind → drag should be in -X
        Vector3 vel  = new Vector3(20f, 0f, 0f);
        Vector3 wind = new Vector3(0f, 0f, 0f);
        Vector3 drag = BallPhysics.getAirDrag(vel, wind, 0.01f).cpy();
        assertTrue(drag.x < 0, "Drag should oppose velocity (+X → drag in -X)");
        assertEquals(0f, drag.y, DELTA);
        assertEquals(0f, drag.z, DELTA);
    }

    @Test
    public void testAirDragIsZeroWhenBallMatchesWind() {
        // Relative velocity = 0 → no drag
        Vector3 vel  = new Vector3(5f, 0f, 0f);
        Vector3 wind = new Vector3(5f, 0f, 0f);
        Vector3 drag = BallPhysics.getAirDrag(vel, wind, 0.01f).cpy();
        assertEquals(0f, drag.len(), DELTA);
    }

    @Test
    public void testAirDragScalesWithDragCoeff() {
        Vector3 vel  = new Vector3(10f, 0f, 0f);
        Vector3 wind = new Vector3(0f,  0f, 0f);
        Vector3 lowDrag  = BallPhysics.getAirDrag(vel, wind, 0.005f).cpy();
        Vector3 highDrag = BallPhysics.getAirDrag(vel, wind, 0.020f).cpy();
        assertTrue(Math.abs(highDrag.x) > Math.abs(lowDrag.x),
                "Higher drag coefficient should produce greater drag force");
    }

    @Test
    public void testAirDragIsZeroForVerySlowBall() {
        Vector3 vel  = new Vector3(0.005f, 0f, 0f); // below 0.01 threshold
        Vector3 wind = new Vector3(0f, 0f, 0f);
        Vector3 drag = BallPhysics.getAirDrag(vel, wind, 0.01f).cpy();
        assertEquals(0f, drag.len(), DELTA);
    }

    // ── getMagnusForce ─────────────────────────────────────────────────────────

    @Test
    public void testMagnusForceZeroWhenNospin() {
        Vector3 vel  = new Vector3(20f, 0f, 0f);
        Vector3 wind = new Vector3(0f,  0f, 0f);
        Vector3 spin = new Vector3(0f,  0f, 0f); // no spin
        Vector3 mag  = BallPhysics.getMagnusForce(vel, wind, spin, 1f, 1f).cpy();
        assertEquals(0f, mag.len(), DELTA);
    }

    @Test
    public void testMagnusForceZeroWhenTooSlow() {
        // Airspeed < 2.5 → no Magnus
        Vector3 vel  = new Vector3(1f, 0f, 0f);
        Vector3 wind = new Vector3(0f, 0f, 0f);
        Vector3 spin = new Vector3(0f, 0f, 100f);
        Vector3 mag  = BallPhysics.getMagnusForce(vel, wind, spin, 1f, 1f).cpy();
        assertEquals(0f, mag.len(), DELTA);
    }

    @Test
    public void testMagnusForceNonZeroWithSpinAndSpeed() {
        Vector3 vel  = new Vector3(30f, 0f, 0f);
        Vector3 wind = new Vector3(0f,  0f, 0f);
        Vector3 spin = new Vector3(0f, 100f, 0f); // top spin
        Vector3 mag  = BallPhysics.getMagnusForce(vel, wind, spin, 1f, 1f).cpy();
        assertTrue(mag.len() > 0f, "Magnus force should be non-zero with spin and speed");
    }

    // ── checkWaterTransition ───────────────────────────────────────────────────

    @Test
    public void testWaterTransitionDetectedWhenCrossingWaterSurface() {
        // Ball was above water last frame, now below
        Vector3 pos = new Vector3(0f, 0.5f, 0f); // current y = 0.5, below waterLevel=1
        Vector3 vel = new Vector3(0f, -10f, 0f);  // moving downward
        // previousY = 0.5 - (-10 * 0.016) = 0.5 + 0.16 = 0.66 > 1? No, 0.66 < 1 too
        // Let's put waterLevel at 0.6 so previousY=0.66 > 0.6, current=0.5 < 0.6
        assertTrue(BallPhysics.checkWaterTransition(pos, vel, 0.6f));
    }

    @Test
    public void testNoWaterTransitionWhenAlwaysAboveWater() {
        Vector3 pos = new Vector3(0f, 5f, 0f);
        Vector3 vel = new Vector3(0f, -1f, 0f);
        assertFalse(BallPhysics.checkWaterTransition(pos, vel, 1f));
    }

    @Test
    public void testNoWaterTransitionWhenAlreadyUnderWater() {
        // Both previous and current frames below water → no new transition
        Vector3 pos = new Vector3(0f, 0.5f, 0f);
        Vector3 vel = new Vector3(0f, -1f, 0f); // previousY = 0.5 + 0.016 = 0.516, still < 1
        assertFalse(BallPhysics.checkWaterTransition(pos, vel, 1f));
    }

    // ── handleTrunkCollision ───────────────────────────────────────────────────

    @Test
    public void testTrunkCollisionAppliesToApproachingBall() {
        // dx,dz is the ball's offset FROM the trunk (outward normal = +X).
        // A ball approaching has velocity opposing that normal, i.e. in -X.
        Vector3 pos  = new Vector3(0f, 0f, 0f);
        Vector3 vel  = new Vector3(-5f, 0f, 0f); // moving -X, toward the trunk at +X side
        Vector3 spin = new Vector3(0f, 0f, 0f);
        boolean hit = BallPhysics.handleTrunkCollision(pos, vel, spin, 1f, 0f);
        assertTrue(hit, "Should detect a collision when ball moves toward the trunk");
    }

    @Test
    public void testTrunkCollisionIgnoresRecedingBall() {
        // Ball at +X side of trunk (dx=1), moving further +X (away from trunk).
        Vector3 pos  = new Vector3(0f, 0f, 0f);
        Vector3 vel  = new Vector3(5f, 0f, 0f);  // moving +X, away from trunk
        Vector3 spin = new Vector3(0f, 0f, 0f);
        boolean hit = BallPhysics.handleTrunkCollision(pos, vel, spin, 1f, 0f);
        assertFalse(hit, "Should not collide when ball is moving away");
    }

    @Test
    public void testTrunkCollisionReversesVelocityComponent() {
        // Ball approaching trunk in -X; after collision velocity should be in +X.
        Vector3 pos  = new Vector3(0f, 0f, 0f);
        Vector3 vel  = new Vector3(-5f, 0f, 0f);
        Vector3 spin = new Vector3(0f, 0f, 0f);
        BallPhysics.handleTrunkCollision(pos, vel, spin, 1f, 0f);
        assertTrue(vel.x > 0, "Velocity component toward trunk should be reversed after collision");
    }

    // ── getRollingFriction ─────────────────────────────────────────────────────

    @Test
    public void testRollingFrictionOpposesMotion() {
        Vector3 vel    = new Vector3(5f, 0f, 0f);
        Vector3 normal = new Vector3(0f, 1f, 0f);
        Vector3 fric   = BallPhysics.getRollingFriction(vel, normal, Terrain.TerrainType.FAIRWAY, -9.81f).cpy();
        assertTrue(fric.x < 0, "Rolling friction should oppose +X velocity");
    }

    @Test
    public void testRollingFrictionZeroForStationaryBall() {
        Vector3 vel    = new Vector3(0.001f, 0f, 0f); // below 0.01 threshold
        Vector3 normal = new Vector3(0f, 1f, 0f);
        Vector3 fric   = BallPhysics.getRollingFriction(vel, normal, Terrain.TerrainType.FAIRWAY, -9.81f).cpy();
        assertEquals(0f, fric.len(), DELTA);
    }

    @Test
    public void testRoughHasMoreFrictionThanFairway() {
        Vector3 vel    = new Vector3(5f, 0f, 0f);
        Vector3 normal = new Vector3(0f, 1f, 0f);
        Vector3 fairwayFric = BallPhysics.getRollingFriction(vel, normal, Terrain.TerrainType.FAIRWAY, -9.81f).cpy();
        Vector3 roughFric   = BallPhysics.getRollingFriction(vel, normal, Terrain.TerrainType.ROUGH,   -9.81f).cpy();
        assertTrue(roughFric.len() > fairwayFric.len(),
                "ROUGH should produce more friction than FAIRWAY");
    }

    @Test
    public void testSandHasMoreFrictionThanRough() {
        Vector3 vel    = new Vector3(5f, 0f, 0f);
        Vector3 normal = new Vector3(0f, 1f, 0f);
        Vector3 roughFric = BallPhysics.getRollingFriction(vel, normal, Terrain.TerrainType.ROUGH, -9.81f).cpy();
        Vector3 sandFric  = BallPhysics.getRollingFriction(vel, normal, Terrain.TerrainType.SAND,  -9.81f).cpy();
        assertTrue(sandFric.len() > roughFric.len(),
                "SAND should produce more friction than ROUGH");
    }

    // ── isWallCollision ────────────────────────────────────────────────────────

    @Test
    public void testFlatSurfaceIsNotWall() {
        Vector3 flatNormal = new Vector3(0f, 1f, 0f); // pointing straight up
        assertFalse(BallPhysics.isWallCollision(flatNormal, 60f));
    }

    @Test
    public void testVerticalSurfaceIsWall() {
        Vector3 wallNormal = new Vector3(1f, 0f, 0f); // pointing sideways
        assertTrue(BallPhysics.isWallCollision(wallNormal, 60f));
    }
}
