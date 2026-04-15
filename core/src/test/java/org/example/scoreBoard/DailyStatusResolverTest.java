package org.example.scoreBoard;

import org.example.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DailyStatusResolverTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** A DailySubmissionCache that behaves as if it has fetched and has the given types submitted. */
    private static DailySubmissionCache fetchedCache(CourseType... types) {
        DailySubmissionCache cache = new DailySubmissionCache() {
            @Override public boolean isFetched() { return true; }
        };
        for (CourseType t : types) cache.markSubmitted(t);
        return cache;
    }

    /** A DailySubmissionCache that has NOT yet fetched (pending). */
    private static DailySubmissionCache unfetchedCache() {
        return new DailySubmissionCache(); // isFetched() returns false by default
    }

    private static GameSession submittedSession() {
        GameSession s = new GameSession();
        s.markSubmitted();
        return s;
    }

    private static GameSession unsubmittedSession() {
        return new GameSession();
    }

    // -----------------------------------------------------------------------
    // isEffectivelySubmitted
    // -----------------------------------------------------------------------

    @Test
    public void nullSessionAndNullCache_notSubmitted() {
        assertFalse(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, null, null));
    }

    @Test
    public void nullSessionAndNullCache_allTypes_notSubmitted() {
        for (CourseType type : CourseType.values()) {
            assertFalse(DailyStatusResolver.isEffectivelySubmitted(type, null, null),
                    "Expected not-submitted for " + type);
        }
    }

    @Test
    public void submittedSessionAndNullCache_isSubmitted() {
        assertTrue(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, submittedSession(), null));
    }

    @Test
    public void unsubmittedSessionAndNullCache_notSubmitted() {
        assertFalse(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, unsubmittedSession(), null));
    }

    @Test
    public void submittedSessionAndUnfetchedCache_isSubmitted() {
        // Local flag wins even if cache hasn't loaded yet
        assertTrue(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, submittedSession(), unfetchedCache()));
    }

    @Test
    public void unsubmittedSessionAndFetchedCacheWithMatch_isSubmitted() {
        // Remote cache confirms submission even when local session isn't flagged
        DailySubmissionCache cache = fetchedCache(CourseType.HOLES_18);
        assertFalse(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_9,  null, cache), "HOLES_9 not in cache");
        assertTrue(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, null, cache), "HOLES_18 is in cache");
    }

    @Test
    public void unsubmittedSessionAndFetchedCacheWithoutMatch_notSubmitted() {
        DailySubmissionCache cache = fetchedCache(CourseType.HOLES_9); // only 9 is in
        assertFalse(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, unsubmittedSession(), cache));
        assertFalse(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_1,  unsubmittedSession(), cache));
    }

    @Test
    public void unfetchedCacheDoesNotCount() {
        // Cache has the type marked but hasn't completed fetching — should not count
        DailySubmissionCache cache = unfetchedCache();
        cache.markSubmitted(CourseType.HOLES_18); // internal mark, but isFetched() is false
        assertFalse(DailyStatusResolver.isEffectivelySubmitted(CourseType.HOLES_18, unsubmittedSession(), cache));
    }

    // -----------------------------------------------------------------------
    // isAvailableOrPending (inverse of isEffectivelySubmitted)
    // -----------------------------------------------------------------------

    @Test
    public void nullSessionAndNullCache_isAvailable() {
        assertTrue(DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_18, null, null));
    }

    @Test
    public void submittedSession_notAvailable() {
        assertFalse(DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_18, submittedSession(), null));
    }

    @Test
    public void cacheConfirmsSubmission_notAvailable() {
        DailySubmissionCache cache = fetchedCache(CourseType.HOLES_1);
        assertFalse(DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_1, null, cache));
    }

    @Test
    public void unsubmittedSession_isAvailable() {
        assertTrue(DailyStatusResolver.isAvailableOrPending(CourseType.HOLES_9, unsubmittedSession(), unfetchedCache()));
    }
}
