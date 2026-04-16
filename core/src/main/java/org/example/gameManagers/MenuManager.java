package org.example.gameManagers;

import org.example.GameConfig;
import org.example.hud.renderer.MainMenuRenderer.MenuState;
import org.example.input.GameInputProcessor;
import org.example.scoreBoard.CourseType;
import org.example.scoreBoard.DailyStatusResolver;
import org.example.scoreBoard.DailySubmissionCache;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;
import org.example.terrain.level.LevelData;
import org.example.tutorial.TutorialPrefs;

public class MenuManager {
    private MenuState currentMenuState = MenuState.MAIN;
    private int menuSelection = 0;
    private int pendingMatchMode = 0;
    private LevelData.Archetype selectedArchetype = null;
    private int mapScrollOffset = 0;

    private static final int SCROLL_WINDOW = 8;
    private static final int MAP_SELECT_TOTAL = LevelData.Archetype.values().length + 1; // archetypes + BACK

    public void handleInput(GameInputProcessor input, MenuHandler callback, CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        int maxSelection = getMaxSelection();
        int oldSelection = menuSelection;

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_UP)) {
            do {
                menuSelection = (menuSelection - 1 + maxSelection) % maxSelection;
            } while (isSelectionLocked(sessions, dailyCache) && menuSelection != oldSelection);
            if (currentMenuState == MenuState.MAP_SELECT) adjustScrollOffset();
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_DOWN)) {
            do {
                menuSelection = (menuSelection + 1) % maxSelection;
            } while (isSelectionLocked(sessions, dailyCache) && menuSelection != oldSelection);
            if (currentMenuState == MenuState.MAP_SELECT) adjustScrollOffset();
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU) && currentMenuState != MenuState.MAIN) {
            handleBackNavigation();
            return;
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_SELECT)) {
            processSelection(callback, sessions, dailyCache);
        }
    }

    private void adjustScrollOffset() {
        if (menuSelection < mapScrollOffset) {
            mapScrollOffset = menuSelection;
        } else if (menuSelection >= mapScrollOffset + SCROLL_WINDOW) {
            mapScrollOffset = menuSelection - SCROLL_WINDOW + 1;
        }
        mapScrollOffset = Math.max(0, Math.min(mapScrollOffset, MAP_SELECT_TOTAL - SCROLL_WINDOW));
    }

    public void handleExternalSelection(int index, MenuHandler callback, CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        this.menuSelection = index;
        processSelection(callback, sessions, dailyCache);
    }

    private void processSelection(MenuHandler callback, CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        if (isSelectionLocked(sessions, dailyCache)) return;

        switch (currentMenuState) {
            case MAIN -> handleMain(callback);
            case PLAY_OPTIONS -> handlePlayOptions(callback);
            case MAP_SELECT -> handleMapSelect(callback);
            case EIGHTEEN_HOLES -> handleEighteen(callback, sessions);
            case DIFFICULTY_SELECT -> handleDifficulty(callback);
            case PRACTICE -> handlePractice(callback);
        }
    }

    private boolean isSelectionLocked(CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        if (currentMenuState == MenuState.EIGHTEEN_HOLES) {
            if (menuSelection == 0) return sessions.standard != null && sessions.standard.isFinished();
            if (menuSelection == 1) return DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, sessions.daily18, dailyCache);
            if (menuSelection == 2) return DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_9,  sessions.daily9,  dailyCache);
            if (menuSelection == 3) return DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_1,  sessions.daily1,  dailyCache);
        }
        return false;
    }

    public void navigateBack() {
        if (currentMenuState != MenuState.MAIN) handleBackNavigation();
    }

    private void handleBackNavigation() {
        switch (currentMenuState) {
            case MAP_SELECT -> { currentMenuState = MenuState.PLAY_OPTIONS; menuSelection = 1; mapScrollOffset = 0; }
            case PLAY_OPTIONS -> { currentMenuState = MenuState.MAIN; menuSelection = 0; }
            case DIFFICULTY_SELECT -> { currentMenuState = MenuState.EIGHTEEN_HOLES; menuSelection = pendingMatchMode; }
            default -> { currentMenuState = MenuState.MAIN; menuSelection = 0; }
        }
    }

    private void handleMain(MenuHandler callback) {
        if (!TutorialPrefs.isComplete()) {
            // TUTORIAL(0) PLAY(1) COMPETITIVE(2) INSTRUCTIONS(3) PRACTICE(4) LOG OUT(5)
            switch (menuSelection) {
                case 0 -> callback.onStartTutorial();
                case 1 -> { currentMenuState = MenuState.PLAY_OPTIONS; menuSelection = 0; }
                case 2 -> { currentMenuState = MenuState.EIGHTEEN_HOLES; menuSelection = 0; }
                case 3 -> callback.onShowInstructions();
                case 4 -> { currentMenuState = MenuState.PRACTICE; menuSelection = 0; }
                case 5 -> callback.onLogout();
            }
        } else {
            // PLAY(0) COMPETITIVE(1) INSTRUCTIONS(2) PRACTICE(3) LOG OUT(4)
            switch (menuSelection) {
                case 0 -> { currentMenuState = MenuState.PLAY_OPTIONS; menuSelection = 0; }
                case 1 -> { currentMenuState = MenuState.EIGHTEEN_HOLES; menuSelection = 0; }
                case 2 -> callback.onShowInstructions();
                case 3 -> { currentMenuState = MenuState.PRACTICE; menuSelection = 0; }
                case 4 -> callback.onLogout();
            }
        }
    }

    private void handlePlayOptions(MenuHandler callback) {
        switch (menuSelection) {
            case 0 -> callback.onStartQuickPlay();
            case 1 -> { currentMenuState = MenuState.MAP_SELECT; menuSelection = 0; mapScrollOffset = 0; }
            case 2 -> callback.onStartWithClipboardSeed();
            case 3 -> { currentMenuState = MenuState.MAIN; menuSelection = 0; }
        }
    }

    private void handleMapSelect(MenuHandler callback) {
        LevelData.Archetype[] archetypes = LevelData.Archetype.values();
        if (menuSelection < archetypes.length) {
            callback.onStartWithArchetype(archetypes[menuSelection]);
        } else {
            currentMenuState = MenuState.PLAY_OPTIONS;
            menuSelection = 1;
            mapScrollOffset = 0;
        }
    }

    private void handleEighteen(MenuHandler callback, CompetitiveSessions sessions) {
        switch (menuSelection) {
            case 0 -> callback.onSelectStandard18();
            case 1 -> handleDailySelect(callback, sessions != null ? sessions.daily18 : null, CourseType.HOLES_18, callback::onSelectDaily18);
            case 2 -> handleDailySelect(callback, sessions != null ? sessions.daily9  : null, CourseType.HOLES_9,  callback::onSelectDaily9);
            case 3 -> handleDailySelect(callback, sessions != null ? sessions.daily1  : null, CourseType.HOLES_1,  callback::onSelectDaily1);
            case 4 -> { currentMenuState = MenuState.MAIN; menuSelection = 1; }
        }
    }

    private void handleDailySelect(MenuHandler callback, GameSession session, CourseType type, Runnable normalStart) {
        if (session != null && session.isFinished() && !session.isSubmitted()) {
            callback.onResubmitDaily(type);
        } else {
            normalStart.run();
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
        if (TutorialPrefs.isComplete()) {
            // DRIVING RANGE(0) PUTTING GREEN(1) TUTORIAL(2) BACK(3)
            switch (menuSelection) {
                case 0 -> callback.onStartPracticeRange();
                case 1 -> callback.onStartPuttingGreen();
                case 2 -> callback.onStartTutorial();
                case 3 -> { currentMenuState = MenuState.MAIN; menuSelection = 3; }
            }
        } else {
            // DRIVING RANGE(0) PUTTING GREEN(1) BACK(2)
            switch (menuSelection) {
                case 0 -> callback.onStartPracticeRange();
                case 1 -> callback.onStartPuttingGreen();
                case 2 -> { currentMenuState = MenuState.MAIN; menuSelection = 4; }
            }
        }
    }

    private int getMaxSelection() {
        return switch (currentMenuState) {
            case MAIN -> TutorialPrefs.isComplete() ? 5 : 6;
            case PLAY_OPTIONS -> 4;
            case MAP_SELECT -> MAP_SELECT_TOTAL;
            case EIGHTEEN_HOLES -> 5;
            case PRACTICE -> TutorialPrefs.isComplete() ? 4 : 3;
            case DIFFICULTY_SELECT -> GameConfig.Difficulty.values().length + 1;
        };
    }

    public MenuState getCurrentMenuState() { return currentMenuState; }
    public int getMenuSelection() { return menuSelection; }
    public int getMapScrollOffset() { return mapScrollOffset; }
    public LevelData.Archetype getSelectedArchetype() { return selectedArchetype; }
    public void setSelectedArchetype(LevelData.Archetype arch) { this.selectedArchetype = arch; }
    public void setPendingMatchMode(int mode) { this.pendingMatchMode = mode; }
    public void setMenuState(MenuState state) { this.currentMenuState = state; }
    public void setMenuSelection(int selection) { this.menuSelection = selection; }

    public interface MenuHandler {
        void onStartQuickPlay();
        void onShowInstructions();
        void onStartWithClipboardSeed();
        void onStartWithArchetype(LevelData.Archetype archetype);
        void onSelectStandard18();
        void onSelectDaily18();
        void onSelectDaily9();
        void onSelectDaily1();
        void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode);
        void onStartPracticeRange();
        void onStartPuttingGreen();
        void onLogout();
        /** Called when a completed but unsubmitted daily session is selected from the menu. */
        default void onResubmitDaily(CourseType type) {}
        /** Launch the tutorial. */
        default void onStartTutorial() {}
    }
}
