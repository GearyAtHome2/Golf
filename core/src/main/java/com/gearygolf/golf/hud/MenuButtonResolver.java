package com.gearygolf.golf.hud;

import com.gearygolf.golf.hud.renderer.MainMenuRenderer.MenuState;
import com.gearygolf.golf.scoreBoard.CourseType;
import com.gearygolf.golf.scoreBoard.DailyStatusResolver;
import com.gearygolf.golf.scoreBoard.DailySubmissionCache;
import com.gearygolf.golf.session.CompetitiveSessions;
import com.gearygolf.golf.session.GameSession;
import com.gearygolf.golf.tutorial.TutorialPrefs;

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
            case PRACTICE       -> resolvePractice();
            case SETTINGS       -> resolveSettings();
            default             -> null;
        };
    }

    // -------------------------------------------------------------------------

    private static List<MenuButtonDescriptor> resolveMain(CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        boolean competitiveSparkle = sessions == null
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_18,    sessions.daily18,   dailyCache)
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_9,     sessions.daily9,    dailyCache)
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_1_PAR3, sessions.dailyPar3, dailyCache)
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_1_PAR4, sessions.dailyPar4, dailyCache)
            || DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_1_PAR5, sessions.dailyPar5, dailyCache);

        java.util.List<MenuButtonDescriptor> items = new java.util.ArrayList<>();
        if (!TutorialPrefs.isComplete()) {
            items.add(new MenuButtonDescriptor("TUTORIAL", false, true));
        }
        items.add(MenuButtonDescriptor.enabled("PLAY >"));
        items.add(new MenuButtonDescriptor("COMPETITIVE >", false, competitiveSparkle));
        items.add(MenuButtonDescriptor.enabled("SETTINGS"));
        items.add(MenuButtonDescriptor.enabled("PRACTICE >"));
        items.add(MenuButtonDescriptor.enabled("MULTIPLAYER >"));
        items.add(MenuButtonDescriptor.enabled("LOG OUT"));
        return items;
    }

    private static List<MenuButtonDescriptor> resolveSettings() {
        return java.util.Arrays.asList(
            MenuButtonDescriptor.enabled("SOUND"),
            MenuButtonDescriptor.enabled("INSTRUCTIONS"),
            MenuButtonDescriptor.enabled("PROFILE"),
            MenuButtonDescriptor.enabled("< BACK TO MAIN")
        );
    }

    private static List<MenuButtonDescriptor> resolvePractice() {
        java.util.List<MenuButtonDescriptor> items = new java.util.ArrayList<>();
        items.add(MenuButtonDescriptor.enabled("DRIVING RANGE"));
        items.add(MenuButtonDescriptor.enabled("PUTTING GREEN"));
        if (TutorialPrefs.isComplete()) {
            items.add(MenuButtonDescriptor.enabled("TUTORIAL"));
        }
        items.add(MenuButtonDescriptor.enabled("IMPORT SHOT"));
        items.add(MenuButtonDescriptor.enabled("< BACK TO MAIN"));
        return items;
    }

    private static List<MenuButtonDescriptor> resolveEighteenHoles(CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        GameSession standard  = sessions != null ? sessions.standard   : null;
        GameSession daily18   = sessions != null ? sessions.daily18    : null;
        GameSession daily9    = sessions != null ? sessions.daily9     : null;
        GameSession dailyPar3 = sessions != null ? sessions.dailyPar3  : null;
        GameSession dailyPar4 = sessions != null ? sessions.dailyPar4  : null;
        GameSession dailyPar5 = sessions != null ? sessions.dailyPar5  : null;

        boolean standardFinished = standard != null && standard.isFinished();
        String play18Label = (standard != null && !standardFinished)
            ? "CONTINUE 18 (" + (standard.getCurrentHoleIndex() + 1) + "/18)"
            : "PLAY 18";

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

        boolean par3Done = DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_1_PAR3, dailyPar3, dailyCache);
        String par3Label = par3Done
            ? "DAILY PAR 3 (SUBMITTED TODAY)"
            : (dailyPar3 != null && dailyPar3.isFinished() ? "DAILY PAR 3 [SUBMIT SCORE]"
            : (dailyPar3 != null ? "CONTINUE DAILY PAR 3 (1/1)" : "DAILY PAR 3"));

        boolean par4Done = DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_1_PAR4, dailyPar4, dailyCache);
        String par4Label = par4Done
            ? "DAILY PAR 4 (SUBMITTED TODAY)"
            : (dailyPar4 != null && dailyPar4.isFinished() ? "DAILY PAR 4 [SUBMIT SCORE]"
            : (dailyPar4 != null ? "CONTINUE DAILY PAR 4 (1/1)" : "DAILY PAR 4"));

        boolean par5Done = DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_1_PAR5, dailyPar5, dailyCache);
        String par5Label = par5Done
            ? "DAILY PAR 5 (SUBMITTED TODAY)"
            : (dailyPar5 != null && dailyPar5.isFinished() ? "DAILY PAR 5 [SUBMIT SCORE]"
            : (dailyPar5 != null ? "CONTINUE DAILY PAR 5 (1/1)" : "DAILY PAR 5"));

        return Arrays.asList(
            new MenuButtonDescriptor(play18Label,  false,    false),
            new MenuButtonDescriptor(daily18Label, daily18Done, !daily18Done),
            new MenuButtonDescriptor(daily9Label,  daily9Done,  !daily9Done),
            new MenuButtonDescriptor(par3Label,    par3Done,    !par3Done),
            new MenuButtonDescriptor(par4Label,    par4Done,    !par4Done),
            new MenuButtonDescriptor(par5Label,    par5Done,    !par5Done),
            MenuButtonDescriptor.enabled("< BACK TO MAIN")
        );
    }
}
