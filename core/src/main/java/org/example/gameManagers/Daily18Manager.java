package org.example.gameManagers;

import com.badlogic.gdx.utils.TimeUtils;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class Daily18Manager {


    public static long getDailySeed() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(TimeUtils.millis()));
        
        long year = cal.get(Calendar.YEAR);
        long month = cal.get(Calendar.MONTH) + 1; // Months are 0-indexed
        long day = cal.get(Calendar.DAY_OF_MONTH);
        
        // Format as YYYYMMDD (e.g., 20260325)
        return year * 10000 + month * 100 + day;
    }

    public static void startDailyChallenge() {
        long seed = getDailySeed();
        System.out.println("Starting Daily 18 with Seed: " + seed);
        
        // Here you would initialize your GameState or LevelGenerator 
        // using this seed to ensure consistent pin positions/wind.
        // GameController.getInstance().initDaily18(seed);
    }
}