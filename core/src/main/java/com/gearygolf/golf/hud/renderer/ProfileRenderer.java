package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.hud.UIUtils;
import com.gearygolf.golf.scoreBoard.RoundHistoryService;
import com.gearygolf.golf.scoreBoard.SkillRatingStore;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Renders the Player Profile overlay.
 *
 * Overview page: 5 difficulty buttons showing name + handicap status.
 * Detail page: full round list for the selected difficulty (up to 20 rounds).
 *
 * Call load() when entering the state; reset() when leaving.
 * render() is called every frame while PROFILE_SCREEN is active.
 */
public class ProfileRenderer {

    /** Flip to true to bypass Firebase and render with hard-coded test data. */
    private static final boolean DUMMY_MODE = false;

    private static final float PANEL_W = 700f;
    private static final float PANEL_H = 580f;
    private static final int   PAR     = 72;

    private static final float BTN_H   = 54f;
    private static final float BTN_GAP = 10f;

    private enum LoadState { IDLE, LOADING, READY }
    private enum View      { OVERVIEW, DETAIL }

    private LoadState loadState  = LoadState.IDLE;
    private View      currentView = View.OVERVIEW;
    private GameConfig.Difficulty selectedDiff = null;
    private String    loadedUid   = "";

    private final Map<GameConfig.Difficulty, List<RoundHistoryService.Round>> data =
            new EnumMap<>(GameConfig.Difficulty.class);

    // Panel position — updated each frame
    private float panelX, panelY;

    // Button hit areas for the overview page — updated each render
    private final float[] btnTopY   = new float[5]; // y passed to font.draw (LibGDX top-of-text in world coords)
    private float btnX, btnW;

    // Back-button hit area for the detail page
    private float backBtnX, backBtnY, backBtnW, backBtnH;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Begin fetching all difficulty histories. Safe to call multiple times; no-op if already loading/ready. */
    public void load(RoundHistoryService service, String uid, String idToken) {
        if (loadState != LoadState.IDLE) return;
        data.clear();
        loadedUid = uid != null ? uid : "";
        loadState = LoadState.LOADING;

        if (DUMMY_MODE) {
            injectDummyData();
            return;
        }

        for (GameConfig.Difficulty diff : GameConfig.Difficulty.values()) {
            service.fetchRounds(uid, idToken, diff, new RoundHistoryService.FetchCallback() {
                @Override public void onSuccess(List<RoundHistoryService.Round> rounds) {
                    data.put(diff, rounds);
                    checkAllDone();
                }
                @Override public void onFailure(String reason) {
                    Gdx.app.error("ProfileRenderer", "Failed to load " + diff + ": " + reason);
                    data.put(diff, new ArrayList<>());
                    checkAllDone();
                }
            });
        }
    }

    /**
     * Populates data with fake rounds for UI testing.  Only called when DUMMY_MODE = true.
     * NOVICE: 12 rounds (~+3 over par → handicap -3)
     * INTERMEDIATE: 10 rounds (~+1 over par → handicap -1)
     * ADVANCED: 8 rounds (~1 under par → handicap +1)
     * PRO: 4 rounds (not enough for handicap)
     * TOUR_PRO: 0 rounds
     */
    private void injectDummyData() {
        long now = System.currentTimeMillis();
        long week = 7L * 24 * 60 * 60 * 1000;

        // Spread across 20 weeks; best 8 scores (71,73,74,75,75,76,76,77) are scattered throughout
        int[] noviceScores = {78, 76, 81, 75, 80, 77, 82, 74, 79, 76, 78, 77, 83, 71, 85, 73, 80, 75, 82, 78};
        List<RoundHistoryService.Round> novice = new ArrayList<>();
        for (int i = 0; i < noviceScores.length; i++)
            novice.add(new RoundHistoryService.Round(noviceScores[i], now - i * week));
        data.put(GameConfig.Difficulty.NOVICE, novice);

        int[] interScores = {73, 75, 72, 74, 76, 73, 71, 74, 75, 72};
        List<RoundHistoryService.Round> inter = new ArrayList<>();
        for (int i = 0; i < interScores.length; i++)
            inter.add(new RoundHistoryService.Round(interScores[i], now - i * week));
        data.put(GameConfig.Difficulty.INTERMEDIATE, inter);

        int[] advScores = {71, 70, 73, 69, 72, 71, 70, 68};
        List<RoundHistoryService.Round> adv = new ArrayList<>();
        for (int i = 0; i < advScores.length; i++)
            adv.add(new RoundHistoryService.Round(advScores[i], now - i * week));
        data.put(GameConfig.Difficulty.ADVANCED, adv);

        int[] proScores = {74, 72, 73, 71};
        List<RoundHistoryService.Round> pro = new ArrayList<>();
        for (int i = 0; i < proScores.length; i++)
            pro.add(new RoundHistoryService.Round(proScores[i], now - i * week));
        data.put(GameConfig.Difficulty.PRO, pro);

        data.put(GameConfig.Difficulty.TOUR_PRO, new ArrayList<>());

        checkAllDone();
    }

    /** Call when leaving the profile screen so state is fresh next time. */
    public void reset() {
        loadState   = LoadState.IDLE;
        currentView = View.OVERVIEW;
        selectedDiff = null;
        data.clear();
    }

    /**
     * Handle ESC / back action.
     * @return true if navigation consumed (detail→overview); false if should close the screen.
     */
    public boolean handleBack() {
        if (currentView == View.DETAIL) {
            currentView  = View.OVERVIEW;
            selectedDiff = null;
            return true;
        }
        return false;
    }

    /**
     * Handle a click/touch in world coordinates.
     * @return true if consumed (inside panel); false if outside (caller should close the screen).
     */
    public boolean handleClick(float x, float y) {
        if (x < panelX || x > panelX + PANEL_W || y < panelY || y > panelY + PANEL_H) return false;

        if (currentView == View.DETAIL) {
            if (x >= backBtnX && x <= backBtnX + backBtnW &&
                y >= backBtnY && y <= backBtnY + backBtnH) {
                currentView  = View.OVERVIEW;
                selectedDiff = null;
            }
        } else if (loadState == LoadState.READY) {
            GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();
            for (int i = 0; i < diffs.length; i++) {
                float top    = btnTopY[i];
                float bottom = top - BTN_H;
                if (x >= btnX && x <= btnX + btnW && y >= bottom && y <= top) {
                    selectedDiff = diffs[i];
                    currentView  = View.DETAIL;
                    break;
                }
            }
        }
        return true;
    }

    public void render(SpriteBatch batch, ShapeRenderer sr, BitmapFont font, Viewport vp) {
        float sw = vp.getWorldWidth();
        float sh = vp.getWorldHeight();
        panelX = (sw - PANEL_W) / 2f;
        panelY = (sh - PANEL_H) / 2f;

        float baseScale = sh * 0.0009f;

        // ── Panel background ──────────────────────────────────────────────────
        batch.begin();
        UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3)
              .draw(batch, panelX, panelY, PANEL_W, PANEL_H);
        batch.end();

        // ── Button/row backgrounds (ShapeRenderer) ────────────────────────────
        if (loadState == LoadState.READY && currentView == View.OVERVIEW) {
            renderOverviewBackgrounds(sr, baseScale);
        }

        // ── Text ─────────────────────────────────────────────────────────────
        batch.begin();

        // Title
        font.getData().setScale(baseScale * 2.0f * 1.1f);
        font.setColor(Color.YELLOW);
        GlyphLayout layout = new GlyphLayout(font, "PLAYER PROFILE");
        font.draw(batch, "PLAYER PROFILE", panelX + (PANEL_W - layout.width) / 2f, panelY + PANEL_H - 18f);

        if (loadState != LoadState.READY) {
            font.getData().setScale(baseScale * 1.1f);
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "Loading...", panelX + 30f, panelY + PANEL_H * 0.55f);
        } else if (currentView == View.DETAIL) {
            renderDetailText(batch, font, baseScale);
        } else {
            renderOverviewText(batch, font, baseScale);
        }

        font.getData().setScale(1f);
        batch.end();
    }

    public boolean isClickInside(float x, float y) {
        return x >= panelX && x <= panelX + PANEL_W && y >= panelY && y <= panelY + PANEL_H;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void checkAllDone() {
        if (data.size() == GameConfig.Difficulty.values().length) {
            loadState = LoadState.READY;
            SkillRatingStore.save(loadedUid, computeRatingLabel());
        }
    }

    /**
     * Computes the display rating label from loaded round data.
     *
     * Qualifying threshold: handicap >= -5 (not more than 5 over par on average).
     * Finds highest qualifying difficulty; formats handicap as:
     *   H > 0  → "DIFF +"   (averaging under par — signal to try harder difficulty)
     *   H = 0  → "DIFF 0"
     *   H < 0  → "DIFF -N"  (averaging over par)
     * Fallback: if no difficulty qualifies but NOVICE has >= 8 rounds, show NOVICE + handicap.
     * Default: "NOVICE".
     */
    String computeRatingLabel() {
        GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();

        // Search highest→lowest for first qualifying difficulty
        for (int i = diffs.length - 1; i >= 0; i--) {
            List<RoundHistoryService.Round> rounds = data.getOrDefault(diffs[i], Collections.emptyList());
            Double h = RoundHistoryService.calculateHandicap(rounds);
            if (h != null && h >= -5.0) {
                return diffs[i].name() + " " + formatHandicap(h);
            }
        }

        // No difficulty qualifies — show NOVICE if enough rounds exist
        List<RoundHistoryService.Round> noviceRounds = data.getOrDefault(diffs[0], Collections.emptyList());
        Double noviceH = RoundHistoryService.calculateHandicap(noviceRounds);
        if (noviceH != null) {
            return "NOVICE " + formatHandicap(noviceH);
        }

        return "NOVICE";
    }

    private static String formatHandicap(double h) {
        if (h > 0)  return "+";
        if (h == 0) return "0";
        return String.valueOf((int) Math.round(h)); // e.g. "-3"
    }

    private void renderOverviewBackgrounds(ShapeRenderer sr, float baseScale) {
        GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();
        btnW = PANEL_W - 60f;
        btnX = panelX + 30f;
        float startY = panelY + PANEL_H - 120f;

        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < diffs.length; i++) {
            float top    = startY - i * (BTN_H + BTN_GAP);
            float bottom = top - BTN_H;
            btnTopY[i] = top;
            sr.setColor(new Color(0.12f, 0.12f, 0.15f, 1f));
            sr.rect(btnX, bottom, btnW, BTN_H);
        }
        sr.end();

        // Gold border lines around buttons
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(new Color(0.6f, 0.5f, 0.1f, 0.5f));
        for (int i = 0; i < diffs.length; i++) {
            float top    = btnTopY[i];
            float bottom = top - BTN_H;
            sr.rect(btnX, bottom, btnW, BTN_H);
        }
        sr.end();
    }

    private void renderOverviewText(SpriteBatch batch, BitmapFont font, float baseScale) {
        GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();
        btnW = PANEL_W - 60f;
        btnX = panelX + 30f;
        float startY = panelY + PANEL_H - 120f;

        // Overall status line
        GameConfig.Difficulty statusLevel = computeStatus();
        String statusText = "Status: " + (statusLevel != null ? statusLevel.name() : "NOVICE");
        font.getData().setScale(baseScale * 1.15f);
        font.setColor(statusLevel != null ? Color.YELLOW : Color.LIGHT_GRAY);
        font.draw(batch, statusText, panelX + 24f, panelY + PANEL_H - 65f);

        GlyphLayout layout = new GlyphLayout();

        for (int i = 0; i < diffs.length; i++) {
            GameConfig.Difficulty diff = diffs[i];
            float top    = startY - i * (BTN_H + BTN_GAP);
            btnTopY[i]   = top;
            float textY  = top - (BTN_H - baseScale * 14f) / 2f;

            List<RoundHistoryService.Round> rounds = data.getOrDefault(diff, Collections.emptyList());
            Double handicap = RoundHistoryService.calculateHandicap(rounds);

            // Difficulty name (left)
            font.getData().setScale(baseScale * 1.1f);
            font.setColor(Color.WHITE);
            font.draw(batch, diff.name(), btnX + 18f, textY);

            // Status label (right)
            String rightText;
            Color  rightColor;
            if (handicap == null) {
                int needed = Math.max(0, 8 - rounds.size());
                rightText  = needed > 0
                        ? "Need " + needed + " more round" + (needed == 1 ? "" : "s")
                        : "Calculating...";
                rightColor = Color.GRAY;
            } else {
                rightText  = String.format("Handicap: %+.1f", handicap);
                rightColor = handicap >= 0 ? new Color(0.4f, 1f, 0.4f, 1f) : Color.ORANGE;
            }
            layout.setText(font, rightText);
            font.setColor(rightColor);
            font.draw(batch, rightText, btnX + btnW - layout.width - 18f, textY);
        }

        // Footer
        font.getData().setScale(baseScale * 0.8f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Tap a difficulty to view rounds  ·  ESC or tap outside to close",
                panelX + 20f, panelY + 28f);
    }

    private void renderDetailText(SpriteBatch batch, BitmapFont font, float baseScale) {
        List<RoundHistoryService.Round> rounds = data.getOrDefault(selectedDiff, Collections.emptyList());
        Double handicap = RoundHistoryService.calculateHandicap(rounds);

        // Difficulty name
        font.getData().setScale(baseScale * 1.4f);
        font.setColor(Color.WHITE);
        font.draw(batch, selectedDiff.name(), panelX + 24f, panelY + PANEL_H - 78f);

        // Handicap / progress
        font.getData().setScale(baseScale * 1.0f);
        if (handicap == null) {
            int needed = Math.max(0, 8 - rounds.size());
            font.setColor(Color.GRAY);
            String msg = needed > 0
                    ? "Need " + needed + " more round" + (needed == 1 ? "" : "s") + " to calculate handicap"
                    : "Calculating...";
            font.draw(batch, msg, panelX + 24f, panelY + PANEL_H - 112f);
        } else {
            font.setColor(handicap >= 0 ? new Color(0.4f, 1f, 0.4f, 1f) : Color.ORANGE);
            font.draw(batch, String.format("Handicap: %+.1f", handicap), panelX + 24f, panelY + PANEL_H - 112f);
        }

        // Round count
        font.getData().setScale(baseScale * 0.82f);
        font.setColor(new Color(0.55f, 0.55f, 0.55f, 1f));
        font.draw(batch, rounds.size() + " / 20 rounds recorded", panelX + 24f, panelY + PANEL_H - 138f);

        // Column headers
        float listTop = panelY + PANEL_H - 165f;
        font.getData().setScale(baseScale * 0.75f);
        font.setColor(new Color(0.5f, 0.5f, 0.5f, 1f));
        font.draw(batch, "#",     panelX + 24f,  listTop);
        font.draw(batch, "SCORE", panelX + 72f,  listTop);
        font.draw(batch, "PAR",   panelX + 200f, listTop);
        font.draw(batch, "DATE",  panelX + 320f, listTop);

        // Compute which rounds are in the best-8 (lowest scores used for handicap)
        Set<Long> best8 = new HashSet<>();
        if (rounds.size() >= 8) {
            List<RoundHistoryService.Round> byScore = new ArrayList<>(rounds);
            byScore.sort((a, b) -> Integer.compare(a.score, b.score));
            for (int i = 0; i < 8; i++) best8.add(byScore.get(i).timestamp);
        }

        // Round rows (newest first)
        List<RoundHistoryService.Round> sorted = new ArrayList<>(rounds);
        sorted.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        int count = Math.min(sorted.size(), 20);
        float rowH = Math.max(baseScale * 16f, 18f);

        Color TOP8_NUM   = new Color(0.95f, 0.78f, 0.10f, 1f); // gold row number
        Color TOP8_SCORE = new Color(1.00f, 1.00f, 0.70f, 1f); // pale-gold score

        for (int i = 0; i < count; i++) {
            RoundHistoryService.Round r = sorted.get(i);
            boolean top8 = best8.contains(r.timestamp);
            int overPar = r.score - PAR;
            String rel = overPar == 0 ? "E" : (overPar > 0 ? "+" + overPar : String.valueOf(overPar));
            float ry = listTop - (i + 1) * rowH;

            font.getData().setScale(baseScale * 0.75f);
            // Row number: gold for top-8, grey otherwise
            font.setColor(top8 ? TOP8_NUM : new Color(0.5f, 0.5f, 0.5f, 1f));
            font.draw(batch, (top8 ? "* " : "  ") + (i + 1), panelX + 14f, ry);
            // Score: pale-gold for top-8, white otherwise
            font.setColor(top8 ? TOP8_SCORE : Color.WHITE);
            font.draw(batch, String.valueOf(r.score),           panelX + 72f,  ry);
            font.setColor(overPar <= 0 ? new Color(0.45f, 0.95f, 0.45f, 1f) : Color.ORANGE);
            font.draw(batch, rel,                               panelX + 200f, ry);
            font.setColor(new Color(0.55f, 0.55f, 0.55f, 1f));
            font.draw(batch, formatDate(r.timestamp),           panelX + 320f, ry);
        }

        if (rounds.isEmpty()) {
            font.getData().setScale(baseScale * 0.9f);
            font.setColor(new Color(0.4f, 0.4f, 0.4f, 1f));
            font.draw(batch, "(no rounds recorded at this difficulty)", panelX + 24f, listTop - rowH);
        }

        // Back button
        backBtnH = 30f;
        backBtnW = 140f;
        backBtnX = panelX + 20f;
        backBtnY = panelY + 14f;
        font.getData().setScale(baseScale * 0.9f);
        font.setColor(Color.YELLOW);
        font.draw(batch, "[ < BACK ]", backBtnX, backBtnY + backBtnH);

        if (!Platform.isAndroid()) {
            font.getData().setScale(baseScale * 0.8f);
            font.setColor(Color.GRAY);
            font.draw(batch, "ESC to close", panelX + PANEL_W - 165f, panelY + 28f);
        }
    }

    private GameConfig.Difficulty computeStatus() {
        GameConfig.Difficulty best = null;
        for (GameConfig.Difficulty diff : GameConfig.Difficulty.values()) {
            List<RoundHistoryService.Round> rounds = data.getOrDefault(diff, Collections.emptyList());
            Double h = RoundHistoryService.calculateHandicap(rounds);
            if (h != null && h > -5.0) best = diff;
        }
        return best;
    }

    private static String formatDate(long epochMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy", Locale.US);
        return sdf.format(new Date(epochMs));
    }
}
