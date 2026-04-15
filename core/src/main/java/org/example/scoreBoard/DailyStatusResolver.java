package org.example.scoreBoard;

import org.example.session.GameSession;

/**
 * Single source of truth for whether a daily course type is effectively submitted
 * or still available to play/submit.
 */
public final class DailyStatusResolver {

    private DailyStatusResolver() {}

    /**
     * Returns true if the daily round is effectively submitted — either the server
     * confirmed it (via DailySubmissionCache) or the local session flag is set.
     */
    public static boolean isEffectivelySubmitted(CourseType type, GameSession session, DailySubmissionCache cache) {
        if (cache != null && cache.isFetched() && cache.hasSubmitted(type)) return true;
        return session != null && session.isSubmitted();
    }

    /**
     * Returns true if the daily should sparkle — not submitted yet (locally or remotely),
     * regardless of whether it is finished or not.
     */
    public static boolean isAvailableOrPending(CourseType type, GameSession session, DailySubmissionCache cache) {
        return !isEffectivelySubmitted(type, session, cache);
    }
}
