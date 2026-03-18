package org.example.input;

import com.badlogic.gdx.math.Vector2;
import org.example.ball.ShotController;

import java.util.EnumMap;
import java.util.Map;

public class MobileInputProcessor extends com.badlogic.gdx.input.GestureDetector.GestureAdapter implements GameInputProcessor {

    private final Map<Action, Boolean> pressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> accumulatedJustPressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> currentFrameJustPressedMap = new EnumMap<>(Action.class);

    private ShotController shotController; // Add this

    private float dragX = 0;
    private float dragY = 0;
    private float scrollY = 0;

    // Multi-touch tracking
    private float lastPinchDistance = 0;

    // Flag to prevent camera movement when interacting with HUD
    private boolean isConsumed = false;

    public MobileInputProcessor() {
        for (Action action : Action.values()) {
            pressedMap.put(action, false);
            accumulatedJustPressedMap.put(action, false);
            currentFrameJustPressedMap.put(action, false);
        }
    }

    /**
     * Prevents this processor from updating drag/zoom values until touches are released.
     */
    public void consumeCurrentTouch() {
        this.isConsumed = true;
    }

    @Override
    public void update(float delta) {
        // If no fingers are on the screen, reset the consumption flag
        if (!com.badlogic.gdx.Gdx.input.isTouched()) {
            isConsumed = false;
        }

        for (Action action : Action.values()) {
            currentFrameJustPressedMap.put(action, accumulatedJustPressedMap.get(action));
            accumulatedJustPressedMap.put(action, false);
        }
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (isConsumed) return true; // Pretend we handled it, but do nothing

        this.dragX += deltaX;
        this.dragY += deltaY;
        return true;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        return isConsumed;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
        if (isConsumed) return true;

        float currentDistance = pointer1.dst(pointer2);
        if (lastPinchDistance > 0) {
            float deltaDistance = lastPinchDistance - currentDistance;
            this.scrollY += (deltaDistance * 0.05f);
        }
        lastPinchDistance = currentDistance;
        return true;
    }

    @Override
    public void pinchStop() {
        lastPinchDistance = 0;
    }

    public void resetDrags() {
        this.dragX = 0;
        this.dragY = 0;
        this.scrollY = 0;
    }

    @Override
    public float getActionValue(Action action) {
        if (action == Action.DRAG_X) return dragX;
        if (action == Action.DRAG_Y) return dragY;
        if (action == Action.SCROLL_Y) return scrollY;
        return 0f;
    }

    @Override
    public boolean isActionPressed(Action action) {
        return pressedMap.get(action);
    }

    @Override
    public boolean isActionJustPressed(Action action) {
        return currentFrameJustPressedMap.get(action);
    }

    public void setShotController(org.example.ball.ShotController controller) {
        this.shotController = controller;
    }

    // Helper to check if club switching is allowed
    private boolean isClubChangeAllowed(Action action) {
        if (action == Action.CLUB_UP || action == Action.CLUB_DOWN) {
            return shotController == null || !shotController.isCharging();
        }
        return true;
    }

    public void setActionState(Action action, boolean pressed) {
        if (!isClubChangeAllowed(action)) return; // BLOCKING PASSIVE CHECK

        if (pressed && !pressedMap.get(action)) {
            accumulatedJustPressedMap.put(action, true);
        }
        pressedMap.put(action, pressed);
    }

    public void triggerAction(Action action) {
        if (!isClubChangeAllowed(action)) return; // BLOCKING PASSIVE CHECK

        accumulatedJustPressedMap.put(action, true);
        pressedMap.put(action, true);
    }
}