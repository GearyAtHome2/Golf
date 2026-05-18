package com.gearygolf.golf.tutorial;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.Platform;

public class TutorialOverlayRenderer {

    private static final float DIM_ALPHA = 0.60f;
    private static final Color BOX_FILL  = new Color(0.04f, 0.04f, 0.16f, 0.93f);
    private static final Color HIGHLIGHT  = new Color(1f, 0.85f, 0.1f, 0.55f);
    private static final float SPOTLIGHT_PAD = 6f; // px extra space around highlighted button

    private final GlyphLayout layout = new GlyphLayout();

    /**
     * @param btnBounds  Stage-coord bounds of the button to spotlight, or null if no spotlight.
     * @param wind       Current level wind vector (used for STEP_2_AIM hint text), may be null.
     */
    public void render(SpriteBatch batch, ShapeRenderer sr, BitmapFont font,
                       Viewport viewport, TutorialController.Step step,
                       Rectangle btnBounds, Vector3 wind) {
        if (step == null || !step.isOverlayVisible()) return;

        boolean mobile  = Platform.isAndroid();
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(viewport.getCamera().combined);

        if (mobile && step.isSpotlightStyle() && btnBounds != null) {
            Rectangle mgBounds = (step == TutorialController.Step.STEP_6_HIT)
                    ? minigameBarBounds(w, h) : null;
            drawSpotlight(sr, w, h, btnBounds, mgBounds);
        } else if (mobile && step == TutorialController.Step.STEP_10_AIM && btnBounds != null) {
            // No screen dim — just a gold border around the HIT button so the player can see and aim
            drawHighlightBorder(sr, btnBounds);
        } else if (mobile) {
            // Hint-bar style: light dim only, no spotlight (player needs to see the course)
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(0f, 0f, 0f, 0.35f);
            sr.rect(0, 0, w, h);
            sr.end();
        }
        // Desktop: no dim at all — just show the text hint bar


        String mainText  = mainText(step, wind);
        String hintText  = mobile ? null : desktopHint(step);
        drawTextBox(batch, sr, font, viewport, w, h, step, mainText, hintText, btnBounds);

        if (step == TutorialController.Step.STEP_6_HIT) {
            drawMinigameHighlight(sr, w, h);
        }
    }

    // -----------------------------------------------------------------  Spotlight: 4 dim rects leaving the button area bright, then a glowing border

    /**
     * Dims everything except {@code btn} (and optionally {@code second}).
     * When {@code second} is non-null both areas are left bright using 7 rects
     * instead of the usual 4 (assumes btn is below second with no vertical overlap).
     */
    private void drawSpotlight(ShapeRenderer sr, float w, float h, Rectangle btn, Rectangle second) {
        float bx = btn.x - SPOTLIGHT_PAD;
        float by = btn.y - SPOTLIGHT_PAD;
        float bw = btn.width  + SPOTLIGHT_PAD * 2;
        float bh = btn.height + SPOTLIGHT_PAD * 2;

        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, DIM_ALPHA);
        if (second == null) {
            sr.rect(0,       by + bh, w,  h - by - bh);
            sr.rect(0,       0,       w,  by);
            sr.rect(0,       by,      bx, bh);
            if (bx + bw < w) sr.rect(bx + bw, by, w - bx - bw, bh);
        } else {
            float sx = second.x;
            float sy = second.y;
            float sw = second.width;
            float sh = second.height;

            // Both elements may be side-by-side or vertically offset; use a combined
            // bounding box to avoid drawing overlapping dim rects in any gap between them.
            float combX    = Math.min(bx, sx);
            float combY    = Math.min(by, sy);
            float combMaxX = Math.max(bx + bw, sx + sw);
            float combMaxY = Math.max(by + bh, sy + sh);
            float combW    = combMaxX - combX;
            float combH    = combMaxY - combY;

            if (h - combMaxY > 0) sr.rect(0,        combMaxY, w,        h - combMaxY); // above
            if (combY > 0)        sr.rect(0,        0,        w,        combY);         // below
            if (combX > 0)        sr.rect(0,        combY,    combX,    combH);         // left
            if (combMaxX < w)     sr.rect(combMaxX, combY,    w - combMaxX, combH);    // right
        }
        sr.end();

        drawHighlightBorder(sr, btn);
    }

    private void drawHighlightBorder(ShapeRenderer sr, Rectangle btn) {
        float bx = btn.x - SPOTLIGHT_PAD;
        float by = btn.y - SPOTLIGHT_PAD;
        float bw = btn.width  + SPOTLIGHT_PAD * 2;
        float bh = btn.height + SPOTLIGHT_PAD * 2;
        float t  = 4f;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(HIGHLIGHT);
        sr.rect(bx - t,  by - t,  bw + t * 2, t);   // bottom
        sr.rect(bx - t,  by + bh, bw + t * 2, t);   // top
        sr.rect(bx - t,  by,      t,           bh);  // left
        sr.rect(bx + bw, by,      t,           bh);  // right
        sr.end();
    }

    // ----------------------------------------------------------------- Minigame bar highlight

    /** Returns the bounds of the minigame bar area (matching MinigameUI layout). */
    private Rectangle minigameBarBounds(float w, float h) {
        float barW = w * 0.6f;
        float pad  = 10f;
        float rx   = (w - barW) / 2f - pad;
        float ry   = h - 150f - 15f - pad;   // bar y minus needle overhang minus padding
        float rw   = barW + pad * 2f;
        float rh   = 30f + 30f + pad * 2f;   // bar height + needle overhang top+bottom
        return new Rectangle(rx, ry, rw, rh);
    }

    /** Draws a gold border around the shot-power minigame bar (same layout as MinigameUI). */
    private void drawMinigameHighlight(ShapeRenderer sr, float w, float h) {
        Rectangle r = minigameBarBounds(w, h);
        float rx = r.x, ry = r.y, rw = r.width, rh = r.height;
        float t  = 4f;

        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(HIGHLIGHT);
        sr.rect(rx - t,      ry - t,      rw + t * 2f, t);   // bottom edge
        sr.rect(rx - t,      ry + rh,     rw + t * 2f, t);   // top edge
        sr.rect(rx - t,      ry,          t,            rh);  // left edge
        sr.rect(rx + rw,     ry,          t,            rh);  // right edge
        sr.end();
    }

    // ----------------------------------------------------------------- Text box

    private void drawTextBox(SpriteBatch batch, ShapeRenderer sr, BitmapFont font,
                              Viewport viewport, float w, float h,
                              TutorialController.Step step,
                              String mainText, String hintText, Rectangle btnBounds) {
        boolean mobile = Platform.isAndroid();
        float textScale = h * 0.0013f;
        float hintScale = textScale * 0.72f;

        // Position the box away from the highlighted button
        float boxH = mobile ? h * 0.17f : h * 0.12f;
        if (hintText != null) boxH += h * 0.05f;
        float boxW = w * (mobile ? 0.72f : 0.55f);
        float boxX = (w - boxW) / 2f;
        float boxY = chooseBoxY(h, step, btnBounds, boxH);

        // Draw box background
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(BOX_FILL);
        sr.rect(boxX, boxY, boxW, boxH);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GOLD);
        sr.rect(boxX, boxY, boxW, boxH);
        sr.end();

        // Draw text — use wrap+centre so long strings break gracefully inside the box
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float textAreaW = boxW * 0.88f;
        float textAreaX = boxX + (boxW - textAreaW) / 2f;

        font.getData().setScale(textScale);
        layout.setText(font, mainText, Color.WHITE, textAreaW, Align.center, true);
        float ty = boxY + boxH - (boxH - layout.height * (hintText != null ? 1.6f : 1f)) / 2f;

        font.setColor(0f, 0f, 0f, 0.7f);
        font.draw(batch, mainText, textAreaX + 1, ty - 1, textAreaW, Align.center, true);
        font.setColor(Color.WHITE);
        font.draw(batch, mainText, textAreaX, ty, textAreaW, Align.center, true);

        if (hintText != null) {
            font.getData().setScale(hintScale);
            layout.setText(font, hintText, Color.WHITE, textAreaW, Align.center, true);
            float hy = ty - layout.height * 2.0f;
            font.setColor(0f, 0f, 0f, 0.6f);
            font.draw(batch, hintText, textAreaX + 1, hy - 1, textAreaW, Align.center, true);
            font.setColor(new Color(1f, 0.9f, 0.5f, 1f));
            font.draw(batch, hintText, textAreaX, hy, textAreaW, Align.center, true);
        }

        batch.end();
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    /** Place the text box above or below the spotlight button, or centred for non-spotlight steps. */
    private float chooseBoxY(float h, TutorialController.Step step, Rectangle btnBounds, float boxH) {
        if (!step.isSpotlightStyle() || btnBounds == null) {
            return h * 0.05f; // bottom strip for hint-only / desktop
        }
        float btnCentreY = btnBounds.y + btnBounds.height / 2f;
        float aboveY = btnBounds.y + btnBounds.height + SPOTLIGHT_PAD + 10f;
        float belowY = btnBounds.y - boxH - SPOTLIGHT_PAD - 10f;

        if (btnCentreY < h / 2f) {
            // Button in lower half — show box above
            return Math.min(aboveY, h - boxH - 10f);
        } else {
            // Button in upper half — show box below
            return Math.max(belowY, 10f);
        }
    }

    // ----------------------------------------------------------------- Text content

    private String mainText(TutorialController.Step step, Vector3 wind) {
        return switch (step) {
            case STEP_1_DISTANCE -> "Check the distance to the hole";
            case STEP_2_AIM      -> windAimText(wind);
            case STEP_3_INFO     -> "Check your club distances";
            case STEP_4_CLUB     -> "Switch to the 9 Iron";
            case STEP_5_POWER    -> "Take a full swing";
            case STEP_6_HIT      -> "Hit the ball at the right time!";
            case STEP_8_PUTTER -> "Press >> to select the putter";
            case STEP_9_PROJECT -> "View shot projection";
            case STEP_10_AIM      -> "Aim at the flag, hold HIT to pick shot power";
            case STEP_12_COMPLETE_1 -> "At higher difficulties, you'll lose utilities such as distance and shot projection.";
            case STEP_13_COMPLETE_2 -> "Some courses and holes are a lot harder than others. Do your daily challenges, and good luck!";
            default              -> "";
        };
    }

    private String desktopHint(TutorialController.Step step) {
        return switch (step) {
            case STEP_1_DISTANCE -> "Press F";
            case STEP_2_AIM      -> "Drag to rotate the camera";
            case STEP_3_INFO     -> "Press I";
            case STEP_4_CLUB     -> "Scroll to select 69 Iron";
            case STEP_5_POWER    -> "Hold Ctrl + Space";
            case STEP_6_HIT      -> "Press Space to stop the needle";
            case STEP_8_PUTTER -> "Scroll to Putter, then aim and press Space";
            case STEP_9_PROJECT -> "Press R to view shot projection";
            case STEP_10_AIM      -> "Aim at the flag, hold SPACE to pick shot power";
            case STEP_12_COMPLETE_1, STEP_13_COMPLETE_2 -> "Press N to continue";
            default              -> null;
        };
    }

    private String windAimText(Vector3 wind) {
        if (wind == null || wind.len() < 0.3f) {
            return "Wind is calm — aim directly at the hole";
        }
        float ax = Math.abs(wind.x);
        float az = Math.abs(wind.z);
        if (ax >= az) {
            return wind.x > 0
                ? "Wind blowing left — aim right of the hole"
                : "Wind blowing right — aim left of the hole";
        } else {
            return wind.z > 0
                ? "Tailwind — aim straight, you have extra distance"
                : "Headwind — aim straight, take a little extra power";
        }
    }
}
