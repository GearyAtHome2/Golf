package org.example.scoreBoard;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Firestore array serialisation helpers in HighscoreService
 * and the HighscoreEntry.hasScorecard() helper.
 */
public class HighscoreScorecardTest {

    // -----------------------------------------------------------------------
    // toFirestoreIntArray
    // -----------------------------------------------------------------------

    @Test
    public void toFirestoreIntArray_singleElement() {
        String json = HighscoreService.toFirestoreIntArray(new int[]{5});
        assertEquals("{\"arrayValue\":{\"values\":[{\"integerValue\":5}]}}", json);
    }

    @Test
    public void toFirestoreIntArray_multipleElements() {
        String json = HighscoreService.toFirestoreIntArray(new int[]{3, 4, 5, 3, 4});
        assertEquals(
            "{\"arrayValue\":{\"values\":[" +
            "{\"integerValue\":3}," +
            "{\"integerValue\":4}," +
            "{\"integerValue\":5}," +
            "{\"integerValue\":3}," +
            "{\"integerValue\":4}]}}",
            json);
    }

    @Test
    public void toFirestoreIntArray_emptyArray() {
        String json = HighscoreService.toFirestoreIntArray(new int[]{});
        assertEquals("{\"arrayValue\":{\"values\":[]}}", json);
    }

    // -----------------------------------------------------------------------
    // fromFirestoreIntArray
    // -----------------------------------------------------------------------

    @Test
    public void fromFirestoreIntArray_singleElement() {
        String raw = "{\"arrayValue\":{\"values\":[{\"integerValue\":7}]}}";
        JsonValue field = new JsonReader().parse(raw);
        int[] result = HighscoreService.fromFirestoreIntArray(field);
        assertNotNull(result);
        assertArrayEquals(new int[]{7}, result);
    }

    @Test
    public void fromFirestoreIntArray_multipleElements() {
        String raw = "{\"arrayValue\":{\"values\":[" +
            "{\"integerValue\":3},{\"integerValue\":4},{\"integerValue\":5}]}}";
        JsonValue field = new JsonReader().parse(raw);
        int[] result = HighscoreService.fromFirestoreIntArray(field);
        assertNotNull(result);
        assertArrayEquals(new int[]{3, 4, 5}, result);
    }

    @Test
    public void fromFirestoreIntArray_emptyValues_returnsNull() {
        String raw = "{\"arrayValue\":{\"values\":[]}}";
        JsonValue field = new JsonReader().parse(raw);
        int[] result = HighscoreService.fromFirestoreIntArray(field);
        assertNull(result);
    }

    @Test
    public void fromFirestoreIntArray_missingValues_returnsNull() {
        // Firestore occasionally omits the values key when array is empty
        String raw = "{\"arrayValue\":{}}";
        JsonValue field = new JsonReader().parse(raw);
        int[] result = HighscoreService.fromFirestoreIntArray(field);
        assertNull(result);
    }

    // -----------------------------------------------------------------------
    // Round-trip
    // -----------------------------------------------------------------------

    @Test
    public void roundTrip_9holes() {
        int[] pars   = {3, 4, 5, 3, 4, 4, 3, 5, 4};
        int[] scores = {3, 4, 4, 4, 5, 3, 3, 5, 4};

        String parsJson   = HighscoreService.toFirestoreIntArray(pars);
        String scoresJson = HighscoreService.toFirestoreIntArray(scores);

        assertArrayEquals(pars,   HighscoreService.fromFirestoreIntArray(new JsonReader().parse(parsJson)));
        assertArrayEquals(scores, HighscoreService.fromFirestoreIntArray(new JsonReader().parse(scoresJson)));
    }

    @Test
    public void roundTrip_18holes() {
        int[] pars = new int[18];
        int[] scores = new int[18];
        for (int i = 0; i < 18; i++) { pars[i] = 3 + (i % 3); scores[i] = pars[i] + (i % 3 - 1); }

        assertArrayEquals(pars,   HighscoreService.fromFirestoreIntArray(new JsonReader().parse(HighscoreService.toFirestoreIntArray(pars))));
        assertArrayEquals(scores, HighscoreService.fromFirestoreIntArray(new JsonReader().parse(HighscoreService.toFirestoreIntArray(scores))));
    }

    // -----------------------------------------------------------------------
    // HighscoreEntry.hasScorecard()
    // -----------------------------------------------------------------------

    @Test
    public void hasScorecard_withArrays_returnsTrue() {
        HighscoreService.HighscoreEntry e = new HighscoreService.HighscoreEntry(
            "Player", 36, "NOVICE", "2026-04-02T10:00:00Z", 0f,
            new int[]{3, 4, 5}, new int[]{3, 4, 5});
        assertTrue(e.hasScorecard());
    }

    @Test
    public void hasScorecard_nullPars_returnsFalse() {
        HighscoreService.HighscoreEntry e = new HighscoreService.HighscoreEntry(
            "Player", 36, "NOVICE", "2026-04-02T10:00:00Z", 0f, null, new int[]{3, 4, 5});
        assertFalse(e.hasScorecard());
    }

    @Test
    public void hasScorecard_nullScores_returnsFalse() {
        HighscoreService.HighscoreEntry e = new HighscoreService.HighscoreEntry(
            "Player", 36, "NOVICE", "2026-04-02T10:00:00Z", 0f, new int[]{3, 4, 5}, null);
        assertFalse(e.hasScorecard());
    }

    @Test
    public void hasScorecard_emptyArrays_returnsFalse() {
        HighscoreService.HighscoreEntry e = new HighscoreService.HighscoreEntry(
            "Player", 36, "NOVICE", "2026-04-02T10:00:00Z", 0f, new int[]{}, new int[]{});
        assertFalse(e.hasScorecard());
    }

    @Test
    public void hasScorecard_legacyConstructor_returnsFalse() {
        HighscoreService.HighscoreEntry e = new HighscoreService.HighscoreEntry(
            "Player", 36, "NOVICE", "2026-04-02T10:00:00Z");
        assertFalse(e.hasScorecard());
    }
}
