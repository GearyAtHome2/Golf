package com.gearygolf.golf.multiplayer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.gearygolf.golf.hud.UIUtils;

import java.util.List;

/**
 * Compact in-game scoreboard shown during a multiplayer round.
 * Tap the panel to dismiss it (restores the toggle button).
 *
 * Call {@link #update} whenever player stroke data changes.
 */
public class LiveScoreboardActor extends Table {

    public static class ScoreEntry {
        public final String  displayName;
        public final int     holeStrokes;   // shots taken this hole (0 = not yet shot)
        public final int     totalStrokes;  // sum of all holes including current
        public final boolean holedOut;      // true when ball is in the hole this hole
        public final boolean isLocal;       // true for the local player

        public ScoreEntry(String displayName, int holeStrokes, int totalStrokes,
                          boolean holedOut, boolean isLocal) {
            this.displayName  = displayName;
            this.holeStrokes  = holeStrokes;
            this.totalStrokes = totalStrokes;
            this.holedOut     = holedOut;
            this.isLocal      = isLocal;
        }
    }

    private static final Color COLOR_HOLED   = Color.GOLD;
    private static final Color COLOR_LOCAL   = Color.WHITE;
    private static final Color COLOR_REMOTE  = new Color(0.82f, 0.82f, 0.82f, 1f);
    private static final Color COLOR_HEADER  = new Color(1f, 0.85f, 0.2f, 0.90f);
    private static final Color BG_COLOR      = new Color(0.04f, 0.04f, 0.04f, 0.88f);

    private final Skin  skin;
    private final float rowScale;

    public LiveScoreboardActor(Skin skin, float rowScale, Runnable onDismiss) {
        this.skin     = skin;
        this.rowScale = rowScale;

        setTouchable(Touchable.enabled);
        addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                onDismiss.run();
            }
        });
        setVisible(false);
    }

    /** Rebuild the panel with fresh data. May be called every frame. */
    public void update(List<ScoreEntry> entries) {
        clearChildren();
        setBackground(UIUtils.createGoldBorderedPanel(BG_COLOR, 2));
        pad(10f, 12f, 10f, 12f);

        // ── Header row ──────────────────────────────────────────────────
        addCell("PLAYER", COLOR_HEADER, 0.78f, 200f);
        addCell("HOLE",   COLOR_HEADER, 0.78f,  80f);
        addCell("TOT",    COLOR_HEADER, 0.78f,  80f);
        row().padBottom(6f);

        // ── Player rows ─────────────────────────────────────────────────
        for (ScoreEntry e : entries) {
            Color c = e.holedOut ? COLOR_HOLED : (e.isLocal ? COLOR_LOCAL : COLOR_REMOTE);
            String name = truncate(e.displayName, 11);
            if (e.holedOut) name += " *";

            addCell(name, c, 1.0f, 200f);
            addCell(e.holeStrokes  <= 0 ? "-" : String.valueOf(e.holeStrokes),  c, 1.0f, 80f);
            addCell(e.totalStrokes <= 0 ? "-" : String.valueOf(e.totalStrokes), c, 1.0f, 80f);
            row().padBottom(4f);
        }
    }

    private void addCell(String text, Color color, float scaleMultiplier, float width) {
        Label l = new Label(text, skin, "default");
        l.setFontScale(rowScale * scaleMultiplier);
        l.setColor(color);
        l.setAlignment(Align.center);
        add(l).width(width).center();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ">";
    }
}
