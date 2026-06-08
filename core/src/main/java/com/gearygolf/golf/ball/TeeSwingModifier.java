package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.hud.SwingResult;

/**
 * Tee-specific swing result overrides.
 *
 * Fat behaviour is fundamentally different on a tee: the ball is elevated so
 * the club cannot strike the ground first.  Mild fat (q ≥ 0.3) just sweeps a
 * little high and adds loft; only extreme late-fat (q < 0.3) becomes a sky/pop-up.
 * A perfect strike gets a +6° loft bonus and full spin preservation.
 */
class TeeSwingModifier extends TerrainSwingModifier {

    static final TeeSwingModifier INSTANCE = new TeeSwingModifier();
    private TeeSwingModifier() {}

    @Override
    public boolean handleLateFat(SwingResult s, MinigameResult r, float q) {
        if (q >= 0.3f) {
            // ── Tee late zone (q: 1.0→0.3) — no ground contact; club sweeping upward.
            // Loft rises (6° at barely fat → 12° at threshold); spin drops slightly.
            float t = (1f - q) / 0.7f;  // 0 at q=1.0, 1 at q=0.3
            r.powerMod      = 1.0f;
            r.loftDeltaDeg  = MathUtils.lerp(6f,   12f,  t);
            r.tempoSpinMult = MathUtils.lerp(1.0f,  0.6f, t);
        } else {
            // ── Tee sky / pop-up (q: 0.3→0.0) ───────────────────────────────────
            float t = 1f - (q / 0.3f);  // 0 at q=0.3, 1 at q=0.0
            r.powerMod      = MathUtils.lerp(1.0f,  0.30f, t);
            r.loftDeltaDeg  = MathUtils.lerp(0f,    35f,   t);
            r.tempoSpinMult = MathUtils.lerp(1.0f,  0.20f, t);
        }
        return true;
    }

    @Override
    public void onPerfect(SwingResult s, MinigameResult r, float compositeQuality) {
        // Clean tee strike: slight launch boost and full spin.
        r.loftDeltaDeg  = 6f;
        r.tempoSpinMult = 1f;
    }
}
