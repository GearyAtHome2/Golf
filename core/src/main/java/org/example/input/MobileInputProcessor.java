package org.example.input;

import java.util.EnumMap;
import java.util.Map;

public class MobileInputProcessor implements GameInputProcessor {

    private final Map<Action, Boolean> pressedMap = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> justPressedMap = new EnumMap<>(Action.class);

    public MobileInputProcessor() {
        for (Action action : Action.values()) {
            pressedMap.put(action, false);
            justPressedMap.put(action, false);
        }
    }

    @Override
    public void update(float delta) {
        // Reset justPressedMap at the end of the frame or start of next
        // In LibGDX, we can reset it here, but we need to make sure 
        // it stays true long enough for one update cycle.
        for (Action action : Action.values()) {
            justPressedMap.put(action, false);
        }
    }

    @Override
    public boolean isActionPressed(Action action) {
        return pressedMap.get(action);
    }

    @Override
    public boolean isActionJustPressed(Action action) {
        return justPressedMap.get(action);
    }

    /**
     * Sets the state of an action. To be called by UI elements (buttons).
     */
    public void setActionState(Action action, boolean pressed) {
        if (pressed && !pressedMap.get(action)) {
            justPressedMap.put(action, true);
        }
        pressedMap.put(action, pressed);
    }

    public void triggerAction(Action action) {
        justPressedMap.put(action, true);
        pressedMap.put(action, true);
    }
}
