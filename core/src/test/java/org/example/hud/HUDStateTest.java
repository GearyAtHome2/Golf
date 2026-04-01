package org.example.hud;

import org.example.GameConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HUDStateTest {

    // ── AnimSpeed cycle ────────────────────────────────────────────────────────

    @Test
    public void testAnimSpeedCyclesThrough3Values() {
        GameConfig config = new GameConfig();
        assertEquals(GameConfig.AnimSpeed.NONE, config.animSpeed);

        config.cycleAnimation();
        assertEquals(GameConfig.AnimSpeed.SLOW, config.animSpeed);

        config.cycleAnimation();
        assertEquals(GameConfig.AnimSpeed.FAST, config.animSpeed);

        config.cycleAnimation(); // wraps back
        assertEquals(GameConfig.AnimSpeed.NONE, config.animSpeed);
    }

    @Test
    public void testAnimSpeedMultipliersAreOrdered() {
        // NONE < SLOW < FAST
        assertTrue(GameConfig.AnimSpeed.NONE.mult < GameConfig.AnimSpeed.SLOW.mult);
        assertTrue(GameConfig.AnimSpeed.SLOW.mult < GameConfig.AnimSpeed.FAST.mult);
    }

    // ── Difficulty cycle ───────────────────────────────────────────────────────

    @Test
    public void testDifficultyCyclesThrough5Values() {
        GameConfig config = new GameConfig();
        assertEquals(GameConfig.Difficulty.NOVICE, config.difficulty);

        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.INTERMEDIATE, config.difficulty);

        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.ADVANCED, config.difficulty);

        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.PRO, config.difficulty);

        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.TOUR_PRO, config.difficulty);

        config.cycleDifficulty(); // wraps back
        assertEquals(GameConfig.Difficulty.NOVICE, config.difficulty);
    }

    @Test
    public void testDifficultyNeedleSpeedMultipliersAreOrdered() {
        // Harder difficulty → faster needle
        float prev = 0f;
        for (GameConfig.Difficulty d : GameConfig.Difficulty.values()) {
            assertTrue(d.needleSpeedMult > prev,
                    d.name() + " needleSpeedMult (" + d.needleSpeedMult + ") should be > " + prev);
            prev = d.needleSpeedMult;
        }
    }

    @Test
    public void testSetDifficultyDirectly() {
        GameConfig config = new GameConfig();
        config.setDifficulty(GameConfig.Difficulty.TOUR_PRO);
        assertEquals(GameConfig.Difficulty.TOUR_PRO, config.difficulty);
    }

    @Test
    public void testDifficultyGetNames() {
        String[] names = GameConfig.Difficulty.getNames();
        assertEquals(GameConfig.Difficulty.values().length, names.length);
        assertEquals("NOVICE", names[0]);
        assertEquals("TOUR_PRO", names[names.length - 1]);
    }

    // ── CameraType cycle ───────────────────────────────────────────────────────

    @Test
    public void testCameraTypeCycles() {
        GameConfig config = new GameConfig();
        assertEquals(GameConfig.CameraType.FREE, config.cameraType);

        config.cycleCamera();
        assertEquals(GameConfig.CameraType.DRAG, config.cameraType);

        config.cycleCamera();
        assertEquals(GameConfig.CameraType.FIXED, config.cameraType);

        config.cycleCamera(); // wraps back
        assertEquals(GameConfig.CameraType.FREE, config.cameraType);
    }

    // ── Game speed ─────────────────────────────────────────────────────────────

    @Test
    public void testDefaultGameSpeedIs1() {
        GameConfig config = new GameConfig();
        assertEquals(1.0f, config.getGameSpeed(), 0.001f);
    }

    @Test
    public void testGameSpeedIncreasesAndDecreases() {
        GameConfig config = new GameConfig();
        config.adjustGameSpeed(true);
        assertEquals(2.0f, config.getGameSpeed(), 0.001f);

        config.adjustGameSpeed(false);
        assertEquals(1.0f, config.getGameSpeed(), 0.001f);

        config.adjustGameSpeed(false);
        assertEquals(0.5f, config.getGameSpeed(), 0.001f);
    }

    @Test
    public void testGameSpeedClampsAtMinimum() {
        GameConfig config = new GameConfig();
        for (int i = 0; i < 20; i++) config.adjustGameSpeed(false);
        assertEquals(0.25f, config.getGameSpeed(), 0.001f);
    }

    @Test
    public void testGameSpeedClampsAtMaximum() {
        GameConfig config = new GameConfig();
        for (int i = 0; i < 20; i++) config.adjustGameSpeed(true);
        assertEquals(5.0f, config.getGameSpeed(), 0.001f);
    }

    // ── CameraConfig ───────────────────────────────────────────────────────────

    @Test
    public void testCameraConfigDefaultsAreNotInverted() {
        GameConfig config = new GameConfig();
        assertFalse(config.cameraSettings.invertX);
        assertFalse(config.cameraSettings.invertY);
    }

    @Test
    public void testCameraConfigGetXMultNotInverted() {
        GameConfig config = new GameConfig();
        config.cameraSettings.invertX = false;
        assertEquals(-1f, config.cameraSettings.getXMult(), 0.001f);
    }

    @Test
    public void testCameraConfigGetXMultInverted() {
        GameConfig config = new GameConfig();
        config.cameraSettings.invertX = true;
        assertEquals(1f, config.cameraSettings.getXMult(), 0.001f);
    }

    @Test
    public void testCameraConfigGetYMultNotInverted() {
        GameConfig config = new GameConfig();
        config.cameraSettings.invertY = false;
        assertEquals(-1f, config.cameraSettings.getYMult(), 0.001f);
    }

    @Test
    public void testCameraConfigGetYMultInverted() {
        GameConfig config = new GameConfig();
        config.cameraSettings.invertY = true;
        assertEquals(1f, config.cameraSettings.getYMult(), 0.001f);
    }

    // ── Particles ──────────────────────────────────────────────────────────────

    @Test
    public void testParticlesEnabledByDefault() {
        GameConfig config = new GameConfig();
        assertTrue(config.particlesEnabled);
    }
}
