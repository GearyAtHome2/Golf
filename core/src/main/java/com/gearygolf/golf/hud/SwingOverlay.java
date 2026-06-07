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

    /** Distinguishes a ball-placement drag from a real swing drag. */
    private enum TouchMode { NONE, BALL_PLACEMENT, SWINGING }

    // Club head icon size in HUD world units (~1280-wide coordinate space).
    // Thin and tall: from the slightly tilted side-on angle this reads as a club face.
    // These are defaults (Tour Pro / no-difficulty); set per-difficulty via setClubSize().
    private float clubW = 11f;
    private float clubH = 34f;
    // How far to the RIGHT of the projected ball the club head sits.
    // "Right" in swing-view screen space = away from the target = where the backswing starts.
    private static final float CLUB_RIGHT_OFFSET = 46f;

    // ── Attack angle / ball placement constants ──────────────────────────────
    /** Conversion from camera world-space offset (metres) to degrees of attack angle change. */
    private static final float CAM_SCALE = 0.10f;
    /** Maximum player-adjustable delta on top of the club's natural attack angle (degrees). */
    private static final float MANUAL_RANGE_DEG = 8.0f;
    /** Degrees of attack angle change per HUD pixel of lateral drag.
     *  Full ±20% screen width (≈256 HUD units @ 1280) = ±MANUAL_RANGE_DEG. */
    private static final float DEG_PER_HUD_PX = MANUAL_RANGE_DEG / (0.20f * 1280f);

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
    // trailTimesMs is a parallel list of System.currentTimeMillis() for each trail point,
    // used to compute time-based velocity (screen widths per second) independent of frame rate.
    private final List<Vector2> trail        = new ArrayList<>();
    private final List<Long>    trailTimesMs = new ArrayList<>();
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
    /** HUD Y of the trail crossing point (the actual strike height). Used to freeze clubY at impact. */
    private float impactCrossY = 0f;
    /** Set once per swing after follow-through is assessed; cleared by {@link #consumeResult}. */
    private SwingResult pendingResult = null;
    /**
     * True while the overview panel is showing (swing complete, waiting for the player
     * to tap anywhere to confirm and fire the shot).
     * Cleared by {@link #consumeResult()} when ShotController picks up the result.
     */
    private boolean showingOverview = false;
    /** Snapshot of the swing result captured at the end of the gesture; displayed in the overview. */
    private SwingResult savedResult = null;
    /** True for one frame after injectTestResult() to prevent the same tap from immediately confirming. */
    private boolean overviewTapGuard = false;

    // Last-captured impact data for on-screen display (during the active swing, before release).
    private float lastContactOffset = 0f;
    private float lastPathDeg       = 0f;

    // ── Attack angle / ball placement state ──────────────────────────────────
    /** Current touch mode — determined on the first frame of each touch. */
    private TouchMode touchMode = TouchMode.NONE;
    /** Previous-frame HUD X of the touch point; used to compute placement delta. */
    private float lastTouchHudX = 0f;
    /** Club's natural attack angle (degrees). Set by HUD.setSwingNaturalAttackAngle() before each swing. */
    private float naturalAttackAngleDeg = 0f;
    /** Player's manual adjustment on top of the natural angle (degrees, clamped to ±MANUAL_RANGE_DEG). */
    private float manualAttackDeltaDeg = 0f;

    // ── Base tempo windows (set per-shot from GolfGame via setBaseTempoWindows) ──
    /** Base perfect-window fraction before any attack-angle penalty. */
    private float baseTempoWindowPerfect = 0.10f;
    /** Base max-window fraction before any attack-angle penalty. */
    private float baseTempoWindowMax     = 0.50f;

    /**
     * Speed (screen widths per second, sw/s) that corresponds to 100% power ceiling.
     * Must be kept in sync with ShotController.SWING_FULL_POWER_SPEED.
     * Used only for the power-% display in the result overview panel.
     */
    private float fullPowerSpeed = 2.0f;

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

    /** True while the post-swing overview panel is displayed, waiting for a tap to fire. */
    public boolean isShowingOverview() { return showingOverview; }

    public SwingGestureAnalyser getAnalyser() { return analyser; }

    /**
     * Injects a synthetic result directly into the overview panel (for TEST_SWING).
     * The overview will display normally; a tap confirms and fires the shot via the usual path.
     */
    public void injectTestResult(SwingResult r) {
        savedResult      = r;
        showingOverview  = true;
        pendingResult    = null; // set on tap, as normal
        overviewTapGuard = true; // skip tap-check for the frame this was injected
    }

    /**
     * Returns the completed swing result and clears it, or null if no result is ready.
     * GolfGame should call this every frame; a non-null return means "fire the shot".
     * Also clears the overview state so the panel disappears.
     */
    public SwingResult consumeResult() {
        SwingResult r = pendingResult;
        pendingResult = null;
        if (r != null) {
            showingOverview = false;
            savedResult = null;
        }
        return r;
    }

    public void setTerrainType(Terrain.TerrainType type) {
        this.terrainType = (type != null) ? type : Terrain.TerrainType.FAIRWAY;
    }

    public void setDivotEnabled(boolean enabled) {
        this.divotEnabled = enabled;
    }

    /** Sets the full-power speed threshold for the overview power-% display. Keep in sync with ShotController. */
    public void setFullPowerSpeed(float speed) { this.fullPowerSpeed = speed; }

    /** Sets the rendered club head size. Larger values make contact more forgiving (offset is normalised by clubH). */
    public void setClubSize(float w, float h) {
        this.clubW = w;
        this.clubH = h;
    }

    /** Set the natural attack angle for the current club. Call once when the swing view opens. */
    public void setNaturalAttackAngleDeg(float deg) {
        this.naturalAttackAngleDeg = deg;
        this.manualAttackDeltaDeg  = 0f; // reset manual offset for new club/shot
    }

    /** Sets the follow-through angle threshold (degrees) for HIGH/NEUTRAL/LOW classification. */
    public void setFollowThroughThreshold(float degrees) {
        analyser.setFollowThroughThreshold(degrees);
    }

    /**
     * Store the base tempo windows for this shot (club + difficulty) and apply them
     * immediately to the analyser.  Call this instead of analyser.setTempoWindows()
     * directly so that ball-placement adjustments can narrow the windows correctly.
     */
    public void setBaseTempoWindows(float perfectFraction, float maxFraction) {
        this.baseTempoWindowPerfect = perfectFraction;
        this.baseTempoWindowMax     = maxFraction;
        analyser.setTempoWindows(perfectFraction, maxFraction);
    }

    /**
     * Re-apply tempo windows to the analyser, narrowed by the current manual attack
     * angle delta.  Called when a ball-placement drag is confirmed (finger lifted).
     * Symmetric: ascending and descending deviations from naturalAttackAngleDeg are
     * penalised equally.  Max 50% narrowing at full ±MANUAL_RANGE_DEG.
     */
    private void applyAttackAngleTempoAdjustment() {
        float penalty     = Math.abs(manualAttackDeltaDeg) / MANUAL_RANGE_DEG; // 0→1
        float narrowFactor = 1f - penalty * 0.5f;                              // 1.0→0.5
        analyser.setTempoWindows(
                baseTempoWindowPerfect * narrowFactor,
                baseTempoWindowMax     * narrowFactor);
        if (Math.abs(manualAttackDeltaDeg) > 0.5f) {
            Gdx.app.log("SwingDebug", String.format(
                "[ATTACK_TEMPO] delta=%.1fdeg penalty=%.0f%% → perfect=%.3f max=%.3f",
                manualAttackDeltaDeg, penalty * 50f,
                baseTempoWindowPerfect * narrowFactor,
                baseTempoWindowMax     * narrowFactor));
        }
    }

    /** Combined attack angle = natural + player manual adjustment (degrees). */
    public float getTotalAttackAngleDeg() {
        return naturalAttackAngleDeg + manualAttackDeltaDeg;
    }

    /**
     * World-space camera aim offset to pass to CameraController.setSwingCamAimOffset().
     * Negative total attack angle (descending) pushes camera toward target (ball appears right).
     * Positive (ascending) pulls camera back from target (ball appears left).
     */
    public float getCamAimOffset() {
        return -getTotalAttackAngleDeg() * CAM_SCALE;
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

        // ── Overview state: waiting for tap to confirm and fire ───────────────
        // pendingResult is set here but showingOverview stays true until
        // consumeResult() clears it — this keeps isShowingOverview() returning
        // true for ShotController's cancel-guard check within the same frame.
        if (showingOverview) {
            if (overviewTapGuard) {
                overviewTapGuard = false; // consume the guard; allow tap next frame
                return;
            }
            boolean justTapped = Platform.isAndroid()
                    ? Gdx.input.justTouched()
                    : Gdx.input.isButtonJustPressed(Buttons.LEFT);
            if (justTapped) {
                pendingResult = savedResult;
                // showingOverview cleared by consumeResult() when ShotController picks it up
            }
            return;
        }

        boolean pressing = Platform.isAndroid()
                ? Gdx.input.isTouched()
                : Gdx.input.isButtonPressed(Buttons.LEFT);

        if (pressing) {
            float touchHudX = Gdx.input.getX() * scaleXCache;

            // ── First frame of touch: determine mode ─────────────────────────
            // Touch LEFT of ball (screen-left = toward target = "in front") → placement.
            // Touch RIGHT of ball (screen-right = away from target = "behind") → swing.
            // The club head always sits to the right of the ball, so this gives a clean split.
            if (!wasTouching) {
                if (analyser.getPhase() == SwingGestureAnalyser.Phase.IDLE
                        && ballHudX >= 0f
                        && touchHudX < ballHudX) {
                    touchMode = TouchMode.BALL_PLACEMENT;
                } else {
                    touchMode = TouchMode.SWINGING;
                }
                lastTouchHudX = touchHudX;
                wasTouching = true;
            }

            if (touchMode == TouchMode.BALL_PLACEMENT) {
                // ── Ball placement: lateral drag shifts the camera aim offset ─
                // Drag LEFT (toward target, negative delta) → ascending blow (+deg).
                float delta = touchHudX - lastTouchHudX;
                manualAttackDeltaDeg -= delta * DEG_PER_HUD_PX;
                manualAttackDeltaDeg = MathUtils.clamp(manualAttackDeltaDeg, -MANUAL_RANGE_DEG, MANUAL_RANGE_DEG);
                lastTouchHudX = touchHudX;
            } else {
                // ── Normal swing drag ─────────────────────────────────────────
                trail.add(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
                trailTimesMs.add(System.currentTimeMillis());
                analyser.update(trail, trailTimesMs, true);

                // ── Impact detection ──────────────────────────────────────────
                if (!impactDetected
                        && analyser.getPhase() == SwingGestureAnalyser.Phase.FORWARD_SWING
                        && ballHudX >= 0f
                        && trail.size() >= 2) {
                    detectImpact();
                    if (impactDetected && divotEnabled) buildDivotScar();
                }
            }

        } else if (wasTouching) {
            if (touchMode == TouchMode.BALL_PLACEMENT) {
                // Release confirms placement — narrow tempo windows by attack angle delta
                applyAttackAngleTempoAdjustment();
                touchMode = TouchMode.NONE;
            } else {
                // ── End of swing drag ─────────────────────────────────────────
                analyser.update(trail, trailTimesMs, false); // signal release → COMPLETE
                if (impactDetected) {
                    assessFollowThrough();
                    savedResult = analyser.buildResult(getTotalAttackAngleDeg());
                    showingOverview = true;  // display overview; pendingResult set on next tap
                }
                trail.clear();
                trailTimesMs.clear();
                analyser.reset();            // ready for next gesture attempt
                impactDetected   = false;
                impactTrailIndex = -1;
                touchMode        = TouchMode.NONE;
            }
            wasTouching = false;
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
        SwingGestureAnalyser.Phase phase = analyser.getPhase();
        float clubX, clubY;
        if (!showingOverview && phase != SwingGestureAnalyser.Phase.IDLE) {
            if (analyser.isImpactCaptured() || phase == SwingGestureAnalyser.Phase.COMPLETE) {
                // Freeze at the actual crossing height so heel/toe is visible on the face.
                clubX = ballHudX + clubW * 0.85f;
                clubY = impactCrossY;
            } else {
                // Follow the live drag/touch position, but stop before the ball.
                float rawX = Gdx.input.getX() * scaleXCache;
                float rawY = (Gdx.graphics.getHeight() - Gdx.input.getY()) * scaleYCache;
                clubX = Math.max(rawX, ballHudX + clubW * 0.85f);
                clubY = rawY;
            }
        } else {
            clubX = ballHudX + CLUB_RIGHT_OFFSET;
            clubY = ballHudY;
        }

        if (batch.isDrawing()) batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.setProjectionMatrix(hudViewport.getCamera().combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // ── Club head colour: white → green → red during forward swing ────────
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

        // ── Contact marker: coloured dot on club face at the ball's position ───
        // clubY is frozen at impactCrossY (where the trail crossed), so the dot
        // at ballHudY correctly shows where on the face the ball was struck.
        if (analyser.isImpactCaptured()) {
            Color markerCol = Math.abs(lastContactOffset) < 0.25f ? Color.GREEN
                            : Math.abs(lastContactOffset) < 0.6f  ? Color.YELLOW
                            : Color.RED;
            sr.setColor(markerCol);
            sr.circle(clubX, ballHudY, 4f, 12);
        }

        // ── Follow-through reference line + band edges ───────────────────────
        // Shown once impact is captured (not during overview).
        // Central line: projects from crossing point in the path direction — this is
        // where the follow-through should go for a neutral result.
        // Band edges: ±threshold degrees either side of the central line.
        if (!showingOverview && analyser.isImpactCaptured()) {
            float pathRad   = lastPathDeg * MathUtils.degreesToRadians;
            float threshold = analyser.getFollowThroughThreshold();

            // Central line — extends to left edge of viewport, bright white
            float cDirX = -(float) Math.cos(pathRad);
            float cDirY =  (float) Math.sin(pathRad);
            float cLen  = -ballHudX / cDirX; // t where x reaches 0
            sr.setColor(1f, 1f, 1f, 0.85f);
            sr.rectLine(ballHudX, impactCrossY,
                        ballHudX + cDirX * cLen,
                        impactCrossY + cDirY * cLen, 3f);

            // Band edges — to left edge, bright yellow
            sr.setColor(1f, 0.85f, 0.2f, 0.65f);
            for (int sign = -1; sign <= 1; sign += 2) {
                float bandRad = (lastPathDeg + sign * threshold) * MathUtils.degreesToRadians;
                float bDirX   = -(float) Math.cos(bandRad);
                float bDirY   =  (float) Math.sin(bandRad);
                float bLen    = -ballHudX / bDirX;
                sr.rectLine(ballHudX, impactCrossY,
                            ballHudX + bDirX * bLen,
                            impactCrossY + bDirY * bLen, 2f);
            }
        }

        // ── Full-backswing reference line ─────────────────────────────────────
        // Vertical guide at the screen position that maps to backswingNorm = 1.0.
        // Shown during active swing (not overview) to give the player a visual target.
        if (!showingOverview) {
            float lineHudX = hudViewport.getWorldWidth() * SwingGestureAnalyser.FULL_SWING_LINE_FRAC;
            sr.setColor(1f, 1f, 1f, 0.35f);
            sr.rect(lineHudX - 1f, 0f, 2f, hudViewport.getWorldHeight());
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

        // ── Overview panel background (drawn here while blend is still enabled) ─
        if (showingOverview && savedResult != null) {
            float W = hudViewport.getWorldWidth();
            float H = hudViewport.getWorldHeight();
            float panelX = W * 0.24f;
            float panelY = H * 0.57f;
            float panelW = W * 0.52f;
            float panelH = H * 0.37f;
            float rowH   = panelH / 9f;
            float barW   = W * 0.008f;
            sr.setColor(0.06f, 0.06f, 0.10f, 0.88f);
            sr.rect(panelX, panelY, panelW, panelH);
            // Coloured quality bars — one per data row.
            float[] rowTops = {
                panelY + panelH - rowH * 2.1f,
                panelY + panelH - rowH * 3.1f,
                panelY + panelH - rowH * 4.1f,
                panelY + panelH - rowH * 5.1f,
                panelY + panelH - rowH * 6.1f,
                panelY + panelH - rowH * 7.1f,
                panelY + panelH - rowH * 8.1f,
            };
            Color[] rowColors = {
                overviewTempoColor(savedResult),
                overviewContactColor(savedResult),
                overviewPathColor(savedResult),
                overviewFtColor(savedResult),
                overviewBackswingColor(savedResult),
                overviewSpeedColor(savedResult),
                overviewAttackColor(savedResult),
            };
            for (int i = 0; i < rowColors.length; i++) {
                sr.setColor(rowColors[i]);
                sr.rect(panelX, rowTops[i], barW, rowH);
            }
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
        String label;
        Color  col;
        if (showingOverview) {
            label = "RESULT";
            col   = COL_COMPLETE;
        } else if (touchMode == TouchMode.BALL_PLACEMENT) {
            label = "PLACEMENT";
            col   = COL_TRANSITION;
        } else {
            label = phaseLabel(phase);
            col   = phaseColour(phase);
        }

        float savedScale = font.getScaleX();
        // During overview the label is smaller (20%) and sits higher in the panel header.
        float labelScale = showingOverview
                ? hudViewport.getWorldHeight() * 0.0021f
                : hudViewport.getWorldHeight() * 0.0026f;
        font.getData().setScale(labelScale);
        font.setColor(col);
        layout.setText(font, label);
        float lx = (hudViewport.getWorldWidth() - layout.width) * 0.5f;
        float ly = showingOverview
                ? hudViewport.getWorldHeight() * 0.935f
                : hudViewport.getWorldHeight() * 0.88f;

        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        font.draw(batch, label, lx, ly);

        // ── Attack angle readout (shown during ball placement) ────────────────
        if (touchMode == TouchMode.BALL_PLACEMENT) {
            float total = getTotalAttackAngleDeg();
            String atkDir = total > 0.5f ? "ASCENDING" : total < -0.5f ? "DESCENDING" : "LEVEL";
            String atkLine = String.format("%s  %+.1f\u00b0", atkDir, total);
            font.getData().setScale(hudViewport.getWorldHeight() * 0.0020f);
            // Colour: green near natural angle (small manual delta), yellow/orange as pushed far
            float manualAbs = Math.abs(manualAttackDeltaDeg);
            Color atkColor = manualAbs < 3f ? Color.GREEN
                           : manualAbs < 6f ? Color.YELLOW
                           : new Color(1f, 0.5f, 0.1f, 1f);
            font.setColor(atkColor);
            layout.setText(font, atkLine);
            font.draw(batch, atkLine,
                    (hudViewport.getWorldWidth() - layout.width) * 0.5f,
                    hudViewport.getWorldHeight() * 0.83f);
        }

        // ── Contact / path / tempo debug line (shown during active swing) ────
        if (!showingOverview && analyser.isImpactCaptured()) {
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

        // ── Overview panel text ───────────────────────────────────────────────
        if (showingOverview && savedResult != null) {
            renderOverviewText(batch, font, hudViewport, savedResult);
        }

        batch.end();

        font.getData().setScale(savedScale);
        font.setColor(Color.WHITE);
    }

    // ── Overview panel helpers ────────────────────────────────────────────────

    private void renderOverviewText(SpriteBatch batch, BitmapFont font, Viewport vp, SwingResult r) {
        float W = vp.getWorldWidth();
        float H = vp.getWorldHeight();
        // Panel bounds — must match the SR background drawn earlier.
        float panelX = W * 0.24f;
        float panelY = H * 0.57f;
        float panelW = W * 0.52f;
        float panelH = H * 0.37f;
        float pad    = W * 0.018f;
        float col2X  = panelX + panelW * 0.36f;

        float rowScale    = H * 0.0016f;
        float footerScale = H * 0.0012f;

        font.getData().setScale(rowScale);

        // Row baselines are spaced evenly across the lower 8/9 of the panel
        // (top 1/9 is the header area, occupied by the "RESULT" phase label).
        float rowH = panelH / 9f;
        float row1Y = panelY + panelH - rowH * 1.6f;
        float row2Y = row1Y - rowH;
        float row3Y = row2Y - rowH;
        float row4Y = row3Y - rowH;
        float row5Y = row4Y - rowH;
        float row6Y = row5Y - rowH;
        float row7Y = row6Y - rowH;

        // Row 1: TEMPO
        String tempoVal = switch (r.tempoResult) {
            case PERFECT    -> String.format("CLEAN   %d%%", (int)(r.tempoQuality * 100));
            case EARLY_THIN -> String.format("THIN    %d%%", (int)(r.tempoQuality * 100));
            case LATE_FAT   -> String.format("FAT     %d%%", (int)(r.tempoQuality * 100));
            default         -> "---";
        };
        font.setColor(Color.WHITE);
        font.draw(batch, "TEMPO", panelX + pad, row1Y);
        font.setColor(overviewTempoColor(r));
        font.draw(batch, tempoVal, col2X, row1Y);

        // Row 2: CONTACT
        String contactVal = r.contactOffset < -0.25f
                ? String.format("HEEL  (%.2f)", r.contactOffset)
                : r.contactOffset >  0.25f
                ? String.format("TOE   (+%.2f)", r.contactOffset)
                : String.format("CENTRE (%.2f)", r.contactOffset);
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTACT", panelX + pad, row2Y);
        font.setColor(overviewContactColor(r));
        font.draw(batch, contactVal, col2X, row2Y);

        // Row 3: PATH
        String pathDir = r.pathDeg > 2f ? "IN-TO-OUT" : r.pathDeg < -2f ? "OUT-TO-IN" : "STRAIGHT";
        String pathVal = String.format("%s  %.1f\u00b0", pathDir, r.pathDeg);
        font.setColor(Color.WHITE);
        font.draw(batch, "PATH", panelX + pad, row3Y);
        font.setColor(overviewPathColor(r));
        font.draw(batch, pathVal, col2X, row3Y);

        // Row 4: FOLLOW-THROUGH
        String ftLabel = switch (r.followThroughResult) {
            case LOW     -> "LOW";
            case NEUTRAL -> "NEUTRAL";
            case HIGH    -> "HIGH";
            default      -> "---";
        };
        String ftVal = r.followThroughResult == SwingGestureAnalyser.FollowThroughResult.NONE
                ? "---"
                : String.format("%s  %+.1f\u00b0", ftLabel, r.followThroughAngleDeg);
        font.setColor(Color.WHITE);
        font.draw(batch, "FOLLOW", panelX + pad, row4Y);
        font.setColor(overviewFtColor(r));
        font.draw(batch, ftVal, col2X, row4Y);

        // Row 5: BACKSWING
        String bkLabel = r.backswingNorm >= 0.75f ? "FULL"
                       : r.backswingNorm >= 0.40f ? "PARTIAL"
                       : "SHORT";
        String bkVal   = String.format("%s  %d%%", bkLabel, (int)(r.backswingNorm * 100));
        font.setColor(Color.WHITE);
        font.draw(batch, "BACKSWING", panelX + pad, row5Y);
        font.setColor(overviewBackswingColor(r));
        font.draw(batch, bkVal, col2X, row5Y);

        // Row 6: SPEED — raw forward swing speed as % of SWING_FULL_POWER_SPEED (honest, pre-backswing)
        float spdFrac = MathUtils.clamp(r.peakForwardSpeed / fullPowerSpeed, 0f, 1f);
        String spdVal = String.format("%.2f sw/s  (%d%%)", r.peakForwardSpeed, (int)(spdFrac * 100));
        font.setColor(Color.WHITE);
        font.draw(batch, "SPEED", panelX + pad, row6Y);
        font.setColor(overviewSpeedColor(r));
        font.draw(batch, spdVal, col2X, row6Y);

        // Row 7: ATTACK ANGLE
        String atkDir = r.attackAngleDeg > 0.5f ? "ASCENDING" : r.attackAngleDeg < -0.5f ? "DESCENDING" : "LEVEL";
        String atkVal = String.format("%s  %+.1f\u00b0", atkDir, r.attackAngleDeg);
        font.setColor(Color.WHITE);
        font.draw(batch, "ATTACK", panelX + pad, row7Y);
        font.setColor(overviewAttackColor(r));
        font.draw(batch, atkVal, col2X, row7Y);

        // Footer
        font.getData().setScale(footerScale);
        font.setColor(0.45f, 0.45f, 0.45f, 1f);
        String footer = "TAP TO SWING";
        layout.setText(font, footer);
        font.draw(batch, footer,
                panelX + (panelW - layout.width) * 0.5f,
                panelY + rowH * 0.72f);
    }

    private static Color overviewTempoColor(SwingResult r) {
        return switch (r.tempoResult) {
            case PERFECT    -> Color.GREEN;
            case EARLY_THIN -> Color.YELLOW;
            case LATE_FAT   -> new Color(1f, 0.5f, 0.1f, 1f);
            default         -> Color.WHITE;
        };
    }

    private static Color overviewContactColor(SwingResult r) {
        float abs = Math.abs(r.contactOffset);
        return abs < 0.25f ? Color.GREEN
             : abs < 0.60f ? Color.YELLOW
             : Color.RED;
    }

    private static Color overviewPathColor(SwingResult r) {
        float abs = Math.abs(r.pathDeg);
        return abs < 2f ? Color.GREEN
             : abs < 5f ? Color.YELLOW
             : new Color(1f, 0.5f, 0.1f, 1f);
    }

    private static Color overviewFtColor(SwingResult r) {
        return switch (r.followThroughResult) {
            case LOW     -> Color.GREEN;
            case NEUTRAL -> Color.YELLOW;
            case HIGH    -> Color.RED;
            default      -> Color.WHITE;
        };
    }

    private static Color overviewBackswingColor(SwingResult r) {
        return r.backswingNorm >= 0.75f ? Color.GREEN
             : r.backswingNorm >= 0.40f ? Color.YELLOW
             : new Color(1f, 0.5f, 0.1f, 1f);
    }

    private Color overviewSpeedColor(SwingResult r) {
        float pct = MathUtils.clamp(r.peakForwardSpeed / fullPowerSpeed, 0f, 1f);
        return pct >= 0.70f ? Color.GREEN
             : pct >= 0.40f ? Color.YELLOW
             : new Color(1f, 0.5f, 0.1f, 1f);
    }

    private static Color overviewAttackColor(SwingResult r) {
        // Neutral is green; extreme values (> ±5°) go orange — just informational.
        float abs = Math.abs(r.attackAngleDeg);
        return abs < 3f ? Color.GREEN
             : abs < 6f ? Color.YELLOW
             : new Color(1f, 0.5f, 0.1f, 1f);
    }

    // -------------------------------------------------------------------------

    public void reset() {
        trail.clear();
        trailTimesMs.clear();
        wasTouching          = false;
        impactDetected       = false;
        impactTrailIndex     = -1;
        impactCrossY         = 0f;
        lastContactOffset    = 0f;
        lastPathDeg          = 0f;
        divotFadeTimer       = 0f;
        divotScarRightX      = -9999f;
        divotEnabled         = true;
        pendingResult        = null;
        showingOverview      = false;
        savedResult          = null;
        overviewTapGuard     = false;
        touchMode            = TouchMode.NONE;
        manualAttackDeltaDeg = 0f;
        // Re-apply base windows so analyser is clean for next shot
        analyser.setTempoWindows(baseTempoWindowPerfect, baseTempoWindowMax);
        analyser.reset();
    }

    // -------------------------------------------------------------------------

    /**
     * Called on touch release when impact was detected.
     * Computes the signed angle between the projected path reference line and the
     * vector from the crossing point to the final pointer position.
     *
     * Positive angle = follow-through deviated toward the flip side of the path.
     * Negative angle = good extension (stayed on or below the path line).
     * Zero = followed through exactly along the path direction.
     *
     * This is length-independent: a short follow-through and a full one that land
     * at the same angle from the path line score identically.
     */
    private void assessFollowThrough() {
        if (impactTrailIndex < 0 || trail.isEmpty()) {
            analyser.onFollowThrough(0f);
            return;
        }

        // Final pointer position in HUD coords.
        Vector2 last  = trail.get(trail.size() - 1);
        float finalHudX = last.x * scaleXCache;
        float finalHudY = (Gdx.graphics.getHeight() - last.y) * scaleYCache;

        // Vector from impact crossing point to final position.
        float ftDx = finalHudX - ballHudX;
        float ftDy = finalHudY - impactCrossY;

        // If the player barely moved after impact, report neutral.
        if (ftDx * ftDx + ftDy * ftDy < 4f) {
            analyser.onFollowThrough(0f);
            return;
        }

        // Path direction vector (unit, pointing left toward target).
        // pathDeg=0 → horizontal-left; +ve → upward-left (in-to-out); -ve → downward-left (out-to-in).
        float pathRad = lastPathDeg * MathUtils.degreesToRadians;
        float dirX    = -(float) Math.cos(pathRad);
        float dirY    =  (float) Math.sin(pathRad);

        // Signed angle from path direction to follow-through direction.
        // Negate so that positive = flip side (above path line) and negative = extension.
        // atan2(cross, dot) gives the CCW angle from path to ftDir; negate for our convention.
        float cross    = dirX * ftDy - dirY * ftDx;
        float dot      = dirX * ftDx + dirY * ftDy;
        float angleDeg = -(float)(Math.atan2(cross, dot) * MathUtils.radiansToDegrees);

        analyser.onFollowThrough(angleDeg);
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
     * When found, interpolate the Y at crossing, derive heel/toe offset and path angle,
     * and compute impact speed as a windowed average over ±IMPACT_SPEED_HALF_WINDOW trail
     * segments on each side of the crossing.  Speed is in screen widths per second (sw/s)
     * — device-independent regardless of resolution or frame rate.
     */
    private static final int IMPACT_SPEED_HALF_WINDOW = 4;

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
                float deltaX = bx - ax;
                float deltaY = by - ay;
                float pathDeg = (float)(Math.atan2(deltaY, -deltaX) * MathUtils.radiansToDegrees);

                // Speed: average over ±IMPACT_SPEED_HALF_WINDOW segments around the crossing.
                // Computed in raw screen pixels / screen_width / seconds = screen widths per second.
                // Averaging over a window avoids single-frame noise at the crossing moment.
                float screenW    = Gdx.graphics.getWidth();
                int   winStart   = Math.max(1, i - IMPACT_SPEED_HALF_WINDOW);
                int   winEnd     = Math.min(size - 1, i + IMPACT_SPEED_HALF_WINDOW);
                float totalSpeed = 0f;
                int   validSegs  = 0;
                for (int j = winStart; j <= winEnd; j++) {
                    long dtMs = trailTimesMs.get(j) - trailTimesMs.get(j - 1);
                    if (dtMs <= 0) continue;
                    float rdx  = trail.get(j).x - trail.get(j - 1).x;
                    float rdy  = trail.get(j).y - trail.get(j - 1).y;
                    float dist = (float) Math.sqrt(rdx * rdx + rdy * rdy);
                    totalSpeed += dist / screenW / (dtMs / 1000f);
                    validSegs++;
                }
                float impactSpeed = validSegs > 0 ? totalSpeed / validSegs : 0f;

                lastContactOffset = heelToe;
                lastPathDeg       = pathDeg;
                impactCrossY      = crossY;
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
