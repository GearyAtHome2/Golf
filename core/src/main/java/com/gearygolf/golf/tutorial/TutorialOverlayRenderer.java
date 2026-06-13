package com.gearygolf.golf.tutorial;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.hud.UIUtils;

public class TutorialOverlayRenderer {

    private static final float DIM_ALPHA = 0.60f;
    private static final Color BOX_FILL  = new Color(0.04f, 0.04f, 0.16f, 0.93f);

    // Lazily created to match the standard game button theme
    private Drawable nextBtnDrawable;
    // Bounds of the NEXT button in viewport world-space, updated every frame it is drawn
    private final Rectangle lastNextButtonBounds = new Rectangle();

    public Rectangle getLastNextButtonBounds() { return lastNextButtonBounds; }
    private static final Color HIGHLIGHT  = new Color(1f, 0.85f, 0.1f, 0.55f);
    private static final float SPOTLIGHT_PAD = 6f; // px extra space around highlighted button

    private final GlyphLayout layout = new GlyphLayout();

    /**
     * @param btnBounds      Stage-coord bounds of the button to spotlight, or null if no spotlight.
     * @param wind           Current level wind vector (used for STEP_2_AIM hint text), may be null.
     * @param aimYawDelta    Current camera yaw minus the yaw recorded at STEP_2_AIM start, in degrees.
     *                       Pass Float.NaN when not at STEP_2_AIM.
     * @param extraHighlight Additional screen-space rectangle to outline with a gold border (e.g. the
     *                       RANGE distance text during STEP_2_AIM), or null if not needed.
     */
    public void render(SpriteBatch batch, ShapeRenderer sr, BitmapFont font,
                       Viewport viewport, TutorialController.Step step,
                       Rectangle btnBounds, Vector3 wind, float aimYawDelta,
                       Rectangle extraHighlight) {
        if (step == null || !step.isOverlayVisible()) return;

        boolean mobile  = Platform.isAndroid();
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(viewport.getCamera().combined);

        if (step.isDimHandledExternally() || step.isNoDimHint()) {
            // No dim: big spin overlay handles it, or hint-only step that shouldn't obscure the course
        } else if (mobile && step.isSpotlightStyle() && btnBounds != null) {
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

        if (step == TutorialController.Step.STEP_2_AIM && !Float.isNaN(aimYawDelta)) {
            drawAimBar(sr, w, h, aimYawDelta, wind);
        }

        if (extraHighlight != null) {
            drawHighlightBorder(sr, extraHighlight);
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

    // ----------------------------------------------------------------- Aim direction bar (STEP_2_AIM)

    private static final float BAR_RANGE_DEG  = 25f;  // ±30° shown across the full bar width
    private static final float AIM_THRESHOLD  = 4.5f; // degrees before the zone begins
    private static final float AIM_ZONE_MAX   = 6f;  // degrees at the far edge of the target zone

    /**
     * Draws a horizontal aim indicator above the text box.
     * The bar spans ±BAR_RANGE_DEG. A gold zone marks where the player should drag;
     * it turns green once the needle enters it.
     */
    private void drawAimBar(ShapeRenderer sr, float w, float h, float aimYawDelta, Vector3 wind) {
        // Determine target direction from wind: +1 = aim right, -1 = aim left, 0 = straight/calm
        int targetDir = 0;
        if (wind != null && wind.len() >= 0.3f) {
            float ax = Math.abs(wind.x), az = Math.abs(wind.z);
            if (ax >= az) targetDir = wind.x > 0 ? 1 : -1;
            // az > ax → headwind/tailwind → aim straight, targetDir stays 0
        }

        float barW = w * 0.65f;
        float barH = h * 0.022f;
        float barX = (w - barW) / 2f;
        // Position above the hint text box (which sits at h*0.05 and is h*0.17 tall on mobile)
        float boxH  = Platform.isAndroid() ? h * 0.17f : h * 0.12f;
        float barY  = h * 0.05f + boxH + h * 0.018f;

        float midX       = barX + barW / 2f;
        float pxPerDeg   = barW / (2f * BAR_RANGE_DEG);

        // Zone bounds in degrees
        float zoneLeft, zoneRight;
        if (targetDir > 0) {
            zoneLeft  = AIM_THRESHOLD;
            zoneRight = AIM_ZONE_MAX;
        } else if (targetDir < 0) {
            zoneLeft  = -AIM_ZONE_MAX;
            zoneRight = -AIM_THRESHOLD;
        } else {
            // Straight: show a symmetric zone — any small rotation in either direction is fine
            zoneLeft  = -AIM_THRESHOLD - 1f;
            zoneRight =  AIM_THRESHOLD + 1f;
        }

        // Is the needle currently inside the target zone?
        boolean inZone = targetDir > 0  ? (aimYawDelta >= AIM_THRESHOLD && aimYawDelta <= AIM_ZONE_MAX)
                       : targetDir < 0  ? (aimYawDelta <= -AIM_THRESHOLD && aimYawDelta >= -AIM_ZONE_MAX)
                       : Math.abs(aimYawDelta) >= AIM_THRESHOLD && Math.abs(aimYawDelta) <= AIM_ZONE_MAX;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Background
        sr.setColor(0.1f, 0.1f, 0.1f, 0.88f);
        sr.rect(barX, barY, barW, barH);

        // Target zone
        float zx = midX + zoneLeft  * pxPerDeg;
        float zw = (zoneRight - zoneLeft) * pxPerDeg;
        float clampedZx = Math.max(barX, zx);
        float clampedZw = Math.min(zw - (clampedZx - zx), barX + barW - clampedZx);
        if (clampedZw > 0) {
            if (inZone) sr.setColor(0.15f, 0.80f, 0.25f, 0.75f); // green — reached
            else        sr.setColor(1.00f, 0.82f, 0.08f, 0.60f); // gold  — not yet
            sr.rect(clampedZx, barY, clampedZw, barH);
        }

        // Centre tick
        sr.setColor(0.7f, 0.7f, 0.7f, 1f);
        sr.rect(midX - 1f, barY - barH * 0.3f, 2f, barH * 1.6f);

        // Needle
        float needleX = MathUtils.clamp(midX + aimYawDelta * pxPerDeg, barX + 2f, barX + barW - 2f);
        float needleH = barH * 2.2f;
        sr.setColor(Color.WHITE);
        sr.rect(needleX - 1.5f, barY - barH * 0.15f, 3f, needleH);
        sr.circle(needleX, barY + needleH * 0.9f, barH * 0.6f, 10);

        sr.end();

        // Gold border around the whole bar
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(HIGHLIGHT);
        sr.rect(barX, barY, barW, barH);
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
        boolean showNextBtn = step.isNextButtonStep();

        // NEXT button dimensions
        float nextBtnH = h * 0.052f;
        float nextBtnW = h * 0.22f;
        float nextBtnMargin = h * 0.014f; // gap between text and button, and button to box edge

        // Position the box away from the highlighted button
        float boxH = mobile ? h * 0.17f : h * 0.12f;
        if (hintText != null) boxH += h * 0.05f;
        if (showNextBtn) boxH += nextBtnH + nextBtnMargin * 2f;
        float boxW = w * (mobile ? 0.72f : 0.55f);
        float boxX = (w - boxW) / 2f;
        float boxY = chooseBoxY(h, step, btnBounds, boxH);

        // NEXT button centered at the bottom of the box
        float nextBtnX = boxX + (boxW - nextBtnW) / 2f;
        float nextBtnY = boxY + nextBtnMargin;

        // Draw box background
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(BOX_FILL);
        sr.rect(boxX, boxY, boxW, boxH);
        sr.end();

        // Draw box border
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GOLD);
        sr.rect(boxX, boxY, boxW, boxH);
        sr.end();

        // Draw text and NEXT button (batch pass)
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float textAreaW = boxW * 0.88f;
        float textAreaX = boxX + (boxW - textAreaW) / 2f;
        // Shift text up to leave room for the button strip
        float textOffsetY = showNextBtn ? nextBtnH + nextBtnMargin * 2f : 0f;

        font.getData().setScale(textScale);
        layout.setText(font, mainText, Color.WHITE, textAreaW, Align.center, true);
        float ty = boxY + boxH - (boxH - textOffsetY - layout.height * (hintText != null ? 1.6f : 1f)) / 2f;

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

        if (showNextBtn) {
            lastNextButtonBounds.set(nextBtnX, nextBtnY, nextBtnW, nextBtnH);
            if (nextBtnDrawable == null) {
                nextBtnDrawable = UIUtils.createRoundedRectDrawable(new Color(0.2f, 0.2f, 0.2f, 0.5f), 12);
            }
            nextBtnDrawable.draw(batch, nextBtnX, nextBtnY, nextBtnW, nextBtnH);
            font.getData().setScale(textScale * 0.85f);
            font.setColor(Color.WHITE);
            font.draw(batch, "NEXT  >", nextBtnX, nextBtnY + nextBtnH * 0.72f, nextBtnW, Align.center, false);
        }

        batch.end();
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    /** Place the text box above or below the spotlight button, or centred for non-spotlight steps. */
    private float chooseBoxY(float h, TutorialController.Step step, Rectangle btnBounds, float boxH) {
        if (step.isDimHandledExternally() || step == TutorialController.Step.STEP_4_CLUB) {
            return h - boxH - h * 0.04f; // top of screen — avoids covering the club name display
        }
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
            case STEP_4_CLUB     -> "Distance: 180 - press > a few times to switch to the 9 Iron";
            case STEP_5_POWER    -> "Take a full swing";
            case STEP_6_HIT      -> "Stop the needle in the sweet spot!";
            case STEP_8_PUTTER   -> "Press >> to select the putter";
            case STEP_9_PROJECT  -> "View shot projection";
            case STEP_10_AIM     -> "Aim at the flag, hold HIT to pick about 40% shot power";
            case STEP_12_COMPLETE_1 -> "At higher difficulties, you'll lose utilities such as distance and shot projection.";
            case STEP_13_COMPLETE_2 -> "Some courses and holes are a lot harder than others.";
            // Level 2
            case STEP_L2_1_SPINDICATOR -> "This is the spin indicator - it controls where you strike the ball. Tap it to see the full view.";
            case STEP_L2_1B_AIM        -> "Move your aim point to the lower-left of the ball. This adds loft for extra carry and draws the ball to hold your line against the wind.";
            case STEP_L2_2_HIT         -> "Take your shot";
            case STEP_L2_4_APPROACH    -> "Check your distance and pick the right club to reach the green with the wind.";
            case STEP_L2_6_COMPLETE_1  -> "Hitting upwards on a driver from under the ball is key to massive distance";
            case STEP_L2_7_COMPLETE_2  -> "On longer holes, pick your approach club carefully. Getting close to the flag makes putting much easier .";
            // Level 3
            case STEP_L3_1_SPINDICATOR -> "There's a strong headwind - hit the ball from the top to keep your flight path low.";
            case STEP_L3_3_LIE         -> "Observe the shot projection to see how the slope affects your ball's trajectory after landing.";
            case STEP_L3_4_TIP         -> "Consider using a longer iron or wood - it's more forgiving in the fairway. Make sure to hit low under the wind for maximum distance.";
            case STEP_L3_4B_WIND_TIP   -> "When hitting into the wind, pick a club that should go further than the green and let the wind slow it.";
            case STEP_L3_6_CONGRATS    -> "Congrats! You've got all the basics down.";
            case STEP_L3_7_DAILY_PROMPT -> "Try today's Daily Par-3 challenge to compete against other players on the leaderboard!";
            default              -> "";
        };
    }

    private String desktopHint(TutorialController.Step step) {
        return switch (step) {
            case STEP_1_DISTANCE -> "Press F";
            case STEP_2_AIM      -> "Drag to rotate the camera";
            case STEP_3_INFO     -> "Press I";
            case STEP_4_CLUB     -> "Scroll to select 9 Iron";
            case STEP_5_POWER    -> "Hold Ctrl + Space";
            case STEP_6_HIT      -> "Press Space to stop the needle";
            case STEP_8_PUTTER   -> "Scroll to Putter, then aim and press Space";
            case STEP_9_PROJECT  -> "Press P to view shot projection";
            case STEP_10_AIM     -> "Aim at the flag, hold SPACE to pick shot power";
            case STEP_12_COMPLETE_1, STEP_13_COMPLETE_2 -> "Press N to continue";
            // Level 2
            case STEP_L2_1_SPINDICATOR -> "Click the spin indicator to open it";
            case STEP_L2_1B_AIM        -> "Use arrow keys to move the aim dot lower-left";
            case STEP_L2_2_HIT         -> "Press Space to stop the needle";
            case STEP_L2_4_APPROACH    -> "Press F to check distance, scroll to select club";
            case STEP_L2_6_COMPLETE_1, STEP_L2_7_COMPLETE_2 -> "Press N to continue";
            case STEP_L3_3_LIE, STEP_L3_4_TIP, STEP_L3_4B_WIND_TIP -> "Press N to continue";
            case STEP_L3_6_CONGRATS, STEP_L3_7_DAILY_PROMPT -> "Press N to continue";
            default              -> null;
        };
    }

    private String windAimText(Vector3 wind) {
        if (wind == null || wind.len() < 0.3f) {
            return "Wind is calm - aim directly at the hole";
        }
        float ax = Math.abs(wind.x);
        float az = Math.abs(wind.z);
        if (ax >= az) {
            return wind.x > 0
                ? "Wind blowing left -> drag to aim right of the hole"
                : "Wind blowing right -> drag to aim left of the hole";
        } else {
            return wind.z > 0
                ? "Tailwind - aim straight, you have extra distance"
                : "Headwind - aim straight, take a little extra power";
        }
    }
}
