package org.example.session;

import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

public class SessionPersistenceTest {

    @Test
    public void testTodayTimestampMatchesCalendar() {
        Calendar cal = Calendar.getInstance();
        int year  = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        int day   = cal.get(Calendar.DAY_OF_MONTH);
        long expected = (long) year * 10000 + month * 100 + day;

        assertEquals(expected, SessionPersistence.getTodayTimestamp());
    }

    @Test
    public void testTodayTimestampHas8Digits() {
        long ts = SessionPersistence.getTodayTimestamp();
        // e.g. 20260401 → 8 digits
        assertTrue(ts >= 10_000_000L && ts <= 99_999_999L,
                "Timestamp " + ts + " is not 8 digits");
    }

    @Test
    public void testTodayTimestampYearComponent() {
        long ts = SessionPersistence.getTodayTimestamp();
        int year = Calendar.getInstance().get(Calendar.YEAR);
        long extractedYear = ts / 10000;
        assertEquals(year, extractedYear);
    }

    @Test
    public void testTodayTimestampMonthComponent() {
        long ts = SessionPersistence.getTodayTimestamp();
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        long extractedMonth = (ts % 10000) / 100;
        assertEquals(month, extractedMonth);
    }

    @Test
    public void testTodayTimestampDayComponent() {
        long ts = SessionPersistence.getTodayTimestamp();
        int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        long extractedDay = ts % 100;
        assertEquals(day, extractedDay);
    }

    @Test
    public void testTodayTimestampIsConsistentAcrossCalls() {
        // Two calls within the same test should produce the same value
        long ts1 = SessionPersistence.getTodayTimestamp();
        long ts2 = SessionPersistence.getTodayTimestamp();
        assertEquals(ts1, ts2);
    }
}
