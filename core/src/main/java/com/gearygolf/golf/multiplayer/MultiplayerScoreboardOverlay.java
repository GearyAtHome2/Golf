package com.gearygolf.golf.multiplayer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import com.gearygolf.golf.Platform;
import com.gearygolf.golf.hud.UIUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * Rendered over the 3D scene after the local player holes out in a multiplayer match.
 *
 * Shows:
 *   - Title: "HOLE X COMPLETE" or "MATCH COMPLETE"
 *   - Ranked table: Pos | Player | This Hole | Total
 *   - Host: "NEXT HOLE" button (disabled until all players done), becomes "END MATCH" on final hole
 *   - Final scoreboard: all players get "RETURN TO MENU" button
 *
 * Call {@link #update} each time poll data arrives; call {@link #act}/{@link #draw} each frame.
 */
public class MultiplayerScoreboardOverlay implements Disposable {

    private final Stage   stage;
    private final Skin    skin;
    private final Runnable onNextHole;
    private final Runnable onReturnToMenu;
    private boolean hidden            = false;
    private float   returnBtnCooldown = 0f;
    private boolean wasFinal          = false;
    private boolean nextHoleInFlight  = false;

    // Cached last update() args so the hidden tab can immediately rebuild the panel
    private boolean                    hasCachedData        = false;
    private Map<String, String>        cachedPlayers        = null;
    private Map<String, String>        cachedDifficulties   = null;
    private Map<String, int[]>         cachedHoleScores     = null;
    private int                        cachedHoleIndex      = 0;
    private boolean                    cachedAllDone        = false;
    private boolean                    cachedIsFinal        = false;
    private boolean                    cachedIsHost         = false;

    public MultiplayerScoreboardOverlay(Skin skin, Runnable onNextHole, Runnable onReturnToMenu) {
        this.skin           = skin;
        this.onNextHole     = onNextHole;
        this.onReturnToMenu = onReturnToMenu;
        this.stage          = new Stage(new ExtendViewport(1280, 720));
    }

    /** Resets hidden state — call when showing for a new hole. */
    public void resetHidden() { hidden = false; nextHoleInFlight = false; }

    /**
     * Marks the "Next Hole" Firebase call as in-flight (true) or resolved (false).
     * While true the button is greyed out and labelled "ADVANCING…".
     * Call this from GolfGame before firing the RTDB write, and again on failure.
     */
    public void setNextHoleInFlight(boolean v) { nextHoleInFlight = v; }

    public Stage getStage() { return stage; }

    public void resize(int w, int h) { stage.getViewport().update(w, h, true); }

    public void act(float delta) {
        if (returnBtnCooldown > 0) returnBtnCooldown = Math.max(0f, returnBtnCooldown - delta);
        stage.act(delta);
    }
    public void draw()           { stage.draw(); }

    /**
     * Rebuilds the overlay with fresh data. Call whenever poll data changes.
     *
     * @param players             uid → displayName map (all players in the room)
     * @param playerDifficulties  uid → difficulty name (e.g. "NOVICE") — shown as first letter
     * @param holeScores          uid → int[9] stroke counts (0 = not yet played)
     * @param holeIndex           0-based index of the hole just completed
     * @param allDone             true when every player has submitted a score for holeIndex
     * @param isFinal             true when this was the last hole (match complete)
     * @param isHost              true if the local player is the room host
     */
    public void update(Map<String, String> players,
                       Map<String, String> playerDifficulties,
                       Map<String, int[]>  holeScores,
                       int holeIndex,
                       boolean allDone,
                       boolean isFinal,
                       boolean isHost) {
        // Cache args so buildHiddenTab can immediately restore the full panel
        hasCachedData      = true;
        cachedPlayers      = players;
        cachedDifficulties = playerDifficulties;
        cachedHoleScores   = holeScores;
        cachedHoleIndex    = holeIndex;
        cachedAllDone      = allDone;
        cachedIsFinal      = isFinal;
        cachedIsHost       = isHost;

        stage.clear();

        // Start 1s cooldown the first time the final scoreboard appears
        if (isFinal && !wasFinal) { returnBtnCooldown = 1.0f; wasFinal = true; }
        else if (!isFinal)        { wasFinal = false; }

        boolean android    = Platform.isAndroid();
        float panelW       = android ? 780f : 560f;
        float btnH         = android ? 90f  : 54f;
        float pad          = 28f;
        float sp           = 10f;
        float titleScale   = android ? 1.35f : 1.25f;
        float headerScale  = android ? 1.05f : 0.90f;
        float rowScale     = android ? 1.10f : 0.95f;
        float btnScale     = android ? 1.15f : 0.95f;

        if (hidden) {
            buildHiddenTab(btnH, btnScale);
            return;
        }

        // Semi-transparent full-screen background
        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.enabled);
        root.setBackground(UIUtils.createRoundedRectDrawable(new Color(0f, 0f, 0f, 0.60f), 0));

        // Panel
        Table panel = new Table();
        panel.setBackground(UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3));
        panel.pad(pad);

        // Title
        String titleText = isFinal ? "MATCH COMPLETE" : "HOLE " + (holeIndex + 1) + " COMPLETE";
        Label title = new Label(titleText, skin, "default");
        title.setFontScale(titleScale);
        title.setColor(Color.GOLD);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(sp * 2).row();

        // Build ranked rows
        java.util.List<PlayerRow> rows = buildRankedRows(players, playerDifficulties, holeScores, holeIndex);

        // Column header
        Table header = new Table();
        addHeaderCell(header, "POS",    65f,  headerScale);
        addHeaderCell(header, "PLAYER", 210f, headerScale);
        addHeaderCell(header, "D",       45f, headerScale);
        addHeaderCell(header, "HOLE",    80f, headerScale);
        addHeaderCell(header, "TOTAL",   80f, headerScale);
        panel.add(header).expandX().fillX().padBottom(sp).row();

        // Separator
        panel.add(new Image(UIUtils.createRoundedRectDrawable(Color.GOLD, 0)))
             .height(1).expandX().fillX().padBottom(sp).row();

        // Data rows
        for (PlayerRow row : rows) {
            Table r = new Table();
            addDataCell(r, row.rank,  65f,  rowScale, row.rankColor);
            addDataCell(r, row.name,  210f, rowScale, Color.WHITE);
            addDataCell(r, row.diff,   45f, rowScale, Color.LIGHT_GRAY);
            addDataCell(r, row.hole,   80f, rowScale, Color.WHITE);
            addDataCell(r, row.total,  80f, rowScale, Color.WHITE);
            panel.add(r).expandX().fillX().padBottom(4f).row();
        }

        panel.add(new Image(UIUtils.createRoundedRectDrawable(Color.DARK_GRAY, 0)))
             .height(1).expandX().fillX().padTop(sp).padBottom(sp * 1.5f).row();

        // Buttons
        if (isFinal) {
            // All players get "Return to Menu" on the final scoreboard
            boolean cooldown = returnBtnCooldown > 0;
            TextButton returnBtn = new TextButton("RETURN TO MENU", skin, "default");
            returnBtn.getLabel().setFontScale(btnScale);
            returnBtn.setDisabled(cooldown);
            returnBtn.setTouchable(cooldown ? Touchable.disabled : Touchable.enabled);
            returnBtn.getLabel().setColor(cooldown ? Color.GRAY : Color.WHITE);
            returnBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                    if (returnBtnCooldown <= 0) onReturnToMenu.run();
                }
            });
            panel.add(returnBtn).width(panelW - 60f).height(btnH).row();
        } else if (isHost) {
            // Host gets "Next Hole" button, disabled until all done or while Firebase call is in flight
            boolean btnEnabled = allDone && !nextHoleInFlight;
            String  btnLabel   = nextHoleInFlight ? "ADVANCING..." : (isFinal ? "END MATCH" : "NEXT HOLE");
            TextButton nextBtn = new TextButton(btnLabel, skin, "default");
            nextBtn.getLabel().setFontScale(btnScale);
            nextBtn.setDisabled(!btnEnabled);
            nextBtn.setTouchable(btnEnabled ? Touchable.enabled : Touchable.disabled);
            nextBtn.getLabel().setColor(btnEnabled ? Color.WHITE : Color.GRAY);
            nextBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                    if (allDone && !nextHoleInFlight) onNextHole.run();
                }
            });
            panel.add(nextBtn).width(panelW - 60f).height(btnH).row();

            if (!allDone) {
                Label waiting = new Label("Waiting for other players...", skin, "default");
                waiting.setFontScale(rowScale * 0.85f);
                waiting.setColor(Color.LIGHT_GRAY);
                waiting.setAlignment(Align.center);
                panel.add(waiting).expandX().fillX().padTop(sp * 0.5f).row();
            } else if (nextHoleInFlight) {
                Label advancing = new Label("Advancing to next hole...", skin, "default");
                advancing.setFontScale(rowScale * 0.85f);
                advancing.setColor(Color.LIGHT_GRAY);
                advancing.setAlignment(Align.center);
                panel.add(advancing).expandX().fillX().padTop(sp * 0.5f).row();
            }
        } else {
            // Guest — no button, just waiting message
            Label waiting = new Label("Waiting for host...", skin, "default");
            waiting.setFontScale(rowScale * 0.85f);
            waiting.setColor(Color.LIGHT_GRAY);
            waiting.setAlignment(Align.center);
            panel.add(waiting).expandX().fillX().row();
        }

        // Hide button — always available (except final scoreboard)
        if (!isFinal) {
            TextButton hideBtn = new TextButton("HIDE", skin, "default");
            hideBtn.getLabel().setFontScale(btnScale * 0.85f);
            hideBtn.getLabel().setColor(Color.LIGHT_GRAY);
            hideBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                    hidden = true;
                    stage.clear();
                    buildHiddenTab(btnH, btnScale);
                }
            });
            panel.add(hideBtn).width(panelW - 60f).height(btnH * 0.7f).padTop(sp * 0.5f).row();
        }

        root.add(panel).width(panelW).expand().center();
        stage.addActor(root);
    }

    private void buildHiddenTab(float btnH, float btnScale) {
        TextButton showBtn = new TextButton("SCORES ▲", skin, "default");
        showBtn.getLabel().setFontScale(btnScale * 1.3f);
        showBtn.setColor(new Color(0.05f, 0.05f, 0.05f, 0.90f));
        showBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent e, float x, float y) {
                hidden = false;
                // Rebuild the full panel immediately using cached data rather than waiting for the next poll
                if (hasCachedData) {
                    update(cachedPlayers, cachedDifficulties, cachedHoleScores,
                           cachedHoleIndex, cachedAllDone, cachedIsFinal, cachedIsHost);
                }
            }
        });

        Table tab = new Table();
        tab.setFillParent(true);
        tab.add(showBtn).width(260f).height(btnH)
           .expandX().center()
           .expandY().bottom().padBottom(20f);
        stage.addActor(tab);
    }

    // -------------------------------------------------------------------------
    // Ranking logic
    // -------------------------------------------------------------------------

    private static class PlayerRow {
        String rank, name, diff, hole, total;
        Color rankColor;
    }

    private java.util.List<PlayerRow> buildRankedRows(Map<String, String> players,
                                             Map<String, String> playerDifficulties,
                                             Map<String, int[]> holeScores,
                                             int holeIndex) {
        // Collect totals — [uid, name, diffLetter, holeScore, total]
        java.util.List<String[]> data = new ArrayList<>();
        for (Map.Entry<String, String> e : players.entrySet()) {
            String uid  = e.getKey();
            String name = e.getValue();
            int[]  arr  = holeScores.get(uid);
            int holeScore = (arr != null && holeIndex < arr.length) ? arr[holeIndex] : 0;
            int total = 0;
            if (arr != null) {
                for (int i = 0; i <= holeIndex && i < arr.length; i++) total += arr[i];
            }
            String diffName   = playerDifficulties != null ? playerDifficulties.get(uid) : null;
            String diffLetter = (diffName != null && !diffName.isEmpty())
                                ? String.valueOf(diffName.charAt(0)) : "N";
            data.add(new String[]{ uid, name, diffLetter, String.valueOf(holeScore), String.valueOf(total) });
        }

        // data[] = { uid[0], name[1], diffLetter[2], holeScore[3], total[4] }
        // Sort ascending by total strokes (0 = not yet finished → sort last)
        data.sort((a, b) -> {
            int ta = Integer.parseInt(a[4]);
            int tb = Integer.parseInt(b[4]);
            if (ta == 0 && tb == 0) return 0;
            if (ta == 0) return 1;
            if (tb == 0) return -1;
            return Integer.compare(ta, tb);
        });

        // Assign ranks with ties
        java.util.List<PlayerRow> rows = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < data.size(); i++) {
            String[] d = data.get(i);
            int total = Integer.parseInt(d[4]);
            if (i > 0 && total > 0 && Integer.parseInt(data.get(i - 1)[4]) == total) {
                if (!rows.get(i - 1).rank.endsWith("=")) {
                    rows.get(i - 1).rank = rows.get(i - 1).rank + "=";
                }
            } else {
                rank = i + 1;
            }

            PlayerRow row = new PlayerRow();
            boolean tiesWithNext = (i + 1 < data.size())
                && Integer.parseInt(data.get(i + 1)[4]) == total && total > 0;
            if (tiesWithNext || (i > 0 && Integer.parseInt(data.get(i - 1)[4]) == total && total > 0)) {
                row.rank = rank + "=";
            } else {
                row.rank = ordinal(rank);
            }

            row.name      = truncate(d[1], 16);
            row.diff      = d[2];
            row.hole      = total == 0 ? "-" : d[3].equals("0") ? "-" : d[3];
            row.total     = total == 0 ? "-" : d[4];
            row.rankColor = rank == 1 && total > 0 ? Color.GOLD : Color.LIGHT_GRAY;
            rows.add(row);
        }
        return rows;
    }

    private static String ordinal(int n) {
        if (n == 1) return "1st";
        if (n == 2) return "2nd";
        if (n == 3) return "3rd";
        return n + "th";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // -------------------------------------------------------------------------
    // Cell helpers
    // -------------------------------------------------------------------------

    private void addHeaderCell(Table t, String text, float width, float scale) {
        Label l = new Label(text, skin, "default");
        l.setFontScale(scale);
        l.setColor(Color.GOLD);
        l.setAlignment(Align.center);
        t.add(l).width(width).center();
    }

    private void addDataCell(Table t, String text, float width, float scale, Color color) {
        Label l = new Label(text, skin, "default");
        l.setFontScale(scale);
        l.setColor(color);
        l.setAlignment(Align.center);
        t.add(l).width(width).center();
    }

    // -------------------------------------------------------------------------

    @Override
    public void dispose() { stage.dispose(); }
}
