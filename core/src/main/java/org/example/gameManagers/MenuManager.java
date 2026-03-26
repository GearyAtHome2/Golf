package org.example.gameManagers;

import org.example.GameConfig;
import org.example.hud.renderer.MainMenuRenderer.MenuState;
import org.example.input.GameInputProcessor;

public class MenuManager {
    private MenuState currentMenuState = MenuState.MAIN;
    private int menuSelection = 0;
    private int pendingMatchMode = 0;

    public void handleInput(GameInputProcessor input, MenuHandler callback, GameSession standard, GameSession daily) {
        int maxSelection = getMaxSelection();
        int oldSelection = menuSelection;

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_UP)) {
            do {
                menuSelection = (menuSelection - 1 + maxSelection) % maxSelection;
            } while (isSelectionLocked(standard, daily) && menuSelection != oldSelection);
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_DOWN)) {
            do {
                menuSelection = (menuSelection + 1) % maxSelection;
            } while (isSelectionLocked(standard, daily) && menuSelection != oldSelection);
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU) && currentMenuState != MenuState.MAIN) {
            handleBackNavigation();
            return;
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_SELECT)) {
            processSelection(callback, standard, daily);
        }
    }

    /**
     * Called by Android UI buttons to bypass the MENU_SELECT action check.
     */
    public void handleExternalSelection(int index, MenuHandler callback, GameSession standard, GameSession daily) {
        this.menuSelection = index;
        processSelection(callback, standard, daily);
    }

    private void processSelection(MenuHandler callback, GameSession standard, GameSession daily) {
        if (isSelectionLocked(standard, daily)) return;

        switch (currentMenuState) {
            case MAIN -> handleMain(callback);
            case EIGHTEEN_HOLES -> handleEighteen(callback);
            case DIFFICULTY_SELECT -> handleDifficulty(callback);
            case PRACTICE -> handlePractice(callback);
        }
    }

    private boolean isSelectionLocked(GameSession standard, GameSession daily) {
        if (currentMenuState == MenuState.EIGHTEEN_HOLES) {
            if (menuSelection == 0) return standard != null && standard.isFinished();
            if (menuSelection == 1) return daily != null && daily.isFinished();
        }
        return false;
    }

    private void handleBackNavigation() {
        if (currentMenuState == MenuState.DIFFICULTY_SELECT) {
            currentMenuState = MenuState.EIGHTEEN_HOLES;
            menuSelection = pendingMatchMode;
        } else {
            currentMenuState = MenuState.MAIN;
            menuSelection = 0;
        }
    }

    private void handleMain(MenuHandler callback) {
        switch (menuSelection) {
            case 0 -> callback.onStartQuickPlay();
            case 1 -> { currentMenuState = MenuState.EIGHTEEN_HOLES; menuSelection = 0; }
            case 2 -> callback.onShowInstructions();
            case 3 -> { currentMenuState = MenuState.PRACTICE; menuSelection = 0; }
            case 4 -> callback.onStartWithClipboardSeed();
        }
    }

    private void handleEighteen(MenuHandler callback) {
        switch (menuSelection) {
            case 0 -> callback.onSelectStandard18();
            case 1 -> callback.onSelectDailyChallenge();
            case 2 -> { currentMenuState = MenuState.MAIN; menuSelection = 1; }
        }
    }

    private void handleDifficulty(MenuHandler callback) {
        int backIndex = GameConfig.Difficulty.values().length;
        if (menuSelection == backIndex) {
            handleBackNavigation();
        } else {
            callback.onDifficultyFinalized(GameConfig.Difficulty.values()[menuSelection], pendingMatchMode);
        }
    }

    private void handlePractice(MenuHandler callback) {
        switch (menuSelection) {
            case 0 -> callback.onStartPracticeRange();
            case 1 -> callback.onStartPuttingGreen();
            case 2 -> { currentMenuState = MenuState.MAIN; menuSelection = 3; }
        }
    }

    private int getMaxSelection() {
        return switch (currentMenuState) {
            case MAIN -> 5;
            case EIGHTEEN_HOLES, PRACTICE -> 3;
            case DIFFICULTY_SELECT -> 6;
        };
    }

    public MenuState getCurrentMenuState() { return currentMenuState; }
    public int getMenuSelection() { return menuSelection; }
    public void setPendingMatchMode(int mode) { this.pendingMatchMode = mode; }
    public void setMenuState(MenuState state) { this.currentMenuState = state; }
    public void setMenuSelection(int selection) { this.menuSelection = selection; }

    public interface MenuHandler {
        void onStartQuickPlay();
        void onShowInstructions();
        void onStartWithClipboardSeed();
        void onSelectStandard18();
        void onSelectDailyChallenge();
        void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode);
        void onStartPracticeRange();
        void onStartPuttingGreen();
    }
}