package org.example.gameManagers;

import com.badlogic.gdx.math.Vector3;
import org.example.ball.Ball;
import org.example.terrain.Terrain;

public class HazardManager {
    private float resetTimer = 0f;
    private static final float RESET_DELAY = 1.0f;
    private boolean hasCurrentBallBeenHit = false;

    public interface HazardListener {
        void onOutOfBounds();
        void onWaterHazard();
        void onPracticeReset();
    }

    public void update(float delta, Ball ball, Terrain terrain, boolean isPracticeRange, HazardListener listener) {
        // Instant Hazards
        if (!isPracticeRange && checkInstantHazards(ball, terrain, listener)) return;

        // Stationary/Water Timers
        if (ball.getState() == Ball.State.AIR && !ball.isInWater(terrain)) {
            resetTimer = 0f;
            return;
        }

        if (shouldActivateTimer(ball, terrain, isPracticeRange)) {
            resetTimer += delta;
            if (resetTimer >= RESET_DELAY) {
                handleTimerThreshold(ball, terrain, isPracticeRange, listener);
                resetTimer = 0f;
            }
        } else {
            resetTimer = 0f;
        }
    }

    private boolean checkInstantHazards(Ball ball, Terrain terrain, HazardListener listener) {
        Vector3 pos = ball.getPosition();
        if (terrain.isPointOutOfBounds(pos.x, pos.z)) {
            listener.onOutOfBounds();
            return true;
        }
        return false;
    }

    private boolean shouldActivateTimer(Ball ball, Terrain terrain, boolean isPracticeRange) {
        if (!hasCurrentBallBeenHit) return false;
        return ball.isInWater(terrain) || isPracticeRange || ball.getState() == Ball.State.STATIONARY;
    }

    private void handleTimerThreshold(Ball ball, Terrain terrain, boolean isPracticeRange, HazardListener listener) {
        if (isPracticeRange) {
            listener.onPracticeReset();
        } else if (ball.isInWater(terrain)) {
            listener.onWaterHazard();
        }
    }

    public void setBallHit(boolean hit) { this.hasCurrentBallBeenHit = hit; }
    public void resetTimer() { this.resetTimer = 0f; }
}