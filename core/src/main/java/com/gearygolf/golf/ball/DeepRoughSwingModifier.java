package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.hud.SwingResult;

/**
 * Deep rough-specific swing result overrides.
 *
 * Deep rough has the same tempoSeverity as fairway (1.0) — the difficulty is in
 * achieving good tempo, not in extra punishment once you miss.  The default
 * fat/chunk curves therefore run unchanged.
 *
 * The additional mechanic: a fat strike into deep rough can deflect the ball
 * laterally before impact — long grass catches the hosel and pushes the ball
 * sideways as the club arrives.  This is modelled as contact noise on r.accuracy,
 * scaled by how fat the shot is.  A clean or barely-fat shot sees almost no noise;
 * a deep chunk has significant unpredictable lateral deviation.
 *
 * Noise onset is at q < 0.8 (just past the ideal tempo window), scaling smoothly
 * to ±0.35 accuracy units at q = 0.0.  Perfect and thin shots are unaffected.
 */
class DeepRoughSwingModifier extends TerrainSwingModifier {

    static final DeepRoughSwingModifier INSTANCE = new DeepRoughSwingModifier();
    private DeepRoughSwingModifier() {}

    private static final float NOISE_ONSET = 0.8f;   // q below which grass deflection begins
    private static final float NOISE_MAX   = 0.35f;  // max accuracy noise at q=0.0

    @Override
    public void onAfterLateFat(SwingResult s, MinigameResult r, float q) {
        if (q >= NOISE_ONSET) return;
        float depth     = (NOISE_ONSET - q) / NOISE_ONSET;   // 0 at q=0.8, 1 at q=0.0
        float noiseMag  = depth * NOISE_MAX;
        r.accuracy = MathUtils.clamp(r.accuracy + noiseMag * MathUtils.random(-1f, 1f), -1f, 1f);
    }
}
