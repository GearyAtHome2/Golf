package com.gearygolf.golf.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.terrain.Terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the swing-view overlay (club head icon + gesture trail) and owns the
 * SwingGestureAnalyser that classifies the drag into swing phases.
 *
 * Step 2: club head icon + raw trail.
 * Step 3: phase state machine + on-screen label + debug log.
 * Step 4: impact detection (crossing ball X) → contact offset + path angle debug text.
 */
public class SwingOverlay {

    // Club head icon size in HUD world units (~1280-wide coordinate space).
    // Thin and tall: from the slightly tilted side-on angle this reads as a club face.
    // These are defaults (Tour Pro / no-difficulty); set per-difficulty via setClubSize().
    private float clubW = 11f;
    private float clubH = 34f;
    // How far to the RIGHT of the projected ball the club head sits.
    // "Right" in swing-view screen space = away from the target = where the backswing starts.
    private static final float CLUB_RIGHT_OFFSET = 46f;

    private static final float TRAIL_THICKNESS = 2.5f;

    // Phase label colours
    private static final Color COL_IDLE         = new Color(0.6f, 0.6f, 0.6f, 1f);
    private static final Color COL_BACKSWING    = new Color(1f,   0.8f, 0.2f, 1f);
    private static final Color COL_TRANSITION   = new Color(0.4f, 0.8f, 1f,   1f);
    private static final Color COL_FORWARD      = new Color(0.3f, 1f,   0.4f, 1f);
    private static final Color COL_COMPLETE     = new Color(1f,   1f,   1f,   1f);

    // Divot scar dimensions in HUD units
    private static final float DIVOT_LENGTH_PERFECT = 50f;
    private static final float DIVOT_LENGTH_FAT     = 56f;
    private static final float DIVOT_LENGTH_THIN    = 18f;
    private static final float DIVOT_HEIGHT         = 13f;
    // How far RIGHT of the ball (positive = before ball in swing direction) the divot start
    // point is offset for each tempo result.
    private static final float DIVOT_OFFSET_PERFECT =  0f;
    private static final float DIVOT_OFFSET_FAT     = 35f;  // starts before ball
    private static final float DIVOT_OFFSET_THIN    = -15f; // starts past ball

    private boolean active      = false;
    private boolean wasTouching = false;

    // Trail stored as raw screen pixels (x right from left, y down from top).
    private final List<Vector2> trail    = new ArrayList<>();
    private final Vector3       tempProj = new Vector3();
    private final GlyphLayout   layout   = new GlyphLayout();

    private final SwingGestureAnalyser analyser = new SwingGestureAnalyser();

    // ── Step 4: cached ball HUD coords (updated each render, used in next update) ──
    private float ballHudX     = -1f; // -1 = not yet known
    private float ballHudY     = 0f;
    private float scaleXCache  = 1f;
    private float scaleYCache  = 1f;
    private boolean impactDetected  = false;
    /** Index into trail[] of the point just AFTER the ball crossing — start of follow-through. */
    private int impactTrailIndex = -1;
    /** Set once per swing after follow-through is assessed; cleared by {@link #consumeResult}. */
    private SwingResult pendingResult = null;

    // Last-captured impact data for on-screen display.
    private float lastContactOffset = 0f;
    private float lastPathDeg       = 0f;

    // ── Terrain type (set from GolfGame before swing view is entered) ────────
    private Terrain.TerrainType terrainType = Terrain.TerrainType.FAIRWAY;
    /** False for driver and fairway woods — they sweep without digging. */
    private boolean divotEnabled = true;

    // ── Divot scar state ─────────────────────────────────────────────────────
    /** Right edge of the divot ellipse in HUD coords. Updated once per impact. */
    private float divotScarRightX = -9999f;
    private float divotScarLength = 0f;
    /** Remaining seconds of divot scar visibility (counts down). */
    private float divotFadeTimer  = 0f;
    private final Color divotColor = new Color();

    // Reusable Color for club head tinting during forward swing (avoids allocation per frame).
    private final Color clubHeadColor = new Color(Color.WHITE);

    // -------------------------------------------------------------------------

    public void setActive(boolean active) {
        if (!active && this.active) reset();
        this.active = active;
    }

    public boolean isActive() { return active; }

    public SwingGestureAnalyser getAnalyser() { return analyser; }

    /**
     * Returns the completed swing result and clears it, or null if no result is ready.
     * GolfGame should call this every frame; a non-null return means "fire the shot".
     */
    public SwingResult consumeResult() {
        SwingResult r = pendingResult;
        pendingResult = null;
        return r;
    }

    public void setTerrainType(Terrain.TerrainType type) {
        this.terrainType = (type != null) ? type : Terrain.TerrainType.FAIRWAY;
    }

    public void setDivotEnabled(boolean enabled) {
        this.divotEnabled = enabled;
    }

    /** Sets the rendered club head size. Larger values make contact more forgiving (offset is normalised by clubH). */
    public void setClubSize(float w, float h) {
        this.clubW = w;
        this.clubH = h;
    }

    // -------------------------------------------------------------------------

    /**
     * Call every frame while the playing loop is running.
     * Records touch / mouse-drag points and feeds them to the analyser.
     * Also detects the moment the drag trail crosses the ball's screen position
     * (impact) and extracts contact offset + path angle.
     */
    public void update() {
        if (!active) return;

        boolean pressing = Platform.isAndroid()
                ? Gdx.input.isTouched()
                : Gdx.input.isButtonPressed(Buttons.LEFT);

        if (pressing) {
            trail.add(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
            wasTouching = true;
            analyser.update(trail, true);

            // ── Impact detection ────────────────────────────────────────────
            // Only look for crossing once we're in FORWARD_SWING and the ball
            // position is known (ballHudX set by a previous render call).
            if (!impactDetected
                    && analyser.getPhase() == SwingGestureAnalyser.Phase.FORWARD_SWING
                    && ballHudX >= 0f
                    && trail.size() >= 2) {
                detectImpact();
                if (impactDetected && divotEnabled) buildDivotScar();
            }

        } else if (wasTouching) {
            analyser.update(trail, false); // signal release → COMPLETE
            if (impactDetected) {
                assessFollowThrough();     // sets followThrough fields on analyser
                pendingResult = analyser.buildResult();
            }
            trail.clear();
            analyser.reset();             // ready for next gesture attempt
            wasTouching      = false;
            impactDetected   = false;
            impactTrailIndex = -1;
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Renders the club head icon, gesture trail, current phase label, and
     * (after impact) a contact/path debug line.
     *
     * @param sr           HUD's ShapeRenderer
     * @param batch        HUD's SpriteBatch
     * @param font         HUD's BitmapFont (for the phase label)
     * @param hudViewport  The HUD's ExtendViewport
     * @param gameCamera   The 3D perspective camera
     * @param ballWorldPos Ball position in 3D world space
     */
    public void render(ShapeRenderer sr, SpriteBatch batch, BitmapFont font,
                       Viewport hudViewport, Camera gameCamera, Vector3 ballWorldPos) {
        if (!active) return;

        // ── Project ball → HUD viewport coordinates ──────────────────────────
        tempProj.set(ballWorldPos);
        gameCamera.project(tempProj);
        scaleXCache = hudViewport.getWorldWidth()  / Gdx.graphics.getWidth();
        scaleYCache = hudViewport.getWorldHeight() / Gdx.graphics.getHeight();
        ballHudX    = tempProj.x * scaleXCache;
        ballHudY    = tempProj.y * scaleYCache;

        // ── Club head icon ────────────────────────────────────────────────────
        float clubX = ballHudX + CLUB_RIGHT_OFFSET;
        float clubY = ballHudY;

        if (batch.isDrawing()) batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.setProjectionMatrix(hudViewport.getCamera().combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // ── Club head colour: white → green → red during forward swing ────────
        SwingGestureAnalyser.Phase phase = analyser.getPhase();
        if (phase == SwingGestureAnalyser.Phase.FORWARD_SWING && !analyser.isImpactCaptured()) {
            float t = analyser.getForwardSwingTimingT();
            if (t <= 1f) {
                // white (t=0) → bright green (t=1)
                clubHeadColor.set(1f - t, 1f, 1f - t, 1f);
            } else {
                // bright green (t=1) → red (t=2)
                float excess = Math.min(t - 1f, 1f);
                clubHeadColor.set(excess, 1f - excess, 0f, 1f);
            }
        } else {
            clubHeadColor.set(Color.WHITE);
        }
        sr.setColor(clubHeadColor);
        sr.rect(clubX - clubW * 0.5f, clubY - clubH * 0.5f, clubW, clubH);

        // ── Contact marker: coloured dot on club face at impact position ──────
        if (analyser.isImpactCaptured()) {
            float contactY = ballHudY + lastContactOffset * (clubH * 0.5f);
            Color markerCol = Math.abs(lastContactOffset) < 0.25f ? Color.GREEN
                            : Math.abs(lastContactOffset) < 0.6f  ? Color.YELLOW
                            : Color.RED;
            sr.setColor(markerCol);
            sr.circle(clubX, contactY, 4f, 12);
        }

        // ── Divot scar ellipse ────────────────────────────────────────────────
        if (divotFadeTimer > 0f) {
            divotFadeTimer -= Gdx.graphics.getDeltaTime();
            float alpha = MathUtils.clamp(divotFadeTimer, 0f, 1f) * 0.72f;
            sr.setColor(divotColor.r, divotColor.g, divotColor.b, alpha);
            // Right edge of bounding box = divotScarRightX; extends left (toward target).
            float ellipseX = divotScarRightX - divotScarLength;
            float ellipseY = ballHudY - DIVOT_HEIGHT * 0.5f;
            sr.ellipse(ellipseX, ellipseY, divotScarLength, DIVOT_HEIGHT);
        }

        sr.end();

        // ── Gesture trail ─────────────────────────────────────────────────────
        if (trail.size() > 1) {
            Gdx.gl.glLineWidth(TRAIL_THICKNESS);
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(Color.YELLOW);
            for (int i = 1; i < trail.size(); i++) {
                Vector2 a = trail.get(i - 1);
                Vector2 b = trail.get(i);
                // input y is from screen top; flip to match OpenGL bottom-origin
                float ax = a.x * scaleXCache;
                float ay = (Gdx.graphics.getHeight() - a.y) * scaleYCache;
                float bx = b.x * scaleXCache;
                float by = (Gdx.graphics.getHeight() - b.y) * scaleYCache;
                sr.line(ax, ay, bx, by);
            }
            sr.end();
            Gdx.gl.glLineWidth(1f);
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Phase label ───────────────────────────────────────────────────────
        String label = phaseLabel(phase);
        Color  col   = phaseColour(phase);

        float savedScale = font.getScaleX();
        font.getData().setScale(hudViewport.getWorldHeight() * 0.0026f);
        font.setColor(col);
        layout.setText(font, label);
        float lx = (hudViewport.getWorldWidth() - layout.width) * 0.5f;
        float ly = hudViewport.getWorldHeight() * 0.88f;

        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        font.draw(batch, label, lx, ly);

        // ── Contact / path / tempo debug line (shown after impact) ──────────
        if (analyser.isImpactCaptured()) {
            String contactLabel = lastContactOffset < -0.3f ? "heel"
                                : lastContactOffset >  0.3f ? "toe" : "centre";
            String pathLabel    = lastPathDeg >  2f ? "in-to-out"
                                : lastPathDeg < -2f ? "out-to-in" : "straight";
            SwingGestureAnalyser.TempoResult tempo = analyser.getTempoResult();
            String tempoLabel   = switch (tempo) {
                case PERFECT    -> "CLEAN";
                case EARLY_THIN -> "THIN";
                case LATE_FAT   -> "FAT";
                default         -> "";
            };
            String debugLine = String.format("Contact: %s (%.2f)  Path: %.1f deg %s  |  %s",
                    contactLabel, lastContactOffset, lastPathDeg, pathLabel, tempoLabel);
            font.getData().setScale(hudViewport.getWorldHeight() * 0.0017f);
            Color tempoColor = switch (tempo) {
                case PERFECT    -> Color.GREEN;
                case EARLY_THIN -> Color.YELLOW;
                case LATE_FAT   -> new Color(1f, 0.4f, 0.1f, 1f);
                default         -> Color.WHITE;
            };
            font.setColor(tempoColor);
            layout.setText(font, debugLine);
            font.draw(batch, debugLine,
                    (hudViewport.getWorldWidth() - layout.width) * 0.5f,
                    hudViewport.getWorldHeight() * 0.82f);
        }

        batch.end();

        font.getData().setScale(savedScale);
        font.setColor(Color.WHITE);
    }

    // -------------------------------------------------------------------------

    public void reset() {
        trail.clear();
        wasTouching       = false;
        impactDetected    = false;
        impactTrailIndex  = -1;
        lastContactOffset = 0f;
        lastPathDeg       = 0f;
        divotFadeTimer    = 0f;
        divotScarRightX   = -9999f;
        divotEnabled      = true;
        pendingResult     = null;
        analyser.reset();
    }

    // -------------------------------------------------------------------------

    /**
     * Called on touch release when impact was detected.
     * Scans trail points after the crossing index and measures how far above (+)
     * or below (-) the ball line the follow-through arc peaked.
     * Result is normalised: +1 = full flip, -1 = full low extension, 0 = neutral.
     */
    private void assessFollowThrough() {
        if (impactTrailIndex < 0 || ballHudY == 0f) {
            analyser.onFollowThrough(0f);
            return;
        }

        float maxAbove = 0f;  // highest point above ball line (flip direction)
        float maxBelow = 0f;  // furthest point below ball line (good extension)
        int   count    = 0;

        for (int i = impactTrailIndex + 1; i < trail.size(); i++) {
            // Convert trail screen coords → HUD coords (Y-flip matches render)
            float hudY = (Gdx.graphics.getHeight() - trail.get(i).y) * scaleYCache;
            float dy   = hudY - ballHudY;  // +ve = above ball line = flip tendency
            if (dy > maxAbove) maxAbove = dy;
            if (dy < maxBelow) maxBelow = dy;
            count++;
        }

        if (count == 0) {
            analyser.onFollowThrough(0f);
            return;
        }

        // Normalise: 60 HUD units above/below the ball line = ±1.0
        final float REFERENCE = 60f;
        float norm = maxAbove > Math.abs(maxBelow)
                   ? Math.min( maxAbove / REFERENCE,  1f)
                   : Math.max( maxBelow / REFERENCE, -1f);

        analyser.onFollowThrough(norm);
    }

    /**
     * Called once when impact is first detected.  Positions the divot scar ellipse
     * based on the tempo result and terrain type.
     */
    private void buildDivotScar() {
        SwingGestureAnalyser.TempoResult tempo = analyser.getTempoResult();
        switch (tempo) {
            case LATE_FAT -> {
                divotScarRightX = ballHudX + DIVOT_OFFSET_FAT;
                divotScarLength = DIVOT_LENGTH_FAT;
                divotFadeTimer  = 2.5f;
            }
            case EARLY_THIN -> {
                divotScarRightX = ballHudX + DIVOT_OFFSET_THIN;
                divotScarLength = DIVOT_LENGTH_THIN;
                divotFadeTimer  = 1.2f;
            }
            default -> {  // PERFECT or NONE
                divotScarRightX = ballHudX + DIVOT_OFFSET_PERFECT;
                divotScarLength = DIVOT_LENGTH_PERFECT;
                divotFadeTimer  = 2.5f;
            }
        }
        divotColor.set(getDivotColorForTerrain(terrainType));
        // Thin shots leave a very faint mark; reduce alpha via a lower initial timer cap.
        // (The render clamps alpha to the timer value, so starting at 1.2 means max alpha
        // is already 1.0 for most of the fade. No extra logic needed.)
    }

    private static Color getDivotColorForTerrain(Terrain.TerrainType type) {
        return switch (type) {
            case SAND  -> new Color(0.82f, 0.72f, 0.50f, 1f);  // tan/sand
            case STONE -> new Color(0.70f, 0.70f, 0.68f, 1f);  // grey scratch
            case MUD   -> new Color(0.22f, 0.13f, 0.07f, 1f);  // dark mud
            default    -> new Color(0.38f, 0.22f, 0.12f, 1f);  // earth/dirt
        };
    }

    /**
     * Scan the most-recent trail segment for a right-to-left crossing of ballHudX.
     * When found, interpolate the Y at crossing, derive heel/toe offset and path
     * angle, and pass them to the analyser.
     */
    private void detectImpact() {
        int size = trail.size();
        // Scan backwards from most-recent so we catch the very first crossing
        for (int i = size - 1; i >= 1; i--) {
            float ax = trail.get(i - 1).x * scaleXCache;
            float ay = (Gdx.graphics.getHeight() - trail.get(i - 1).y) * scaleYCache;
            float bx = trail.get(i).x     * scaleXCache;
            float by = (Gdx.graphics.getHeight() - trail.get(i).y)     * scaleYCache;

            // Right-to-left crossing: a is right of ball, b is left of (or at) ball
            if (ax >= ballHudX && bx <= ballHudX) {
                float t       = (ax == bx) ? 0.5f : (ballHudX - ax) / (bx - ax);
                float crossY  = ay + t * (by - ay);

                // Heel-toe: perpendicular distance normalised by half club height.
                // If the trail passes ABOVE the ball the club center is high, so
                // the ball contacts the LOWER (heel) part of the face → negative.
                // Negate so: trail above ball = heel (-), trail below ball = toe (+).
                float heelToe = (ballHudY - crossY) / (clubH * 0.5f);
                heelToe = MathUtils.clamp(heelToe, -1f, 1f);

                // Path: angle of drag vector from "straight left" (target direction).
                // deltaX is negative for a leftward stroke; negate so 0° = straight.
                // +ve = in-to-out (draw bias), -ve = out-to-in (fade bias).
                float deltaX       = bx - ax;
                float deltaY       = by - ay;
                float pathDeg      = (float)(Math.atan2(deltaY, -deltaX) * MathUtils.radiansToDegrees);
                // Speed in HUD units/frame at the moment of crossing — the primary power input.
                float impactSpeed  = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                lastContactOffset = heelToe;
                lastPathDeg       = pathDeg;
                analyser.onImpact(heelToe, pathDeg, impactSpeed);
                impactDetected   = true;
                impactTrailIndex = i;  // everything after this index is follow-through
                break;
            }
        }
    }

    // -------------------------------------------------------------------------

    private static String phaseLabel(SwingGestureAnalyser.Phase p) {
        return switch (p) {
            case IDLE          -> "ADDRESS";
            case BACKSWING     -> "BACKSWING";
            case TRANSITION    -> "TRANSITION";
            case FORWARD_SWING -> "FORWARD";
            case COMPLETE      -> "COMPLETE";
        };
    }

    private static Color phaseColour(SwingGestureAnalyser.Phase p) {
        return switch (p) {
            case IDLE          -> COL_IDLE;
            case BACKSWING     -> COL_BACKSWING;
            case TRANSITION    -> COL_TRANSITION;
            case FORWARD_SWING -> COL_FORWARD;
            case COMPLETE      -> COL_COMPLETE;
        };
    }
}
