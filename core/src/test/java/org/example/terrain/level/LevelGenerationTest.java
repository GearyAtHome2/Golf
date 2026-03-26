package org.example.terrain.level;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class LevelGenerationTest {

    @Test
    public void test18HoleGenerationDiversity() {
        List<LevelData> course = LevelDataGenerator.generate18Holes(50);
        assertNotNull(course);
        assertEquals(18, course.size(), "Should generate exactly 18 holes");

        // Check for diversity: No two consecutive holes should have the same archetype
        for (int i = 0; i < course.size() - 1; i++) {
            assertNotEquals(course.get(i).getArchetype(), course.get(i + 1).getArchetype(),
                    "Consecutive holes " + (i + 1) + " and " + (i + 2) + " have the same archetype: " + course.get(i).getArchetype());
        }
    }

    @Test
    public void testFixedLevelData() {
        long seed = 123456789L;
        LevelData data1 = LevelDataGenerator.createFixedLevelData(seed);
        LevelData data2 = LevelDataGenerator.createFixedLevelData(seed);

        assertEquals(data1.getSeed(), data2.getSeed());
        assertEquals(data1.getArchetype(), data2.getArchetype());
        assertEquals(data1.getDistance(), data2.getDistance());
        assertEquals(data1.getTeeHeight(), data2.getTeeHeight(), 0.001f);
        assertEquals(data1.getGreenHeight(), data2.getGreenHeight(), 0.001f);
    }
}
