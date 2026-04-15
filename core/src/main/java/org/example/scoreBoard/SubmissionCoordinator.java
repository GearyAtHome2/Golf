package org.example.scoreBoard;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import org.example.auth.AuthService;
import org.example.auth.UserSession;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;
import org.example.session.SessionManager;

/**
 * Owns the score-submission lifecycle: flag management, input blocking, and
 * ScoreSubmissionHandler construction. GolfGame holds one instance and delegates
 * all three submission paths here.
 */
public class SubmissionCoordinator {

    public interface Callbacks {
        void onSubmissionStarted();   // block input, set flag
        void onSubmissionEnded();     // unblock input, clear flag
        void onMenuInvalidate();      // force mobile menu rebuild
        void onSubmitSuccess();       // called after successful end-of-round submit (goes to main menu)
        void onAutoRetrySuccess();    // silent retry succeeded → show toast
        Stage getSubmitStage();
        Stage getMenuStage();
        Skin getSkin();
    }

    private final SessionManager sessionManager;
    private final UserSession userSession;
    private final AuthService authService;
    private final DailySubmissionCache dailyCache;
    private final Callbacks callbacks;

    private boolean submissionInProgress = false;

    public SubmissionCoordinator(SessionManager sessionManager, UserSession userSession,
                                  AuthService authService, DailySubmissionCache dailyCache,
                                  Callbacks callbacks) {
        this.sessionManager = sessionManager;
        this.userSession = userSession;
        this.authService = authService;
        this.dailyCache = dailyCache;
        this.callbacks = callbacks;
    }

    public boolean isSubmissionInProgress() { return submissionInProgress; }

    /** Used from the end-of-round victory screen (user presses SUBMIT SCORE). */
    public void submitEndOfRound(GameSession session) {
        submissionInProgress = true;
        callbacks.onSubmissionStarted();

        new ScoreSubmissionHandler(
                session,
                userSession.getDisplayName(),
                userSession.getUid(),
                userSession.getIdToken(),
                dailyCache,
                authService,
                userSession,
                () -> Gdx.app.postRunnable(() -> {
                    submissionInProgress = false;
                    callbacks.onSubmissionEnded();
                    callbacks.onSubmitSuccess();
                }),
                () -> Gdx.app.postRunnable(() -> {
                    submissionInProgress = false;
                    callbacks.onSubmissionEnded();
                })
        ).trigger(callbacks.getSubmitStage(), callbacks.getSkin());
    }

    /** Used from the competitive menu when the user taps [SUBMIT SCORE] on a pending session. */
    public void resubmitDaily(CourseType type) {
        GameSession session = switch (type) {
            case HOLES_18 -> sessionManager.getDaily18();
            case HOLES_9  -> sessionManager.getDaily9();
            case HOLES_1  -> sessionManager.getDaily1();
        };
        if (session == null) return;

        submissionInProgress = true;
        callbacks.onSubmissionStarted();

        new ScoreSubmissionHandler(
                session,
                userSession.getDisplayName(),
                userSession.getUid(),
                userSession.getIdToken(),
                dailyCache,
                authService,
                userSession,
                () -> Gdx.app.postRunnable(() -> {
                    submissionInProgress = false;
                    callbacks.onSubmissionEnded();
                    callbacks.onMenuInvalidate();
                }),
                () -> Gdx.app.postRunnable(() -> {
                    submissionInProgress = false;
                    callbacks.onSubmissionEnded();
                })
        ).trigger(callbacks.getMenuStage(), callbacks.getSkin());
    }

    /** Called silently after login/cache-fetch to retry any pending unsubmitted daily sessions. */
    public void tryAutoRetryAll() {
        CompetitiveSessions sessions = sessionManager.getCompetitiveSessions();
        tryAutoRetrySession(sessions.daily18, CourseType.HOLES_18);
        tryAutoRetrySession(sessions.daily9,  CourseType.HOLES_9);
        tryAutoRetrySession(sessions.daily1,  CourseType.HOLES_1);
    }

    private void tryAutoRetrySession(GameSession session, CourseType type) {
        if (session == null || !session.isFinished() || session.isSubmitted()) return;
        if (dailyCache.isFetched() && dailyCache.hasSubmitted(type)) return;

        new ScoreSubmissionHandler(
                session,
                userSession.getDisplayName(),
                userSession.getUid(),
                userSession.getIdToken(),
                dailyCache,
                authService,
                userSession,
                () -> Gdx.app.postRunnable(() -> {
                    callbacks.onMenuInvalidate();
                    callbacks.onAutoRetrySuccess();
                }),
                null
        ).triggerSilent();
    }
}
