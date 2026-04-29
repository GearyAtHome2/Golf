package org.example.scoreBoard;

import org.example.session.GameSession;

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
    HOLES_1(
            "entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole/entries",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole:runQuery",
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores/1_hole:runAggregationQuery"
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
        // Note: Make sure these GameMode enums match your latest GameSession implementation
        return switch (mode) {
            case DAILY_1 -> HOLES_1;
            case DAILY_9 -> HOLES_9;
            default -> HOLES_18;
        };
    }
}