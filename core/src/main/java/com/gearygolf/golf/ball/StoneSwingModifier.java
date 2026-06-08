package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.hud.SwingResult;

/**
 * Stone-specific swing result overrides.
 *
 * The hard surface cannot be dug into, so power loss from fat is minimal across
 * the whole q range.  Instead the failure mode is directional: the club face
 * deflects off the rock at an unpredictable angle, adding accuracy scatter that
 * grows continuously through the fat range.  The gradient steepens below q=0.7
 * (where contact becomes a real deflection rather than a light skid) but there is
 * no discontinuity — the scatter magnitude is the same at q=0.7 from both sides.
 *
 * Power curve: quadratic, stays near 1.0 at high q (skid preserves energy),
 * floors at ~0.5 at extreme chunk (shock to the hosel still costs something).
 *
 * Scatter zones:
 *   q ≥ 0.7  gentle: 0% at q=1.0 → 8% at q=0.7   (gradient ≈ 0.27%/Δq)
 *   q <  0.7  steep:  8% at q=0.7 → 50% at q=0.0  (gradient ≈ 0.60%/Δq)
 */
class StoneSwingModifier extends TerrainSwingModifier {

    static final StoneSwingModifier INSTANCE = new StoneSwingModifier();
    private StoneSwingModifier() {}

    @Override
    public boolean handleLateFat(SwingResult s, MinigameResult r, float q) {
        // Power: quadratic decay — stays near full for high q, floors at 0.5.
        float fatT = 1f - q;
        r.powerMod = 1.0f - (fatT * fatT * 0.50f);

        // Loft: small amount from deflection angle, far less than a ground dig.
        r.loftDeltaDeg = MathUtils.lerp(0f, 8f, fatT);

        // Spin: hard surface gives clean grooves on good contact; fat kills spin.
        r.tempoSpinMult = MathUtils.lerp(0.1f, 0.9f, q);

        // Accuracy scatter: two-zone, continuous at q=0.7.
        float scatterMag;
        if (q >= 0.7f) {
            float t = (1f - q) / 0.3f;          // 0→1 as q: 1.0→0.7
            scatterMag = MathUtils.lerp(0f, 0.08f, t);
        } else {
            float t = 1f - (q / 0.7f);           // 0→1 as q: 0.7→0.0
            scatterMag = MathUtils.lerp(0.08f, 0.50f, t);
        }
        r.accuracy = MathUtils.clamp(r.accuracy + scatterMag * MathUtils.random(-1f, 1f), -1f, 1f);

        return true;
    }

    @Override
    public void onPerfect(SwingResult s, MinigameResult r, float compositeQuality) {
        // Clean stone lie — no grass between face and ball, slight spin bonus.
        r.tempoSpinMult = Math.min(r.tempoSpinMult + 0.05f, 1.0f);
    }
}
