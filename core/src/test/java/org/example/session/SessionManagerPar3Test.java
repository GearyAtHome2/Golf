package org.example.session;

import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerPar3Test {

    @Test
    public void createPar3HoleAlwaysPar3() {
        long[] seeds = {12345L, 98367287L, 20260101L, 99999999L, -1L, Long.MAX_VALUE};
        for (long seed : seeds) {
            LevelData hole = LevelDataGenerator.createPar3Hole(seed);
            assertEquals(3, hole.getPar(), "createPar3Hole must always return par-3 for seed " + seed);
        }
    }

    @Test
    public void createPar3HoleIsDeterministic() {
        long seed = 12345678L;
        LevelData first  = LevelDataGenerator.createPar3Hole(seed);
        LevelData second = LevelDataGenerator.createPar3Hole(seed);
        assertEquals(first.getPar(),       second.getPar());
        assertEquals(first.getArchetype(), second.getArchetype());
        assertEquals(first.getDistance(),  second.getDistance());
    }

    @Test
    public void createPar3HoleProducesVariety() {
        // With enough seeds, more than one archetype should appear (fixed and variable)
        Set<LevelData.Archetype> seen = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            seen.add(LevelDataGenerator.createPar3Hole(seed).getArchetype());
        }
        assertTrue(seen.size() > 1,
                "Expected multiple par-3 archetypes across 500 seeds, got: " + seen);
    }

    @Test
    public void createPar3HoleVariableParArchetypeStillPar3() {
        // Even when a variable-par archetype (e.g. STANDARD_LINKS) is chosen, result must be par-3
        // Run enough seeds that we're very likely to hit STANDARD_LINKS at least once
        for (long seed = 0; seed < 500; seed++) {
            assertEquals(3, LevelDataGenerator.createPar3Hole(seed).getPar(),
                    "createPar3Hole must always return par-3 regardless of chosen archetype, seed=" + seed);
        }
    }

    @Test
    public void createPar3HoleAcrossSimulatedDates() {
        long[] timestamps = {20260101L, 20260215L, 20260401L, 20260701L, 20261231L};
        for (long ts : timestamps) {
            long seed = applySecretSauce(ts * 71L + 3L);
            LevelData hole = LevelDataGenerator.createPar3Hole(seed);
            assertEquals(3, hole.getPar(),
                    "DAILY_1 hole must be par-3 for timestamp " + ts);
        }
    }

    // Replicated from SessionManager so we can drive the test without instantiating it
    private static long applySecretSauce(long seed) {
        for (int i = 0; i < 10; i++) {
            seed = (seed * 13) + 8;
            if (String.valueOf(seed).length() > 9) seed /= 17;
        }
        return seed;
    }
}
