package org.example.session;

import org.example.GameConfig;
import org.example.terrain.level.LevelData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameSessionCourseVariantTest {

    private List<LevelData> buildCourse(int holes) {
        List<LevelData> course = new ArrayList<>();
        for (int i = 0; i < holes; i++) {
            LevelData d = new LevelData();
            d.setPar(4);
            course.add(d);
        }
        return course;
    }

    @Test
    public void testDaily1HoleCount() {
        GameSession session = new GameSession(1L, GameConfig.Difficulty.NOVICE, GameSession.GameMode.DAILY_1, 20260101L);
        session.setCourseLayout(buildCourse(1));
        assertEquals(1, session.getCourseLayout().size());
    }

    @Test
    public void testDaily9HoleCount() {
        GameSession session = new GameSession(1L, GameConfig.Difficulty.NOVICE, GameSession.GameMode.DAILY_9, 20260101L);
        session.setCourseLayout(buildCourse(9));
        assertEquals(9, session.getCourseLayout().size());
    }

    @Test
    public void testDaily18HoleCount() {
        GameSession session = new GameSession(1L, GameConfig.Difficulty.NOVICE, GameSession.GameMode.DAILY_18, 20260101L);
        session.setCourseLayout(buildCourse(18));
        assertEquals(18, session.getCourseLayout().size());
    }

    @Test
    public void testStandard18HoleCount() {
        GameSession session = new GameSession(1L, GameConfig.Difficulty.NOVICE, GameSession.GameMode.STANDARD_18, 20260101L);
        session.setCourseLayout(buildCourse(18));
        assertEquals(18, session.getCourseLayout().size());
    }
}
