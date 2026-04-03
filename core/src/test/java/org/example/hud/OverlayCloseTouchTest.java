package org.example.hud;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Documents the known bug where a tap that closes an overlay on Android also
 * triggers buttons in the underlying Stage.
 */
public class OverlayCloseTouchTest {

    /**
     * BUG: Tapping outside the Instructions or Camera Config overlay on Android
     * to close it also fires buttons (e.g. NEW MAP, RESET BALL, INFO) in the
     * pause-menu Stage beneath, causing their visual fill/press effects to play.
     *
     * Root cause: GolfGame.handleOverlayInput() transitions state but does NOT
     * call ((MobileInputProcessor) input).consumeCurrentTouch() after detecting
     * the outside tap. The touch event therefore reaches Stage.act() on the same
     * frame and is dispatched normally to whichever actor sits at that screen
     * position.
     *
     * The bug also bypasses the pause-menu Stage protection because the state
     * transition to PAUSED happens mid-frame, after the overlay has already
     * decided not to consume the touch.
     *
     * Expected fix: in GolfGame.handleOverlayInput(), after the block that calls
     * changeState(previousState) for an outside tap, add:
     *   if (input instanceof MobileInputProcessor m) m.consumeCurrentTouch();
     *
     * This test cannot be automated as a headless unit test because the failure
     * mode requires a running LibGDX Stage with live actors and GL context.
     * Re-enable and verify manually on device/emulator once the fix is applied.
     */
    @Test
    @Disabled("Known bug — overlay close tap bleeds through to underlying Stage buttons on Android")
    public void testOverlayCloseTapIsConsumedAndDoesNotActivateUnderlyingButtons() {
        fail("Not yet fixed");
    }
}
