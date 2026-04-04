package org.example.scoreBoard;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import org.example.hud.UIUtils;

public class ScorecardPopup {

    private static final Color COLOR_UNDER_PAR = new Color(0.25f, 0.85f, 0.35f, 1f);
    private static final Color COLOR_OVER_PAR  = new Color(0.90f, 0.25f, 0.25f, 1f);
    private static final Color COLOR_EVEN_PAR  = Color.WHITE;
    private static final Color COLOR_HEADER    = new Color(0.65f, 0.65f, 0.65f, 1f);

    private final Table dimLayer;
    private final Table card;

    public ScorecardPopup(Stage stage, Skin skin, HighscoreService.HighscoreEntry entry) {
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;

        // --- Dim layer: full-screen, tapping outside the card dismisses ---
        dimLayer = new Table();
        dimLayer.setFillParent(true);
        dimLayer.setTouchable(Touchable.enabled);
        dimLayer.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { dismiss(stage); }
        });

        // --- Card ---
        card = new Table(skin);
        card.setTouchable(Touchable.enabled);
        // Empty ClickListener ensures the card area swallows touches so they don't reach dimLayer
        card.addListener(new ClickListener() {});
        card.setBackground(UIUtils.createGoldBorderedPanel(new Color(0.08f, 0.08f, 0.08f, 1f), 4));

        float stageW = stage.getWidth();
        float stageH = stage.getHeight();
        float pad    = stageH * 0.025f;
        float scale  = stageH * 0.00105f; // label font scale relative to stage height
        card.pad(pad);

        // --- Header row: name + score, and X button on desktop ---
        int totalToPar = 0;
        for (int i = 0; i < entry.pars.length; i++) totalToPar += entry.scores[i] - entry.pars[i];
        String parStr  = totalToPar == 0 ? "Even" : (totalToPar > 0 ? "+" + totalToPar : String.valueOf(totalToPar));
        Color parColor = totalToPar < 0 ? COLOR_UNDER_PAR : (totalToPar > 0 ? COLOR_OVER_PAR : COLOR_EVEN_PAR);

        Label nameLabel  = makeLabel(skin, entry.name.toUpperCase(), scale * 1.35f, Color.GOLD);
        Label scoreLabel = makeLabel(skin, entry.score + " strokes  (" + parStr + ")", scale * 1.05f, parColor);

        float cardW = Math.min(stageW * 0.72f, 740f);

        if (!isAndroid) {
            TextButton closeBtn = new TextButton("X", skin, "default");
            closeBtn.getLabel().setFontScale(scale * 1.1f);
            closeBtn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) { dismiss(stage); }
            });
            Table hdr = new Table();
            hdr.add(nameLabel).expandX().left();
            hdr.add(scoreLabel).center().padLeft(pad);
            hdr.add(closeBtn).right().size(stageH * 0.058f).padLeft(pad);
            card.add(hdr).width(cardW).row();
        } else {
            card.add(nameLabel).left().row();
            card.add(scoreLabel).left().padBottom(pad * 0.5f).row();
        }

        // --- Divider under header ---
        addDivider(skin, card, cardW);

        // --- Scorecard table ---
        card.add(buildScorecard(skin, entry, cardW, scale, pad * 0.4f))
            .padTop(pad * 0.5f).row();

        // --- Position card centred on stage ---
        card.pack();
        card.setPosition(
            Math.round((stageW - card.getWidth()) / 2f),
            Math.round((stageH - card.getHeight()) / 2f)
        );

        stage.addActor(dimLayer);
        stage.addActor(card);   // added after → wins hit-test over dimLayer for its area
    }

    // -------------------------------------------------------------------------

    private Table buildScorecard(Skin skin, HighscoreService.HighscoreEntry entry, float cardW, float scale, float cellPad) {
        int holes = entry.pars.length;
        Table t = new Table();
        if (holes == 18) {
            float colW = cardW * 0.46f;
            Table left  = buildColumn(skin, entry, 0, 9,  colW, scale, cellPad);
            Table right = buildColumn(skin, entry, 9, 18, colW, scale, cellPad);
            t.add(left).top().padRight(cardW * 0.08f);
            t.add(right).top();
        } else {
            // 9-hole: single centred column
            t.add(buildColumn(skin, entry, 0, holes, cardW * 0.65f, scale, cellPad));
        }
        return t;
    }

    private Table buildColumn(Skin skin, HighscoreService.HighscoreEntry entry, int from, int to, float colW, float scale, float pad) {
        float hW = colW * 0.22f; // hole number
        float pW = colW * 0.22f; // par
        float sW = colW * 0.22f; // score
        float tW = colW * 0.26f; // running total

        Table col = new Table();
        float hdrScale = scale * 0.82f;
        float rowScale = scale * 0.90f;

        col.add(makeLabel(skin, "HOLE",  hdrScale, COLOR_HEADER)).width(hW).center().padBottom(pad);
        col.add(makeLabel(skin, "PAR",   hdrScale, COLOR_HEADER)).width(pW).center().padBottom(pad);
        col.add(makeLabel(skin, "SCORE", hdrScale, COLOR_HEADER)).width(sW).center().padBottom(pad);
        col.add(makeLabel(skin, "TOTAL", hdrScale, COLOR_HEADER)).width(tW).center().padBottom(pad);
        col.row();

        // Running total starts from the sum of all holes before this column
        int running = 0;
        for (int i = 0; i < from; i++) running += entry.scores[i];

        for (int i = from; i < to; i++) {
            int par   = entry.pars[i];
            int score = entry.scores[i];
            int diff  = score - par;
            running  += score;
            Color scoreColor = diff < 0 ? COLOR_UNDER_PAR : (diff > 0 ? COLOR_OVER_PAR : COLOR_EVEN_PAR);

            col.add(makeLabel(skin, String.format("%02d", i + 1), rowScale, Color.WHITE)).width(hW).center().pad(pad);
            col.add(makeLabel(skin, String.valueOf(par),           rowScale, Color.WHITE)).width(pW).center().pad(pad);
            col.add(makeLabel(skin, String.valueOf(score),         rowScale, scoreColor )).width(sW).center().pad(pad);
            col.add(makeLabel(skin, String.valueOf(running),       rowScale, Color.WHITE)).width(tW).center().pad(pad);
            col.row();
        }
        return col;
    }

    private static void addDivider(Skin skin, Table parent, float width) {
        if (skin.has("white", Drawable.class)) {
            parent.add(new Image(skin.newDrawable("white", Color.DARK_GRAY)))
                .width(width).height(1f).padTop(4).padBottom(4).row();
        }
    }

    private static Label makeLabel(Skin skin, String text, float fontScale, Color color) {
        Label lbl = new Label(text, skin, "default");
        lbl.setFontScale(fontScale);
        lbl.setColor(color);
        lbl.setAlignment(Align.center);
        return lbl;
    }

    public void dismiss(Stage stage) {
        dimLayer.remove();
        card.remove();
    }
}
