package org.example.gameManagers;

import org.example.GameConfig;
import org.example.hud.renderer.MainMenuRenderer;
import org.example.input.GameInputProcessor;
import org.example.session.CompetitiveSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MenuManagerCompetitiveTest {

    private MenuManager menuManager;
    private TrackingMenuHandler handler;
    private CompetitiveSessions noSessions;

    /** Minimal handler that records which callback was called. */
    private static class TrackingMenuHandler implements MenuManager.MenuHandler {
        String lastCalled = "";

        @Override public void onStartQuickPlay() { lastCalled = "quickPlay"; }
        @Override public void onShowInstructions() { lastCalled = "instructions"; }
        @Override public void onStartWithClipboardSeed() { lastCalled = "clipboardSeed"; }
        @Override public void onSelectStandard18() { lastCalled = "standard18"; }
        @Override public void onSelectDaily18() { lastCalled = "daily18"; }
        @Override public void onSelectDaily9() { lastCalled = "daily9"; }
        @Override public void onSelectDaily1() { lastCalled = "daily1"; }
        @Override public void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode) { lastCalled = "difficulty:" + mode; }
        @Override public void onStartPracticeRange() { lastCalled = "practiceRange"; }
        @Override public void onStartPuttingGreen() { lastCalled = "puttingGreen"; }
    }

    @BeforeEach
    public void setUp() {
        menuManager = new MenuManager();
        handler = new TrackingMenuHandler();
        noSessions = new CompetitiveSessions(null, null, null, null);
    }

    @Test
    public void testNavigateToCompetitiveMenuFromMain() {
        // Select index 1 from MAIN to enter EIGHTEEN_HOLES
        menuManager.handleExternalSelection(1, handler, noSessions);
        assertEquals(MainMenuRenderer.MenuState.EIGHTEEN_HOLES, menuManager.getCurrentMenuState());
    }

    @Test
    public void testSelectStandard18CallsCorrectCallback() {
        menuManager.setMenuState(MainMenuRenderer.MenuState.EIGHTEEN_HOLES);
        menuManager.handleExternalSelection(0, handler, noSessions);
        assertEquals("standard18", handler.lastCalled);
    }

    @Test
    public void testSelectDaily18CallsCorrectCallback() {
        menuManager.setMenuState(MainMenuRenderer.MenuState.EIGHTEEN_HOLES);
        menuManager.handleExternalSelection(1, handler, noSessions);
        assertEquals("daily18", handler.lastCalled);
    }

    @Test
    public void testSelectDaily9CallsCorrectCallback() {
        menuManager.setMenuState(MainMenuRenderer.MenuState.EIGHTEEN_HOLES);
        menuManager.handleExternalSelection(2, handler, noSessions);
        assertEquals("daily9", handler.lastCalled);
    }

    @Test
    public void testSelectDaily1CallsCorrectCallback() {
        menuManager.setMenuState(MainMenuRenderer.MenuState.EIGHTEEN_HOLES);
        menuManager.handleExternalSelection(3, handler, noSessions);
        assertEquals("daily1", handler.lastCalled);
    }

    @Test
    public void testBackFromCompetitiveGoesToMain() {
        menuManager.setMenuState(MainMenuRenderer.MenuState.EIGHTEEN_HOLES);
        menuManager.handleExternalSelection(4, handler, noSessions);
        assertEquals(MainMenuRenderer.MenuState.MAIN, menuManager.getCurrentMenuState());
    }

    @Test
    public void testCompetitiveMenuMaxSelectionIsFive() {
        // Verify all 5 indices (0-4) map to valid actions without throwing
        int[] indices = {0, 1, 2, 3, 4};
        for (int idx : indices) {
            menuManager.setMenuState(MainMenuRenderer.MenuState.EIGHTEEN_HOLES);
            final int capturedIdx = idx;
            assertDoesNotThrow(() -> menuManager.handleExternalSelection(capturedIdx, handler, noSessions));
        }
    }
}
