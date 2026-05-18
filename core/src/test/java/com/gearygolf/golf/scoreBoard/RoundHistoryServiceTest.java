package com.gearygolf.golf.scoreBoard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoundHistoryServiceTest {

    private static List<RoundHistoryService.Round> rounds(int... scores) {
        List<RoundHistoryService.Round> list = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            list.add(new RoundHistoryService.Round(scores[i], (long) i * 1000));
        }
        return list;
    }

    // ── calculateHandicap ────────────────────────────────────────────────────

    @Test
    void returnsNullWhenFewerThanEightRounds() {
        assertNull(RoundHistoryService.calculateHandicap(rounds(72, 73, 74)));
        assertNull(RoundHistoryService.calculateHandicap(rounds(72, 72, 72, 72, 72, 72, 72))); // exactly 7
        assertNull(RoundHistoryService.calculateHandicap(null));
    }

    @Test
    void returnsZeroWhenBestEightAverageParExactly() {
        assertEquals(0.0, RoundHistoryService.calculateHandicap(rounds(72, 72, 72, 72, 72, 72, 72, 72)), 0.001);
    }

    @Test
    void returnsNegativeOneWhenBestEightAverageOneOverPar() {
        // All 8 rounds at 73 (+1) → handicap = -1
        assertEquals(-1.0, RoundHistoryService.calculateHandicap(rounds(73, 73, 73, 73, 73, 73, 73, 73)), 0.001);
    }

    @Test
    void returnsPositiveHandicapForUnderParAverage() {
        // All 8 rounds at 70 (-2) → handicap = +2
        assertEquals(2.0, RoundHistoryService.calculateHandicap(rounds(70, 70, 70, 70, 70, 70, 70, 70)), 0.001);
    }

    @Test
    void selectsBestEightFromLargerSet() {
        // 8 rounds at par (72) + 4 terrible rounds (100) → best 8 are all par → handicap = 0
        List<RoundHistoryService.Round> list = rounds(72, 72, 72, 72, 72, 72, 72, 72, 100, 100, 100, 100);
        assertEquals(0.0, RoundHistoryService.calculateHandicap(list), 0.001);
    }

    @Test
    void averagesMixedBestEight() {
        // Best 8 are: four at 72 (par) and four at 74 (+2) → avg over par = 1 → handicap = -1
        List<RoundHistoryService.Round> list = rounds(72, 72, 72, 72, 74, 74, 74, 74, 90, 90);
        assertEquals(-1.0, RoundHistoryService.calculateHandicap(list), 0.001);
    }

    @Test
    void statusThresholdBoundary() {
        // Handicap exactly -5 should NOT qualify (must be > -5)
        // Best 8 all at 77 (+5) → handicap = -5
        Double h = RoundHistoryService.calculateHandicap(rounds(77, 77, 77, 77, 77, 77, 77, 77));
        assertNotNull(h);
        assertFalse(h > -5.0); // exactly -5, does not qualify

        // Best 8 all at 76 (+4) → handicap = -4 → qualifies
        Double h2 = RoundHistoryService.calculateHandicap(rounds(76, 76, 76, 76, 76, 76, 76, 76));
        assertNotNull(h2);
        assertTrue(h2 > -5.0);
    }

    // ── buildRoundsDocument / parseRoundsDocument round-trip ─────────────────

    @Test
    void roundTripSerialisation() {
        List<RoundHistoryService.Round> original = rounds(74, 76, 80);
        String json = RoundHistoryService.buildRoundsDocument(original);
        // Wrap in a fake Firestore GET response envelope
        String envelope = "{\"fields\":" + json.substring("{\"fields\":".length());
        // Re-parse
        List<RoundHistoryService.Round> parsed = RoundHistoryService.parseRoundsDocument(envelope);
        assertEquals(original.size(), parsed.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i).score,     parsed.get(i).score);
            assertEquals(original.get(i).timestamp, parsed.get(i).timestamp);
        }
    }
}
