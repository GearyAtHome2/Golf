package org.example.input;

public interface GameInputProcessor {
    void update(float delta);
    boolean isActionPressed(Action action);
    boolean isActionJustPressed(Action action);

    enum Action {
        CHARGE_SHOT,
        RESET_BALL,
        NEW_LEVEL,
        PAUSE,
        MAIN_MENU,
        HELP,
        CAM_CONFIG,
        CYCLE_ANIMATION,
        CYCLE_DIFFICULTY,
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
        CANCEL_MENU
    }
}
