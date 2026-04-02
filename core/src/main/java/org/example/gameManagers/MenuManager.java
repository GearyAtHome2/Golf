package org.example.gameManagers;

import org.example.GameConfig;
import org.example.hud.renderer.MainMenuRenderer.MenuState;
import org.example.input.GameInputProcessor;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;

public class MenuManager {
    private MenuState currentMenuState = MenuState.MAIN;
    private int menuSelection = 0;
    private int pendingMatchMode = 0;

    public void handleInput(GameInputProcessor input, MenuHandler callback, CompetitiveSessions sessions) {
        int maxSelection = getMaxSelection();
        int oldSelection = menuSelection;

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_UP)) {
            do {
                menuSelection = (menuSelection - 1 + maxSelection) % maxSelection;
            } while (isSelectionLocked(sessions) && menuSelection != oldSelection);
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_DOWN)) {
            do {
                menuSelection = (menuSelection + 1) % maxSelection;
            } while (isSelectionLocked(sessions) && menuSelection != oldSelection);
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU) && currentMenuState != MenuState.MAIN) {
            handleBackNavigation();
            return;
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_SELECT)) {
            processSelection(callback, sessions);
        }
    }

    public void handleExternalSelection(int index, MenuHandler callback, CompetitiveSessions sessions) {
        this.menuSelection = index;
        processSelection(callback, sessions);
    }

    private void processSelection(MenuHandler callback, CompetitiveSessions sessions) {
        if (isSelectionLocked(sessions)) return;

        switch (currentMenuState) {
            case MAIN -> handleMain(callback);
            case EIGHTEEN_HOLES -> handleEighteen(callback);
            case DIFFICULTY_SELECT -> handleDifficulty(callback);
            case PRACTICE -> handlePractice(callback);
        }
    }

    private boolean isSelectionLocked(CompetitiveSessions sessions) {
        if (currentMenuState == MenuState.EIGHTEEN_HOLES) {
            if (menuSelection == 0) return sessions.standard != null && sessions.standard.isFinished();
            if (menuSelection == 1) return sessions.daily18 != null && sessions.daily18.isFinished();
            if (menuSelection == 2) return sessions.daily9 != null && sessions.daily9.isFinished();
            if (menuSelection == 3) return sessions.daily1 != null && sessions.daily1.isFinished();
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
            case 1 -> callback.onSelectDaily18();
            case 2 -> callback.onSelectDaily9();
            case 3 -> callback.onSelectDaily1();
            case 4 -> { currentMenuState = MenuState.MAIN; menuSelection = 1; }
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
            case EIGHTEEN_HOLES -> 5;
            case PRACTICE -> 3;
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
        void onSelectDaily18();
        void onSelectDaily9();
        void onSelectDaily1();
        void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode);
        void onStartPracticeRange();
        void onStartPuttingGreen();
    }
}
