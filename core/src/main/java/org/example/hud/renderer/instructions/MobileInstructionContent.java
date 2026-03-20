package org.example.hud.renderer.instructions;

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
                "When in overhead mode, you can pinch to both zoom and pan, and single touch drag",
                "to rotate",
                "When in the shot minigame, you can't adjust your aim, but you can adjust your spin",
                "and tap to quit.",
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
                "POWER: Hold SPACE to charge power. Release to start swing minigame.",
                "SWING: Press SPACE when the dial is in sweet spots to hit accurately and",
                "powerfully, with increased spin.",
                "SPIN: Touch the 'Spindicator' bottom-left to set your spindication.",
                "Hitting the bottom of the ball will generate more loft, and lower spin.",
                "Hitting the top of the ball will generate less loft, and more spin.",
                "Physics: Lift and curl is based on velocity and spin magnitude",
                "TERRAIN: Slopes kick initial hit trajectory left/right.",
                "WIND: Increases at higher altitudes."
        };
    }

    @Override
    public String getFooter() {
        return "Drag to scroll, tap outside to return";
    }

    public float getMaxScroll() {
        return 1950f;
    }
}