package org.example.ball;

import org.example.terrain.level.LevelData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompetitiveScoreTest {

    private CompetitiveScore score;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<LevelData> buildCourse(int holes, int parPerHole) {
        List<LevelData> layout = new ArrayList<>();
        for (int i = 0; i < holes; i++) {
            LevelData d = new LevelData();
            d.setPar(parPerHole);
            layout.add(d);
        }
        return layout;
    }

    @BeforeEach
    public void setUp() {
        score = new CompetitiveScore(buildCourse(18, 4));
    }

    // ── Default initialisation ─────────────────────────────────────────────────

    @Test
    public void testDefaultScoresAreZero() {
        for (int i = 0; i < 18; i++) {
            assertEquals(0, score.getScoreForHole(i), "Score at hole " + i + " should be 0");
        }
    }

    @Test
    public void testDefaultCurrentHoleIsZero() {
        assertEquals(0, score.getCurrentHoleIndex());
        assertEquals(1, score.getCurrentHoleNumber());
    }

    @Test
    public void testParsLoadedFromCourse() {
        List<LevelData> course = buildCourse(18, 5);
        CompetitiveScore s = new CompetitiveScore(course);
        for (int i = 0; i < 18; i++) {
            assertEquals(5, s.getParForHole(i));
        }
    }

    @Test
    public void testMixedParsFromCourse() {
        List<LevelData> course = new ArrayList<>();
        int[] pars = {3, 4, 5, 4, 3, 4, 5, 4, 4, 4, 4, 4, 5, 3, 4, 4, 5, 4};
        for (int par : pars) {
            LevelData d = new LevelData();
            d.setPar(par);
            course.add(d);
        }
        CompetitiveScore s = new CompetitiveScore(course);
        for (int i = 0; i < pars.length; i++) {
            assertEquals(pars[i], s.getParForHole(i));
        }
    }

    // ── recordStroke ───────────────────────────────────────────────────────────

    @Test
    public void testRecordStroke() {
        score.recordStroke(0, 3);
        assertEquals(3, score.getScoreForHole(0));
    }

    @Test
    public void testRecordStrokeOverwritesPreviousValue() {
        score.recordStroke(0, 3);
        score.recordStroke(0, 5);
        assertEquals(5, score.getScoreForHole(0));
    }

    @Test
    public void testRecordStrokeOutOfRangeIsIgnored() {
        score.recordStroke(-1, 99);
        score.recordStroke(18, 99);
        // Scores should be unchanged
        for (int i = 0; i < 18; i++) assertEquals(0, score.getScoreForHole(i));
    }

    // ── Boundary access ────────────────────────────────────────────────────────

    @Test
    public void testGetScoreForHoleOutOfRangeReturnsZero() {
        assertEquals(0, score.getScoreForHole(-1));
        assertEquals(0, score.getScoreForHole(18));
    }

    @Test
    public void testGetParForHoleOutOfRangeReturnsFallback() {
        assertEquals(4, score.getParForHole(-1));
        assertEquals(4, score.getParForHole(18));
    }

    // ── Total strokes ──────────────────────────────────────────────────────────

    @Test
    public void testTotalStrokesWithNoScoresIsZero() {
        assertEquals(0, score.getTotalStrokes());
    }

    @Test
    public void testTotalStrokesAfterRecording() {
        score.recordStroke(0, 3);
        score.recordStroke(1, 5);
        score.recordStroke(2, 4);
        assertEquals(12, score.getTotalStrokes());
    }

    @Test
    public void testTotalStrokesIncludesAllHoles() {
        for (int i = 0; i < 18; i++) score.recordStroke(i, 4);
        assertEquals(72, score.getTotalStrokes());
    }

    // ── Total to par ───────────────────────────────────────────────────────────

    @Test
    public void testTotalToParEvenWhenAllParScores() {
        for (int i = 0; i < 18; i++) score.recordStroke(i, 4);
        assertEquals(0, score.getTotalToPar());
    }

    @Test
    public void testTotalToParPositiveWhenOverPar() {
        score.recordStroke(0, 6); // +2 on a par 4
        assertEquals(2, score.getTotalToPar());
    }

    @Test
    public void testTotalToParNegativeWhenUnderPar() {
        score.recordStroke(0, 2); // -2 on a par 4
        assertEquals(-2, score.getTotalToPar());
    }

    @Test
    public void testTotalToParIgnoresHolesWithZeroScore() {
        // Unplayed holes (score=0) should not count toward the total
        score.recordStroke(0, 4);
        // Holes 1-17 still have score=0 and must not subtract 4 each
        assertEquals(0, score.getTotalToPar());
    }

    @Test
    public void testTotalToParCombined() {
        score.recordStroke(0, 3); // -1
        score.recordStroke(1, 5); // +1
        score.recordStroke(2, 6); // +2
        assertEquals(2, score.getTotalToPar());
    }

    // ── getToParString ─────────────────────────────────────────────────────────

    @Test
    public void testToParStringEven() {
        for (int i = 0; i < 18; i++) score.recordStroke(i, 4);
        assertEquals("Even", score.getToParString());
    }

    @Test
    public void testToParStringPositive() {
        score.recordStroke(0, 6); // +2
        assertEquals("+2", score.getToParString());
    }

    @Test
    public void testToParStringNegative() {
        score.recordStroke(0, 2); // -2
        assertEquals("-2", score.getToParString());
    }

    @Test
    public void testToParStringNoScoresIsEven() {
        // No strokes recorded yet → all scores 0 → toPar = 0 → "Even"
        assertEquals("Even", score.getToParString());
    }

    // ── isCourseComplete ───────────────────────────────────────────────────────

    @Test
    public void testCourseNotCompleteWithNoScores() {
        assertFalse(score.isCourseComplete());
    }

    @Test
    public void testCourseNotCompleteWithPartialScores() {
        for (int i = 0; i < 17; i++) score.recordStroke(i, 4);
        assertFalse(score.isCourseComplete());
    }

    @Test
    public void testCourseCompleteWhenAllHolesScored() {
        for (int i = 0; i < 18; i++) score.recordStroke(i, 4);
        assertTrue(score.isCourseComplete());
    }

    // ── currentHoleIndex ──────────────────────────────────────────────────────

    @Test
    public void testSetCurrentHoleIndex() {
        score.setCurrentHoleIndex(5);
        assertEquals(5, score.getCurrentHoleIndex());
        assertEquals(6, score.getCurrentHoleNumber());
    }
}
