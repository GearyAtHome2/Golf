package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.Vector3;
import com.gearygolf.golf.multiplayer.DeterminismRecorder;

/**
 * Pure-Java air-physics simulator for determinism testing.
 *
 * Runs the same gravity + drag + Magnus loop that Ball uses for airborne flight,
 * with quantization applied after each sub-step, but with no Terrain, no GL,
 * no LibGDX application context.  Suitable for JUnit tests.
 *
 * The flat-ground assumption (ball stops when y ≤ 0) is intentional: air flight
 * is the highest-chaos scenario (spin, Magnus, drag all interact), so determinism
 * here implies determinism across the full trajectory.
 */
public class DeterminismSimulator {

    // Mirror Ball's physics constants exactly.
    private static final float GRAVITY       = -9.81f;
    private static final float AIR_DRAG_COEFF =  0.0048f;
    private static final float LIFT_COEFF    =  5.0f;
    private static final float SIDE_COEFF    =  5.0f;
    private static final int   SUB_STEPS     =  4;
    private static final float FRAME_DELTA   =  1f / 60f;  // 60 fps fixed step

    public static class Inputs {
        public final Vector3 startPos;
        public final Vector3 startVel;
        public final Vector3 startSpin;
        public final Vector3 wind;
        public final int     maxFrames;

        public Inputs(Vector3 startPos, Vector3 startVel, Vector3 startSpin,
                      Vector3 wind, int maxFrames) {
            this.startPos  = startPos.cpy();
            this.startVel  = startVel.cpy();
            this.startSpin = startSpin.cpy();
            this.wind      = wind.cpy();
            this.maxFrames = maxFrames;
        }
    }

    /**
     * Runs the simulation and returns a Recording.
     * Step indices are global sub-step counts (frame * SUB_STEPS + sub).
     */
    public static DeterminismRecorder.Recording run(Inputs in) {
        DeterminismRecorder rec = new DeterminismRecorder();

        Vector3 pos  = in.startPos.cpy();
        Vector3 vel  = in.startVel.cpy();
        Vector3 spin = in.startSpin.cpy();

        float subDelta = FRAME_DELTA / SUB_STEPS;
        int   step     = 0;

        outer:
        for (int frame = 0; frame < in.maxFrames; frame++) {
            for (int sub = 0; sub < SUB_STEPS; sub++) {
                // Air forces
                Vector3 windAtH = BallPhysics.getWindAtHeight(in.wind, pos.y).cpy();
                Vector3 gravity = BallPhysics.getGravityForce(GRAVITY).cpy();
                Vector3 drag    = BallPhysics.getAirDrag(vel, windAtH, AIR_DRAG_COEFF).cpy();
                Vector3 magnus  = BallPhysics.getMagnusForce(vel, windAtH, spin, LIFT_COEFF, SIDE_COEFF).cpy();

                vel.x += (gravity.x + drag.x + magnus.x) * subDelta;
                vel.y += (gravity.y + drag.y + magnus.y) * subDelta;
                vel.z += (gravity.z + drag.z + magnus.z) * subDelta;

                pos.x += vel.x * subDelta;
                pos.y += vel.y * subDelta;
                pos.z += vel.z * subDelta;

                // Spin decay (mirrors Ball.handleEnvironmentPhysics)
                float speed = vel.len();
                if (speed > 0.1f) {
                    float decay = Math.max(0f, 1.0f - ((0.05f + (speed * speed * 0.0002f)) * subDelta));
                    spin.scl(decay);
                }

                // Quantize — same mask Ball uses
                pos.set(Ball.q(pos.x), Ball.q(pos.y), Ball.q(pos.z));
                vel.set(Ball.q(vel.x), Ball.q(vel.y), Ball.q(vel.z));
                spin.set(Ball.q(spin.x), Ball.q(spin.y), Ball.q(spin.z));

                rec.onStep(step++,
                    pos.x, pos.y, pos.z,
                    vel.x, vel.y, vel.z,
                    spin.x, spin.y, spin.z);

                // Stop when ball hits flat ground
                if (pos.y <= 0f) break outer;
            }
        }

        return rec.finish();
    }
}
