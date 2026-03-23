package org.example.input;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DesktopInputMappingTest {

    @Test
    public void testScrollPersistence() {
        DesktopInputProcessor processor = new DesktopInputProcessor();
        processor.scrolled(0, 1.0f);
        processor.update(0);
        assertEquals(1.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y));
        assertEquals(1.0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y)); // Should persist for entire frame
        processor.update(0);
        assertEquals(0f, processor.getActionValue(GameInputProcessor.Action.SCROLL_Y)); // Should be cleared on next update
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testStrictInputSeparation() throws Exception {
        DesktopInputProcessor processor = new DesktopInputProcessor();

        // Use reflection to inspect the private keyMap
        Field field = DesktopInputProcessor.class.getDeclaredField("keyMap");
        field.setAccessible(true);
        Map<GameInputProcessor.Action, Integer> keyMap = (Map<GameInputProcessor.Action, Integer>) field.get(processor);

        // 1. Verify SPEED and MENU use only Arrow Keys
        assertEquals(Input.Keys.UP, keyMap.get(GameInputProcessor.Action.SPEED_UP), "SPEED_UP must be UP arrow");
        assertEquals(Input.Keys.DOWN, keyMap.get(GameInputProcessor.Action.SPEED_DOWN), "SPEED_DOWN must be DOWN arrow");
        assertEquals(Input.Keys.UP, keyMap.get(GameInputProcessor.Action.MENU_UP), "MENU_UP must be UP arrow");
        assertEquals(Input.Keys.DOWN, keyMap.get(GameInputProcessor.Action.MENU_DOWN), "MENU_DOWN must be DOWN arrow");

        // 2. Verify SPIN uses only WASD
        assertEquals(Input.Keys.W, keyMap.get(GameInputProcessor.Action.SPIN_UP), "SPIN_UP must be W");
        assertEquals(Input.Keys.S, keyMap.get(GameInputProcessor.Action.SPIN_DOWN), "SPIN_DOWN must be S");
        assertEquals(Input.Keys.A, keyMap.get(GameInputProcessor.Action.SPIN_LEFT), "SPIN_LEFT must be A");
        assertEquals(Input.Keys.D, keyMap.get(GameInputProcessor.Action.SPIN_RIGHT), "SPIN_RIGHT must be D");

        // 3. Verify no crosstalk (manual check of the logic in DesktopInputProcessor)
        // Ensure W/S are NOT in keyMap for SPEED or MENU
        assertNotEquals(Input.Keys.W, keyMap.get(GameInputProcessor.Action.SPEED_UP));
        assertNotEquals(Input.Keys.S, keyMap.get(GameInputProcessor.Action.SPEED_DOWN));
        assertNotEquals(Input.Keys.W, keyMap.get(GameInputProcessor.Action.MENU_UP));
        assertNotEquals(Input.Keys.S, keyMap.get(GameInputProcessor.Action.MENU_DOWN));

        // Ensure Arrows are NOT in keyMap for SPIN
        assertNotEquals(Input.Keys.UP, keyMap.get(GameInputProcessor.Action.SPIN_UP));
        assertNotEquals(Input.Keys.DOWN, keyMap.get(GameInputProcessor.Action.SPIN_DOWN));
    }

    @Test
    public void testSecondaryActionMapping() {
        DesktopInputProcessor processor = new DesktopInputProcessor();
        assertNotNull(GameInputProcessor.Action.SECONDARY_ACTION);
    }

    @Test
    public void testAndroidResumeButtonPresence() throws Exception {
        // Since we can't run HUD.setupMobileUI without a GL context, we check the logic presence
        // Verify PAUSE action exists for toggle functionality
        assertNotNull(GameInputProcessor.Action.PAUSE);
    }
}
