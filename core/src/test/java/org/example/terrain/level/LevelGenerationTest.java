package org.example.terrain.level;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class LevelGenerationTest {

    // ── 18-hole generation ─────────────────────────────────────────────────────

    @Test
    public void test18HoleGenerationProducesExactly18Holes() {
        List<LevelData> course = LevelDataGenerator.generate18Holes(50);
        assertNotNull(course);
        assertEquals(18, course.size());
    }

    @Test
    public void testNoDuplicateConsecutiveArchetypes() {
        List<LevelData> course = LevelDataGenerator.generate18Holes(50);
        for (int i = 0; i < course.size() - 1; i++) {
            assertNotEquals(course.get(i).getArchetype(), course.get(i + 1).getArchetype(),
                    "Consecutive holes " + (i + 1) + " and " + (i + 2) + " share archetype: "
                            + course.get(i).getArchetype());
        }
    }

    @Test
    public void testSameSeedProducesIdentical18HoleCourse() {
        long seed = 999999L;
        List<LevelData> course1 = LevelDataGenerator.generate18Holes(seed);
        List<LevelData> course2 = LevelDataGenerator.generate18Holes(seed);

        assertEquals(course1.size(), course2.size());
        for (int i = 0; i < course1.size(); i++) {
            assertEquals(course1.get(i).getArchetype(), course2.get(i).getArchetype(),
                    "Archetype mismatch at hole " + (i + 1));
            assertEquals(course1.get(i).getSeed(), course2.get(i).getSeed(),
                    "Seed mismatch at hole " + (i + 1));
        }
    }

    @Test
    public void testDifferentSeedProducesDifferentCourse() {
        List<LevelData> course1 = LevelDataGenerator.generate18Holes(1L);
        List<LevelData> course2 = LevelDataGenerator.generate18Holes(2L);

        // Seeds are different, so at least one hole's archetype or seed should differ
        boolean anyDifference = false;
        for (int i = 0; i < course1.size(); i++) {
            if (course1.get(i).getArchetype() != course2.get(i).getArchetype()
                    || course1.get(i).getSeed() != course2.get(i).getSeed()) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference, "Two different seeds should produce different courses");
    }

    @Test
    public void testAllHolesHaveValidParValues() {
        List<LevelData> course = LevelDataGenerator.generate18Holes(42L);
        for (int i = 0; i < course.size(); i++) {
            int par = course.get(i).getPar();
            assertTrue(par >= 3 && par <= 5,
                    "Hole " + (i + 1) + " has invalid par: " + par);
        }
    }

    @Test
    public void testAllHolesHavePositiveDistance() {
        List<LevelData> course = LevelDataGenerator.generate18Holes(12345L);
        for (int i = 0; i < course.size(); i++) {
            assertTrue(course.get(i).getDistance() > 0,
                    "Hole " + (i + 1) + " has non-positive distance");
        }
    }

    @Test
    public void testCourseContainsMultipleArchetypes() {
        List<LevelData> course = LevelDataGenerator.generate18Holes(77L);
        Set<LevelData.Archetype> archetypes = new HashSet<>();
        for (LevelData hole : course) archetypes.add(hole.getArchetype());
        assertTrue(archetypes.size() >= 10,
                "Expected at least 10 distinct archetypes in an 18-hole course, got " + archetypes.size());
    }

    // ── Fixed level data ───────────────────────────────────────────────────────

    @Test
    public void testFixedLevelDataIsReproducible() {
        long seed = 123456789L;
        LevelData d1 = LevelDataGenerator.createFixedLevelData(seed);
        LevelData d2 = LevelDataGenerator.createFixedLevelData(seed);

        assertEquals(d1.getSeed(), d2.getSeed());
        assertEquals(d1.getArchetype(), d2.getArchetype());
        assertEquals(d1.getDistance(), d2.getDistance());
        assertEquals(d1.getTeeHeight(), d2.getTeeHeight(), 0.001f);
        assertEquals(d1.getGreenHeight(), d2.getGreenHeight(), 0.001f);
        assertEquals(d1.getPar(), d2.getPar());
    }

    @Test
    public void testForcedArchetypeIsRespected() {
        for (LevelData.Archetype arch : LevelData.Archetype.values()) {
            LevelData data = LevelDataGenerator.createFixedLevelData(42L, arch);
            assertEquals(arch, data.getArchetype(),
                    "Forced archetype " + arch + " was not respected");
        }
    }

    @Test
    public void testAllArchetypesGenerateWithoutError() {
        for (LevelData.Archetype arch : LevelData.Archetype.values()) {
            assertDoesNotThrow(() -> LevelDataGenerator.createFixedLevelData(100L, arch),
                    "Archetype " + arch + " threw an exception");
        }
    }

    @Test
    public void testWaterLevelBelowMinOfTeeAndGreen() {
        // Water should be below the lower of tee/green height so players don't start in water
        for (LevelData.Archetype arch : LevelData.Archetype.values()) {
            LevelData data = LevelDataGenerator.createFixedLevelData(55L, arch);
            float minHeight = Math.min(data.getTeeHeight(), data.getGreenHeight());
            assertTrue(data.getWaterLevel() < minHeight,
                    arch + ": waterLevel=" + data.getWaterLevel()
                            + " should be below min(tee,green)=" + minHeight);
        }
    }

    @Test
    public void testWindIsNotNull() {
        LevelData data = LevelDataGenerator.createFixedLevelData(1L);
        assertNotNull(data.getWind(), "Wind vector should not be null");
    }

    @Test
    public void testTerrainAlgorithmIsNotNull() {
        LevelData data = LevelDataGenerator.createFixedLevelData(1L);
        assertNotNull(data.getTerrainAlgorithm());
    }

    @Test
    public void testTreeSchemeIsNotNull() {
        for (LevelData.Archetype arch : LevelData.Archetype.values()) {
            LevelData data = LevelDataGenerator.createFixedLevelData(1L, arch);
            assertNotNull(data.getTreeScheme(), arch + " has null tree scheme");
        }
    }

    @Test
    public void testAllArchetypesRepresentedOverMultipleCourses() {
        Set<LevelData.Archetype> seen = EnumSet.noneOf(LevelData.Archetype.class);
        for (long seed = 0; seed < 20; seed++) {
            for (LevelData hole : LevelDataGenerator.generate18Holes(seed)) {
                seen.add(hole.getArchetype());
            }
        }
        assertEquals(LevelData.Archetype.values().length, seen.size(),
                "Not all archetypes appeared across 20 seeded courses");
    }
}
