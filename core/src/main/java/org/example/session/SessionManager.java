package org.example.session;

import com.badlogic.gdx.math.MathUtils;
import org.example.GameConfig;
import org.example.terrain.level.LevelDataGenerator;

public class SessionManager {
    private GameSession activeSession;
    private GameSession standardSession;
    private GameSession dailySession;
    private final GameConfig config;

    public SessionManager(GameConfig config) {
        this.config = config;
        this.standardSession = SessionPersistence.loadSession(GameSession.GameMode.STANDARD_18);
        this.dailySession = SessionPersistence.loadSession(GameSession.GameMode.DAILY_CHALLENGE);
    }

    public void startCompetitiveMatch(long seed, GameSession.GameMode mode) {
        activeSession = new GameSession(seed, config.difficulty, mode, SessionPersistence.getTodayTimestamp());
        activeSession.setSaveCallback(() -> SessionPersistence.saveSession(activeSession));
        activeSession.setCourseLayout(LevelDataGenerator.generate18Holes(seed));
        
        if (mode == GameSession.GameMode.STANDARD_18) standardSession = activeSession;
        else dailySession = activeSession;
        
        SessionPersistence.saveSession(activeSession);
    }

    public void startDailyChallenge() {
        startCompetitiveMatch(applySecretSauce(SessionPersistence.getTodayTimestamp()), GameSession.GameMode.DAILY_CHALLENGE);
    }

    public void startStandardMatch() {
        startCompetitiveMatch(Math.abs(MathUtils.random.nextLong()), GameSession.GameMode.STANDARD_18);
    }

    private long applySecretSauce(long seed) {
        for (int i = 0; i < 10; i++) {
            seed = (seed * 13) + 8;
            if (String.valueOf(seed).length() > 9) seed /= 17;
        }
        return seed;
    }

    public void saveActive() {
        if (activeSession != null) SessionPersistence.saveSession(activeSession);
    }

    public GameSession getActive() { return activeSession; }
    public void setActive(GameSession session) { this.activeSession = session; }
    public GameSession getStandard() { return standardSession; }
    public GameSession getDaily() { return dailySession; }
    public void clearActive() { activeSession = null; }

    public boolean isDailyActive() {
        return activeSession != null && activeSession.getMode() == GameSession.GameMode.DAILY_CHALLENGE;
    }
}