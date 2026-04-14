package org.example.session;

import com.badlogic.gdx.math.MathUtils;
import org.example.GameConfig;
import org.example.terrain.level.LevelDataGenerator;
import java.util.Collections;
import java.util.List;
import org.example.terrain.level.LevelData;

public class SessionManager {
    private GameSession activeSession;
    private GameSession standardSession;
    private GameSession daily18Session;
    private GameSession daily9Session;
    private GameSession daily1Session;
    private final GameConfig config;
    private String uid = "";

    public SessionManager(GameConfig config) {
        this.config = config;
        this.standardSession = SessionPersistence.loadSession(GameSession.GameMode.STANDARD_18);
        reloadDailySessions("");
    }

    /**
     * Reloads daily sessions for the given uid (empty string = not logged in).
     * Call this immediately after login or logout so each account sees only its own saves.
     */
    public void reloadDailySessions(String uid) {
        this.uid = uid != null ? uid : "";
        this.daily18Session = SessionPersistence.loadSession(GameSession.GameMode.DAILY_18, this.uid);
        this.daily9Session  = SessionPersistence.loadSession(GameSession.GameMode.DAILY_9,  this.uid);
        this.daily1Session  = SessionPersistence.loadSession(GameSession.GameMode.DAILY_1,  this.uid);
    }

    public void startCompetitiveMatch(long seed, GameSession.GameMode mode) {
        activeSession = new GameSession(seed, config.difficulty, mode, SessionPersistence.getTodayTimestamp());
        activeSession.setSaveCallback(() -> SessionPersistence.saveSession(activeSession, uid));

        List<LevelData> full18 = LevelDataGenerator.generate18Holes(seed);
        int count = mode.holeCount();
        List<LevelData> layout = (count < full18.size()) ? full18.subList(0, count) : full18;
        activeSession.setCourseLayout(layout);

        switch (mode) {
            case STANDARD_18 -> standardSession = activeSession;
            case DAILY_18 -> daily18Session = activeSession;
            case DAILY_9 -> daily9Session = activeSession;
            case DAILY_1 -> daily1Session = activeSession;
        }

        SessionPersistence.saveSession(activeSession, uid);
    }

    public void startDaily18() {
        startCompetitiveMatch(scrambleDailySeed(SessionPersistence.getTodayTimestamp()), GameSession.GameMode.DAILY_18);
    }

    public void startDaily9() {
        startCompetitiveMatch(scrambleDailySeed(SessionPersistence.getTodayTimestamp() * 37L + 1L), GameSession.GameMode.DAILY_9);
    }

    public void startDaily1() {
        long seed = scrambleDailySeed(SessionPersistence.getTodayTimestamp() * 71L + 3L);
        activeSession = new GameSession(seed, config.difficulty, GameSession.GameMode.DAILY_1, SessionPersistence.getTodayTimestamp());
        activeSession.setSaveCallback(() -> SessionPersistence.saveSession(activeSession, uid));
        activeSession.setCourseLayout(Collections.singletonList(LevelDataGenerator.createPar3Hole(seed)));
        daily1Session = activeSession;
        SessionPersistence.saveSession(activeSession, uid);
    }

    public void startStandardMatch() {
        startCompetitiveMatch(Math.abs(MathUtils.random.nextLong()), GameSession.GameMode.STANDARD_18);
    }

    // Scrambles a timestamp-derived seed so players can't trivially reverse-engineer
    // today's daily map by guessing the input (e.g. "20260411"). All players share
    // the same scrambled seed for a given day. Do NOT change this — doing so would
    // give every player a different daily map and invalidate historic leaderboard seeds.
    private long scrambleDailySeed(long seed) {
        for (int i = 0; i < 10; i++) {
            seed = (seed * 13) + 8;
            if (String.valueOf(seed).length() > 9) seed /= 17;
        }
        return seed;
    }

    public void saveActive() {
        if (activeSession != null) SessionPersistence.saveSession(activeSession, uid);
    }

    public CompetitiveSessions getCompetitiveSessions() {
        return new CompetitiveSessions(standardSession, daily18Session, daily9Session, daily1Session);
    }

    public GameSession getActive() { return activeSession; }
    public void setActive(GameSession session) { this.activeSession = session; }
    public GameSession getStandard() { return standardSession; }
    public GameSession getDaily18() { return daily18Session; }
    public GameSession getDaily9() { return daily9Session; }
    public GameSession getDaily1() { return daily1Session; }
    public void clearActive() { activeSession = null; }

    public boolean isDailyActive() {
        if (activeSession == null) return false;
        GameSession.GameMode m = activeSession.getMode();
        return m == GameSession.GameMode.DAILY_18 || m == GameSession.GameMode.DAILY_9 || m == GameSession.GameMode.DAILY_1;
    }
}
