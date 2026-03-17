package org.example.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.Map;

public class DesktopInputProcessor implements GameInputProcessor {

    private final Map<Action, Integer> keyMap = new EnumMap<>(Action.class);

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
        keyMap.put(Action.MENU_UP, Input.Keys.UP);
        keyMap.put(Action.MENU_DOWN, Input.Keys.DOWN);
        keyMap.put(Action.MENU_SELECT, Input.Keys.ENTER);
        keyMap.put(Action.CANCEL_MENU, Input.Keys.ESCAPE);
    }

    @Override
    public void update(float delta) {
        // No per-frame updates needed for desktop keyboard
    }

    @Override
    public boolean isActionPressed(Action action) {
        if (action == Action.MAX_POWER_SHOT) {
            return (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) &&
                    Gdx.input.isKeyPressed(Input.Keys.SPACE);
        }
        if (action == Action.MENU_UP) {
            return Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        }
        if (action == Action.MENU_DOWN) {
            return Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        }
        if (action == Action.SPIN_UP) {
            return Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        }
        if (action == Action.SPIN_DOWN) {
            return Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
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
        if (action == Action.MENU_UP) {
            return Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W);
        }
        if (action == Action.MENU_DOWN) {
            return Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S);
        }
        if (action == Action.CANCEL_MENU) {
            return Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE);
        }
        Integer key = keyMap.get(action);
        return key != null && Gdx.input.isKeyJustPressed(key);
    }
}
