package org.example.session;

import org.example.GameConfig;
import org.example.terrain.level.LevelData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameSessionTest {

    private GameSession session;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<LevelData> buildCourse(int holes, int parPerHole) {
        List<LevelData> layout = new ArrayList<>();
        for (int i = 0; i < holes; i++) {
            LevelData d = new LevelData();
            d.setSeed(i);
            d.setPar(parPerHole);
            d.setArchetype(LevelData.Archetype.STANDARD_LINKS);
            layout.add(d);
        }
        return layout;
    }

    @BeforeEach
    public void setUp() {
        session = new GameSession(12345L, GameConfig.Difficulty.NOVICE,
                GameSession.GameMode.STANDARD_18, 20260101L);
        session.setCourseLayout(buildCourse(18, 4));
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    public void testInitialStateIsNotStarted() {
        assertFalse(session.isStarted());
    }

    @Test
    public void testInitialStrokeCountIsZero() {
        assertEquals(0, session.getCurrentHoleStrokes());
    }

    @Test
    public void testInitialHoleIndexIsZero() {
        assertEquals(0, session.getCurrentHoleIndex());
    }

    @Test
    public void testInitialIsNotFinished() {
        assertFalse(session.isFinished());
    }

    @Test
    public void testGetMasterSeed() {
        assertEquals(12345L, session.getMasterSeed());
    }

    @Test
    public void testGetDifficulty() {
        assertEquals(GameConfig.Difficulty.NOVICE, session.getDifficulty());
    }

    @Test
    public void testGetMode() {
        assertEquals(GameSession.GameMode.STANDARD_18, session.getMode());
    }

    @Test
    public void testGetTimestamp() {
        assertEquals(20260101L, session.getTimestamp());
    }

    // ── incrementStrokes ───────────────────────────────────────────────────────

    @Test
    public void testIncrementStrokesMarksStarted() {
        session.incrementStrokes();
        assertTrue(session.isStarted());
    }

    @Test
    public void testIncrementStrokesCountsCorrectly() {
        session.incrementStrokes();
        session.incrementStrokes();
        session.incrementStrokes();
        assertEquals(3, session.getCurrentHoleStrokes());
    }

    @Test
    public void testIncrementStrokesRecordsInCompetitiveScore() {
        session.incrementStrokes();
        session.incrementStrokes();
        assertEquals(2, session.getCompetitiveScore().getScoreForHole(0));
    }

    // ── advanceHole ────────────────────────────────────────────────────────────

    @Test
    public void testAdvanceHoleResetsStrokeCount() {
        session.incrementStrokes();
        session.incrementStrokes();
        session.advanceHole();
        assertEquals(0, session.getCurrentHoleStrokes());
    }

    @Test
    public void testAdvanceHoleIncrementsHoleIndex() {
        session.advanceHole();
        assertEquals(1, session.getCurrentHoleIndex());
    }

    @Test
    public void testAdvanceHoleMultipleTimes() {
        for (int i = 0; i < 5; i++) session.advanceHole();
        assertEquals(5, session.getCurrentHoleIndex());
    }

    // ── isFinished ─────────────────────────────────────────────────────────────

    @Test
    public void testNotFinishedPartwayThrough() {
        for (int i = 0; i < 17; i++) session.advanceHole();
        assertFalse(session.isFinished());
    }

    @Test
    public void testFinishedAfterAllHoles() {
        for (int i = 0; i < 18; i++) session.advanceHole();
        assertTrue(session.isFinished());
    }

    // ── getCurrentLevel ────────────────────────────────────────────────────────

    @Test
    public void testGetCurrentLevelAtHoleZero() {
        LevelData level = session.getCurrentLevel();
        assertNotNull(level);
        assertEquals(0, session.getCourseLayout().indexOf(level));
    }

    @Test
    public void testGetCurrentLevelAdvancesCorrectly() {
        session.advanceHole();
        LevelData level = session.getCurrentLevel();
        assertNotNull(level);
        assertEquals(1, session.getCourseLayout().indexOf(level));
    }

    @Test
    public void testGetCurrentLevelClampsAtLastHole() {
        // Advance past the end; should clamp to the last hole
        for (int i = 0; i < 20; i++) session.advanceHole();
        LevelData level = session.getCurrentLevel();
        assertNotNull(level);
        assertSame(session.getCourseLayout().get(17), level);
    }

    // ── getTotalScore ──────────────────────────────────────────────────────────

    @Test
    public void testTotalScoreZeroInitially() {
        assertEquals(0, session.getTotalScore());
    }

    @Test
    public void testTotalScoreAccumulatesAcrossHoles() {
        session.incrementStrokes(); // hole 0 → 1 stroke
        session.incrementStrokes(); // hole 0 → 2 strokes
        session.advanceHole();      // records 2, moves to hole 1

        session.incrementStrokes(); // hole 1 → 1 stroke
        session.incrementStrokes(); // hole 1 → 2 strokes
        session.incrementStrokes(); // hole 1 → 3 strokes
        session.advanceHole();      // records 3

        assertEquals(5, session.getTotalScore());
    }

    // ── save callback ──────────────────────────────────────────────────────────

    @Test
    public void testSaveCallbackFiredOnIncrementStrokes() {
        int[] callCount = {0};
        session.setSaveCallback(() -> callCount[0]++);
        session.incrementStrokes();
        assertEquals(1, callCount[0]);
    }

    @Test
    public void testSaveCallbackFiredOnAdvanceHole() {
        int[] callCount = {0};
        session.setSaveCallback(() -> callCount[0]++);
        session.advanceHole();
        assertEquals(1, callCount[0]);
    }

    // ── edge cases ─────────────────────────────────────────────────────────────

    @Test
    public void testIsFinishedReturnsFalseWithEmptyCourse() {
        GameSession empty = new GameSession(1L, GameConfig.Difficulty.NOVICE,
                GameSession.GameMode.STANDARD_18, 0L);
        assertFalse(empty.isFinished());
    }

    @Test
    public void testGetCurrentLevelReturnsNullWithEmptyCourse() {
        GameSession empty = new GameSession(1L, GameConfig.Difficulty.NOVICE,
                GameSession.GameMode.STANDARD_18, 0L);
        assertNull(empty.getCurrentLevel());
    }
}
