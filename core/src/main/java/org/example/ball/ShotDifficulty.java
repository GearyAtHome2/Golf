package org.example.ball;

public class ShotDifficulty {
    // The "Where": Base multiplier from Fairway, Rough, Bunker, etc.
    public float terrainDifficulty = 1.0f;

    // The "How": Combined incline/decline and side-hill lie.
    public float slopeDifficulty = 1.0f;

    // The "What": Larger/longer clubs are harder to time perfectly.
    public float clubDifficulty = 1.0f;

    // The "Intensity": Scaling based on how much spin/power is being attempted.
    public float swingDifficulty = 1.0f;

    public ShotDifficulty() {
    }

    public float getCombinedValue() {
        return terrainDifficulty * slopeDifficulty * clubDifficulty * swingDifficulty;
    }
}