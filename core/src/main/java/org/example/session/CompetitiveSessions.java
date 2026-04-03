package org.example.session;

public class CompetitiveSessions {
    public final GameSession standard;
    public final GameSession daily18;
    public final GameSession daily9;
    public final GameSession daily1;

    public CompetitiveSessions(GameSession standard, GameSession daily18, GameSession daily9, GameSession daily1) {
        this.standard = standard;
        this.daily18 = daily18;
        this.daily9 = daily9;
        this.daily1 = daily1;
    }

    public GameSession getDaily18() { return daily18; }
    public GameSession getDaily9() { return daily9; }
    public GameSession getDaily1() { return daily1; }
}
