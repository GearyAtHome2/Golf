package org.example.input;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DesktopInputMappingTest {

    private DesktopInputProcessor processor;

    @BeforeEach
    public void setUp() {
        processor = new DesktopInputProcessor();
    }

    // ── Scroll accumulation ────────────────────────────────────────────────────

    @Test
    public void testScrollValueAvailableAfterUpdate() {
        processor.scrolled(0, 1.0f);
        processor.update(0);
        assertEquals(1.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
    }

    @Test
    public void testScrollPersistsForEntireFrame() {
        processor.scrolled(0, 2.0f);
        processor.update(0);
        // Multiple reads within the same frame should return the same value
        assertEquals(2.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
        assertEquals(2.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
    }

    @Test
    public void testScrollClearedOnNextUpdate() {
        processor.scrolled(0, 1.0f);
        processor.update(0);
        assertEquals(1.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
        processor.update(0); // second frame — no new scroll input
        assertEquals(0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
    }

    @Test
    public void testMultipleScrollEventsAccumulate() {
        processor.scrolled(0, 1.0f);
        processor.scrolled(0, 1.0f);
        processor.scrolled(0, 1.0f);
        processor.update(0);
        assertEquals(3.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y), 0.001f);
    }

    @Test
    public void testScrollInBothDirectionsAccumulate() {
        processor.scrolled(0, 3.0f);
        processor.scrolled(0, -1.0f);
        processor.update(0);
        assertEquals(2.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y), 0.001f);
    }

    @Test
    public void testScrollXIsIgnored() {
        // Only Y scrolling is tracked; X scroll should not affect SCROLL_Y
        processor.scrolled(5.0f, 0f);
        processor.update(0);
        assertEquals(0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
    }

    @Test
    public void testScrollZeroBeforeAnyInput() {
        processor.update(0);
        assertEquals(0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
    }

    // ── Input blocked ──────────────────────────────────────────────────────────

    @Test
    public void testScrollReturnedWhenNotBlocked() {
        processor.scrolled(0, 2.0f);
        processor.update(0);
        processor.setInputBlocked(false);
        assertEquals(2.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y), 0.001f);
    }

    @Test
    public void testScrollSuppressedWhenBlocked() {
        processor.scrolled(0, 2.0f);
        processor.update(0);
        processor.setInputBlocked(true);
        assertEquals(0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
    }

    @Test
    public void testIsActionPressedReturnsFalseWhenBlocked() {
        processor.setInputBlocked(true);
        // All actions should return false when blocked (no Gdx.input call is made)
        assertFalse(processor.isActionPressed(GameInputProcessor.Action.CHARGE_SHOT));
        assertFalse(processor.isActionPressed(GameInputProcessor.Action.SPIN_UP));
        assertFalse(processor.isActionPressed(GameInputProcessor.Action.MENU_UP));
    }

    @Test
    public void testIsActionJustPressedReturnsFalseWhenBlocked() {
        processor.setInputBlocked(true);
        assertFalse(processor.isActionJustPressed(GameInputProcessor.Action.CHARGE_SHOT));
        assertFalse(processor.isActionJustPressed(GameInputProcessor.Action.PAUSE));
    }

    // ── Key map correctness ────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    public void testSpinUsesWASD() throws Exception {
        Field field = DesktopInputProcessor.class.getDeclaredField("keyMap");
        field.setAccessible(true);
        Map<GameInputProcessor.Action, Integer> keyMap =
                (Map<GameInputProcessor.Action, Integer>) field.get(processor);

        assertEquals(Input.Keys.W, keyMap.get(GameInputProcessor.Action.SPIN_UP));
        assertEquals(Input.Keys.S, keyMap.get(GameInputProcessor.Action.SPIN_DOWN));
        assertEquals(Input.Keys.A, keyMap.get(GameInputProcessor.Action.SPIN_LEFT));
        assertEquals(Input.Keys.D, keyMap.get(GameInputProcessor.Action.SPIN_RIGHT));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMenuAndSpeedUseArrowKeys() throws Exception {
        Field field = DesktopInputProcessor.class.getDeclaredField("keyMap");
        field.setAccessible(true);
        Map<GameInputProcessor.Action, Integer> keyMap =
                (Map<GameInputProcessor.Action, Integer>) field.get(processor);

        assertEquals(Input.Keys.UP,   keyMap.get(GameInputProcessor.Action.MENU_UP));
        assertEquals(Input.Keys.DOWN, keyMap.get(GameInputProcessor.Action.MENU_DOWN));
        assertEquals(Input.Keys.UP,   keyMap.get(GameInputProcessor.Action.SPEED_UP));
        assertEquals(Input.Keys.DOWN, keyMap.get(GameInputProcessor.Action.SPEED_DOWN));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSpinDoesNotUseArrowKeys() throws Exception {
        Field field = DesktopInputProcessor.class.getDeclaredField("keyMap");
        field.setAccessible(true);
        Map<GameInputProcessor.Action, Integer> keyMap =
                (Map<GameInputProcessor.Action, Integer>) field.get(processor);

        assertNotEquals(Input.Keys.UP,   keyMap.get(GameInputProcessor.Action.SPIN_UP));
        assertNotEquals(Input.Keys.DOWN, keyMap.get(GameInputProcessor.Action.SPIN_DOWN));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testChargeShotAndStopNeedleBothMappedToSpace() throws Exception {
        Field field = DesktopInputProcessor.class.getDeclaredField("keyMap");
        field.setAccessible(true);
        Map<GameInputProcessor.Action, Integer> keyMap =
                (Map<GameInputProcessor.Action, Integer>) field.get(processor);

        assertEquals(Input.Keys.SPACE, keyMap.get(GameInputProcessor.Action.CHARGE_SHOT));
        assertEquals(Input.Keys.SPACE, keyMap.get(GameInputProcessor.Action.STOP_NEEDLE));
    }
}
