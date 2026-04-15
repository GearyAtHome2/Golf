package org.example.hud;

import org.example.hud.renderer.MainMenuRenderer.MenuState;
import org.example.scoreBoard.CourseType;
import org.example.scoreBoard.DailyStatusResolver;
import org.example.scoreBoard.DailySubmissionCache;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;

import java.util.Arrays;
import java.util.List;

/**
 * Single source of truth for menu button labels, locked state, and sparkle.
 * Both MainMenuRenderer (desktop) and MobileUIFactory (Android) consume this
 * so the logic lives in exactly one place.
 *
 * Only covers states with dynamic content (MAIN, EIGHTEEN_HOLES).
 * Returns null for states that need no dynamic resolution.
 */
public final class MenuButtonResolver {

    private MenuButtonResolver() {}

    public static List<MenuButtonDescriptor> resolve(MenuState state, CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        return switch (state) {
            case MAIN           -> resolveMain(sessions, dailyCache);
            case EIGHTEEN_HOLES -> resolveEighteenHoles(sessions, dailyCache);
            default             -> null;
        };
    }

    // -------------------------------------------------------------------------

    private static List<MenuButtonDescriptor> resolveMain(CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        boolean competitiveSparkle = sessions == null
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_18, sessions.daily18, dailyCache)
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_9,  sessions.daily9,  dailyCache)
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_1,  sessions.daily1,  dailyCache);

        return Arrays.asList(
            MenuButtonDescriptor.enabled("PLAY >"),
            new MenuButtonDescriptor("COMPETITIVE >", false, competitiveSparkle),
            MenuButtonDescriptor.enabled("INSTRUCTIONS"),
            MenuButtonDescriptor.enabled("PRACTICE >"),
            MenuButtonDescriptor.enabled("LOG OUT")
        );
    }

    private static List<MenuButtonDescriptor> resolveEighteenHoles(CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        GameSession standard = sessions != null ? sessions.standard : null;
        GameSession daily18  = sessions != null ? sessions.daily18  : null;
        GameSession daily9   = sessions != null ? sessions.daily9   : null;
        GameSession daily1   = sessions != null ? sessions.daily1   : null;

        boolean standardFinished = standard != null && standard.isFinished();
        String play18Label = standardFinished
            ? "18 HOLES (COMPLETED)"
            : (standard != null ? "CONTINUE 18 (" + (standard.getCurrentHoleIndex() + 1) + "/18)" : "PLAY 18");

        boolean daily18Done = DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, daily18, dailyCache);
        String daily18Label = daily18Done
            ? "DAILY 18 (SUBMITTED TODAY)"
            : (daily18 != null && daily18.isFinished() ? "DAILY 18 [SUBMIT SCORE]"
            : (daily18 != null ? "CONTINUE DAILY 18 (" + (daily18.getCurrentHoleIndex() + 1) + "/18)" : "DAILY 18"));

        boolean daily9Done = DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_9, daily9, dailyCache);
        String daily9Label = daily9Done
            ? "DAILY 9 (SUBMITTED TODAY)"
            : (daily9 != null && daily9.isFinished() ? "DAILY 9 [SUBMIT SCORE]"
            : (daily9 != null ? "CONTINUE DAILY 9 (" + (daily9.getCurrentHoleIndex() + 1) + "/9)" : "DAILY 9"));

        boolean daily1Done = DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_1, daily1, dailyCache);
        String daily1Label = daily1Done
            ? "DAILY 1-HOLE (SUBMITTED TODAY)"
            : (daily1 != null && daily1.isFinished() ? "DAILY 1-HOLE [SUBMIT SCORE]"
            : (daily1 != null ? "CONTINUE DAILY 1-HOLE (1/1)" : "DAILY 1-HOLE"));

        return Arrays.asList(
            new MenuButtonDescriptor(play18Label,  standardFinished, false),
            new MenuButtonDescriptor(daily18Label, daily18Done,      !daily18Done),
            new MenuButtonDescriptor(daily9Label,  daily9Done,       !daily9Done),
            new MenuButtonDescriptor(daily1Label,  daily1Done,       !daily1Done),
            MenuButtonDescriptor.enabled("< BACK TO MAIN")
        );
    }
}
