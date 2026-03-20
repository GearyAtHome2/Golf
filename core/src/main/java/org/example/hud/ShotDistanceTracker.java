package org.example.hud;

import org.example.ball.Ball;

public class ShotDistanceTracker {
    private float persistentShotDistance = 0f;
    private float maxDistanceSeen = 0f;
    private float displayTimer = 0f;
    private boolean ballWasActive = false;

    public void update(float delta, Ball ball) {
        Ball.State s = ball.getState();
        boolean isMoving = (s == Ball.State.AIR || s == Ball.State.ROLLING || s == Ball.State.CONTACT);

        if (isMoving) {
            ballWasActive = true;
            float currentShotDist = ball.getShotDistance();
            if (currentShotDist > maxDistanceSeen) maxDistanceSeen = currentShotDist;
            displayTimer = 0f;
        } else if (s == Ball.State.STATIONARY && ballWasActive) {
            persistentShotDistance = maxDistanceSeen;
            displayTimer = 5.0f;
            ballWasActive = false;
            maxDistanceSeen = 0f;
        }

        if (displayTimer > 0) displayTimer -= delta;
    }

    public float getDisplayDistance() {
        return persistentShotDistance;
    }

    public boolean shouldShow() {
        return displayTimer > 0;
    }

    public float getTimer() {
        return displayTimer;
    }

    public void reset() {
        persistentShotDistance = 0;
        maxDistanceSeen = 0;
        displayTimer = 0;
        ballWasActive = false;
    }
}