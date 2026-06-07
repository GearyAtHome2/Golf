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
     * Signed follow-through angle in degrees: the angle between the projected path
     * reference line and the vector from the crossing point to where the pointer stopped.
     * Positive = flip side (above path line), negative = good extension (below path line).
     */
    public final float followThroughAngleDeg;

    /**
     * Normalised backswing length: 0 = no backswing, 1.0 = full backswing reaching the
     * reference line. Determines the power ceiling for this swing (short = 60% max power,
     * full = 100% max power).
     */
    public final float backswingNorm;

    /**
     * Attack angle in degrees. Positive = ascending blow (driver off tee), negative =
     * descending (steep iron). Combines the club's natural arc-bottom angle with any manual
     * ball-placement adjustment the player made before the swing.
     */
    public final float attackAngleDeg;

    public SwingResult(
            float contactOffset,
            float pathDeg,
            float peakForwardSpeed,
            SwingGestureAnalyser.TempoResult tempoResult,
            float tempoQuality,
            SwingGestureAnalyser.FollowThroughResult followThroughResult,
            float followThroughAngleDeg,
            float backswingNorm,
            float attackAngleDeg) {
        this.contactOffset       = contactOffset;
        this.pathDeg             = pathDeg;
        this.peakForwardSpeed    = peakForwardSpeed;
        this.tempoResult         = tempoResult;
        this.tempoQuality        = tempoQuality;
        this.followThroughResult   = followThroughResult;
        this.followThroughAngleDeg = followThroughAngleDeg;
        this.backswingNorm       = backswingNorm;
        this.attackAngleDeg      = attackAngleDeg;
    }

    @Override
    public String toString() {
        return String.format(
            "SwingResult{contact=%.2f path=%.1fdeg peakSpd=%.1f tempo=%s(q=%.2f) ft=%s(h=%.2f) bk=%.2f atk=%.1fdeg}",
            contactOffset, pathDeg, peakForwardSpeed,
            tempoResult, tempoQuality,
            followThroughResult, followThroughAngleDeg, backswingNorm, attackAngleDeg);
    }
}
