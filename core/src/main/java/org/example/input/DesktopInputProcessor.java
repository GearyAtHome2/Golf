package org.example.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.Map;

public class DesktopInputProcessor extends com.badlogic.gdx.InputAdapter implements GameInputProcessor {

    private final Map<Action, Integer> keyMap = new EnumMap<>(Action.class);
    private float accumulatedScroll = 0f;
    private float currentFrameScroll = 0f;

    public DesktopInputProcessor() {
        keyMap.put(Action.CHARGE_SHOT, Input.Keys.SPACE);
        keyMap.put(Action.RESET_BALL, Input.Keys.R);
        keyMap.put(Action.NEW_LEVEL, Input.Keys.N);
        keyMap.put(Action.PAUSE, Input.Keys.ESCAPE);
        keyMap.put(Action.MAIN_MENU, Input.Keys.M);
        keyMap.put(Action.HELP, Input.Keys.I);
        keyMap.put(Action.CAM_CONFIG, Input.Keys.O);
        keyMap.put(Action.TOGGLE_PARTICLES, Input.Keys.L);
        keyMap.put(Action.COPY_SEED, Input.Keys.C);
        keyMap.put(Action.CLUB_UP, Input.Keys.UP);
        keyMap.put(Action.CLUB_DOWN, Input.Keys.DOWN);
        keyMap.put(Action.CYCLE_ANIMATION, Input.Keys.A);
        keyMap.put(Action.CYCLE_DIFFICULTY, Input.Keys.D);
        keyMap.put(Action.SHOW_RANGE, Input.Keys.F);
        keyMap.put(Action.PROJECTION, Input.Keys.P);
        keyMap.put(Action.STOP_NEEDLE, Input.Keys.SPACE);
        keyMap.put(Action.OVERHEAD_VIEW, Input.Keys.TAB);
        keyMap.put(Action.SPIN_UP, Input.Keys.W);
        keyMap.put(Action.SPIN_DOWN, Input.Keys.S);
        keyMap.put(Action.SPIN_LEFT, Input.Keys.A);
        keyMap.put(Action.SPIN_RIGHT, Input.Keys.D);
        keyMap.put(Action.MENU_UP, Input.Keys.UP);
        keyMap.put(Action.MENU_DOWN, Input.Keys.DOWN);
        keyMap.put(Action.SPEED_UP, Input.Keys.UP);
        keyMap.put(Action.SPEED_DOWN, Input.Keys.DOWN);
        keyMap.put(Action.MENU_SELECT, Input.Keys.ENTER);
        keyMap.put(Action.CANCEL_MENU, Input.Keys.ESCAPE);
    }

    @Override
    public void update(float delta) {
        currentFrameScroll = accumulatedScroll;
        accumulatedScroll = 0f;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (amountY != 0) {
            accumulatedScroll += amountY;
        }
        return true;
    }

    @Override
    public float getActionValue(Action action) {
        if (action == Action.DRAG_X) return Gdx.input.getDeltaX();
        if (action == Action.DRAG_Y) return Gdx.input.getDeltaY();
        if (action == Action.SCROLL_Y) {
            return currentFrameScroll;
        }
        return 0f;
    }

    @Override
    public boolean isActionPressed(Action action) {
        if (action == Action.SECONDARY_ACTION) {
            return Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        }
        if (action == Action.MAX_POWER_SHOT) {
            return (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) &&
                    Gdx.input.isKeyPressed(Input.Keys.SPACE);
        }
        Integer key = keyMap.get(action);
        return key != null && Gdx.input.isKeyPressed(key);
    }

    @Override
    public boolean isActionJustPressed(Action action) {
        if (action == Action.MAX_POWER_SHOT) {
            return (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) &&
                    Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        }
        if (action == Action.CANCEL_MENU) {
            return Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE);
        }
        Integer key = keyMap.get(action);
        return key != null && Gdx.input.isKeyJustPressed(key);
    }
}
