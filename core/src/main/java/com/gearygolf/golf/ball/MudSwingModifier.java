package com.gearygolf.golf.ball;

import com.badlogic.gdx.math.MathUtils;
import com.gearygolf.golf.hud.SwingResult;

/**
 * Mud-specific swing result overrides.
 *
 * Mud offers no resistance to the club sinking in — the "chunk" collapse begins
 * earlier than on fairway.  The fat/chunk boundary shifts from q=0.5 to q=0.65:
 * the club starts losing power rapidly sooner, but the curve shape within each
 * zone is the same as the default.  Both zones are continuous at q=0.65.
 *
 * Thin shots (EARLY_THIN) use default fairway curves — hitting thin in mud means
 * the club cleared the surface, so there is no extra terrain interaction.
 */
class MudSwingModifier extends TerrainSwingModifier {

    static final MudSwingModifier INSTANCE = new MudSwingModifier();
    private MudSwingModifier() {}

    private static final float CHUNK_THRESHOLD = 0.65f;

    @Override
    public boolean handleLateFat(SwingResult s, MinigameResult r, float q) {
        if (q >= CHUNK_THRESHOLD) {
            // ── Mud fat zone (q: 1.0→0.65) ───────────────────────────────────
            // Same end values as the default fat zone, but compressed into a narrower q range.
            float t = (1f - q) / (1f - CHUNK_THRESHOLD);  // 0 at q=1.0, 1 at q=0.65
            r.powerMod      = MathUtils.lerp(1.0f,  0.40f, t);
            r.loftDeltaDeg  = MathUtils.lerp(0f,    20f,   t);
            r.tempoSpinMult = MathUtils.lerp(1.0f,  0.40f, t);
        } else {
            // ── Mud chunk zone (q: 0.65→0.0) ─────────────────────────────────
            // Identical shape to default chunk zone.
            float t = 1f - (q / CHUNK_THRESHOLD);          // 0 at q=0.65, 1 at q=0.0
            r.powerMod      = MathUtils.lerp(0.40f, 0.0f,  t);
            r.loftDeltaDeg  = MathUtils.lerp(20f,   45f,   t);
            r.tempoSpinMult = MathUtils.lerp(0.40f, 0.05f, t);
        }
        return true;
    }
}
