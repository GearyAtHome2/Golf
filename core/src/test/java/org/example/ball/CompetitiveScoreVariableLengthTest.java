package org.example.ball;

import org.example.terrain.level.LevelData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompetitiveScoreVariableLengthTest {

    private List<LevelData> buildCourse(int holes, int par) {
        List<LevelData> course = new ArrayList<>();
        for (int i = 0; i < holes; i++) {
            LevelData d = new LevelData();
            d.setPar(par);
            course.add(d);
        }
        return course;
    }

    @Test
    public void testOneHoleCourseTracksStrokes() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(1, 4));
        score.recordStroke(0, 3);
        assertEquals(3, score.getScoreForHole(0));
    }

    @Test
    public void testOneHoleCourseComplete() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(1, 4));
        assertFalse(score.isCourseComplete());
        score.recordStroke(0, 4);
        assertTrue(score.isCourseComplete());
    }

    @Test
    public void testNineHoleCourseTracksAllHoles() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(9, 4));
        for (int i = 0; i < 9; i++) {
            score.recordStroke(i, i + 1);
        }
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, score.getScoreForHole(i));
        }
    }

    @Test
    public void testNineHoleCourseIncompleteWithEightHoles() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(9, 4));
        for (int i = 0; i < 8; i++) {
            score.recordStroke(i, 4);
        }
        assertFalse(score.isCourseComplete());
    }

    @Test
    public void testOutOfRangeHoleIgnoredForNineHoleCourse() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(9, 4));
        // Index 9 is out of range for a 9-hole course (valid: 0-8)
        score.recordStroke(9, 99);
        assertEquals(0, score.getScoreForHole(9)); // returns 0 for out-of-range
        // Verify it didn't corrupt any valid hole
        for (int i = 0; i < 9; i++) {
            assertEquals(0, score.getScoreForHole(i));
        }
    }

    @Test
    public void testOneHoleToParCalculation() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(1, 4));
        score.recordStroke(0, 6); // +2
        assertEquals(2, score.getTotalToPar());
    }

    @Test
    public void testNineHoleToParCalculation() {
        CompetitiveScore score = new CompetitiveScore(buildCourse(9, 4));
        for (int i = 0; i < 9; i++) {
            score.recordStroke(i, 4); // even par
        }
        assertEquals(0, score.getTotalToPar());
        assertEquals("Even", score.getToParString());
    }
}
