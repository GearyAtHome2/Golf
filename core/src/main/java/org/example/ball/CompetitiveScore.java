package org.example.ball;

import org.example.terrain.level.LevelData;
import java.util.List;

public class CompetitiveScore {
    private final int totalHoles = 18;
    // Removed final to allow JSON deserialization to fill these
    private int[] pars = new int[totalHoles];
    private int[] scores = new int[totalHoles];
    private int currentHoleIndex = 0;

    public CompetitiveScore() {
        // Initialize arrays so the JSON reader has a target to write into
        for (int i = 0; i < totalHoles; i++) {
            pars[i] = 4;
            scores[i] = 0;
        }
    }

    public CompetitiveScore(List<LevelData> course) {
        this(); // Call no-arg to ensure arrays are ready
        for (int i = 0; i < totalHoles; i++) {
            if (i < course.size()) {
                pars[i] = course.get(i).getPar();
            } else {
                pars[i] = 4; // Fallback
            }
            scores[i] = 0;
        }
    }

    public void recordStroke(int holeIndex, int strokes) {
        if (holeIndex >= 0 && holeIndex < totalHoles) {
            scores[holeIndex] = strokes;
        }
    }

    public int getParForHole(int holeIndex) {
        if (holeIndex < 0 || holeIndex >= totalHoles) return 4;
        return pars[holeIndex];
    }

    public int getScoreForHole(int holeIndex) {
        if (holeIndex < 0 || holeIndex >= totalHoles) return 0;
        return scores[holeIndex];
    }

    public void setCurrentHoleIndex(int index) {
        this.currentHoleIndex = index;
    }

    public int getCurrentHoleIndex() {
        return currentHoleIndex;
    }

    public int getCurrentHoleNumber() {
        return currentHoleIndex + 1;
    }

    public int getTotalToPar() {
        int relativeScore = 0;
        for (int i = 0; i < totalHoles; i++) {
            if (scores[i] > 0) {
                relativeScore += (scores[i] - pars[i]);
            }
        }
        return relativeScore;
    }

    public int getTotalStrokes() {
        int total = 0;
        for (int score : scores) {
            total += score;
        }
        return total;
    }

    public boolean isCourseComplete() {
        for (int i = 0; i < totalHoles; i++) {
            if (scores[i] == 0) return false;
        }
        return true;
    }

    public String getToParString() {
        int toPar = getTotalToPar();
        if (toPar == 0) return "E";
        return (toPar > 0 ? "+" : "") + toPar;
    }

    public int[] getScores() { return scores; }
    public int[] getPars() { return pars; }
}