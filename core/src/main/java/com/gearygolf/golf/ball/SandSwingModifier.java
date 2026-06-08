package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.hud.SwingResult;

/**
 * Sand-specific swing result overrides.
 *
 * Fat is the correct bunker technique — the club enters the sand behind the ball
 * and the sand cushion carries the ball out.  There is therefore no accuracy
 * penalty for fat: path and contact still determine direction as normal.
 *
 * Power decreases continuously from q=1.0 (88% — even the best fat-sand shot
 * loses some energy to the sand) down to 0% at q=0.0 (all sand, ball barely
 * moves).  More sand between club and ball = less power, always.
 *
 * Spin also decreases continuously — sand between the grooves and ball kills spin.
 *
 * Thin in sand (handleEarlyThin) uses the default thin/top curves: blading the
 * ball in a bunker is genuinely disastrous and the standard punishment applies.
 *
 * Perfect tempo uses default fairway behaviour — the difficulty of achieving it
 * (tempoDifficulty = 1.3) is the primary deterrent, not a special outcome penalty.
 */
class SandSwingModifier extends TerrainSwingModifier {

    static final SandSwingModifier INSTANCE = new SandSwingModifier();
    private SandSwingModifier() {}

    @Override
    public boolean handleLateFat(SwingResult s, MinigameResult r, float q) {
        // Power: linear from 88% (barely fat) to 0% (complete chunk).
        // No accuracy change — sand cushion guides the ball regardless of entry point.
        r.powerMod      = q * 0.88f;
        r.loftDeltaDeg  = 0f;
        r.tempoSpinMult = MathUtils.lerp(0.05f, 0.65f, q);
        return true;
    }
}
