package org.example.hud.renderer.instructions;

public interface InstructionContent {
    float getMaxScroll();
    String getTitle();
    String getControlsHeader();
    String[] getControlLines();
    String getClubsHeader();
    String[][] getClubInfo();
    String getGameplayHeader();
    String[] getGameplayLines();
    String getFooter();
}