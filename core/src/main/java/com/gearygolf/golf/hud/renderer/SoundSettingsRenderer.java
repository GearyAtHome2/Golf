package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.glamour.SoundManager;
import com.gearygolf.golf.glamour.SoundPrefs;
import com.gearygolf.golf.hud.UIUtils;

/**
 * Overlay panel with main sound settings (master/SFX/ambient + arcade toggle)
 * and an advanced sub-page for per-channel fine-tuning.
 *
 * Font draw convention (LibGDX y-up): the y parameter is the TOP of the text;
 * glyphs render downward from there.  All Y offsets below are measured from
 * panelY (panel bottom) upward, and the title is placed via
 *   panelY + panelHeight - TITLE_INSET
 * which puts the top of the title TITLE_INSET units below the panel top border.
 */
public class SoundSettingsRenderer {

    // ── Panel geometry ────────────────────────────────────────────────────────
    private static final float PANEL_W     = 520f;
    private static final float PANEL_H     = 560f;   // main page (extra row for cinematic mode)
    private static final float ADV_PANEL_H = 620f;   // advanced page

    // ── Slider geometry ───────────────────────────────────────────────────────
    private static final float TRACK_W = 200f;
    private static final float TRACK_H =   6f;
    private static final float THUMB_W =  14f;
    private static final float THUMB_H =  24f;

    // X offsets from panelX
    private static final float LABEL_X_OFF = 20f;
    private static final float TRACK_X_OFF = 210f;
    private static final float VALUE_X_OFF = TRACK_X_OFF + TRACK_W + 14f;

    // ── Main page — Y offsets from panelY (bottom = 0) ───────────────────────
    private static final float TITLE_INSET      = 38f;   // top of title text below panel top
    private static final float[] ROW_Y_OFF      = { 425f, 340f, 255f };  // MASTER, SFX, AMBIENT

    private static final float CB_ROW_Y_OFF     = 185f;  // arcade toggle center Y
    private static final float CIN_ROW_Y_OFF    = 105f;  // cinematic mode toggle center Y
    private static final float TOGGLE_X_OFF     = 370f;
    private static final float TOGGLE_W         =  58f;
    private static final float TOGGLE_H         =  24f;

    private static final float ADV_BTN_Y_OFF = 48f;  // ADVANCED button rect bottom Y
    private static final float ADV_BTN_W     = 150f;
    private static final float ADV_BTN_H     =  24f;

    // ── Advanced page — Y offsets from panelY ────────────────────────────────
    // 6 rows at 80px spacing, first (BOUNCE) at y=505, last (BIRDSONG) at y=105
    private static final float[] ADV_ROW_Y_OFF = { 505f, 425f, 345f, 265f, 185f, 105f };
    private static final String[] ADV_LABELS   = {
        "BOUNCE", "ARCADE AIR", "AIR WHOOSH", "AMB TREES", "AMB WATER", "BIRDSONG"
    };

    private static final float BACK_BTN_Y_OFF = 48f;
    private static final float BACK_BTN_W     = 100f;
    private static final float BACK_BTN_H     =  24f;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean showAdvanced = false;

    // ── Computed each frame ───────────────────────────────────────────────────
    private float panelX, panelY;
    private final float[] trackCenterY    = new float[3];
    private final float[] advTrackCenterY = new float[6];

    // ── Input state ───────────────────────────────────────────────────────────
    private int     draggedSlider = -1;
    private boolean wasPressed    = false;

    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3     tmpV3  = new Vector3();

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isClickInside(float worldX, float worldY) {
        float h = showAdvanced ? ADV_PANEL_H : PANEL_H;
        return worldX >= panelX && worldX <= panelX + PANEL_W
            && worldY >= panelY && worldY <= panelY + h;
    }

    public void render(SpriteBatch batch, ShapeRenderer sr, BitmapFont font,
                       Viewport viewport, SoundManager sm, GameConfig config) {

        float sw = viewport.getWorldWidth();
        float sh = viewport.getWorldHeight();
        float panelH = showAdvanced ? ADV_PANEL_H : PANEL_H;
        panelX = (sw - PANEL_W) / 2f;
        panelY = (sh - panelH) / 2f;
        for (int i = 0; i < 3; i++) trackCenterY[i]    = panelY + ROW_Y_OFF[i];
        for (int i = 0; i < 6; i++) advTrackCenterY[i] = panelY + ADV_ROW_Y_OFF[i];

        handleInput(viewport, sm, config);

        batch.begin();
        UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3)
              .draw(batch, panelX, panelY, PANEL_W, panelH);
        batch.end();

        if (showAdvanced) {
            renderAdvancedPage(batch, sr, font, sh, sm);
        } else {
            renderMainPage(batch, sr, font, sh, sm, config);
        }
    }

    // ── Main page ─────────────────────────────────────────────────────────────

    private void renderMainPage(SpriteBatch batch, ShapeRenderer sr, BitmapFont font,
                                float sh, SoundManager sm, GameConfig config) {
        float[] values = { sm.getMasterVolume(), sm.getSfxVolume(), sm.getAmbientVolume() };

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        float trackX = panelX + TRACK_X_OFF;
        for (int i = 0; i < 3; i++) {
            drawSlider(sr, trackX, trackCenterY[i], values[i], draggedSlider == i);
        }

        // Arcade toggle
        float toggleX = panelX + TOGGLE_X_OFF;
        float toggleY = panelY + CB_ROW_Y_OFF - TOGGLE_H / 2f;
        sr.setColor(sm.isArcadeFlightSoundsEnabled()
            ? new Color(0.55f, 0.40f, 0.05f, 1f)
            : new Color(0.18f, 0.18f, 0.18f, 1f));
        sr.rect(toggleX, toggleY, TOGGLE_W, TOGGLE_H);

        // Cinematic mode toggle
        float cinToggleY = panelY + CIN_ROW_Y_OFF - TOGGLE_H / 2f;
        sr.setColor(config.cinematicMode
            ? new Color(0.55f, 0.40f, 0.05f, 1f)
            : new Color(0.18f, 0.18f, 0.18f, 1f));
        sr.rect(toggleX, cinToggleY, TOGGLE_W, TOGGLE_H);

        // ADVANCED button — centered in panel
        float advBtnX = panelX + (PANEL_W - ADV_BTN_W) / 2f;
        float advBtnY = panelY + ADV_BTN_Y_OFF;
        sr.setColor(0.15f, 0.12f, 0.04f, 1f);
        sr.rect(advBtnX, advBtnY, ADV_BTN_W, ADV_BTN_H);

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        float baseScale  = sh * 0.0009f;
        float titleScale = baseScale * 2.0f;

        // Title — top of text sits TITLE_INSET below the panel top border
        font.getData().setScale(titleScale);
        font.setColor(Color.YELLOW);
        String title = "SETTINGS";
        layout.setText(font, title);
        font.draw(batch, title,
                  panelX + (PANEL_W - layout.width) / 2f,
                  panelY + PANEL_H - TITLE_INSET);

        // Slider labels + values
        font.getData().setScale(baseScale * 1.1f);
        float labelX = panelX + LABEL_X_OFF;
        float valueX = panelX + VALUE_X_OFF;
        for (int i = 0; i < 3; i++) {
            float textY = trackCenterY[i] + font.getCapHeight() * 0.5f;
            font.setColor(Color.WHITE);
            font.draw(batch, new String[]{"MASTER","SFX","AMBIENT"}[i], labelX, textY);
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, (int)(values[i] * 100) + "%", valueX, textY);
        }

        // Arcade toggle row
        font.getData().setScale(baseScale);
        float cbTextY = panelY + CB_ROW_Y_OFF + font.getCapHeight() * 0.5f;
        font.setColor(Color.WHITE);
        font.draw(batch, "ARCADE SHOT SOUNDS", labelX, cbTextY);
        String toggleLabel = sm.isArcadeFlightSoundsEnabled() ? "ON" : "OFF";
        font.setColor(sm.isArcadeFlightSoundsEnabled() ? Color.YELLOW : Color.GRAY);
        layout.setText(font, toggleLabel);
        font.draw(batch, toggleLabel,
                  panelX + TOGGLE_X_OFF + (TOGGLE_W - layout.width) / 2f, cbTextY);

        // Cinematic mode row
        float cinTextY = panelY + CIN_ROW_Y_OFF + font.getCapHeight() * 0.5f;
        font.setColor(Color.WHITE);
        font.draw(batch, "CINEMATIC MODE", labelX, cinTextY);
        String cinLabel = config.cinematicMode ? "ON" : "OFF";
        font.setColor(config.cinematicMode ? Color.YELLOW : Color.GRAY);
        layout.setText(font, cinLabel);
        font.draw(batch, cinLabel,
                  panelX + TOGGLE_X_OFF + (TOGGLE_W - layout.width) / 2f, cinTextY);

        // ADVANCED button label
        float advBtnCenterY = panelY + ADV_BTN_Y_OFF + ADV_BTN_H * 0.5f + font.getCapHeight() * 0.5f;
        font.setColor(new Color(0.85f, 0.70f, 0.25f, 1f));
        String advLabel = "ADVANCED >";
        layout.setText(font, advLabel);
        font.draw(batch, advLabel,
                  advBtnX + (ADV_BTN_W - layout.width) / 2f, advBtnCenterY);

        // Hint
        font.getData().setScale(baseScale * 0.85f);
        font.setColor(Color.GRAY);
        String hint = "ESC  /  TAP OUTSIDE  TO CLOSE";
        layout.setText(font, hint);
        font.draw(batch, hint,
                  panelX + (PANEL_W - layout.width) / 2f,
                  panelY + 22f + font.getCapHeight());

        font.getData().setScale(1.0f);
        batch.end();
    }

    // ── Advanced page ─────────────────────────────────────────────────────────

    private void renderAdvancedPage(SpriteBatch batch, ShapeRenderer sr, BitmapFont font,
                                    float sh, SoundManager sm) {
        float[] advValues = {
            sm.getBounceScale(), sm.getArcadeAirborneScale(), sm.getAirWhooshScale(),
            sm.getAmbientTreesScale(), sm.getAmbientWaterScale(), sm.getBirdsongScale()
        };

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        float trackX = panelX + TRACK_X_OFF;
        for (int i = 0; i < 6; i++) {
            drawSlider(sr, trackX, advTrackCenterY[i], advValues[i], draggedSlider == i);
        }

        // Back button
        float backBtnX = panelX + LABEL_X_OFF;
        float backBtnY = panelY + BACK_BTN_Y_OFF;
        sr.setColor(0.15f, 0.12f, 0.04f, 1f);
        sr.rect(backBtnX, backBtnY, BACK_BTN_W, BACK_BTN_H);

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        float baseScale  = sh * 0.0009f;
        float titleScale = baseScale * 2.0f;

        // Title
        font.getData().setScale(titleScale);
        font.setColor(Color.YELLOW);
        String title = "ADVANCED SOUND";
        layout.setText(font, title);
        font.draw(batch, title,
                  panelX + (PANEL_W - layout.width) / 2f,
                  panelY + ADV_PANEL_H - TITLE_INSET);

        // Slider labels + values
        font.getData().setScale(baseScale * 1.1f);
        float labelX = panelX + LABEL_X_OFF;
        float valueX = panelX + VALUE_X_OFF;
        for (int i = 0; i < 6; i++) {
            float textY = advTrackCenterY[i] + font.getCapHeight() * 0.5f;
            font.setColor(Color.WHITE);
            font.draw(batch, ADV_LABELS[i], labelX, textY);
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, (int)(advValues[i] * 100) + "%", valueX, textY);
        }

        // Back button label
        font.getData().setScale(baseScale);
        float backBtnCenterY = panelY + BACK_BTN_Y_OFF + BACK_BTN_H * 0.5f + font.getCapHeight() * 0.5f;
        font.setColor(new Color(0.85f, 0.70f, 0.25f, 1f));
        String backLabel = "< BACK";
        layout.setText(font, backLabel);
        font.draw(batch, backLabel,
                  backBtnX + (BACK_BTN_W - layout.width) / 2f, backBtnCenterY);

        // Hint
        font.getData().setScale(baseScale * 0.85f);
        font.setColor(Color.GRAY);
        String hint = "ESC  TO CLOSE";
        layout.setText(font, hint);
        font.draw(batch, hint,
                  panelX + (PANEL_W - layout.width) / 2f,
                  panelY + 22f + font.getCapHeight());

        font.getData().setScale(1.0f);
        batch.end();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private void handleInput(Viewport viewport, SoundManager sm, GameConfig config) {
        boolean isNowPressed = Gdx.input.isTouched();
        boolean justPressed  = isNowPressed && !wasPressed;
        boolean justReleased = !isNowPressed && wasPressed;

        float worldMX = 0, worldMY = 0;
        if (isNowPressed || justReleased) {
            tmpV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(tmpV3);
            worldMX = tmpV3.x;
            worldMY = tmpV3.y;
        }

        if (justPressed) {
            draggedSlider = -1;
            float trackX = panelX + TRACK_X_OFF;

            if (showAdvanced) {
                for (int i = 0; i < 6; i++) {
                    if (hitSlider(worldMX, worldMY, trackX, advTrackCenterY[i])) {
                        draggedSlider = i;
                        break;
                    }
                }
                // Back button
                float backBtnX = panelX + LABEL_X_OFF;
                float backBtnY = panelY + BACK_BTN_Y_OFF;
                if (worldMX >= backBtnX && worldMX <= backBtnX + BACK_BTN_W
                 && worldMY >= backBtnY - 6 && worldMY <= backBtnY + BACK_BTN_H + 6) {
                    showAdvanced = false;
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    if (hitSlider(worldMX, worldMY, trackX, trackCenterY[i])) {
                        draggedSlider = i;
                        break;
                    }
                }
                // Arcade toggle
                float cbX  = panelX + LABEL_X_OFF;
                float cbY  = panelY + CB_ROW_Y_OFF - TOGGLE_H / 2f;
                if (worldMX >= cbX && worldMX <= panelX + TOGGLE_X_OFF + TOGGLE_W + 10
                 && worldMY >= cbY - 6 && worldMY <= cbY + TOGGLE_H + 6) {
                    sm.setArcadeFlightSoundsEnabled(!sm.isArcadeFlightSoundsEnabled());
                    savePrefs(sm, config);
                }
                // Cinematic mode toggle
                float cinY = panelY + CIN_ROW_Y_OFF - TOGGLE_H / 2f;
                if (worldMX >= cbX && worldMX <= panelX + TOGGLE_X_OFF + TOGGLE_W + 10
                 && worldMY >= cinY - 6 && worldMY <= cinY + TOGGLE_H + 6) {
                    config.cinematicMode = !config.cinematicMode;
                    savePrefs(sm, config);
                }
                // ADVANCED button
                float advBtnX = panelX + (PANEL_W - ADV_BTN_W) / 2f;
                float advBtnY = panelY + ADV_BTN_Y_OFF;
                if (worldMX >= advBtnX && worldMX <= advBtnX + ADV_BTN_W
                 && worldMY >= advBtnY - 6 && worldMY <= advBtnY + ADV_BTN_H + 6) {
                    showAdvanced = true;
                }
            }
        }

        if (isNowPressed && draggedSlider >= 0) {
            float trackX = panelX + TRACK_X_OFF;
            float newVal = MathUtils.clamp((worldMX - trackX) / TRACK_W, 0f, 1f);
            if (showAdvanced) {
                switch (draggedSlider) {
                    case 0 -> sm.setBounceScale(newVal);
                    case 1 -> sm.setArcadeAirborneScale(newVal);
                    case 2 -> sm.setAirWhooshScale(newVal);
                    case 3 -> sm.setAmbientTreesScale(newVal);
                    case 4 -> sm.setAmbientWaterScale(newVal);
                    case 5 -> sm.setBirdsongScale(newVal);
                }
                saveAdvancedPrefs(sm);
            } else {
                switch (draggedSlider) {
                    case 0 -> sm.setMasterVolume(newVal);
                    case 1 -> sm.setSfxVolume(newVal);
                    case 2 -> sm.setAmbientVolume(newVal);
                }
                savePrefs(sm, config);
            }
        }

        if (justReleased) draggedSlider = -1;
        wasPressed = isNowPressed;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawSlider(ShapeRenderer sr, float trackX, float centerY, float val, boolean dragging) {
        float ty = centerY - TRACK_H / 2f;
        sr.setColor(0.18f, 0.18f, 0.18f, 1f);
        sr.rect(trackX, ty, TRACK_W, TRACK_H);
        if (val > 0f) {
            sr.setColor(0.7f, 0.55f, 0.1f, 1f);
            sr.rect(trackX, ty, TRACK_W * val, TRACK_H);
        }
        float thumbX = trackX + TRACK_W * val - THUMB_W / 2f;
        sr.setColor(dragging ? Color.YELLOW : Color.WHITE);
        sr.rect(thumbX, centerY - THUMB_H / 2f, THUMB_W, THUMB_H);
    }

    private boolean hitSlider(float mx, float my, float trackX, float centerY) {
        return mx >= trackX - 10 && mx <= trackX + TRACK_W + 10
            && my >= centerY - THUMB_H * 1.5f && my <= centerY + THUMB_H * 1.5f;
    }

    private void savePrefs(SoundManager sm, GameConfig config) {
        SoundPrefs.save(sm.getMasterVolume(), sm.getSfxVolume(), sm.getAmbientVolume(),
                        sm.isArcadeFlightSoundsEnabled(), config.cinematicMode);
    }

    private void saveAdvancedPrefs(SoundManager sm) {
        SoundPrefs.saveAdvanced(sm.getBounceScale(), sm.getArcadeAirborneScale(),
                                sm.getAirWhooshScale(), sm.getAmbientTreesScale(),
                                sm.getAmbientWaterScale(), sm.getBirdsongScale());
    }
}
