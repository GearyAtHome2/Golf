package org.example.ball;

import org.example.terrain.level.LevelData;

import java.util.ArrayList;
import java.util.List;

public class CompetitiveScore {
    private final int totalHoles = 18;
    private final int[] pars = new int[totalHoles];
    private final int[] scores = new int[totalHoles];
    private int currentHoleIndex = 0;

    public CompetitiveScore(List<LevelData> course) {
        for (int i = 0; i < totalHoles; i++) {
            if (i < course.size()) {
                pars[i] = course.get(i).getPar();
            } else {
                pars[i] = 4; // Fallback
            }
            scores[i] = 0; // 0 indicates the hole hasn't been played/finished
        }
    }

    /**
     * Records the score for the current hole and advances the index.
     */
    public void recordStroke(int holeIndex, int strokes) {
        if (holeIndex >= 0 && holeIndex < totalHoles) {
            scores[holeIndex] = strokes;
        }
    }

    public int getParForHole(int holeIndex) {
        return pars[holeIndex];
    }

    public int getScoreForHole(int holeIndex) {
        return scores[holeIndex];
    }

    public int getCurrentHoleNumber() {
        return currentHoleIndex + 1;
    }

    /**
     * Calculates the total "To Par" value for all completed holes.
     * Example: If Par is 4 and score is 3, returns -1.
     */
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

    /**
     * Returns a formatted string for the HUD like "-2" or "E" or "+4"
     */
    public String getToParString() {
        int toPar = getTotalToPar();
        if (toPar == 0) return "E";
        return (toPar > 0 ? "+" : "") + toPar;
    }
}