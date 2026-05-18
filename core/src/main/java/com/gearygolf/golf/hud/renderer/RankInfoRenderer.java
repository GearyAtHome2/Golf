package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.hud.UIUtils;

/**
 * Renders the rank-info overlay panel shown when the player taps the rank badge.
 * Shows: "Your rank is X", a large badge, and an optional descriptive message.
 */
public class RankInfoRenderer {

    private static final float PANEL_W = 700f;
    private static final float PANEL_H = 600f;

    private float panelX, panelY;

    public void render(SpriteBatch batch, ShapeRenderer sr, BitmapFont font, Viewport vp, RankBadge badge) {
        float sw = vp.getWorldWidth();
        float sh = vp.getWorldHeight();
        panelX = (sw - PANEL_W) / 2f;
        panelY = (sh - PANEL_H) / 2f;

        float baseScale = PANEL_H * 0.00185f;

        // ── Panel background ──────────────────────────────────────────────────
        batch.begin();
        UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3)
              .draw(batch, panelX, panelY, PANEL_W, PANEL_H);
        batch.end();

        // ── Large badge circle ────────────────────────────────────────────────
        float badgeRadius = PANEL_H * 0.21f;
        float badgeCX = panelX + PANEL_W / 2f;
        float badgeCY = panelY + PANEL_H * 0.48f;

        sr.setProjectionMatrix(vp.getCamera().combined);
        badge.renderCircle(sr, batch, badgeCX, badgeCY, badgeRadius);

        // ── Text ─────────────────────────────────────────────────────────────
        GlyphLayout layout = new GlyphLayout();
        batch.begin();

        badge.renderText(batch, font, badgeCX, badgeCY, badgeRadius);

        // "Your rank is"
        font.getData().setScale(baseScale * 1.35f);
        font.setColor(new Color(0.75f, 0.75f, 0.75f, 1f));
        layout.setText(font, "Your rank is");
        font.draw(batch, "Your rank is",
                panelX + (PANEL_W - layout.width) / 2f,
                panelY + PANEL_H - 44f);

        // Rank display name
        String rankName = badge.getDisplayName();
        font.getData().setScale(baseScale * 1.85f);
        font.setColor(badge.getColor());
        layout.setText(font, rankName);
        if (layout.width > PANEL_W - 40f) {
            font.getData().setScale(font.getScaleX() * (PANEL_W - 40f) / layout.width);
            layout.setText(font, rankName);
        }
        font.draw(batch, rankName,
                panelX + (PANEL_W - layout.width) / 2f,
                panelY + PANEL_H - 96f);

        // Optional message — anchored 18px below badge, scaled to reach just above footer
        String msg = badge.getOverlayMessage();
        if (msg != null) {
            String[] msgLines = msg.split("\n");
            float slots = 0;
            for (String l : msgLines) slots += l.isEmpty() ? 0.5f : 1f;

            float startY = badgeCY - badgeRadius - 18f;
            float availH = startY - (panelY + 48f);

            font.getData().setScale(1f);
            float msgScale = (availH / slots) / (font.getLineHeight() * 1.3f);

            float maxLineW = PANEL_W - 48f;
            font.getData().setScale(msgScale);
            for (String l : msgLines) {
                if (l.isEmpty()) continue;
                layout.setText(font, l);
                if (layout.width > maxLineW) {
                    msgScale *= maxLineW / layout.width;
                    font.getData().setScale(msgScale);
                }
            }

            float lineH = font.getLineHeight() * 1.3f;
            float y = startY;
            font.setColor(Color.LIGHT_GRAY);
            for (String l : msgLines) {
                if (l.isEmpty()) { y -= lineH * 0.5f; continue; }
                layout.setText(font, l);
                font.draw(batch, l, panelX + (PANEL_W - layout.width) / 2f, y);
                y -= lineH;
            }
        }

        // Footer
        font.getData().setScale(baseScale * 0.78f);
        font.setColor(Color.GRAY);
        String hint = "ESC or tap outside to close";
        layout.setText(font, hint);
        if (layout.width > PANEL_W - 20f) {
            font.getData().setScale(font.getScaleX() * (PANEL_W - 20f) / layout.width);
            layout.setText(font, hint);
        }
        font.draw(batch, hint, panelX + (PANEL_W - layout.width) / 2f, panelY + 30f);

        font.getData().setScale(1f);
        batch.end();
    }

    public boolean isClickInside(float x, float y) {
        return x >= panelX && x <= panelX + PANEL_W && y >= panelY && y <= panelY + PANEL_H;
    }
}
