package org.example.input;

import com.badlogic.gdx.math.Vector2;
import org.example.ball.ShotController;

import java.util.EnumMap;
import java.util.Map;

public class MobileInputProcessor extends com.badlogic.gdx.input.GestureDetector.GestureAdapter implements GameInputProcessor {

    private final Map<Action, Boolean> pressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> accumulatedJustPressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> currentFrameJustPressedMap = new EnumMap<>(Action.class);

    private ShotController shotController;

    private float dragX = 0;
    private float dragY = 0;
    private float scrollY = 0;
    private int lastPointerCount = 0;
    private final Vector2 lastPinchMidpoint = new Vector2();
    private float lastPinchDistance = 0;
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
        if (isConsumed) return true;

        if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            int currentPointers = 0;
            if (com.badlogic.gdx.Gdx.input.isTouched(0)) currentPointers++;
            if (com.badlogic.gdx.Gdx.input.isTouched(1)) currentPointers++;

            if (currentPointers != lastPointerCount) {
                this.dragX = 0;
                this.dragY = 0;
                lastPointerCount = currentPointers;
                return true;
            }

            boolean isMultiTouch = currentPointers >= 2;
            pressedMap.put(Action.SECONDARY_ACTION, isMultiTouch);
        }

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

        // NEW: Force the secondary action flag to true so the camera knows we are in multi-touch mode
        pressedMap.put(Action.SECONDARY_ACTION, true);

        // 1. Handle Zoom
        float currentDistance = pointer1.dst(pointer2);
        if (lastPinchDistance > 0) {
            float deltaDistance = lastPinchDistance - currentDistance;
            this.scrollY += (deltaDistance * 0.05f);
        }
        lastPinchDistance = currentDistance;

        // 2. Handle Pan (Midpoint Logic)
        float currentMidX = (pointer1.x + pointer2.x) / 2f;
        float currentMidY = (pointer1.y + pointer2.y) / 2f;

        if (lastPinchMidpoint.x != 0 || lastPinchMidpoint.y != 0) {
            float deltaMidX = currentMidX - lastPinchMidpoint.x;
            float deltaMidY = currentMidY - lastPinchMidpoint.y;

            this.dragX += deltaMidX;
            this.dragY += deltaMidY;
        }

        lastPinchMidpoint.set(currentMidX, currentMidY);
        return true;
    }

    @Override
    public void pinchStop() {
        lastPinchDistance = 0;
        lastPinchMidpoint.set(0, 0);

        // NEW: Reset the secondary action flag when fingers are lifted
        pressedMap.put(Action.SECONDARY_ACTION, false);
        lastPointerCount = 0;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        if (isConsumed) return true;
        triggerAction(Action.TAP);
        return true;
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
        if (!isClubChangeAllowed(action)) return;

        if (pressed && !pressedMap.get(action)) {
            accumulatedJustPressedMap.put(action, true);
        }
        pressedMap.put(action, pressed);
    }

    public void triggerAction(Action action) {
        if (!isClubChangeAllowed(action)) return;

        accumulatedJustPressedMap.put(action, true);
        pressedMap.put(action, true);
    }
}