package com.gearygolf.golf.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.List;

/**
 * Analyses the raw touch trail produced by SwingOverlay and classifies it into
 * swing phases.  All velocity values are in raw screen pixels per frame.
 *
 * Step 3: phase detection + basic debug log.
 * Step 4: movement threshold before backswing starts + impact contact/path extraction.
 * Step 5: tempo/divot — backswing duration → expected forward duration → timing quality.
 */
public class SwingGestureAnalyser {

    public enum Phase { IDLE, BACKSWING, TRANSITION, FORWARD_SWING, COMPLETE }

    /** Timing quality: how close the actual impact was to the expected perfect moment. */
    public enum TempoResult { NONE, PERFECT, EARLY_THIN, LATE_FAT }

    /**
     * Follow-through quality: where the trail goes after the ball crossing.
     * HIGH = trail arcs above the ball line → flip/scoop (bad).
     * LOW  = trail stays below/through the ball line → good extension.
     */
    public enum FollowThroughResult { NONE, LOW, NEUTRAL, HIGH }

    // ── Tuning constants ────────────────────────────────────────────────────
    /** Points to average for smoothed X velocity. */
    private static final int VELOCITY_WINDOW = 6;
    /** Minimum rightward peak (px/frame) needed before we respect a slowdown as transition. */
    private static final float BACKSWING_MIN_PEAK = 2.0f;
    /** Rightward velocity dropping below this triggers TRANSITION (once peak is confirmed). */
    private static final float TRANSITION_ENTRY  = 0.8f;
    /** Leftward velocity below this confirms FORWARD_SWING has begun. */
    private static final float FORWARD_ENTRY     = -0.8f;
    /**
     * Minimum distance (raw screen pixels) the finger/mouse must travel from the
     * initial press before the backswing timer starts.  Lets the player hold down
     * in "address" position before committing to a swing.
     */
    private static final float MOVEMENT_THRESHOLD = 15f;
    /**
     * Tempo window as a fraction of the expected forward-swing duration.
     * e.g. 0.10 means ±10% of expectedFwdDurationMs counts as PERFECT.
     * A slow swing gives a larger absolute window; a fast swing a smaller one —
     * matching real-golf feel where slow swings are more forgiving.
     * Tightest (Tour Pro) setting for development; loosen per difficulty via
     * {@link #setTempoWindows}.
     */
    private float perfectWindowFraction = 0.10f;
    /**
     * Beyond this fraction of expectedFwdDurationMs, tempo quality drops to zero.
     */
    private float maxWindowFraction = 0.50f;

    // ── State ────────────────────────────────────────────────────────────────
    private Phase phase = Phase.IDLE;

    private long  backswingStartMs    = 0;
    private float backswingStartX     = 0f;
    private float backswingPeakVx     = 0f;   // most positive (rightward)
    private long  backswingDurationMs = 0;    // set at TRANSITION

    private long  forwardStartMs      = 0;
    private long  expectedFwdDurationMs = 0;  // backswingDurationMs / 3 (3:1 ratio)
    private float forwardPeakVx       = 0f;   // most negative (leftward)

    // ── Step 5: tempo ────────────────────────────────────────────────────────
    private long        impactCapturedMs = 0;   // wall-clock time of impact crossing
    private TempoResult tempoResult      = TempoResult.NONE;
    private float       tempoQuality     = 0f;  // 0 = terrible, 1 = perfect

    // ── Step 6a: follow-through ──────────────────────────────────────────────
    /**
     * Normalised follow-through height relative to the ball line.
     * +1 = fully above (flip/scoop), 0 = on the line, -1 = fully below (good extension).
     */
    private float              followThroughHeight = 0f;
    private FollowThroughResult followThroughResult = FollowThroughResult.NONE;

    // ── Step 4: contact & path ───────────────────────────────────────────────
    /** Heel-to-toe contact: -1 = full heel, 0 = centre, +1 = full toe. */
    private float contactOffset  = 0f;
    /** Path angle at impact in degrees. + = in-to-out (draw bias), - = out-to-in (fade bias). */
    private float pathDeg        = 0f;
    /**
     * Club-head speed at impact in HUD pixels/frame (raw screen units scaled to HUD viewport).
     * Primary input to shot power — not yet normalised; the ball physics layer will do that.
     */
    private float impactSpeed    = 0f;
    private boolean impactCaptured = false;

    // ── Public API ──────────────────────────────────────────────────────────

    public Phase getPhase() { return phase; }

    public float getContactOffset()    { return contactOffset; }
    public float getPathDeg()          { return pathDeg; }
    public float getImpactSpeed()      { return impactSpeed; }
    public boolean isImpactCaptured()  { return impactCaptured; }
    public TempoResult getTempoResult()              { return tempoResult; }
    public float getTempoQuality()                   { return tempoQuality; }
    public FollowThroughResult getFollowThroughResult() { return followThroughResult; }
    public float getFollowThroughHeight()            { return followThroughHeight; }
    public long getExpectedFwdDurationMs() { return expectedFwdDurationMs; }
    public long getForwardStartMs()    { return forwardStartMs; }

    /**
     * Override the timing tolerance windows.  Call once when difficulty is set or changed.
     * Both values are fractions of the expected forward-swing duration, so a slow swing
     * automatically gets a larger absolute window than a fast swing.
     *
     * @param perfectFraction  |delta| / expected ≤ this → PERFECT.
     *                         Suggested: 0.10 (Tour Pro) → 0.30 (Novice).
     * @param maxFraction      Beyond this fraction quality = 0.
     *                         Suggested: 0.50 (Tour Pro) → 1.20 (Novice).
     */
    public void setTempoWindows(float perfectFraction, float maxFraction) {
        this.perfectWindowFraction = perfectFraction;
        this.maxWindowFraction     = maxFraction;
    }

    /**
     * Returns a 0→1→2+ progress value through the expected forward swing.
     * 0 = just started, 1.0 = perfect impact moment, >1 = past expected time.
     * Returns 0 when not in FORWARD_SWING or when no backswing data is available.
     */
    public float getForwardSwingTimingT() {
        if (phase != Phase.FORWARD_SWING || expectedFwdDurationMs <= 0) return 0f;
        long elapsed = System.currentTimeMillis() - forwardStartMs;
        return (float) elapsed / expectedFwdDurationMs;
    }

    /**
     * Call every frame that the swing view is active.
     *
     * @param trail    All recorded points this gesture in raw screen pixels (x right, y from top).
     * @param touching Whether the screen / mouse button is currently pressed.
     */
    public void update(List<Vector2> trail, boolean touching) {
        if (!touching) {
            if (phase == Phase.FORWARD_SWING) {
                phase = Phase.COMPLETE;
                logComplete(trail);
            } else if (phase != Phase.IDLE && phase != Phase.COMPLETE) {
                Gdx.app.log("SwingDebug", "[CANCELLED] phase=" + phase);
                reset();
            }
            return;
        }

        // ── IDLE: wait for movement threshold before starting backswing ──────
        if (phase == Phase.IDLE) {
            if (trail.size() < 2) return;
            Vector2 first = trail.get(0);
            Vector2 last  = trail.get(trail.size() - 1);
            float dx = last.x - first.x;
            float dy = last.y - first.y;
            if ((float) Math.sqrt(dx * dx + dy * dy) < MOVEMENT_THRESHOLD) return;

            // Threshold crossed — begin backswing from the current position
            phase = Phase.BACKSWING;
            backswingStartMs = System.currentTimeMillis();
            backswingStartX  = last.x;
            return;
        }

        if (trail.size() < 2) return;

        float vx = smoothedVx(trail);

        switch (phase) {
            case BACKSWING:
                if (vx > backswingPeakVx) backswingPeakVx = vx;
                if (vx < TRANSITION_ENTRY && backswingPeakVx >= BACKSWING_MIN_PEAK) {
                    phase = Phase.TRANSITION;
                    float dist = trail.get(trail.size() - 1).x - backswingStartX;
                    Gdx.app.log("SwingDebug", String.format(
                        "[TRANSITION] partial_dur=%.2fs dist_px=%.0f peak_vx=%.1f",
                        (System.currentTimeMillis() - backswingStartMs) / 1000f,
                        dist, backswingPeakVx));
                }
                break;

            case TRANSITION:
                if (vx <= FORWARD_ENTRY) {
                    forwardStartMs        = System.currentTimeMillis();
                    // Measure full prep time (backswing + transition pause) at the moment
                    // the forward swing is confirmed — gives a much more accurate ratio.
                    backswingDurationMs   = forwardStartMs - backswingStartMs;
                    expectedFwdDurationMs = backswingDurationMs / 3;  // 3:1 ratio
                    phase = Phase.FORWARD_SWING;
                    Gdx.app.log("SwingDebug", String.format(
                        "[FORWARD_SWING] started  prep=%.2fs  expected_impact_in=%.2fs",
                        backswingDurationMs / 1000f, expectedFwdDurationMs / 1000f));
                }
                break;

            case FORWARD_SWING:
                if (vx < forwardPeakVx) forwardPeakVx = vx;
                break;

            default:
                break;
        }
    }

    /**
     * Called by SwingOverlay when the drag trail crosses the ball's screen position.
     * Captures the contact point (heel/toe offset) and path direction at that moment.
     *
     * @param heelToeNorm  Normalised heel-to-toe offset: -1=heel, 0=centre, +1=toe.
     * @param pathDegrees  Path angle in degrees (+ = in-to-out/draw, - = out-to-in/fade).
     */
    /**
     * Called by SwingOverlay after touch is released, with a normalised measure of
     * how high above (+) or below (-) the ball line the follow-through arc went.
     * +1 = full flip/scoop, -1 = fully extended low, 0 = neutral.
     */
    /**
     * Builds and returns an immutable snapshot of all swing values.
     * Call after {@link #onFollowThrough} and before {@link #reset}.
     */
    public SwingResult buildResult() {
        // Use peak forward-swing velocity (absolute) rather than instantaneous crossing speed.
        // The crossing speed is a single trail segment and very noisy; peak velocity is stable.
        return new SwingResult(
                contactOffset, pathDeg, Math.abs(forwardPeakVx),
                tempoResult, tempoQuality,
                followThroughResult, followThroughHeight);
    }

    public void onFollowThrough(float normHeight) {
        this.followThroughHeight = MathUtils.clamp(normHeight, -1f, 1f);
        this.followThroughResult = normHeight >  0.25f ? FollowThroughResult.HIGH
                                 : normHeight < -0.25f ? FollowThroughResult.LOW
                                 :                       FollowThroughResult.NEUTRAL;
        Gdx.app.log("SwingDebug", String.format(
            "[FOLLOW_THROUGH] height=%.2f result=%s", followThroughHeight, followThroughResult));
    }

    public void onImpact(float heelToeNorm, float pathDegrees, float speedHudPxPerFrame) {
        if (impactCaptured) return;
        this.contactOffset  = heelToeNorm;
        this.pathDeg        = pathDegrees;
        this.impactSpeed    = speedHudPxPerFrame;
        this.impactCaptured = true;

        // ── Tempo quality ────────────────────────────────────────────────────
        if (expectedFwdDurationMs > 0) {
            impactCapturedMs       = System.currentTimeMillis();
            long expectedImpactMs  = forwardStartMs + expectedFwdDurationMs;
            long delta             = impactCapturedMs - expectedImpactMs;
            long absDelta          = Math.abs(delta);
            long perfectWindowMs = (long)(expectedFwdDurationMs * perfectWindowFraction);
            long maxWindowMs     = (long)(expectedFwdDurationMs * maxWindowFraction);
            tempoQuality = absDelta <= perfectWindowMs ? 1f
                         : Math.max(0f, 1f - (float)(absDelta - perfectWindowMs)
                                              / Math.max(1, maxWindowMs - perfectWindowMs));
            tempoResult  = absDelta <= perfectWindowMs ? TempoResult.PERFECT
                         : delta < 0                   ? TempoResult.EARLY_THIN
                         :                               TempoResult.LATE_FAT;
        } else {
            tempoResult  = TempoResult.PERFECT;
            tempoQuality = 1f;
        }

        String contactLabel = heelToeNorm < -0.3f ? "heel"
                            : heelToeNorm >  0.3f ? "toe" : "centre";
        String pathLabel    = pathDegrees >  2f ? "in-to-out"
                            : pathDegrees < -2f ? "out-to-in" : "straight";
        long actualFwdMs   = impactCapturedMs - forwardStartMs;
        long deltaMs       = actualFwdMs - expectedFwdDurationMs;
        long perfectWinMs  = (long)(expectedFwdDurationMs * perfectWindowFraction);
        Gdx.app.log("SwingDebug", String.format(
            "[IMPACT] contact=%.2f(%s) path=%.1fdeg(%s) speed=%.1fpx/f | fwd=%dms exp=%dms delta=%+dms window=±%dms | tempo=%s(q=%.2f)",
            heelToeNorm, contactLabel, pathDegrees, pathLabel, speedHudPxPerFrame,
            actualFwdMs, expectedFwdDurationMs, deltaMs, perfectWinMs,
            tempoResult, tempoQuality));
    }

    public void reset() {
        phase                 = Phase.IDLE;
        backswingStartMs      = 0;
        backswingStartX       = 0f;
        backswingPeakVx       = 0f;
        backswingDurationMs   = 0;
        forwardStartMs        = 0;
        expectedFwdDurationMs = 0;
        forwardPeakVx         = 0f;
        contactOffset         = 0f;
        pathDeg               = 0f;
        impactSpeed           = 0f;
        impactCaptured        = false;
        impactCapturedMs      = 0;
        tempoResult           = TempoResult.NONE;
        tempoQuality          = 0f;
        followThroughHeight   = 0f;
        followThroughResult   = FollowThroughResult.NONE;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /** Average X delta over the last VELOCITY_WINDOW points. Positive = rightward. */
    private float smoothedVx(List<Vector2> trail) {
        int size   = trail.size();
        int window = Math.min(VELOCITY_WINDOW, size - 1);
        float sum  = 0f;
        for (int i = size - window; i < size; i++) {
            sum += trail.get(i).x - trail.get(i - 1).x;
        }
        return sum / window;
    }

    private void logComplete(List<Vector2> trail) {
        // Use stored timestamps so hold-time after impact doesn't inflate the numbers.
        float bkDur  = backswingDurationMs / 1000f;
        float fwdDur = impactCapturedMs > 0
                     ? (impactCapturedMs - forwardStartMs) / 1000f
                     : (System.currentTimeMillis() - forwardStartMs) / 1000f;
        float dist   = trail.isEmpty() ? 0f
                     : trail.get(trail.size() - 1).x - backswingStartX;
        String contactLabel = contactOffset < -0.3f ? "heel"
                            : contactOffset >  0.3f ? "toe" : "centre";
        String pathLabel    = pathDeg >  2f ? "in-to-out"
                            : pathDeg < -2f ? "out-to-in" : "straight";
        Gdx.app.log("SwingDebug", String.format(
            "[COMPLETE] bk_dur=%.2fs fwd_dur=%.2fs bk_dist_px=%.0f " +
            "peak_bk_vx=%.1f peak_fwd_vx=%.1f | " +
            "contact=%.2f(%s) path=%.1fdeg(%s) tempo=%s(q=%.2f)",
            bkDur, fwdDur, dist,
            backswingPeakVx, Math.abs(forwardPeakVx),
            contactOffset, contactLabel, pathDeg, pathLabel,
            tempoResult, tempoQuality));
    }
}
