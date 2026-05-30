package com.gearygolf.golf.session;

public class CompetitiveSessions {
    public final GameSession standard;
    public final GameSession daily18;
    public final GameSession daily9;
    public final GameSession dailyPar3;
    public final GameSession dailyPar4;
    public final GameSession dailyPar5;

    public CompetitiveSessions(GameSession standard, GameSession daily18, GameSession daily9,
                               GameSession dailyPar3, GameSession dailyPar4, GameSession dailyPar5) {
        this.standard   = standard;
        this.daily18    = daily18;
        this.daily9     = daily9;
        this.dailyPar3  = dailyPar3;
        this.dailyPar4  = dailyPar4;
        this.dailyPar5  = dailyPar5;
    }

    public GameSession getDaily18()   { return daily18; }
    public GameSession getDaily9()    { return daily9; }
    public GameSession getDailyPar3() { return dailyPar3; }
    public GameSession getDailyPar4() { return dailyPar4; }
    public GameSession getDailyPar5() { return dailyPar5; }
}
