package com.gearygolf.golf.hud.renderer.instructions;

public class MobileInstructionContent implements InstructionContent {
    @Override
    public String getTitle() {
        return "HOW TO PLAY";
    }

    @Override
    public String getControlsHeader() {
        return "CONTROLS";
    }

    @Override
    public String[] getControlLines() {
        return new String[]{
                "Single touch drag to rotate the camera, and pinch to zoom.",
                "When in overhead mode, you can pinch to both zoom and pan, and single touch drag to rotate.",
                "When in the shot minigame, you can't adjust your aim, but you can adjust your spin and tap to quit.",
        };
    }

    @Override
    public String getClubsHeader() {
        return "THE BAG (CLUBS)";
    }

    @Override
    public String[][] getClubInfo() {
        return new String[][]{
                {"DRIVER", "Maximum power and Tee bonus."},
                {"WOODS (3/5)", "Long range from the fairway."},
                {"2-IRON", "Long and piercing flight."},
                {"HYBRID", "Mid-range precision club."},
                {"IRONS (5-9)", "Higher numbers mean more loft."},
                {"WEDGES (P/G/S)", "Precision clubs. High spin."},
                {"LOB WEDGE", "Extreme loft for obstacles."},
                {"PUTTER", "Zero loft. Designed for the green."}
        };
    }

    @Override
    public String getGameplayHeader() {
        return "GAMEPLAY";
    }

    @Override
    public String[] getGameplayLines() {
        return new String[]{
                "POWER: Hold the HIT button to charge power. Release to start the swing minigame.",
                "SWING: Tap the HIT button when the dial is in the sweet spots to hit accurately and powerfully, with increased spin.",
                "SPIN: Touch the Spindicator (bottom-left) to set your spin direction.",
                "Hitting the bottom of the ball generates more loft and lower spin. Hitting the top generates less loft and more spin.",
                "PHYSICS: Lift and curl is based on velocity and spin magnitude.",
                "TERRAIN: Slopes kick the initial hit trajectory left or right.",
                "WIND: Increases at higher altitudes."
        };
    }

    @Override
    public String getDifficultyHeader() {
        return "DIFFICULTY";
    }

    @Override
    public String[][] getDifficultyInfo() {
        return new String[][]{
                {"NOVICE",       "Slowest needle. Club info, shot line, rangefinder & windicator active."},
                {"INTERMEDIATE", "Slightly faster needle. Club info removed."},
                {"ADVANCED",     "Faster needle. Shot projection also removed."},
                {"PRO",          "Fast needle. Rangefinder removed. Windicator remains."},
                {"TOUR PRO",     "Fastest needle. All aids removed."}
        };
    }

    @Override
    public String getFooter() {
        return "Drag to scroll, tap outside to return";
    }

    public float getMaxScroll() {
        return 2400f;
    }
}