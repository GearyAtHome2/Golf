package org.example.session;

import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerPar3Test {

    @Test
    public void testFindPar3SeedReturnsParThreeHole() {
        long baseSeed = 98367287L; // known seed from save_daily_1.json
        long par3Seed = SessionManager.findPar3Seed(baseSeed);
        List<LevelData> layout = LevelDataGenerator.generate18Holes(par3Seed);
        assertEquals(3, layout.get(0).getPar(), "First hole must be par-3");
    }

    @Test
    public void testFindPar3SeedIsDeterministic() {
        long baseSeed = 12345678L;
        long first = SessionManager.findPar3Seed(baseSeed);
        long second = SessionManager.findPar3Seed(baseSeed);
        assertEquals(first, second, "findPar3Seed must return the same seed for the same input");
    }

    @Test
    public void testFindPar3SeedAcrossMultipleDates() {
        // Simulate the seed derivation used by startDaily1() for several dates
        long[] timestamps = {20260101L, 20260215L, 20260401L, 20260701L, 20261231L};
        for (long ts : timestamps) {
            long baseSeed = applySecretSauce(ts * 71L + 3L);
            long seed = SessionManager.findPar3Seed(baseSeed);
            List<LevelData> layout = LevelDataGenerator.generate18Holes(seed);
            assertEquals(3, layout.get(0).getPar(),
                "DAILY_1 first hole must be par-3 for timestamp " + ts);
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
