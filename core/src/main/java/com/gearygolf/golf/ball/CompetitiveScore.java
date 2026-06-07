package com.gearygolf.golf.ball;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.gearygolf.golf.terrain.level.LevelData;
import java.util.List;

public class CompetitiveScore implements Json.Serializable {
    private int totalHoles = 18;
    private int[] pars = new int[totalHoles];
    private int[] scores = new int[totalHoles];
    private int currentHoleIndex = 0;

    public CompetitiveScore() {
        for (int i = 0; i < totalHoles; i++) {
            pars[i] = 4;
            scores[i] = 0;
        }
    }

    public CompetitiveScore(List<LevelData> course) {
        totalHoles = course.size();
        pars = new int[totalHoles];
        scores = new int[totalHoles];
        for (int i = 0; i < totalHoles; i++) {
            pars[i] = course.get(i).getPar();
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
        if (toPar == 0) return "Even";
        return (toPar > 0 ? "+" : "") + toPar;
    }

    public int[] getScores() { return scores; }
    public int[] getPars() { return pars; }

    // -------------------------------------------------------------------------
    // Explicit serialization — avoids reflection-based int[] issues on Android
    // -------------------------------------------------------------------------

    @Override
    public void write(Json json) {
        json.writeValue("totalHoles", totalHoles);
        json.writeValue("currentHoleIndex", currentHoleIndex);
        json.writeArrayStart("pars");
        for (int p : pars) json.writeValue(p);
        json.writeArrayEnd();
        json.writeArrayStart("scores");
        for (int s : scores) json.writeValue(s);
        json.writeArrayEnd();
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        totalHoles = jsonData.getInt("totalHoles", 18);
        currentHoleIndex = jsonData.getInt("currentHoleIndex", 0);
        pars = new int[totalHoles];
        scores = new int[totalHoles];

        JsonValue parsArr = jsonData.get("pars");
        if (parsArr != null) {
            int i = 0;
            for (JsonValue v = parsArr.child; v != null && i < totalHoles; v = v.next, i++) {
                pars[i] = v.asInt();
            }
        } else {
            java.util.Arrays.fill(pars, 4);
        }

        JsonValue scoresArr = jsonData.get("scores");
        if (scoresArr != null) {
            int i = 0;
            for (JsonValue v = scoresArr.child; v != null && i < totalHoles; v = v.next, i++) {
                scores[i] = v.asInt();
            }
        }
    }
}