package org.example.ball;

import org.example.GdxTestSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinigameResultTest {

    @BeforeAll
    public static void initGdx() {
        // Rating.getRandomPhrase() uses MathUtils.random which needs no Gdx context,
        // but initialising anyway for safety.
        GdxTestSetup.init();
    }

    // ── Enum completeness ──────────────────────────────────────────────────────

    @Test
    public void testAllRatingsExist() {
        MinigameResult.Rating[] ratings = MinigameResult.Rating.values();
        assertEquals(7, ratings.length, "Expected exactly 7 ratings");
    }

    @Test
    public void testRatingOrder() {
        MinigameResult.Rating[] r = MinigameResult.Rating.values();
        assertEquals(MinigameResult.Rating.PERFECTION, r[0]);
        assertEquals(MinigameResult.Rating.SUPER,      r[1]);
        assertEquals(MinigameResult.Rating.GREAT,      r[2]);
        assertEquals(MinigameResult.Rating.GOOD,       r[3]);
        assertEquals(MinigameResult.Rating.POOR,       r[4]);
        assertEquals(MinigameResult.Rating.TERRIBLE,       r[5]);
        assertEquals(MinigameResult.Rating.ABYSMAL,       r[6]);
    }

    // ── Phrases and weights ────────────────────────────────────────────────────

    @Test
    public void testAllRatingsHaveNonEmptyPhrases() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            assertNotNull(r.getPhrases(), r + " has null phrases array");
            assertTrue(r.getPhrases().length > 0, r + " has no phrases");
        }
    }

    @Test
    public void testAllPhrasesAreNonNull() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            for (String phrase : r.getPhrases()) {
                assertNotNull(phrase, r + " contains a null phrase");
                assertFalse(phrase.isEmpty(), r + " contains an empty phrase");
            }
        }
    }

    @Test
    public void testWeightsLengthMatchesPhraseLength() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            assertEquals(r.getPhrases().length, r.getWeights().length,
                    r + ": phrase count (" + r.getPhrases().length
                            + ") != weight count (" + r.getWeights().length + ")");
        }
    }

    @Test
    public void testAllWeightsArePositive() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            for (float w : r.getWeights()) {
                assertTrue(w > 0f, r + " has a non-positive weight: " + w);
            }
        }
    }

    @Test
    public void testTotalWeightMatchesSumOfWeights() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            float sum = 0f;
            for (float w : r.getWeights()) sum += w;
            assertEquals(sum, r.getTotalWeight(), 0.001f,
                    r + " totalWeight does not match sum of individual weights");
        }
    }

    // ── Colours ────────────────────────────────────────────────────────────────

    @Test
    public void testAllRatingsHaveColor() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            assertNotNull(r.getColor(), r + " has null color");
        }
    }

    // ── getRandomPhrase ────────────────────────────────────────────────────────

    @Test
    public void testGetRandomPhraseNeverReturnsNull() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            for (int i = 0; i < 50; i++) {
                assertNotNull(r.getRandomPhrase(), r + " returned null phrase on iteration " + i);
            }
        }
    }

    @Test
    public void testGetRandomPhraseIsAlwaysInPhraseList() {
        for (MinigameResult.Rating r : MinigameResult.Rating.values()) {
            java.util.Set<String> validPhrases = new java.util.HashSet<>(java.util.Arrays.asList(r.getPhrases()));
            for (int i = 0; i < 100; i++) {
                String phrase = r.getRandomPhrase();
                assertTrue(validPhrases.contains(phrase),
                        r + " returned unexpected phrase: \"" + phrase + "\"");
            }
        }
    }

    // ── MinigameResult bean ────────────────────────────────────────────────────

    @Test
    public void testMinigameResultSettersAndGetters() {
        MinigameResult result = new MinigameResult();
        result.setPowerMod(1.25f);
        result.setAccuracy(0.05f);
        result.setRating(MinigameResult.Rating.GREAT);

        assertEquals(1.25f, result.getPowerMod(), 0.001f);
        assertEquals(0.05f, result.getAccuracy(), 0.001f);
        assertEquals(MinigameResult.Rating.GREAT, result.getRating());
    }

    @Test
    public void testPublicFieldsMatchGetters() {
        MinigameResult result = new MinigameResult();
        result.powerMod = 0.90f;
        result.accuracy = -0.20f;
        result.rating   = MinigameResult.Rating.POOR;

        assertEquals(result.powerMod, result.getPowerMod(), 0.001f);
        assertEquals(result.accuracy, result.getAccuracy(), 0.001f);
        assertEquals(result.rating,   result.getRating());
    }
}
