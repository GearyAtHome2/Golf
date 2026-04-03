package org.example.hud.minigame;

import org.example.GdxTestSetup;
import org.example.ball.MinigameResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.example.ball.MinigameResult.Rating.*;
import static org.junit.jupiter.api.Assertions.*;

public class MinigameEngineTest {

    private MinigameEngine engine;

    // With default barWidthMult=1.0:
    //   greenHalfW = (0.25 * 1.0) / 2 = 0.125
    //   GOOD zone:  [0.375, 0.625]  (centre 0.5 ± 0.125)
    //   POOR zone:  distFromEdge <= 0.0625  → [0.625, 0.6875] or [0.3125, 0.375]
    //   WANK zone:  distFromEdge <= 0.125   → [0.6875, 0.75]  or [0.25, 0.3125]
    //   SHIT zone:  beyond 0.75 or below 0.25
    //
    // After reset(1.6+), sweet-spot half-widths (all centred at 0.5 initially):
    //   GREAT:       0.125 * 0.4         = 0.050  → [0.450, 0.550]
    //   SUPER:       0.050 * 0.4         = 0.020  → [0.480, 0.520]
    //   PERFECTION:  0.020 * 0.4         = 0.008  → [0.492, 0.508]

    @BeforeAll
    public static void initGdx() {
        GdxTestSetup.init();
    }

    @BeforeEach
    public void setUp() {
        engine = new MinigameEngine();
    }

    // ── reset() zone population ────────────────────────────────────────────────

    @Test
    public void testResetBelowThreshold_NoSweetSpots() {
        engine.reset(0.5f);
        assertEquals(0, engine.sweetSpots.size);
    }

    @Test
    public void testResetAt1_0_AddsGreatZone() {
        engine.reset(1.0f);
        assertEquals(1, engine.sweetSpots.size);
        assertEquals(GREAT, engine.sweetSpots.get(0).label);
    }

    @Test
    public void testResetAt1_3_AddsSuperZone() {
        engine.reset(1.3f);
        assertEquals(2, engine.sweetSpots.size);
        assertEquals(GREAT, engine.sweetSpots.get(0).label);
        assertEquals(SUPER, engine.sweetSpots.get(1).label);
    }

    @Test
    public void testResetAt1_6_AddsAllThreeZones() {
        engine.reset(1.6f);
        assertEquals(3, engine.sweetSpots.size);
        assertEquals(GREAT, engine.sweetSpots.get(0).label);
        assertEquals(SUPER, engine.sweetSpots.get(1).label);
        assertEquals(PERFECTION, engine.sweetSpots.get(2).label);
    }

    @Test
    public void testResetResetsNeedleToZero() {
        engine.needlePos = 0.75f;
        engine.reset(0f);
        assertEquals(0f, engine.needlePos, 0.001f);
    }

    @Test
    public void testResetResetsGreenCenterToHalf() {
        engine.greenCenter = 0.8f;
        engine.reset(0f);
        assertEquals(0.5f, engine.greenCenter, 0.001f);
    }

    // ── calculateResult — GOOD zone ────────────────────────────────────────────

    @Test
    public void testDeadCentreIsGood() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.5f);
        assertEquals(GOOD, r.rating);
    }

    @Test
    public void testDeadCentreGoodHasZeroAccuracy() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.5f);
        assertEquals(0f, r.accuracy, 0.001f);
    }

    @Test
    public void testDeadCentreGoodPowerModIs1() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.5f);
        assertEquals(1.0f, r.powerMod, 0.001f);
    }

    @Test
    public void testInsideGoodZoneLeftEdge() {
        engine.reset(0f);
        assertEquals(GOOD, engine.calculateResult(0.376f).rating);
    }

    @Test
    public void testInsideGoodZoneRightEdge() {
        engine.reset(0f);
        assertEquals(GOOD, engine.calculateResult(0.624f).rating);
    }

    // ── calculateResult — penalty zones ───────────────────────────────────────

    @Test
    public void testJustOutsideGoodZoneIsPoor() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.65f); // distFromEdge = 0.025 ≤ 0.0625
        assertEquals(POOR, r.rating);
        assertEquals(0.95f, r.powerMod, 0.001f);
    }

    @Test
    public void testPoorZoneOnLeftSide() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.35f); // symmetric, left side
        assertEquals(POOR, r.rating);
        assertEquals(-0.18f, r.accuracy, 0.001f);
    }

    @Test
    public void testPoorZoneAccuracySign() {
        engine.reset(0f);
        float rightAccuracy = engine.calculateResult(0.65f).accuracy;
        float leftAccuracy  = engine.calculateResult(0.35f).accuracy;
        assertTrue(rightAccuracy > 0, "Right-side POOR should have positive accuracy");
        assertTrue(leftAccuracy  < 0, "Left-side POOR should have negative accuracy");
    }

    @Test
    public void testWankZone() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.7f); // distFromEdge = 0.075 > 0.0625, ≤ 0.125
        assertEquals(WANK, r.rating);
        assertEquals(0.85f, r.powerMod, 0.001f);
        assertEquals(0.40f, r.accuracy, 0.001f);
    }

    @Test
    public void testWankZoneLeftSide() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.3f);
        assertEquals(WANK, r.rating);
        assertEquals(-0.40f, r.accuracy, 0.001f);
    }

    @Test
    public void testShitZone() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.95f);
        assertEquals(SHIT, r.rating);
        assertTrue(r.powerMod <= 0.80f);
        assertTrue(r.powerMod >= 0.35f);
    }

    @Test
    public void testShitZoneLowEnd() {
        engine.reset(0f);
        MinigameResult r = engine.calculateResult(0.05f);
        assertEquals(SHIT, r.rating);
    }

    // ── calculateResult — sweet spots ─────────────────────────────────────────

    @Test
    public void testDeadCentreIsGreatWithOnlyGreatZone() {
        engine.reset(1.0f); // only GREAT zone added
        MinigameResult r = engine.calculateResult(0.5f);
        assertEquals(GREAT, r.rating);
        assertEquals(1.10f, r.powerMod, 0.001f);
    }

    @Test
    public void testDeadCentreIsSuperWithTwoZones() {
        engine.reset(1.3f); // GREAT + SUPER
        MinigameResult r = engine.calculateResult(0.5f);
        assertEquals(SUPER, r.rating);
        assertEquals(1.25f, r.powerMod, 0.001f);
    }

    @Test
    public void testDeadCentreIsPerfectionWithAllZones() {
        engine.reset(1.6f); // GREAT + SUPER + PERFECTION
        MinigameResult r = engine.calculateResult(0.5f);
        assertEquals(PERFECTION, r.rating);
        assertEquals(1.50f, r.powerMod, 0.001f);
    }

    @Test
    public void testNeedleInSuperButNotPerfection() {
        engine.reset(1.6f);
        // 0.485 is inside SUPER [0.480, 0.520] but outside PERFECTION [0.492, 0.508]
        MinigameResult r = engine.calculateResult(0.485f);
        assertEquals(SUPER, r.rating);
    }

    @Test
    public void testNeedleInGreatButNotSuper() {
        engine.reset(1.6f);
        // 0.46 is inside GREAT [0.450, 0.550] but outside SUPER [0.480, 0.520]
        MinigameResult r = engine.calculateResult(0.46f);
        assertEquals(GREAT, r.rating);
    }

    @Test
    public void testNeedleOutsideAllSweetSpotsButInsideGreen() {
        engine.reset(1.6f);
        // 0.40 is inside GOOD [0.375, 0.625] but outside GREAT [0.450, 0.550]
        MinigameResult r = engine.calculateResult(0.40f);
        assertEquals(GOOD, r.rating);
    }

    // ── Power mod ordering ─────────────────────────────────────────────────────

    @Test
    public void testPowerModOrderingAcrossRatings() {
        engine.reset(1.6f);
        float perfectionPower = engine.calculateResult(0.5f).powerMod;     // PERFECTION
        float superPower      = engine.calculateResult(0.485f).powerMod;   // SUPER
        float greatPower      = engine.calculateResult(0.46f).powerMod;    // GREAT
        float goodPower       = engine.calculateResult(0.40f).powerMod;    // GOOD
        float poorPower       = engine.calculateResult(0.65f).powerMod;    // POOR

        assertTrue(perfectionPower > superPower,  "PERFECTION > SUPER");
        assertTrue(superPower      > greatPower,  "SUPER > GREAT");
        assertTrue(greatPower      > goodPower,   "GREAT > GOOD");
        assertTrue(goodPower       > poorPower,   "GOOD > POOR");
    }
}
