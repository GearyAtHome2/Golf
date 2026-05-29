package com.gearygolf.golf.hud;

/**
 * Immutable snapshot of all swing gesture values captured at the end of a swing.
 * Produced by SwingOverlay and consumed by GolfGame to drive ball physics.
 *
 * Step 6b: bundle only — no physics wiring yet.
 */
public final class SwingResult {

    /** Heel-to-toe contact: -1 = full heel, 0 = centre, +1 = full toe. */
    public final float contactOffset;

    /** Club path angle at impact in degrees. + = in-to-out (draw), - = out-to-in (fade). */
    public final float pathDeg;

    /**
     * Peak club-head speed during the forward swing in HUD pixels/frame (absolute value).
     * More stable than instantaneous crossing speed; used as the primary power input.
     */
    public final float peakForwardSpeed;

    /** Timing classification relative to the expected impact moment. */
    public final SwingGestureAnalyser.TempoResult tempoResult;

    /** Timing quality 0 (terrible) → 1 (perfect). */
    public final float tempoQuality;

    /** Follow-through arc classification. */
    public final SwingGestureAnalyser.FollowThroughResult followThroughResult;

    /**
     * Normalised follow-through height: +1 = full flip/scoop (bad), 0 = neutral,
     * -1 = fully extended low (good).
     */
    public final float followThroughHeight;

    public SwingResult(
            float contactOffset,
            float pathDeg,
            float peakForwardSpeed,
            SwingGestureAnalyser.TempoResult tempoResult,
            float tempoQuality,
            SwingGestureAnalyser.FollowThroughResult followThroughResult,
            float followThroughHeight) {
        this.contactOffset       = contactOffset;
        this.pathDeg             = pathDeg;
        this.peakForwardSpeed    = peakForwardSpeed;
        this.tempoResult         = tempoResult;
        this.tempoQuality        = tempoQuality;
        this.followThroughResult = followThroughResult;
        this.followThroughHeight = followThroughHeight;
    }

    @Override
    public String toString() {
        return String.format(
            "SwingResult{contact=%.2f path=%.1fdeg peakSpd=%.1f tempo=%s(q=%.2f) ft=%s(h=%.2f)}",
            contactOffset, pathDeg, peakForwardSpeed,
            tempoResult, tempoQuality,
            followThroughResult, followThroughHeight);
    }
}
