package org.example.hud;

import org.example.GameConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HUDStateTest {

    @Test
    public void testGameConfigCycle() {
        GameConfig config = new GameConfig();

        // Initial values (NONE, EASY)
        assertEquals(GameConfig.AnimSpeed.NONE, config.animSpeed);
        assertEquals(GameConfig.Difficulty.EASY, config.difficulty);
        assertTrue(config.particlesEnabled);

        // Cycle Animation
        config.cycleAnimation();
        assertEquals(GameConfig.AnimSpeed.SLOW, config.animSpeed);
        config.cycleAnimation();
        assertEquals(GameConfig.AnimSpeed.FAST, config.animSpeed);
        config.cycleAnimation();
        assertEquals(GameConfig.AnimSpeed.NONE, config.animSpeed);

        // Cycle Difficulty
        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.MEDIUM, config.difficulty);
        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.HARD, config.difficulty);
        config.cycleDifficulty();
        assertEquals(GameConfig.Difficulty.EASY, config.difficulty);

        // Toggle Particles
        config.particlesEnabled = !config.particlesEnabled;
        assertFalse(config.particlesEnabled);
        config.particlesEnabled = !config.particlesEnabled;
        assertTrue(config.particlesEnabled);
    }
}
