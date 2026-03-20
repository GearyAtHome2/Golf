package org.example.hud.mobile;

import com.badlogic.gdx.graphics.Color;

public class MobileUIValues {

    // --- DESIGN TOKENS: DIMENSIONS ---
    protected static final float BTN_WIDTH_STD = 230f;
    protected static final float BTN_HEIGHT_STD = 110f;
    protected static final float BTN_HEIGHT_LARGE = 130f;

    protected static final float HIT_BTN_WIDTH = 240f;
    protected static final float HIT_BTN_HEIGHT = 150f;

    protected static final float MAX_BTN_WIDTH = 200f;
    protected static final float MAX_BTN_HEIGHT = 110f;

    protected static final float INFO_BTN_WIDTH = 120f;
    protected static final float INFO_BTN_HEIGHT = 80f;

    protected static final float SPIN_INDICATOR_SIZE = 160f;
    protected static final float DEBUG_ACTOR_WIDTH = 400f;
    protected static final float DEBUG_ACTOR_HEIGHT = 140f;

    // --- DESIGN TOKENS: PADDING & SPACING ---
    protected static final float PAD_SCREEN_EDGE = 20f;
    protected static final float LEFT_STACK_TOP_PAD = 180f;
    protected static final float RIGHT_STACK_TOP_PAD = -60f;
    protected static final float RIGHT_STACK_SPACER = 180f;
    protected static final float BTN_SPACING_V = 15f;
    protected static final float MENU_TOP_OFFSET_PCT = 0.22f;

    // --- DESIGN TOKENS: CORNER RADII ---
    protected static final int RADIUS_STD = 12;
    protected static final int RADIUS_HIT = 15;

    // --- DESIGN TOKENS: COLORS ---
    protected static final Color COLOR_BTN_UP = new Color(0.2f, 0.2f, 0.2f, 0.5f);
    protected static final Color COLOR_BTN_DOWN = new Color(0.4f, 0.4f, 0.4f, 0.7f);
    protected static final Color COLOR_HIT_UP = new Color(0.8f, 0.2f, 0.2f, 0.7f);
    protected static final Color COLOR_HIT_DOWN = new Color(0.5f, 0.1f, 0.1f, 0.8f);
    protected static final Color COLOR_MAX_UP = new Color(0.5f, 0.05f, 0.05f, 0.75f);
    protected static final Color COLOR_MAX_DOWN = new Color(0.3f, 0.02f, 0.02f, 0.85f);
    protected static final Color COLOR_MENU_UP = new Color(0.15f, 0.15f, 0.15f, 0.6f);
    protected static final Color COLOR_MENU_DOWN = new Color(0.4f, 0.4f, 0.1f, 0.8f);

    // --- DESIGN TOKENS: SCALING ---
    protected static final float FONT_SCALE_GAMEPLAY = 2.5f; // Adjust this until it looks right
    protected static final float FONT_SCALE_START_MENU = 3.85f;
    protected static final float FONT_SCALE_PAUSE_MENU = 3.6f;
    protected static final float MENU_BTN_WIDTH_PCT = 0.65f;
    protected static final float MENU_BTN_HEIGHT_PCT = 0.11f;

}