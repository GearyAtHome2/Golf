package org.example.scoreBoard;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubmissionCoordinator.
 *
 * Tests that don't trigger a network submission can run without LibGDX, because
 * the coordinator only calls Gdx when it actually fires a ScoreSubmissionHandler.
 * Tests that would reach that path (submitEndOfRound, resubmitDaily, tryAutoRetryAll
 * with a non-skipped session) require either a real LibGDX environment or a mocking
 * framework — those are integration-level and not covered here.
 */
public class SubmissionCoordinatorTest {

    private static final SubmissionCoordinator.Callbacks NO_OP_CALLBACKS = new SubmissionCoordinator.Callbacks() {
        @Override public void onSubmissionStarted() {}
        @Override public void onSubmissionEnded() {}
        @Override public void onMenuInvalidate() {}
        @Override public void onSubmitSuccess() {}
        @Override public void onAutoRetrySuccess() {}
        @Override public Stage getSubmitStage() { return null; }
        @Override public Stage getMenuStage()   { return null; }
        @Override public Skin getSkin()         { return null; }
    };

    @Test
    public void isSubmissionInProgress_falseOnConstruction() {
        // Constructor only stores references — no LibGDX calls needed
        SubmissionCoordinator coordinator = new SubmissionCoordinator(
                null, null, null, new DailySubmissionCache(), NO_OP_CALLBACKS);
        assertFalse(coordinator.isSubmissionInProgress());
    }

    @Test
    public void isSubmissionInProgress_independentInstances() {
        SubmissionCoordinator a = new SubmissionCoordinator(null, null, null, new DailySubmissionCache(), NO_OP_CALLBACKS);
        SubmissionCoordinator b = new SubmissionCoordinator(null, null, null, new DailySubmissionCache(), NO_OP_CALLBACKS);
        assertFalse(a.isSubmissionInProgress());
        assertFalse(b.isSubmissionInProgress());
    }
}
