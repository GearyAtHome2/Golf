package org.example.hud.renderer.instructions;

public class DesktopInstructionContent implements InstructionContent {
    @Override public String getTitle() { return "HOW TO PLAY"; }
    @Override public String getControlsHeader() { return "CONTROLS"; }
    @Override public String[] getControlLines() {
        return new String[]{
                "[WASD] - Aim Contact Point (Spin and loft)",
                "[RCLICK + DRAG] - Aim Direction",
                "[SCROLL] - Change Clubs",
                "[SPACE] - Hold for power / Lock in shot minigame",
                "[LTAB] - Map Oversight View",
                " * You can use LClick and drag to rotate, RClick to pan",
                "[F] - Use Rangefinder",
                "[P] - Toggle shot angle projection",
                "[i] - Show club information",
                "[R] - Reset Ball to last position",
                "[N] - Next map",
                "[ESC] - Pause Menu",
                "[UP/DOWN] - Change game speed"
        };
    }
    @Override public String getClubsHeader() { return "THE BAG (CLUBS)"; }
    @Override public String[][] getClubInfo() {
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
    @Override public String getGameplayHeader() { return "GAMEPLAY"; }
    @Override public String[] getGameplayLines() {
        return new String[]{
                "SPIN: 'Bottom' = High loft / Low spin.",
                "Physics: engine simulates lift based on",
                "velocity and spin magnitude consistently.",
                "POWER: Time SPACE in sweet spots.",
                "TERRAIN: Slopes kick trajectory left/right.",
                "WIND: Affects altitudes more significantly."
        };
    }
    @Override public String getFooter() { return "W/S or UP/DOWN to Scroll | [ESC] to Return"; }

    @Override
    public float getMaxScroll(){
        return 850f;
    }

}