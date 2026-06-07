package com.gearygolf.golf.scoreBoard;

import com.gearygolf.golf.session.GameSession;

/**
 * Single source of truth for whether a daily course type is effectively submitted
 * or still available to play/submit.
 */
public final class DailyStatusResolver {

    private DailyStatusResolver() {}

    /**
     * Returns true if the daily round is effectively submitted — checked in priority order:
     * 1. Local on-disk store (available immediately on app start, before Firebase responds)
     * 2. Firebase cache (available once the async fetch completes)
     * 3. In-memory session flag (set the moment a submission succeeds this session)
     */
    public static boolean isEffectivelySubmitted(CourseType type, GameSession session, DailySubmissionCache cache) {
        if (cache != null && cache.isLocallyConfirmed(type)) return true;
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
