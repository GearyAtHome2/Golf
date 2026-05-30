package com.gearygolf.golf.scoreBoard;

import com.gearygolf.golf.session.GameSession;

public enum CourseType {
    HOLES_18(
            "entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/18_holes/entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/18_holes:runQuery",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/18_holes:runAggregationQuery"
    ),
    HOLES_9(
            "entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/9_holes/entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/9_holes:runQuery",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/9_holes:runAggregationQuery"
    ),
    // Reuses the existing 1_hole collection (backward-compatible with pre-split leaderboard data)
    HOLES_1_PAR3(
            "entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole/entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole:runQuery",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole:runAggregationQuery"
    ),
    HOLES_1_PAR4(
            "entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole_par4/entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole_par4:runQuery",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole_par4:runAggregationQuery"
    ),
    HOLES_1_PAR5(
            "entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole_par5/entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole_par5:runQuery",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole_par5:runAggregationQuery"
    );

    public final String collectionId;
    public final String baseUrl;
    public final String queryUrl;
    public final String aggregationQueryUrl;

    CourseType(String collectionId, String baseUrl, String queryUrl, String aggregationQueryUrl) {
        this.collectionId = collectionId;
        this.baseUrl = baseUrl;
        this.queryUrl = queryUrl;
        this.aggregationQueryUrl = aggregationQueryUrl;
    }

    public static CourseType fromMode(GameSession.GameMode mode) {
        return switch (mode) {
            case DAILY_PAR3 -> HOLES_1_PAR3;
            case DAILY_PAR4 -> HOLES_1_PAR4;
            case DAILY_PAR5 -> HOLES_1_PAR5;
            case DAILY_9    -> HOLES_9;
            default         -> HOLES_18;
        };
    }

    /** Returns true for all 1-hole daily par variants. */
    public boolean isOneHole() {
        return this == HOLES_1_PAR3 || this == HOLES_1_PAR4 || this == HOLES_1_PAR5;
    }
}
