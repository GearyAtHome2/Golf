package org.example.input;

import java.util.EnumMap;
import java.util.Map;

public class MobileInputProcessor extends com.badlogic.gdx.input.GestureDetector.GestureAdapter implements GameInputProcessor {

    private final Map<Action, Boolean> pressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> accumulatedJustPressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> currentFrameJustPressedMap = new EnumMap<>(Action.class);
    private float dragX = 0;
    private float dragY = 0;
    private float scrollY = 0;

    public MobileInputProcessor() {
        for (Action action : Action.values()) {
            pressedMap.put(action, false);
            accumulatedJustPressedMap.put(action, false);
            currentFrameJustPressedMap.put(action, false);
        }
    }

    @Override
    public void update(float delta) {
        // Transfer accumulated triggers to the current frame and clear accumulation
        for (Action action : Action.values()) {
            currentFrameJustPressedMap.put(action, accumulatedJustPressedMap.get(action));
            accumulatedJustPressedMap.put(action, false);
        }
        dragX = 0;
        dragY = 0;
        scrollY = 0;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        dragX = deltaX;
        dragY = deltaY;
        return true;
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

    /**
     * Sets the state of an action. To be called by UI elements (buttons).
     */
    public void setActionState(Action action, boolean pressed) {
        if (pressed && !pressedMap.get(action)) {
            accumulatedJustPressedMap.put(action, true);
            System.out.println("[DEBUG_LOG] Mobile action just pressed (SET): " + action);
        }
        pressedMap.put(action, pressed);
    }

    public void triggerAction(Action action) {
        accumulatedJustPressedMap.put(action, true);
        pressedMap.put(action, true); // For actions that might also check isPressed
        System.out.println("[DEBUG_LOG] Mobile action triggered: " + action);
    }
}
