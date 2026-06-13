package com.gearygolf.golf.hud.mobile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MobileUIValues {

    // --- ANCHOR POINTS (Normalized 0.0 to 1.0) ---
    protected static final float MAX_BTN_Y = 0.84f;
    protected static final float CLUB_ARROW_Y = 0.01f;

    // --- DESIGN TOKENS: COLORS ---
    protected static final Color COLOR_BTN_UP = new Color(0.2f, 0.2f, 0.2f, 0.5f);
    protected static final Color COLOR_BTN_DOWN = new Color(0.4f, 0.4f, 0.4f, 0.7f);
    protected static final Color COLOR_HIT_UP = new Color(0.8f, 0.2f, 0.2f, 0.7f);
    protected static final Color COLOR_HIT_DOWN = new Color(0.5f, 0.1f, 0.1f, 0.8f);
    protected static final Color COLOR_MAX_UP = new Color(0.5f, 0.05f, 0.05f, 0.75f);
    protected static final Color COLOR_MAX_DOWN = new Color(0.3f, 0.02f, 0.02f, 0.85f);
    protected static final Color COLOR_MENU_UP = new Color(0.15f, 0.15f, 0.15f, 0.6f);
    protected static final Color COLOR_MENU_DOWN = new Color(0.4f, 0.4f, 0.1f, 0.8f);

    // --- DESIGN TOKENS: DIMENSIONS ---
    protected static final int RADIUS_STD = 12;
    protected static final int RADIUS_HIT = 15;
    protected static final float FONT_SCALE_GAMEPLAY = 1.8f;
    protected static final float FONT_SCALE_START_MENU = 3.85f;
    protected static final float FONT_SCALE_PAUSE_MENU = 3.6f;

    // --- RELATIVE GETTERS ---
    // Switched to WorldHeight-based widths to maintain aspect ratio on wide screens
    protected static float getBtnWidth(Viewport v) { return v.getWorldHeight() * 0.28f; }
    protected static float getBtnHeight(Viewport v) { return v.getWorldHeight() * 0.11f; }
    protected static float getHitBtnWidth(Viewport v) { return v.getWorldHeight() * 0.30f; }
    protected static float getHitBtnHeight(Viewport v) { return v.getWorldHeight() * 0.14f; }

    protected static float getArrowWidth(Viewport v)      { return v.getWorldWidth() * 0.08f; }
    protected static float getArrowHeight(Viewport v)     { return v.getWorldHeight() * 0.09f; }
    protected static float getSmallArrowWidth(Viewport v) { return v.getWorldWidth() * 0.055f; }
    protected static float getSpinSize(Viewport v) { return v.getWorldHeight() * 0.20f; }
    protected static float getDebugWidth(Viewport v) { return v.getWorldHeight() * 0.50f; }

    // --- PADDING & SPACING ---
    // Reduced to 1% to pull right-hand elements closer to the bezel
    protected static float getEdgePad(Viewport v) { return v.getWorldWidth() * 0.01f; }
    protected static float getStackSpacing(Viewport v) { return v.getWorldHeight() * 0.015f; }
    protected static float getLeftStackTopPad(Viewport v) { return v.getWorldHeight() * 0.31f; }

    // --- START MENU LAYOUT FRACTIONS ---
    protected static final float MENU_TABLE_WIDTH_FRAC   = 0.485f;
    protected static final float MENU_PAD_LEFT_FRAC      = 0.016f;
    protected static final float MENU_PAD_TOP_FRAC       = 0.255f;
    protected static final float MENU_BUTTON_WIDTH_FRAC  = 0.453f;
    protected static final float MENU_BUTTON_HEIGHT_FRAC = 0.09f;
    protected static final float MENU_SPACING_FRAC       = 0.010f;
    protected static final float MENU_SCROLL_HEIGHT_FRAC = 0.70f;

    // --- CLUB ARROW ROW GAPS ---
    protected static final float CLUB_ARROW_GAP_INNER_FRAC = 0.02f;  // gap between < and >
    protected static final float CLUB_ARROW_GAP_OUTER_FRAC = 0.01f;  // gap between outer and inner arrows
}