package org.example.gameManagers;

import org.example.GameConfig;
import org.example.terrain.level.LevelData;
import java.util.ArrayList;
import java.util.List;

public class GameSession {
    public enum GameMode {
        STANDARD_18,
        DAILY_CHALLENGE
    }

    private final long masterSeed;
    private final GameConfig.Difficulty difficulty;
    private final GameMode mode;

    private int currentHoleIndex = 0;
    private List<LevelData> courseLayout;
    private int[] scores; // Index matches courseLayout
    private boolean isFinished = false;

    public GameSession(long masterSeed, GameConfig.Difficulty difficulty, GameMode mode) {
        this.masterSeed = masterSeed;
        this.difficulty = difficulty;
        this.mode = mode;
        this.scores = new int[18];
        this.courseLayout = new ArrayList<>();
    }

    // --- State Logic ---

    public void advanceHole(int strokes) {
        if (currentHoleIndex < 18) {
            scores[currentHoleIndex] = strokes;
            currentHoleIndex++;
        }
        if (currentHoleIndex >= 18) {
            isFinished = true;
        }
    }

    public LevelData getCurrentLevel() {
        if (courseLayout == null || courseLayout.isEmpty()) return null;
        return courseLayout.get(currentHoleIndex);
    }

    public int getTotalScore() {
        int total = 0;
        for (int i = 0; i < currentHoleIndex; i++) {
            total += scores[i];
        }
        return total;
    }


    public long getMasterSeed() { return masterSeed; }
    public GameConfig.Difficulty getDifficulty() { return difficulty; }
    public GameMode getMode() { return mode; }
    public int getCurrentHoleIndex() { return currentHoleIndex; }
    public boolean isFinished() { return isFinished; }

    public void setCourseLayout(List<LevelData> layout) {
        this.courseLayout = layout;
    }

    public List<LevelData> getCourseLayout() {
        return courseLayout;
    }
}