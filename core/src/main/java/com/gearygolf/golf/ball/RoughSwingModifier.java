package com.gearygolf.golf.ball;

import com.gearygolf.golf.hud.SwingResult;
import com.gearygolf.golf.terrain.Terrain;

/**
 * Rough-specific swing result softening.
 *
 * The grass absorbs some of the punishment from a fat or thin shot — you can
 * still get the club through, just with reduced efficiency.  The default fat/chunk
 * curves run first (via ShotController), then this modifier scales the penalties
 * back using the terrain's tempoSeverity (0.75 = 75% of normal punishment).
 *
 * "Penalty" is defined as the gap from the neutral value:
 *   power penalty  = 1 - powerMod       → scaled by severity → powerMod raised
 *   loft addition  = loftDeltaDeg       → scaled by severity → addition reduced
 *   spin penalty   = 1 - tempoSpinMult  → scaled by severity → spinMult raised
 */
class RoughSwingModifier extends TerrainSwingModifier {

    static final RoughSwingModifier INSTANCE = new RoughSwingModifier();
    private RoughSwingModifier() {}

    private static final float SEVERITY = (float) Terrain.TerrainType.ROUGH.tempoSeverity; // 0.75

    @Override
    public void onAfterLateFat(SwingResult s, MinigameResult r, float q) {
        r.powerMod      = 1f - (1f - r.powerMod)      * SEVERITY;
        r.loftDeltaDeg *=                               SEVERITY;
        r.tempoSpinMult = 1f - (1f - r.tempoSpinMult) * SEVERITY;
    }
}
