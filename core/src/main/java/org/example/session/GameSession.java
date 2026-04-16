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
        DAILY_18,
        DAILY_9,
        DAILY_1;

        public int holeCount() {
            return switch (this) {
                case DAILY_1 -> 1;
                case DAILY_9 -> 9;
                default -> 18;
            };
        }
    }

    private long masterSeed;
    private GameConfig.Difficulty difficulty;
    private GameMode mode;
    private int currentHoleStrokes = 0;
    private boolean isStarted = false;
    private long timestamp;
    private float elapsedTimeSeconds = 0f;
    private boolean submitted = false;
    private CompetitiveScore competitiveScore;

    // Ball rest position — set when ball becomes stationary mid-hole, cleared on hole completion.
    // Null when no position is cached (ball starts from tee on load).
    private Float ballRestX, ballRestY, ballRestZ;

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
        List<LevelData> full18 = LevelDataGenerator.generate18Holes(masterSeed);
        int count = (mode != null) ? mode.holeCount() : 18;
        this.courseLayout = (count < full18.size()) ? full18.subList(0, count) : full18;
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
            clearBallRestPosition();
            notifyStateChanged();
        }
    }

    /** Records the ball's resting position so it can be restored on next load. */
    public void setBallRestPosition(float x, float y, float z) {
        ballRestX = x; ballRestY = y; ballRestZ = z;
    }

    /**
     * Clears the cached rest position. Call when a shot is taken (so a mid-flight
     * crash does not restore a stale position) and on hole completion.
     */
    public void clearBallRestPosition() {
        ballRestX = ballRestY = ballRestZ = null;
    }

    /**
     * Returns the cached ball rest position as a float[3] {x,y,z}, or null if none is stored.
     * Callers should create a Vector3 from this rather than storing the array.
     */
    public float[] getBallRestPosition() {
        if (ballRestX == null) return null;
        return new float[]{ballRestX, ballRestY, ballRestZ};
    }

    /** Accumulates elapsed time only while the session is active (started and not finished). */
    public void addElapsedTime(float delta) {
        if (isStarted && !isFinished()) {
            elapsedTimeSeconds += delta;
        }
    }

    public float getElapsedTimeSeconds() { return elapsedTimeSeconds; }

    /** Returns elapsed time formatted as "MM:SS.T". */
    public String getElapsedTimeFormatted() {
        int total = (int) elapsedTimeSeconds;
        int minutes = total / 60;
        int seconds = total % 60;
        int tenths = (int) (elapsedTimeSeconds * 10) % 10;
        return String.format("%02d:%02d.%d", minutes, seconds, tenths);
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
        json.writeValue("elapsedTimeSeconds", elapsedTimeSeconds);
        json.writeValue("submitted", submitted);
        json.writeValue("competitiveScore", competitiveScore);
        if (ballRestX != null) {
            json.writeValue("ballRestX", ballRestX);
            json.writeValue("ballRestY", ballRestY);
            json.writeValue("ballRestZ", ballRestZ);
        }
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        this.masterSeed = jsonData.getLong("masterSeed");
        this.difficulty = json.readValue(GameConfig.Difficulty.class, jsonData.get("difficulty"));
        this.mode = json.readValue(GameMode.class, jsonData.get("mode"));
        this.currentHoleStrokes = jsonData.getInt("currentHoleStrokes");
        this.isStarted = jsonData.getBoolean("isStarted");
        this.timestamp = jsonData.getLong("timestamp");
        this.elapsedTimeSeconds = jsonData.getFloat("elapsedTimeSeconds", 0f);
        this.submitted = jsonData.getBoolean("submitted", false);
        this.competitiveScore = json.readValue(CompetitiveScore.class, jsonData.get("competitiveScore"));
        this.ballRestX = jsonData.has("ballRestX") ? jsonData.getFloat("ballRestX") : null;
        this.ballRestY = jsonData.has("ballRestY") ? jsonData.getFloat("ballRestY") : null;
        this.ballRestZ = jsonData.has("ballRestZ") ? jsonData.getFloat("ballRestZ") : null;
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
        return courseLayout.get(Math.min(index, courseLayout.size() - 1));
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
    public boolean isSubmitted() { return submitted; }
    /** Marks this session as successfully submitted and triggers a save. */
    public void markSubmitted() { this.submitted = true; notifyStateChanged(); }
}
