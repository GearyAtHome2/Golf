package org.example.session;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import org.example.GameConfig;
import org.example.ball.CompetitiveScore;
import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import java.util.ArrayList;
import java.util.List;

public class GameSession implements Json.Serializable {
    public enum GameMode {
        STANDARD_18,
        DAILY_CHALLENGE
    }

    private long masterSeed;
    private GameConfig.Difficulty difficulty;
    private GameMode mode;
    private int currentHoleStrokes = 0;
    private boolean isStarted = false;
    private long timestamp;
    private CompetitiveScore competitiveScore;

    private transient List<LevelData> courseLayout = new ArrayList<>();
    private transient Runnable onStateChanged;

    public GameSession() {}

    public GameSession(long masterSeed, GameConfig.Difficulty difficulty, GameMode mode, long timestamp) {
        this.masterSeed = masterSeed;
        this.difficulty = difficulty;
        this.mode = mode;
        this.timestamp = timestamp;
    }

    public void rebuildLayout() {
        this.courseLayout = LevelDataGenerator.generate18Holes(masterSeed);
        if (this.competitiveScore == null) {
            this.competitiveScore = new CompetitiveScore(courseLayout);
        }
    }

    public void setCourseLayout(List<LevelData> layout) {
        this.courseLayout = layout;
        this.competitiveScore = new CompetitiveScore(layout);
    }

    public void incrementStrokes() {
        this.currentHoleStrokes++;
        this.isStarted = true;
        if (competitiveScore != null) {
            competitiveScore.recordStroke(getCurrentHoleIndex(), currentHoleStrokes);
        }
        notifyStateChanged();
    }

    public void advanceHole() {
        if (competitiveScore != null && getCurrentHoleIndex() < courseLayout.size()) {
            competitiveScore.recordStroke(getCurrentHoleIndex(), currentHoleStrokes);
            currentHoleStrokes = 0;
            competitiveScore.setCurrentHoleIndex(getCurrentHoleIndex() + 1);
            notifyStateChanged();
        }
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) onStateChanged.run();
    }

    public void setSaveCallback(Runnable callback) {
        this.onStateChanged = callback;
    }

    @Override
    public void write(Json json) {
        json.writeValue("masterSeed", masterSeed);
        json.writeValue("difficulty", difficulty);
        json.writeValue("mode", mode);
        json.writeValue("currentHoleStrokes", currentHoleStrokes);
        json.writeValue("isStarted", isStarted);
        json.writeValue("timestamp", timestamp);
        json.writeValue("competitiveScore", competitiveScore);
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        this.masterSeed = jsonData.getLong("masterSeed");
        this.difficulty = json.readValue(GameConfig.Difficulty.class, jsonData.get("difficulty"));
        this.mode = json.readValue(GameMode.class, jsonData.get("mode"));
        this.currentHoleStrokes = jsonData.getInt("currentHoleStrokes");
        this.isStarted = jsonData.getBoolean("isStarted");
        this.timestamp = jsonData.getLong("timestamp");
        this.competitiveScore = json.readValue(CompetitiveScore.class, jsonData.get("competitiveScore"));
        rebuildLayout();
    }

    public int getCurrentHoleIndex() { return (competitiveScore != null) ? competitiveScore.getCurrentHoleIndex() : 0; }
    public int[] getScores() { return (competitiveScore != null) ? competitiveScore.getScores() : new int[0]; }
    public int getTotalScore() { return (competitiveScore != null) ? competitiveScore.getTotalStrokes() : 0; }

    public boolean isFinished() {
        if (courseLayout == null || courseLayout.isEmpty()) return false;

        return getCurrentHoleIndex() >= courseLayout.size();
    }

    public LevelData getCurrentLevel() {
        int index = getCurrentHoleIndex();
        if (courseLayout == null || courseLayout.isEmpty()) return null;

        int safeIndex = Math.min(index, courseLayout.size() - 1);
        return courseLayout.get(safeIndex);
    }

    public int getCurrentHoleStrokes() { return currentHoleStrokes; }
    public CompetitiveScore getCompetitiveScore() { return competitiveScore; }
    public List<LevelData> getCourseLayout() { return courseLayout; }
    public long getMasterSeed() { return masterSeed; }
    public GameConfig.Difficulty getDifficulty() { return difficulty; }
    public GameMode getMode() { return mode; }
    public boolean isStarted() { return isStarted; }
    public void setStarted(boolean started) { this.isStarted = started; }
    public long getTimestamp() { return timestamp; }
}