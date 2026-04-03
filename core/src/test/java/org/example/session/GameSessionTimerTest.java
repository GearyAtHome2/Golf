package org.example.session;

import org.example.GdxTestSetup;
import org.example.GameConfig;
import org.example.terrain.level.LevelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameSessionTimerTest {

    @BeforeAll
    public static void initGdx() {
        GdxTestSetup.init();
    }

    private GameSession session;

    private List<LevelData> buildCourse(int holes) {
        List<LevelData> course = new ArrayList<>();
        for (int i = 0; i < holes; i++) {
            LevelData d = new LevelData();
            d.setPar(4);
            course.add(d);
        }
        return course;
    }

    @BeforeEach
    public void setUp() {
        session = new GameSession(1L, GameConfig.Difficulty.NOVICE, GameSession.GameMode.DAILY_1, 20260101L);
        session.setCourseLayout(buildCourse(1));
    }

    @Test
    public void testTimerStartsAtZero() {
        assertEquals(0f, session.getElapsedTimeSeconds(), 0.001f);
    }

    @Test
    public void testTimerDoesNotAdvanceBeforeFirstShot() {
        session.addElapsedTime(5f);
        assertEquals(0f, session.getElapsedTimeSeconds(), 0.001f);
    }

    @Test
    public void testTimerAdvancesAfterFirstShot() {
        session.incrementStrokes();
        session.addElapsedTime(3f);
        assertEquals(3f, session.getElapsedTimeSeconds(), 0.001f);
    }

    @Test
    public void testTimerAccumulatesAcrossFrames() {
        session.incrementStrokes();
        session.addElapsedTime(1f);
        session.addElapsedTime(2f);
        session.addElapsedTime(0.5f);
        assertEquals(3.5f, session.getElapsedTimeSeconds(), 0.001f);
    }

    @Test
    public void testTimerStopsAfterCourseComplete() {
        session.incrementStrokes();
        session.addElapsedTime(10f);
        session.advanceHole(); // finishes the 1-hole course
        assertTrue(session.isFinished());
        session.addElapsedTime(5f);
        // timer should not have advanced after finish
        assertEquals(10f, session.getElapsedTimeSeconds(), 0.001f);
    }

    @Test
    public void testElapsedTimeFormattedStringAt65Seconds() {
        session.incrementStrokes();
        session.addElapsedTime(65f);
        assertEquals("01:05.0", session.getElapsedTimeFormatted());
    }

    @Test
    public void testElapsedTimeFormattedBeforeStart() {
        // Timer is 0 and not started, should show "00:00.0"
        assertEquals("00:00.0", session.getElapsedTimeFormatted());
    }

    @Test
    public void testElapsedTimeFormattedShowsTenths() {
        session.incrementStrokes();
        session.addElapsedTime(65.7f);
        assertEquals("01:05.7", session.getElapsedTimeFormatted());
    }

    @Test
    public void testElapsedTimeFormattedTruncatesNotRounds() {
        session.incrementStrokes();
        session.addElapsedTime(65.99f);
        // Should truncate to .9, not round up to .0 of next second
        assertEquals("01:05.9", session.getElapsedTimeFormatted());
    }
}
