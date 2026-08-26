package com.gearygolf.golf.input;

import com.badlogic.gdx.math.Vector2;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.ball.ShotController;

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
    private float rotateY = 0;
    private int lastPointerCount = 0;
    private float lastPinchDistance = 0;
    private float lastPinchAngle = Float.NaN; // NaN = not yet set this pinch session
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
        // If no fingers are on the screen, reset per-touch state
        if (!com.badlogic.gdx.Gdx.input.isTouched()) {
            isConsumed = false;
            lastPointerCount = 0;
            pressedMap.put(Action.SECONDARY_ACTION, false);
        }

        for (Action action : Action.values()) {
            currentFrameJustPressedMap.put(action, accumulatedJustPressedMap.get(action));
            accumulatedJustPressedMap.put(action, false);
        }
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        if (isConsumed) return true;

        if (Platform.isAndroid()) {
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

            // Two-finger pan/zoom/rotate is handled entirely in pinch(); skip here.
            if (isMultiTouch) return true;
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

        pressedMap.put(Action.SECONDARY_ACTION, true);

        // 1. Zoom from pinch distance delta
        float currentDistance = pointer1.dst(pointer2);
        if (lastPinchDistance > 0) {
            float deltaDistance = lastPinchDistance - currentDistance;
            this.scrollY += (deltaDistance * 0.05f);
        }
        lastPinchDistance = currentDistance;

        // 2. Rotate from angle change between the two fingers
        float currentAngle = (float) Math.toDegrees(Math.atan2(pointer2.y - pointer1.y, pointer2.x - pointer1.x));
        if (!Float.isNaN(lastPinchAngle)) {
            float delta = currentAngle - lastPinchAngle;
            // Wrap to [-180, 180] to avoid discontinuity at ±180°
            if (delta > 180f) delta -= 360f;
            if (delta < -180f) delta += 360f;
            this.rotateY += delta;
        }
        lastPinchAngle = currentAngle;

        return true;
    }

    @Override
    public void pinchStop() {
        lastPinchDistance = 0;
        lastPinchAngle = Float.NaN;

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
        this.rotateY = 0;
    }

    @Override
    public float getActionValue(Action action) {
        if (action == Action.DRAG_X) return dragX;
        if (action == Action.DRAG_Y) return dragY;
        if (action == Action.SCROLL_Y) return scrollY;
        if (action == Action.PINCH_ROTATE) return rotateY;
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

    public void setShotController(com.gearygolf.golf.ball.ShotController controller) {
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