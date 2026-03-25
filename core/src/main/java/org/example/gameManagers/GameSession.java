package org.example.gameManagers;

import org.example.GameConfig;
import org.example.ball.CompetitiveScore;
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
    private int[] scores;
    private boolean isFinished = false;
    private boolean isStarted = false;

    private CompetitiveScore competitiveScore;

    public GameSession(long masterSeed, GameConfig.Difficulty difficulty, GameMode mode) {
        this.masterSeed = masterSeed;
        this.difficulty = difficulty;
        this.mode = mode;
        this.scores = new int[18];
        this.courseLayout = new ArrayList<>();
    }

    public void setCourseLayout(List<LevelData> layout) {
        this.courseLayout = layout;
        this.competitiveScore = new CompetitiveScore(layout);
    }

    public void advanceHole(int strokes) {
        isStarted = true;
        if (currentHoleIndex < courseLayout.size()) {
            scores[currentHoleIndex] = strokes;

            // Sync with the CompetitiveScore object
            if (competitiveScore != null) {
                competitiveScore.recordStroke(currentHoleIndex, strokes);
            }

            currentHoleIndex++;
            if (competitiveScore != null) {
                competitiveScore.setCurrentHoleIndex(currentHoleIndex);
            }
        }

        if (currentHoleIndex >= courseLayout.size() && !courseLayout.isEmpty()) {
            isFinished = true;
        }
    }

    public CompetitiveScore getCompetitiveScore() {
        return competitiveScore;
    }

    public List<LevelData> getCourseLayout() { return courseLayout; }
    public int getCurrentHoleIndex() { return currentHoleIndex; }
    public boolean isFinished() { return isFinished; }
    public int[] getScores() { return scores; }
    public long getMasterSeed() { return masterSeed; }
    public GameConfig.Difficulty getDifficulty() { return difficulty; }
    public GameMode getMode() { return mode; }
    public boolean isStarted() { return isStarted; }
    public void setStarted(boolean started) { this.isStarted = started; }

    public LevelData getCurrentLevel() {
        if (courseLayout == null || currentHoleIndex >= courseLayout.size()) return null;
        return courseLayout.get(currentHoleIndex);
    }

    public int getTotalScore() {
        if (competitiveScore != null) {
            return competitiveScore.getTotalStrokes();
        }
        int total = 0;
        for (int i = 0; i < currentHoleIndex; i++) {
            total += scores[i];
        }
        return total;
    }
}