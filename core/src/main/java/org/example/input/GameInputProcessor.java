package org.example.input;

public interface GameInputProcessor {
    void update(float delta);

    boolean isActionPressed(Action action);

    boolean isActionJustPressed(Action action);

    float getActionValue(Action action);

    enum Action {
        CHARGE_SHOT,
        RESET_BALL,
        NEW_LEVEL,
        PAUSE,
        MAIN_MENU,
        SUBMIT_SCORE,
        HELP,
        CAM_CONFIG,
        CYCLE_ANIMATION,
        CYCLE_DIFFICULTY,
        SPIN_LEFT,
        SPIN_RIGHT,
        TOGGLE_PARTICLES,
        COPY_SEED,
        CLUB_UP,
        CLUB_DOWN,
        SHOW_RANGE,
        PROJECTION,
        MAX_POWER_SHOT,
        STOP_NEEDLE,
        OVERHEAD_VIEW,
        SPIN_UP,
        SPIN_DOWN,
        MENU_UP,
        MENU_DOWN,
        MENU_SELECT,
        CANCEL_MENU,
        SPEED_UP,
        SPEED_DOWN,
        SELECT_OPTION_0,
        SELECT_OPTION_1,
        SELECT_OPTION_2,
        SELECT_OPTION_3,
        SELECT_OPTION_4,
        SELECT_OPTION_5,
        DRAG_X,
        DRAG_Y,
        SCROLL_Y,
        SECONDARY_ACTION,
        TAP
    }
}
