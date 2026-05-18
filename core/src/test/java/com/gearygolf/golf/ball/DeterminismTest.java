package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.Vector3;
import com.gearygolf.golf.multiplayer.DeterminismRecorder;
import com.gearygolf.golf.multiplayer.DeterminismRecorder.CompareResult;
import com.gearygolf.golf.multiplayer.DeterminismRecorder.Recording;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 1 determinism tests.  Three groups:
 *
 *  1. Ball.q() — quantization correctness
 *  2. BallPhysics repeatability — every static method is pure and repeatable
 *  3. DeterminismSimulator end-to-end — record → CSV round-trip → replay;
 *     proves that (a) the physics is bit-identical on two runs from the same
 *     inputs, and (b) the CSV serialisation/deserialisation is lossless.
 */
public class DeterminismTest {

    // ── 1. Ball.q() — quantization ────────────────────────────────────────────

    @Test
    public void quantize_zerosLowest5MantissaBits() {
        float v = 1.0f;
        int bits = Float.floatToRawIntBits(Ball.q(v));
        assertEquals(0, bits & 0x1F, "Lowest 5 bits must be zero after quantization");
    }

    @Test
    public void quantize_isIdempotent() {
        float v = 123.456f;
        assertEquals(Ball.q(v), Ball.q(Ball.q(v)), 0f, "q(q(v)) must equal q(v)");
    }

    @Test
    public void quantize_preservesSign() {
        assertTrue(Ball.q(-5.5f) < 0f);
        assertTrue(Ball.q(5.5f)  > 0f);
    }

    @Test
    public void quantize_preservesZero() {
        assertEquals(0f, Ball.q(0f), 0f);
    }

    @Test
    public void quantize_preservesSmallValues() {
        // Decaying spin of 0.0004 must survive with < 0.02 % relative error.
        float small = 0.0004f;
        float quantized = Ball.q(small);
        assertNotEquals(0f, quantized, "Small values must not be zeroed");
        float relErr = Math.abs(quantized - small) / small;
        assertTrue(relErr < 0.0002f, "Relative error was " + relErr);
    }

    @Test
    public void quantize_relativeErrorBoundedForLargeValues() {
        float large = 1234.5f;
        float quantized = Ball.q(large);
        float relErr = Math.abs(quantized - large) / large;
        assertTrue(relErr < 0.0002f, "Relative error was " + relErr);
    }

    // ── 2. BallPhysics repeatability ──────────────────────────────────────────

    @Test
    public void gravityForce_isRepeatable() {
        assertEquals(BallPhysics.getGravityForce(-9.81f).cpy(), BallPhysics.getGravityForce(-9.81f).cpy());
    }

    @Test
    public void windAtHeight_isRepeatable() {
        Vector3 base = new Vector3(3f, 0f, -2f);
        assertEquals(BallPhysics.getWindAtHeight(base, 12f).cpy(), BallPhysics.getWindAtHeight(base, 12f).cpy());
    }

    @Test
    public void airDrag_isRepeatable() {
        Vector3 vel = new Vector3(25f, -5f, 3f), wind = new Vector3(1f, 0f, -1f);
        assertEquals(BallPhysics.getAirDrag(vel, wind, 0.0048f).cpy(),
                     BallPhysics.getAirDrag(vel, wind, 0.0048f).cpy());
    }

    @Test
    public void magnusForce_isRepeatable() {
        Vector3 vel = new Vector3(30f, 10f, 0f), wind = new Vector3(2f, 0f, 0f), spin = new Vector3(0f, 200f, 0f);
        assertEquals(BallPhysics.getMagnusForce(vel, wind, spin, 5f, 5f).cpy(),
                     BallPhysics.getMagnusForce(vel, wind, spin, 5f, 5f).cpy());
    }

    @Test
    public void bounceWithSpin_isRepeatable() {
        Vector3 vel = new Vector3(10f, -8f, 2f), normal = new Vector3(0f, 1f, 0f), spin = new Vector3(0f, 0f, 50f);
        assertEquals(BallPhysics.calculateBounceWithSpin(vel.cpy(), normal, spin.cpy(), 0.35f, 0.4f, 0.3f).cpy(),
                     BallPhysics.calculateBounceWithSpin(vel.cpy(), normal, spin.cpy(), 0.35f, 0.4f, 0.3f).cpy());
    }

    @Test
    public void isWallCollision_matchesMathAcos() {
        float[] ys = {0.01f, 0.3f, 0.5f, 0.7f, 1.0f};
        for (float y : ys) {
            Vector3 n = new Vector3(0f, y, 0f).nor();
            boolean result   = BallPhysics.isWallCollision(n, 45f);
            boolean expected = (float)(Math.acos(n.y) * (180.0 / Math.PI)) > 45f;
            assertEquals(expected, result, "Mismatch for normal.y=" + y);
        }
    }

    // ── 3. End-to-end: record → CSV round-trip → replay ───────────────────────

    private static DeterminismSimulator.Inputs testInputs(float windX, float windZ) {
        return new DeterminismSimulator.Inputs(
            new Vector3(0f, 0.2f, 0f),            // tee-height start
            new Vector3(10f, 15f, -25f),           // medium driver launch
            new Vector3(0f, 300f, 0f),             // heavy topspin
            new Vector3(windX, 0f, windZ),
            180                                    // max frames (~3 s at 60 fps)
        );
    }

    @Test
    public void straightShot_replayIsBitIdentical() {
        DeterminismSimulator.Inputs inputs = testInputs(0f, 0f);
        Recording rec1 = DeterminismSimulator.run(inputs);
        Recording rec2 = DeterminismSimulator.run(inputs);

        assertFalse(rec1.steps.isEmpty(), "Recording must not be empty");

        CompareResult result = DeterminismRecorder.compare(rec1, rec2);
        assertTrue(result.identical, "Expected bit-identical replay:\n" + result.report);
        System.out.println("[straight shot] " + result.report);
    }

    @Test
    public void windyHighspinShot_replayIsBitIdentical() {
        // This is the highest-chaos scenario from the plan: strong wind + high spin.
        DeterminismSimulator.Inputs inputs = new DeterminismSimulator.Inputs(
            new Vector3(0f, 0.2f, 0f),
            new Vector3(20f, 20f, -30f),
            new Vector3(50f, 500f, -50f),          // extreme spin
            new Vector3(8f, 0f, -5f),              // crosswind
            300
        );
        Recording rec1 = DeterminismSimulator.run(inputs);
        Recording rec2 = DeterminismSimulator.run(inputs);

        CompareResult result = DeterminismRecorder.compare(rec1, rec2);
        assertTrue(result.identical, "Expected bit-identical replay:\n" + result.report);
        System.out.println("[windy high-spin] " + result.report);
    }

    @Test
    public void csvRoundTrip_preservesAllBits() {
        Recording original = DeterminismSimulator.run(testInputs(3f, -2f));
        assertFalse(original.steps.isEmpty());

        String csv   = original.toCsv();
        Recording parsed = Recording.fromCsv(csv);

        assertEquals(original.steps.size(), parsed.steps.size(), "Step count must survive CSV round-trip");
        for (int i = 0; i < original.steps.size(); i++) {
            assertTrue(original.steps.get(i).bitEquals(parsed.steps.get(i)),
                "Step " + i + " changed during CSV round-trip");
        }
    }

    @Test
    public void csvRoundTrip_thenReplayIsBitIdentical() {
        // Full pipeline: record → CSV → parse → compare against a fresh replay.
        DeterminismSimulator.Inputs inputs = testInputs(5f, -3f);
        Recording original = DeterminismSimulator.run(inputs);
        Recording parsed   = Recording.fromCsv(original.toCsv());
        Recording replay   = DeterminismSimulator.run(inputs);

        CompareResult parsedVsOriginal = DeterminismRecorder.compare(original, parsed);
        assertTrue(parsedVsOriginal.identical,
            "CSV round-trip broke bit equality:\n" + parsedVsOriginal.report);

        CompareResult parsedVsReplay = DeterminismRecorder.compare(parsed, replay);
        assertTrue(parsedVsReplay.identical,
            "Parsed CSV vs fresh replay not bit-identical:\n" + parsedVsReplay.report);

        System.out.println("[csv+replay] " + parsedVsReplay.report);
    }

    @Test
    public void compareResult_detectsInjectedDivergence() {
        // Verifies that DeterminismRecorder.compare() actually catches a difference.
        Recording a = DeterminismSimulator.run(testInputs(0f, 0f));
        Recording b = DeterminismSimulator.run(testInputs(0f, 0f));

        // Corrupt step 5 in b.
        if (b.steps.size() > 5) {
            DeterminismRecorder.Snapshot bad = b.steps.get(5);
            b.steps.set(5, new DeterminismRecorder.Snapshot(
                bad.step, bad.px + 1.0f, bad.py, bad.pz,
                bad.vx, bad.vy, bad.vz, bad.sx, bad.sy, bad.sz));
        }

        CompareResult result = DeterminismRecorder.compare(a, b);
        assertFalse(result.identical, "compare() must detect injected divergence");
        assertEquals(5, result.firstDivergentStep, "First divergent step must be 5");
        System.out.println("[divergence detection] " + result.report);
    }
}
